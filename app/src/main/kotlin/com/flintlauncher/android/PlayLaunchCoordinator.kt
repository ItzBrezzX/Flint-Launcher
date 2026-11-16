package com.flintlauncher.android

import com.flintlauncher.android.account.AccountManager
import com.flintlauncher.android.account.AccountType
import com.flintlauncher.android.account.FlintAccount
import com.flintlauncher.android.auth.AuthException
import com.flintlauncher.android.auth.MicrosoftAuthClient
import com.flintlauncher.android.installer.MinecraftInstaller
import com.flintlauncher.android.installer.NativesExtractor
import com.flintlauncher.android.minecraft.manifest.VersionType
import com.flintlauncher.android.minecraft.version.Environment
import com.flintlauncher.android.minecraft.version.LaunchCommandBuilder
import com.flintlauncher.android.minecraft.version.LaunchContext
import com.flintlauncher.android.nativebridge.NativeBridge
import com.flintlauncher.android.runtime.AndroidLwjglNativesInstaller
import com.flintlauncher.android.runtime.GameLauncher
import com.flintlauncher.android.runtime.KnownJreArtifacts
import com.flintlauncher.android.runtime.LaunchRequest
import com.flintlauncher.android.runtime.LaunchResult
import com.flintlauncher.android.runtime.MobileJreManager
import com.flintlauncher.android.runtime.PojavStyleGameLauncher
import com.flintlauncher.android.runtime.RendererInfo
import com.flintlauncher.android.runtime.RendererKind
import kotlinx.coroutines.CancellationException
import java.io.File
import java.security.MessageDigest

/**
 * Prepares a real Minecraft launch request and delegates it to the Pojav-style in-process JVM.
 *
 * Microsoft accounts exchange the stored refresh token for a Minecraft Services access token.
 * Offline accounts use the standard offline identity. Neither path invents a credential.
 */
