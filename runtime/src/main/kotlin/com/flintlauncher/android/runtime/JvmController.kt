package com.flintlauncher.android.runtime

import android.util.Log
import com.flintlauncher.android.nativebridge.NativeBridge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * JVM lifecycle controller. Owns the in-process JVM thread (JLI_Launch),
 * render-thread / main-thread separation, shutdown, restart, and crash recovery.
 *
 * Threading model (Spike 0):
 *   - JVM thread: runs JLI_Launch (Minecraft main). Blocking, dedicated.
 *   - Render thread: owns EGL context + GLFW window + swap buffers. Created by
 *     the app shard (SurfaceView); JvmController only signals it.
 *   - Main/UI thread: never blocked by JVM launch; launch returns immediately
 *     with a GameProcess handle.
 *
 * Crash recovery: a native signal handler (nativeInstallCrashHandler) writes
 * last_crash.txt before terminating. On next launch, [hasPendingCrash] detects
 * it and [lastCrashReport] returns the details so the app can offer "restart".
 */
class JvmController(
    private val crashLogDir: File,
) {
    private val TAG = "FlintJvmController"
    private val launching = AtomicBoolean(false)
    @Volatile private var currentProcess: GameProcess? = null

    /** True if a previous JVM session crashed and the report hasn't been cleared. */
    fun hasPendingCrash(): Boolean {
        if (!NativeBridge.isNativeLoaded) return false
        val report = NativeBridge.safeGetLastCrashReport()
        return report != "none" && !report.startsWith("no-crash") && !report.startsWith("native-not")
    }

    fun lastCrashReport(): String? =
        if (hasPendingCrash()) NativeBridge.safeGetLastCrashReport() else null

    fun clearCrashReport() {
        if (NativeBridge.isNativeLoaded) NativeBridge.nativeClearCrashReport()
    }

    /**
     * Start the JVM non-blocking. Returns a GameProcess immediately; the JVM
     * thread is created and JLI_Launch invoked on it.
     *
     * @param args full argument array (including argv[0] = "java")
     * @param env additional environment variables (LD_LIBRARY_PATH etc.)
     */
    suspend fun start(args: Array<String>, env: Map<String, String>): GameProcess = withContext(Dispatchers.IO) {
        if (!launching.compareAndSet(false, true)) {
            throw IllegalStateException("A JVM is already starting or running")
        }
        val process = GameProcess(id = "jvm-${System.currentTimeMillis()}")
        val started = CompletableDeferred<Unit>()

        // Install crash handler before JLI_Launch.
        if (NativeBridge.isNativeLoaded) {
            crashLogDir.mkdirs()
            NativeBridge.nativeInstallCrashHandler(crashLogDir.absolutePath)
        }

        // Apply env (best-effort; in-process JVM inherits our process env).
        // LD_LIBRARY_PATH is set via NativeLibraryLoader + passed as hint.
        env["LD_LIBRARY_PATH"]?.let { /* in-process: already set via native bridge */ }

        val worker = Thread({
            try {
                started.complete(Unit)
                process.transition(GameProcess.State.RUNNING)
                val exitCode = NativeBridge.nativeLaunchJvm(args)
                process.setExitCode(exitCode)
                if (exitCode == 0) {
                    process.transition(GameProcess.State.EXITED)
                } else {
                    process.transition(GameProcess.State.CRASHED)
                    process.crashReport = "JLI_Launch returned non-zero exit code $exitCode"
                }
            } catch (t: Throwable) {
                process.transition(GameProcess.State.CRASHED)
                process.crashReport = "JNI launch failed: ${t.message}"
                Log.e(TAG, "JVM launch failed", t)
            } finally {
                launching.set(false)
                currentProcess = null
                NativeBridge.nativeShutdown()
            }
        }, "Flint-Minecraft-JVM")
        worker.isDaemon = true
        process.thread = worker
        currentProcess = process
        worker.start()

        // Wait briefly for the thread to enter the native call; if it fails to
        // start, surface that immediately rather than returning a dead handle.
        try {
            withContext(Dispatchers.IO) {
                kotlinx.coroutines.withTimeout(3000) { started.await() }
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            process.transition(GameProcess.State.CRASHED)
            process.crashReport = "JVM launch thread did not start within 3s"
            launching.set(false)
        }
        process
    }

    val runningProcess: GameProcess? get() = currentProcess?.takeIf { it.isAlive }

    /**
     * Shutdown the running JVM. Spike 0: requests cancellation. A hard
     * nativeDestroyJavaVM hook is not yet exposed; tracked in progress doc.
     */
    suspend fun shutdown() {
        currentProcess?.cancel()
        withContext(Dispatchers.IO) {
            currentProcess?.waitFor(5000)
        }
        NativeBridge.nativeShutdown()
    }

    /**
     * Restart: shutdown current, then start again with the same args.
     * Caller supplies args (JvmController does not retain them).
     */
    suspend fun restart(args: Array<String>, env: Map<String, String>): GameProcess {
        shutdown()
        return start(args, env)
    }
}
