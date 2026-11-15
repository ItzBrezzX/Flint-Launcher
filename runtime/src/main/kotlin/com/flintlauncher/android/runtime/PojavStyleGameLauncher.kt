package com.flintlauncher.android.runtime

import com.flintlauncher.android.nativebridge.NativeBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * In-process Pojav-style JVM launcher.
 *
 * JLI_Launch runs on a dedicated thread: calling it must not hold the PLAY coroutine open
 * for the entire Minecraft session. The returned Started result means the JVM call thread
 * was created; it does not claim that Minecraft reached its title screen.
 */
class PojavStyleGameLauncher(
    private val runtimeManager: JavaRuntimeManager,
) : GameLauncher {

    @Volatile private var running = false
    @Volatile private var launchThread: Thread? = null
    private val launchReserved = AtomicBoolean(false)
    private val logBuffer = StringBuilder()

    override suspend fun launch(request: LaunchRequest): LaunchResult = withContext(Dispatchers.IO) {
        if (!launchReserved.compareAndSet(false, true)) {
            return@withContext LaunchResult.Failed("A Minecraft JVM is already running or starting.", stdout())
        }

        fun fail(message: String): LaunchResult.Failed {
            launchReserved.set(false)
            return LaunchResult.Failed(message, stdout())
        }

        try {
            if (!NativeBridge.isNativeLoaded) {
                return@withContext fail(
                    "Native library flint_native not loaded: " +
                        (NativeBridge.nativeLoadError ?: "unknown load error") +
                        ". Build the :native module with NDK/CMake first.",
                )
            }

            if (!request.renderer.available) {
                return@withContext fail("No compatible renderer artifact is available for this launch.")
            }
            if (!request.runtime.verified) {
                return@withContext fail("The selected Java runtime has not passed verification.")
            }
            if (request.mainClass.isBlank()) {
                return@withContext fail("Minecraft launch metadata did not provide a main class.")
            }
            if (request.classpath.isEmpty() || request.classpath.any { !it.isFile }) {
                return@withContext fail("Minecraft classpath is empty or contains missing library files.")
            }
            if (!request.gameDir.isDirectory || !request.nativeDir.isDirectory) {
                return@withContext fail("The game or extracted native-library directory is missing.")
            }

            val verified = runtimeManager.verify(request.runtime)
            if (!verified) {
                return@withContext fail("Java ${request.runtime.majorVersion} failed native JLI verification.")
            }
            if (!NativeBridge.nativeLoadJre(request.runtime.homeDir.absolutePath)) {
                return@withContext fail(
                    "Failed to load libjli.so from " + request.runtime.homeDir.absolutePath +
                        ". Check the mobile OpenJDK layout and process ABI.",
                )
            }

            request.stdioLogFile?.let { log ->
                log.parentFile?.mkdirs()
                if (!NativeBridge.nativeRedirectStdio(log.absolutePath)) {
                    return@withContext fail("Could not open the game log file: ${log.absolutePath}")
                }
            }

            // The JVM lives in this process, so its environment must be in place before JLI_Launch.
            for ((key, value) in request.environment) {
                if (!NativeBridge.nativeSetEnv(key, value)) {
                    return@withContext fail("Could not set the process environment variable $key for the JVM.")
                }
            }

            // LaunchCommandBuilder already supplies -cp for modern and legacy versions.
            // Add it only for a direct caller that did not build a complete Mojang command.
            val args = mutableListOf<String>()
            args += "java"
            args += request.jvmArgs
            val hasClasspathArgument = request.jvmArgs.any { it == "-cp" || it == "-classpath" }
            if (!hasClasspathArgument) {
                args += "-cp"
                args += request.classpath.joinToString(File.pathSeparator) { it.absolutePath }
            }
            args += request.mainClass
            args += request.gameArgs

            synchronized(logBuffer) {
                logBuffer.setLength(0)
                logBuffer.append("Starting Minecraft ").append(request.versionId)
                    .append(" with Java ").append(request.runtime.majorVersion)
                    .append("; classpath entries=").append(request.classpath.size)
                    .append("; JVM arguments=").append(request.jvmArgs.size)
                    .append("; game arguments=").append(request.gameArgs.size)
                    .append('\n')
            }

            val enteredNativeCall = CountDownLatch(1)
            val worker = Thread({
                running = true
                enteredNativeCall.countDown()
                try {
                    val exitCode = NativeBridge.nativeLaunchJvm(args.toTypedArray())
                    appendLog("JLI_Launch returned exit code " + exitCode)
                    if (exitCode != 0) {
                        appendLog("Minecraft exited before a successful game session was confirmed.")
                    }
                } catch (error: Throwable) {
                    appendLog("JNI launch failed: " + (error.message ?: error.javaClass.simpleName))
                } finally {
                    running = false
                    launchThread = null
                    launchReserved.set(false)
                    NativeBridge.nativeShutdown()
                }
            }, "Flint-Minecraft-JVM")

            worker.isDaemon = true
            launchThread = worker
            worker.start()
            if (!enteredNativeCall.await(3, TimeUnit.SECONDS)) {
                launchThread = null
                return@withContext fail("Minecraft launch thread did not start.")
            }

            LaunchResult.Started(pid = null, logFile = request.stdioLogFile)
        } catch (cancelled: CancellationException) {
            launchReserved.set(false)
            throw cancelled
        } catch (error: Throwable) {
            launchReserved.set(false)
            NativeBridge.nativeShutdown()
            LaunchResult.Failed(
                "Could not start Minecraft JVM: " + (error.message ?: error.javaClass.simpleName),
                stdout(),
            )
        }
    }

    override suspend fun isRunning(): Boolean = running

    override suspend fun stop() {
        if (running) {
            appendLog("Stop requested, but the current native bridge does not yet expose a JVM termination hook.")
        }
    }

    override fun stdout(): String = synchronized(logBuffer) { logBuffer.toString() }
    override fun stderr(): String = stdout()

    private fun appendLog(message: String) {
        synchronized(logBuffer) { logBuffer.append(message).append('\n') }
    }
}