class PlayLaunchCoordinator(
    private val installer: MinecraftInstaller,
    private val accounts: AccountManager,
    private val runtimeManager: MobileJreManager?,
    private val deviceArch: String?,
    private val gameRoot: File,
    private val cacheDir: File,
    private val nativeLibraryDir: File,
    private val launcherVersion: String,
    private val microsoftAccounts: MicrosoftAccountStore? = null,
    private val microsoftAuth: MicrosoftAuthClient? = null,
) {
    private val gameLauncher: GameLauncher? = runtimeManager?.let(::PojavStyleGameLauncher)

    suspend fun launch(versionId: String): LaunchResult {
        val account = try {
            accounts.selected()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return LaunchResult.Failed("Could not read the selected account: ${error.message ?: "storage error"}")
        } ?: return LaunchResult.BlockedNotReady

        val session = try {
            resolveSession(account)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: AuthException) {
            return LaunchResult.Failed(error.error.userMessage)
        } catch (error: Exception) {
            return LaunchResult.Failed("Could not create a Minecraft session: ${error.message ?: "authentication failed"}")
        }

        val manager = runtimeManager ?: return LaunchResult.BlockedMissingRuntime
        val arch = deviceArch ?: return LaunchResult.Failed("This APK process ABI is not supported by the Java runtime catalog.")
        val launcher = gameLauncher ?: return LaunchResult.BlockedMissingRuntime

        if (!NativeBridge.isNativeLoaded) {
            return LaunchResult.Failed(
                "Flint native bridge is unavailable: ${NativeBridge.nativeLoadError ?: "library load failed"}.",
            )
        }

        // The APK must contain the GL4ES shared library. The launch path stages its Android
        // ELF libraries into the per-game LWJGL search directory after native extraction.
        val gl4esLibrary = File(nativeLibraryDir, "libGL.so")
        if (!gl4esLibrary.isFile) {
            return LaunchResult.BlockedMissingRenderer
        }

        val installed = try {
            installer.loadInstalledVersion(versionId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return LaunchResult.Failed("Could not prepare Minecraft $versionId: ${error.message ?: "installation metadata error"}")
        } ?: return LaunchResult.BlockedNotReady

        val runtime = try {
            manager.findCompatible(installed.requiredJavaMajor, arch)
                ?: run {
                    val targetMajor = KnownJreArtifacts.compatibleMajor(installed.requiredJavaMajor, arch)
                        ?: return LaunchResult.Failed(
                            "No published Java runtime for ABI $arch satisfies Java ${installed.requiredJavaMajor}.",
                        )
                    manager.install(targetMajor).getOrElse { error ->
                        return LaunchResult.Failed(
                            "Could not install Java $targetMajor: ${error.message ?: "download or verification failed"}",
                        )
                    }
                }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return LaunchResult.Failed("Could not prepare Java runtime: ${error.message ?: "unknown error"}")
        }

        val runtimeVerified = try {
            manager.verify(runtime)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return LaunchResult.Failed("Java runtime verification failed: ${error.message ?: "unknown error"}")
        }
        if (!runtimeVerified) {
            return LaunchResult.Failed("Java ${runtime.majorVersion} (${runtime.arch}) failed the native JLI verification.")
        }

        val gameDirectory = File(File(gameRoot, "profiles"), directoryKey(account.id))
        if (!gameDirectory.isDirectory && !gameDirectory.mkdirs()) {
            return LaunchResult.Failed("Could not create the game directory: ${gameDirectory.absolutePath}")
        }
        if (!gameDirectory.canWrite()) {
            return LaunchResult.Failed("The game directory is not writable: ${gameDirectory.absolutePath}")
        }

        val nativeDirectory = File(File(cacheDir, "minecraft-natives"), directoryKey("$versionId:${account.id}"))
        try {
            if (nativeDirectory.exists() && !nativeDirectory.deleteRecursively()) {
                return LaunchResult.Failed("Could not clear the previous native extraction directory.")
            }
            if (!nativeDirectory.mkdirs() && !nativeDirectory.isDirectory) {
                return LaunchResult.Failed("Could not create the native extraction directory.")
            }
            // Keep desktop Linux .so files out of this Android load path, then stage
            // version-pinned Bionic LWJGL/GLFW/GL4ES binaries from the installed APK.
            NativesExtractor.extract(installed.nativeJars, nativeDirectory, excludeSharedObjects = true)
            AndroidLwjglNativesInstaller.install(nativeLibraryDir, nativeDirectory, arch)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return LaunchResult.Failed("Could not extract Minecraft native libraries: ${error.message ?: "invalid native archive"}")
        }

        val classpath = installed.classpath.joinToString(File.pathSeparator) { it.absolutePath }
        val environment = Environment(
            osName = "linux",
            osArch = arch,
        )
        val context = LaunchContext(
            playerName = session.username,
            uuid = session.uuid,
            accessToken = session.accessToken,
            userType = session.userType,
            versionName = installed.version.id,
            versionType = when (installed.version.type) {
                VersionType.RELEASE -> "release"
                VersionType.SNAPSHOT -> "snapshot"
                VersionType.OLD_BETA -> "old_beta"
                VersionType.OLD_ALPHA -> "old_alpha"
                VersionType.UNKNOWN -> "unknown"
            },
            gameDirectory = gameDirectory.absolutePath,
            assetsRoot = installed.assetsRoot.absolutePath,
            assetsIndexName = installed.assetIndexId,
            nativesDirectory = nativeDirectory.absolutePath,
            libraryDirectory = File(gameRoot, "libraries").absolutePath,
            classpath = classpath,
            launcherVersion = launcherVersion.ifBlank { "dev" },
        )

        val extraJvmArguments = buildList {
            add("-Djava.library.path=${nativeDirectory.absolutePath}")
            add("-Dorg.lwjgl.librarypath=${nativeDirectory.absolutePath}")
            add("-Djava.io.tmpdir=${File(cacheDir, "minecraft-tmp").absolutePath}")
            add("-Duser.home=${gameDirectory.absolutePath}")
            installed.loggingJvmArgument?.let { add(it) }
        }

        val command = try {
            LaunchCommandBuilder.build(
                version = installed.version,
                context = context,
                environment = environment,
                extraJvmArguments = extraJvmArguments,
            )
        } catch (error: Exception) {
            return LaunchResult.Failed("Could not build Minecraft launch arguments: ${error.message ?: "invalid version metadata"}")
        }

        val renderer = RendererInfo(
            id = "gl4es",
            kind = RendererKind.GL4ES,
            displayName = "GL4ES",
            available = gl4esLibrary.isFile,
            notes = "Android GLFW/LWJGL native libraries are staged; production EGL context hand-off is still pending.",
        )
        val request = LaunchRequest(
            versionId = installed.versionId,
            gameDir = gameDirectory,
            assetsDir = installed.assetsRoot,
            classpath = installed.classpath,
            nativeDir = nativeDirectory,
            mainClass = command.mainClass,
            jvmArgs = command.jvmArguments,
            gameArgs = command.gameArguments,
            accountUsername = session.username,
            accountUuid = session.uuid,
            accessToken = session.accessToken,
            userType = session.userType,
            runtime = runtime,
            renderer = renderer,
        )
        return launcher.launch(request)
    }

    private data class LaunchSession(
        val username: String,
        val uuid: String,
        val accessToken: String,
        val userType: String,
    )

    private suspend fun resolveSession(account: FlintAccount): LaunchSession {
        if (account.type != AccountType.MICROSOFT) {
            return LaunchSession(
                username = account.username,
                uuid = account.uuid.replace("-", ""),
                accessToken = "0",
                userType = "legacy",
            )
        }
        val auth = microsoftAuth ?: throw IllegalStateException(
            "Microsoft sign-in is not configured. Set flintMsaClientId to a Mojang-approved Azure application id before launching with this account.",
        )
        val store = microsoftAccounts ?: throw IllegalStateException(
            "Microsoft account storage is not available.",
        )
        val refreshToken = store.refreshToken(account.id)
        val tokens = auth.refresh(refreshToken)
        val rotated = tokens.refreshToken
        if (!rotated.isNullOrBlank() && rotated != refreshToken) {
            store.updateRefreshToken(account.id, rotated)
        }
        val minecraft = auth.loginMinecraft(tokens.accessToken)
        if (minecraft.accessToken.isBlank() || minecraft.profile.id.isBlank() || minecraft.profile.name.isBlank()) {
            throw IllegalStateException("Minecraft Services did not return a usable session.")
        }
        return LaunchSession(
            username = minecraft.profile.name,
            uuid = minecraft.profile.id.replace("-", ""),
            accessToken = minecraft.accessToken,
            userType = "msa",
        )
    }

    private fun directoryKey(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { "%02x".format(it) }
}
