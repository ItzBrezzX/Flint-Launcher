package com.flintlauncher.android.runtime

import com.flintlauncher.android.nativebridge.NativeBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Starts the embedded mobile JVM without blocking the caller until Minecraft exits.
 *
 * JLI_Launch runs on a dedicated native worker. Kotlin observes its state and
 * reaps the worker after exit. The embedded JVM is process-scoped and cannot
 * safely be unloaded or restarted inside the same Android process.
 */
class PojavStyleGameLauncher(
    private val runtimeManager: JavaRuntimeManager,
) : GameLauncher {

    @Volatile private var running = false
    private val logBuffer = StringBuilder()
    private val launchMutex = Mutex()
    private val logLock = Any()
    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var monitorJob: Job? = null

    override suspend fun launch(request: LaunchRequest): LaunchResult = launchMutex.withLock {
        withContext(Dispatchers.IO) {
        synchronized(logLock) { logBuffer.setLength(0) }

        if (!NativeBridge.isNativeLoaded) {
            return@withContext LaunchResult.Failed(
                "Native library flint_native not loaded: ${NativeBridge.nativeLoadError}. " +
                    "Build the :native module with NDK/CMake first.",
            )
        }

        when (NativeBridge.nativeJvmState()) {
            JVM_STARTING, JVM_RUNNING -> {
                running = true
                return@withContext LaunchResult.Failed("A Minecraft JVM session is already active.")
            }
            JVM_EXITED, JVM_FAILED -> {
                NativeBridge.nativeShutdown()
                return@withContext LaunchResult.Failed(
                    "The embedded JVM has already completed or failed. Android must recreate the " +
                        "launcher process before another in-process JVM can be started.",
                    stdout(),
                )
            }
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

        appendLog("Launch argv (${args.size} entries):")
        args.take(12).forEach { appendLog("  $it") }
        if (args.size > 12) appendLog("  ...")
        appendLog("Starting JLI_Launch asynchronously on native worker thread.")

        val started = try {
            NativeBridge.nativeStartJvm(args.toTypedArray())
        } catch (t: Throwable) {
            appendLog("JNI start exception: ${t.message}")
            false
        }

        if (!started) {
            running = false
            val state = runCatching { NativeBridge.nativeJvmState() }.getOrDefault(JVM_FAILED)
            NativeBridge.nativeShutdown()
            return@withContext LaunchResult.Failed(
                "Native JVM worker could not start (state=$state). " +
                    "Check the JRE ABI, libjli dependencies and native logs.",
                stdout(),
            )
        }

        running = true
        startSessionMonitor()
        LaunchResult.Started(pid = null, logFile = null)
        }
    }

    private fun startSessionMonitor() {
        monitorJob?.cancel()
        monitorJob = monitorScope.launch {
            while (true) {
                when (runCatching { NativeBridge.nativeJvmState() }.getOrDefault(JVM_FAILED)) {
                    JVM_STARTING, JVM_RUNNING -> delay(250)
                    JVM_EXITED -> {
                        running = false
                        val exitCode = runCatching { NativeBridge.nativeJvmExitCode() }.getOrDefault(-1)
                        appendLog("Minecraft JVM exited with code $exitCode.")
                        NativeBridge.nativeShutdown()
                        break
                    }
                    JVM_FAILED -> {
                        running = false
                        appendLog("Native JVM worker entered FAILED state.")
                        NativeBridge.nativeShutdown()
                        break
                    }
                    else -> {
                        running = false
                        break
                    }
                }
            }
        }
    }

    override suspend fun isRunning(): Boolean {
        if (!NativeBridge.isNativeLoaded) return false
        return when (NativeBridge.nativeJvmState()) {
            JVM_STARTING, JVM_RUNNING -> {
                running = true
                true
            }
            JVM_EXITED -> {
                running = false
                val exitCode = NativeBridge.nativeJvmExitCode()
                appendLog("Minecraft JVM exited with code $exitCode.")
                NativeBridge.nativeShutdown()
                false
            }
            else -> {
                running = false
                false
            }
        }
    }

    override suspend fun stop() {
        if (!NativeBridge.isNativeLoaded) {
            running = false
            return
        }
        when (NativeBridge.nativeJvmState()) {
            JVM_STARTING, JVM_RUNNING -> {
                // JLI_Launch does not expose a safe in-process cancellation API.
                // Do not dlclose libjli or pretend that the game has stopped.
                running = true
                appendLog(
                    "Stop requested, but safe embedded-JVM termination is not implemented. " +
                        "The session remains active; closing the Android process is not a safe substitute.",
                )
            }
            else -> {
                running = false
                monitorJob?.cancel()
                NativeBridge.nativeShutdown()
            }
        }
    }

    private fun appendLog(line: String) {
        synchronized(logLock) { logBuffer.appendLine(line) }
    }

    override fun stdout(): String = synchronized(logLock) { logBuffer.toString() }
    override fun stderr(): String = stdout()

    private companion object {
        const val JVM_STARTING = 1
        const val JVM_RUNNING = 2
        const val JVM_EXITED = 3
        const val JVM_FAILED = 4
    }
}
