package com.flintlauncher.android.runtime

import com.flintlauncher.android.nativebridge.NativeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Game launcher following the PojavLauncher in-process JVM model:
 * load mobile OpenJDK via dlopen → call JLI_Launch through JNI.
 *
 * JLI_Launch is synchronous: it returns after the Java main class exits.
 * Therefore a zero exit code is a completed game process, not proof that a
 * playable session is still running. Renderer/window integration is separate.
 */
class PojavStyleGameLauncher(
    private val runtimeManager: JavaRuntimeManager,
) : GameLauncher {

    @Volatile private var running = false
    private val logBuffer = StringBuilder()

    override suspend fun launch(request: LaunchRequest): LaunchResult = withContext(Dispatchers.IO) {
        logBuffer.setLength(0)

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

        // Re-check the runtime at launch time. A stale metadata flag must not
        // authorize a launch after files have been moved or corrupted.
        if (!runtimeManager.verify(runtime)) {
            return@withContext LaunchResult.Failed(
                "Java runtime verification failed immediately before launch: ${runtime.homeDir}",
            )
        }

        if (!request.gameDir.isDirectory || !request.gameDir.canWrite()) {
            return@withContext LaunchResult.Failed("Minecraft game directory is missing or not writable.")
        }
        if (!request.assetsDir.isDirectory) {
            return@withContext LaunchResult.Failed("Minecraft assets directory is missing.")
        }
        if (request.mainClass.isBlank()) {
            return@withContext LaunchResult.Failed("Minecraft main class is empty.")
        }
        val missingClasspath = request.classpath.filterNot { it.isFile }
        if (request.classpath.isEmpty() || missingClasspath.isNotEmpty()) {
            return@withContext LaunchResult.Failed(
                "Minecraft classpath is incomplete: ${missingClasspath.size} missing entries.",
                missingClasspath.take(8).joinToString("\n") { it.absolutePath },
            )
        }

        val loaded = NativeBridge.nativeLoadJre(runtime.homeDir.absolutePath)
        if (!loaded) {
            return@withContext LaunchResult.Failed(
                "Failed to load libjli.so under ${runtime.homeDir}. " +
                    "Check the Android mobile OpenJDK layout and ABI.",
            )
        }

        // JLI argv starts with the program name. Account arguments are supplied
        // by the caller through gameArgs / jvmArgs.
        val args = mutableListOf<String>()
        args += "java"
        args += request.jvmArgs
        args += "-cp"
        args += request.classpath.joinToString(File.pathSeparator) { it.absolutePath }
        args += request.mainClass
        args += request.gameArgs

        logBuffer.appendLine("Launch argv (${args.size} entries):")
        args.take(12).forEach { logBuffer.appendLine("  $it") }
        if (args.size > 12) logBuffer.appendLine("  ...")

        running = true
        val code = try {
            NativeBridge.nativeLaunchJvm(args.toTypedArray())
        } catch (t: Throwable) {
            running = false
            NativeBridge.nativeShutdown()
            return@withContext LaunchResult.Failed(
                "JNI launch threw: ${t.message}",
                logBuffer.toString(),
            )
        }

        // JLI_Launch has returned, so the in-process Java main method has exited.
        // Do not report Started: that would make PLAY appear successful without
        // a live Minecraft session or an attached game surface.
        running = false
        NativeBridge.nativeShutdown()
        LaunchResult.Failed(
            if (code == 0) {
                "Minecraft JVM exited normally (exit code 0); no live game session remains. " +
                    "The Android window/input bridge is not yet integrated."
            } else {
                "Minecraft JVM exited with code $code."
            },
            logBuffer.toString(),
        )
    }

    override suspend fun isRunning(): Boolean = running

    override suspend fun stop() {
        running = false
        NativeBridge.nativeShutdown()
    }

    override fun stdout(): String = logBuffer.toString()
    override fun stderr(): String = logBuffer.toString()
}
