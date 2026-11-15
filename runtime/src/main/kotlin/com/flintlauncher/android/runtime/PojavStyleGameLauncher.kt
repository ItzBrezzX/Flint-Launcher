package com.flintlauncher.android.runtime

import android.util.Log
import com.flintlauncher.android.nativebridge.FlintNativeWindowBridge
import com.flintlauncher.android.nativebridge.NativeBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Production in-process JLI launcher.
 *
 * EGL creation, GL4ES initialization, GLFW platform attachment and JLI_Launch
 * are performed by one dedicated thread. Android Surface ownership is therefore
 * never transferred between EGL threads. A Started result means native setup
 * completed and the worker entered JLI; it does not mean Minecraft reached its
 * title screen.
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

            val surface = request.windowSurface
                ?: return@withContext fail("A live Android Surface is required to start Minecraft.")
            if (!surface.isValid) {
                return@withContext fail("The Android game Surface was destroyed before launch.")
            }
            if (request.windowWidth <= 0 || request.windowHeight <= 0) {
                return@withContext fail("The Android game Surface dimensions are invalid.")
            }
            val rendererLibrary = request.rendererLibrary
                ?: return@withContext fail("The selected renderer library was not supplied.")
            if (!rendererLibrary.isFile || rendererLibrary.length() <= 0L) {
                return@withContext fail("The renderer library is missing: ${rendererLibrary.absolutePath}")
            }

            if (!runtimeManager.verify(request.runtime)) {
                return@withContext fail("Java ${request.runtime.majorVersion} failed native JLI verification.")
            }
            if (!NativeBridge.nativeLoadJre(request.runtime.homeDir.absolutePath)) {
                return@withContext fail(
                    "Failed to load libjli.so from " + request.runtime.homeDir.absolutePath +
                        ". Check the mobile OpenJDK layout and process ABI.",
                )
            }

            // LaunchCommandBuilder supplies the classpath for current vanilla versions.
            // Keep a safe fallback for direct callers with an incomplete JVM argument list.
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
                logBuffer.append("Preparing Minecraft ").append(request.versionId)
                    .append(" with Java ").append(request.runtime.majorVersion)
                    .append("; classpath entries=").append(request.classpath.size)
                    .append("; JVM arguments=").append(request.jvmArgs.size)
                    .append("; game arguments=").append(request.gameArgs.size)
                    .append('\n')
            }

            val nativeSetup = CountDownLatch(1)
            val setupError = AtomicReference<String?>(null)
            val nativeReady = AtomicBoolean(false)
            val worker = Thread({
                var eglCreated = false
                var glfwAttached = false
                try {
                    if (!surface.isValid) {
                        throw IllegalStateException("Android Surface became invalid before EGL setup.")
                    }

                    // This thread becomes the permanent EGL owner for this game session.
                    val eglResult = NativeBridge.nativeCreateWindowSurface(surface)
                    if (eglResult != "egl-window-ready") {
                        throw IllegalStateException("Android EGL window setup failed: $eglResult")
                    }
                    eglCreated = true
                    appendLog("Android EGL window created on ${Thread.currentThread().name}.")

                    if (!NativeBridge.nativeLoadGL4ES(rendererLibrary.absolutePath)) {
                        throw IllegalStateException("GL4ES initialization failed on the game EGL context.")
                    }
                    val glVersion = NativeBridge.nativeGL4ESVersion()
                    if (glVersion.startsWith("gl4es-") || glVersion.contains("unavailable")) {
                        throw IllegalStateException("GL4ES did not expose a valid GL version: $glVersion")
                    }
                    appendLog("GL4ES initialized: $glVersion")

                    if (!NativeBridge.nativeGlfwAttachAndroidSurface(request.windowWidth, request.windowHeight)) {
                        throw IllegalStateException("Flint GLFW could not attach to the current Android EGL context.")
                    }
                    glfwAttached = true
                    if (!NativeBridge.nativeGlfwInitialize()) {
                        throw IllegalStateException("Flint GLFW initialization failed on the EGL owner thread.")
                    }
                    appendLog(
                        "Flint GLFW backend ready (${request.windowWidth}x${request.windowHeight}); " +
                            "LWJGL will create the game window through the GLFW C ABI.",
                    )

                    running = true
                    nativeReady.set(true)
                    nativeSetup.countDown()

                    appendLog("Calling JLI_Launch on the EGL/GLFW owner thread.")
                    val exitCode = NativeBridge.nativeLaunchJvm(args.toTypedArray())
                    appendLog("JLI_Launch returned exit code $exitCode.")
                    if (exitCode != 0) {
                        appendLog("Minecraft exited before a successful game session was confirmed.")
                    } else {
                        appendLog("Minecraft's Java main method returned.")
                    }
                } catch (error: Throwable) {
                    val detail = error.message ?: error.javaClass.simpleName
                    if (!nativeReady.get()) {
                        setupError.compareAndSet(null, detail)
                        appendLog("Native game setup failed: $detail")
                    } else {
                        appendLog("Minecraft JLI failed: $detail")
                    }
                    if (nativeSetup.count > 0L) nativeSetup.countDown()
                    Log.e(TAG, "Game thread failed", error)
                } finally {
                    // EGL, GLFW and ANativeWindow teardown must run on their owner thread.
                    if (glfwAttached) {
                        runCatching { NativeBridge.nativeGlfwDestroyWindow() }
                            .onFailure { appendLog("GLFW teardown failed: ${it.message}") }
                    }
                    if (eglCreated) {
                        runCatching { NativeBridge.nativeDestroyWindowSurface() }
                            .onFailure { appendLog("EGL teardown failed: ${it.message}") }
                    }
                    runCatching { NativeBridge.nativeShutdown() }
                    running = false
                    launchThread = null
                    launchReserved.set(false)
                    appendLog("Game runtime thread stopped.")
                }
            }, "Flint-Minecraft-JVM")

            worker.isDaemon = true
            launchThread = worker
            worker.start()

            if (!nativeSetup.await(NATIVE_SETUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                appendLog("Timed out while creating the Android EGL/GL4ES/GLFW game surface.")
                requestGracefulClose()
                worker.join(1000)
                if (!worker.isAlive) launchReserved.set(false)
                return@withContext LaunchResult.Failed(
                    "Timed out while preparing the game window. Check the renderer and Surface logs.",
                    stdout(),
                )
            }

            setupError.get()?.let { detail ->
                worker.join(1000)
                if (!worker.isAlive) launchReserved.set(false)
                return@withContext LaunchResult.Failed(
                    "Could not prepare the Minecraft game window: $detail",
                    stdout(),
                )
            }

            LaunchResult.Started(pid = null, logFile = null)
        } catch (cancelled: CancellationException) {
            // The JLI worker owns native resources after it starts. Ask GLFW to close;
            // never destroy EGL here from the coroutine thread.
            requestGracefulClose()
            if (launchThread?.isAlive != true) launchReserved.set(false)
            throw cancelled
        } catch (error: Throwable) {
            requestGracefulClose()
            if (launchThread?.isAlive != true) launchReserved.set(false)
            LaunchResult.Failed(
                "Could not start Minecraft JVM: " + (error.message ?: error.javaClass.simpleName),
                stdout(),
            )
        }
    }

    override suspend fun isRunning(): Boolean = running || launchReserved.get()

    /**
     * Ask Minecraft to close through GLFW's event queue. This does not kill the
     * process or destroy EGL from the UI thread; the game thread performs teardown.
     */
    override suspend fun stop() = withContext(Dispatchers.IO) {
        val worker = launchThread
        if (worker == null || !worker.isAlive) {
            running = false
            launchReserved.set(false)
            return@withContext
        }

        requestGracefulClose()
        worker.join(STOP_TIMEOUT_MILLIS)
        if (worker.isAlive) {
            appendLog(
                "Graceful close was requested, but JLI is still running after " +
                    "$STOP_TIMEOUT_MILLIS ms. EGL was left intact on the owner thread.",
            )
        }
    }

    override fun stdout(): String = synchronized(logBuffer) { logBuffer.toString() }
    override fun stderr(): String = stdout()

    private fun requestGracefulClose() {
        if (!NativeBridge.isNativeLoaded) return
        runCatching { FlintNativeWindowBridge.requestWindowClose() }
            .onFailure { appendLog("Could not enqueue GLFW close request: ${it.message}") }
    }

    private fun appendLog(message: String) {
        synchronized(logBuffer) {
            logBuffer.append(message).append('\n')
            // Avoid unbounded in-memory logs during long-running sessions.
            if (logBuffer.length > MAX_LOG_CHARS) {
                logBuffer.delete(0, logBuffer.length - MAX_LOG_CHARS)
            }
        }
    }

    private companion object {
        const val TAG = "FlintGameLauncher"
        const val NATIVE_SETUP_TIMEOUT_SECONDS = 15L
        const val STOP_TIMEOUT_MILLIS = 8_000L
        const val MAX_LOG_CHARS = 32 * 1024
    }
}
