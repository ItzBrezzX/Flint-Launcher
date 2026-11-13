package com.flintlauncher.android.runtime

import com.flintlauncher.android.nativebridge.NativeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Flint in-process JVM bootstrap:
 * load mobile OpenJDK libraries → configure runtime paths → call JLI_Launch through JNI.
 *
 * The native bridge reports a completed JLI invocation as Exited, never as
 * Started: JLI_Launch is synchronous here and returns after the Java main
 * method exits. A persistent game-session/window lifecycle is a later milestone.
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

        if (code != 0) {
            return@withContext LaunchResult.Failed("JLI_Launch exited with code $code", logBuffer.toString())
        }
        LaunchResult.Exited(exitCode = code)
    }

    override suspend fun isRunning(): Boolean = running
    override suspend fun stop() {
        running = false
        NativeBridge.nativeShutdown()
    }

    override fun stdout(): String = logBuffer.toString()
    override fun stderr(): String = logBuffer.toString()
}
