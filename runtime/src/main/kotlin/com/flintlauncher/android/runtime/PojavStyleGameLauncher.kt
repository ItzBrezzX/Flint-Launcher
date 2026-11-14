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
                "JRE native loader failed: ${NativeBridge.safeJreStatus()}",
            )
        }

        // Build argv the way JLI_Launch expects (program name first)
        val args = mutableListOf<String>()
        args += "java"
        // LaunchCommandBuilder has already normalized the metadata classpath
        // to exactly one -cp entry; appending another here corrupts modern argv.
        args += request.jvmArgs
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
        if (running) {
            logBuffer.appendLine("Stop requested, but an in-process JLI invocation cannot be safely interrupted yet.")
        }
    }

    override fun stdout(): String = logBuffer.toString()
    override fun stderr(): String = logBuffer.toString()
}
