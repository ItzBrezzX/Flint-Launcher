package com.flintlauncher.android

import com.flintlauncher.android.account.AccountManager
import com.flintlauncher.android.account.AccountType
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
import android.view.Surface
import kotlinx.coroutines.CancellationException
import java.io.File
import java.security.MessageDigest

/**
 * Prepares a real Minecraft launch request and delegates it to the Pojav-style in-process JVM.
 *
 * This is deliberately conservative: Microsoft accounts are blocked until Flint can exchange
 * their stored refresh token for a live Minecraft access token. Offline accounts use the
 * standard offline identity and never receive a fabricated Microsoft credential.
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
) {
    private val gameLauncher: GameLauncher? = runtimeManager?.let(::PojavStyleGameLauncher)

    /** A game launch must be bound to the Activity-owned Android Surface. */
    suspend fun launch(versionId: String): LaunchResult =
        LaunchResult.Failed("A live Android game Surface is required. Open Minecraft from the PLAY screen.")

    suspend fun isGameRunning(): Boolean = gameLauncher?.isRunning() == true

    suspend fun gameLogs(): String = gameLauncher?.stdout().orEmpty()

    suspend fun stopGame() {
        gameLauncher?.stop()
    }

    suspend fun launch(
        versionId: String,
        surface: Surface,
        width: Int,
        height: Int,
    ): LaunchResult {
        if (!surface.isValid) return LaunchResult.Failed("The Android game Surface is no longer valid.")
        if (width <= 0 || height <= 0) {
            return LaunchResult.Failed("The Android game Surface has invalid dimensions: ${width}x${height}.")
        }
        val account = try {
            accounts.selected()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return LaunchResult.Failed("Could not read the selected account: ${error.message ?: "storage error"}")
        } ?: return LaunchResult.BlockedNotReady

        if (account.type == AccountType.MICROSOFT) {
            return LaunchResult.Failed(
                "Microsoft account is selected, but access-token refresh and Minecraft session exchange are not connected yet. Select an offline account or finish Microsoft authentication first.",
            )
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
        val glfwLibrary = File(nativeLibraryDir, "libglfw.so")
        if (!glfwLibrary.isFile) {
            return LaunchResult.Failed("Flint's Android GLFW C ABI is missing from the APK: ${glfwLibrary.absolutePath}")
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
            // only LWJGL JNI libraries. GLFW and GL4ES are already loaded from the APK
            // by libflint_native; reloading copies from this directory would duplicate
            // their process-global state and split the EGL/GLFW session.
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
            playerName = account.username,
            uuid = account.uuid.replace("-", ""),
            accessToken = "0",
            userType = "legacy",
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
            // Keep the GLFW and GL4ES mappings pointed at the exact same APK
            // library files already loaded by libflint_native. This prevents a
            // second copy from being loaded from the per-game native directory.
            add("-Dorg.lwjgl.glfw.libname=${glfwLibrary.absolutePath}")
            add("-Dorg.lwjgl.opengl.libname=${gl4esLibrary.absolutePath}")
            // Android uses these sonames instead of desktop Linux's versioned EGL/GLES names.
            add("-Dorg.lwjgl.egl.libname=libEGL.so")
            add("-Dorg.lwjgl.opengles.libname=libGLESv2.so")
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
            notes = "LWJGL JNI libraries are staged; the APK's GLFW and GL4ES instances are shared with the game thread.",
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
            accountUsername = account.username,
            accountUuid = account.uuid,
            accessToken = "0",
            userType = "legacy",
            runtime = runtime,
            renderer = renderer,
            windowSurface = surface,
            windowWidth = width,
            windowHeight = height,
            rendererLibrary = gl4esLibrary,
        )
        return launcher.launch(request)
    }

    private fun directoryKey(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { "%02x".format(it) }
}
