package com.flintlauncher.android

import com.flintlauncher.android.account.AccountManager
import com.flintlauncher.android.account.AccountType
import com.flintlauncher.android.auth.AuthError
import com.flintlauncher.android.auth.AuthException
import com.flintlauncher.android.installer.MinecraftInstaller
import com.flintlauncher.android.installer.NativesExtractor
import com.flintlauncher.android.minecraft.manifest.VersionType
import com.flintlauncher.android.minecraft.version.Environment
import com.flintlauncher.android.minecraft.version.LaunchCommandBuilder
import com.flintlauncher.android.minecraft.version.LaunchContext
import com.flintlauncher.android.nativebridge.NativeBridge
import com.flintlauncher.android.runtime.GameLauncher
import com.flintlauncher.android.runtime.JvmEnvironment
import com.flintlauncher.android.runtime.KnownJreArtifacts
import com.flintlauncher.android.runtime.LaunchRequest
import com.flintlauncher.android.runtime.LwjglAndroid
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
 * Microsoft accounts trade their stored refresh token for a fresh Minecraft access token right
 * before launch (see [MinecraftSessionProvider]). Offline accounts use the standard offline
 * identity and never receive a fabricated Microsoft credential.
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
    private val microsoftSessions: MinecraftSessionProvider? = null,
    /** Jars of LWJGL built for Android (extracted from the APK). Empty when the APK has none. */
    private val androidLwjglJars: () -> List<File> = { emptyList() },
    /** Where the JVM's stdout/stderr go. Null disables redirection. */
    private val gameLogFile: File? = null,
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

        if (account.type == AccountType.MICROSOFT && microsoftSessions == null) {
            return LaunchResult.Failed(AuthError.ClientIdMissing.userMessage)
        }

        val manager = runtimeManager ?: return LaunchResult.BlockedMissingRuntime
        val arch = deviceArch ?: return LaunchResult.Failed("This APK process ABI is not supported by the Java runtime catalog.")
        val launcher = gameLauncher ?: return LaunchResult.BlockedMissingRuntime

        if (!NativeBridge.isNativeLoaded) {
            return LaunchResult.Failed(
                "Flint native bridge is unavailable: ${NativeBridge.nativeLoadError ?: "library load failed"}.",
            )
        }

        // The APK must contain the GL4ES shared library. This verifies the packaged renderer
        // artifact, not a live LWJGL/Android-GLFW window; that hand-off remains a separate step.
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
            NativesExtractor.extract(installed.nativeJars, nativeDirectory)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return LaunchResult.Failed("Could not extract Minecraft native libraries: ${error.message ?: "invalid native archive"}")
        }

        // Fetched this late on purpose: the Java runtime download above can take minutes and a
        // Minecraft access token should be as fresh as possible when the game starts.
        val identity: LaunchIdentity = if (account.type == AccountType.MICROSOFT) {
            val provider = microsoftSessions ?: return LaunchResult.Failed(AuthError.ClientIdMissing.userMessage)
            val session = try {
                provider.sessionFor(account)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: AuthException) {
                return LaunchResult.Failed(error.error.userMessage)
            } catch (error: Exception) {
                return LaunchResult.Failed("Microsoft sign-in failed: ${error.message ?: "unknown error"}")
            }
            LaunchIdentity(
                username = session.profile.name,
                uuid = account.uuid,
                accessToken = session.accessToken,
                userType = "msa",
            )
        } else {
            LaunchIdentity(account.username, account.uuid, accessToken = "0", userType = "legacy")
        }

        val launchClasspath = when (val swap = LwjglAndroid.substitute(installed.classpath, androidLwjglJars())) {
            is LwjglAndroid.Substitution.Ready -> swap.classpath
            is LwjglAndroid.Substitution.Unsupported -> return LaunchResult.Failed(swap.reason)
        }
        val classpath = launchClasspath.joinToString(File.pathSeparator) { it.absolutePath }
        val environment = Environment(
            osName = "linux",
            osArch = arch,
        )
        val context = LaunchContext(
            playerName = identity.username,
            uuid = identity.uuid.replace("-", ""),
            accessToken = identity.accessToken,
            userType = identity.userType,
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
            add("-Djava.library.path=${nativeLibraryDir.absolutePath}${File.pathSeparator}${nativeDirectory.absolutePath}")
            addAll(LwjglAndroid.jvmArguments(nativeLibraryDir))
            add("-Djava.io.tmpdir=${File(cacheDir, "minecraft-tmp").absolutePath}")
            add("-Duser.home=${gameDirectory.absolutePath}")
            installed.loggingJvmArgument?.let { add(it) }
        }

        File(cacheDir, "minecraft-tmp").mkdirs()

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
            notes = "GL4ES behind Flint's own GLFW shim (libglfw.so). Not verified on a device yet.",
        )
        val request = LaunchRequest(
            versionId = installed.versionId,
            gameDir = gameDirectory,
            assetsDir = installed.assetsRoot,
            classpath = launchClasspath,
            nativeDir = nativeDirectory,
            mainClass = command.mainClass,
            jvmArgs = command.jvmArguments,
            gameArgs = command.gameArguments,
            accountUsername = identity.username,
            accountUuid = identity.uuid,
            accessToken = identity.accessToken,
            userType = identity.userType,
            runtime = runtime,
            renderer = renderer,
            environment = JvmEnvironment.build(
                runtimeHome = runtime.homeDir,
                gameNativeDirectory = nativeDirectory,
                appNativeLibraryDirectory = nativeLibraryDir,
                gameDirectory = gameDirectory,
                temporaryDirectory = File(cacheDir, "minecraft-tmp"),
                inheritedLdLibraryPath = System.getenv("LD_LIBRARY_PATH"),
            ),
            stdioLogFile = gameLogFile,
        )
        return launcher.launch(request)
    }

    private fun directoryKey(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { "%02x".format(it) }
}

/** The player identity handed to Minecraft. [accessToken] must never be logged. */
private data class LaunchIdentity(
    val username: String,
    val uuid: String,
    val accessToken: String,
    val userType: String,
) {
    override fun toString() = "LaunchIdentity(username=$username, userType=$userType)"
}
