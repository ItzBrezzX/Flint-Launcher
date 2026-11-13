package com.flintlauncher.android.runtime

import com.flintlauncher.android.nativebridge.NativeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Game launcher following the PojavLauncher in-process JVM model:
 * load mobile OpenJDK via dlopen → set env → call JLI_Launch through JNI.
 *
 * Until a real ARM64 JRE is installed and the native library is built,
 * every launch attempt returns a precise failure instead of pretending success.
 */
class PojavStyleGameLauncher(
    private val runtimeManager: JavaRuntimeManager,
) : GameLauncher {

    @Volatile private var running = false
    private val logBuffer = StringBuilder()

    override suspend fun launch(request: LaunchRequest): LaunchResult = withContext(Dispatchers.IO) {
        if (!NativeBridge.isNativeLoaded) {
            return@withContext LaunchResult.Failed(
                "Native library flint_native not loaded: ${NativeBridge.nativeLoadError}. " +
                    "Build the :native module with NDK/CMake first.",
            )
        }

        val runtime = request.runtime
        if (!runtime.verified) {
            return@withContext LaunchResult.BlockedMissingRuntime
        }

        val loaded = NativeBridge.nativeLoadJre(runtime.homeDir.absolutePath)
        if (!loaded) {
            return@withContext LaunchResult.Failed(
                "Failed to dlopen libjli.so under ${runtime.homeDir}. " +
                    "Is this a mobile OpenJDK layout with aarch64 libs?",
            )
        }

        // Build argv the way JLI_Launch expects (program name first)
        val args = mutableListOf<String>()
        args += "java"
        args += request.jvmArgs
        args += "-cp"
        args += request.classpath.joinToString(File.pathSeparator) { it.absolutePath }
        args += request.mainClass
        args += request.gameArgs

        // Account identity (offline or Microsoft)
        // Already expected to be present in gameArgs / jvmArgs from the caller.

        logBuffer.appendLine("Launch argv (${args.size} entries):")
        args.take(12).forEach { logBuffer.appendLine("  $it") }
        if (args.size > 12) logBuffer.appendLine("  ...")

        running = true
        val code = try {
            NativeBridge.nativeLaunchJvm(args.toTypedArray())
        } catch (t: Throwable) {
            running = false
            NativeBridge.nativeShutdown()
            return@withContext LaunchResult.Failed("JNI launch threw: ${t.message}", logBuffer.toString())
        }

        running = false
        NativeBridge.nativeShutdown()

        if (code == -2) {
            // Expected until full JLI path is enabled
            return@withContext LaunchResult.Failed(
                "JLI_Launch path not fully enabled in this Spike 0 build. " +
                    "Native load of libjli succeeded; complete argv/signal handling next. " +
                    "Exit code=$code",
                logBuffer.toString(),
            )
        }
        if (code != 0) {
            return@withContext LaunchResult.Failed("JVM exited with code $code", logBuffer.toString())
        }
        LaunchResult.Started(pid = null, logFile = null)
    }

    override suspend fun isRunning(): Boolean = running
    override suspend fun stop() {
        running = false
        NativeBridge.nativeShutdown()
    }

    override fun stdout(): String = logBuffer.toString()
    override fun stderr(): String = logBuffer.toString()
}
