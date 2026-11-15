package com.flintlauncher.android.nativebridge

/**
 * Owns the EGL context for a Flint-managed renderer on a dedicated render thread.
 *
 * Do not attach this worker when LWJGL's GLFW ABI owns the same EGL context: in
 * that mode the Minecraft render thread must make current, poll events and swap.
 * Frames here are event-driven; [renderFrame] issues GL commands and the session
 * swaps only after the callback returns successfully.
 */
class FlintRenderSession(
    private val renderFrame: (FlintGameSurfaceView) -> Unit,
    private val onRenderError: (Throwable) -> Unit = {},
    threadName: String = "Flint-Render",
) : AutoCloseable {

    private val monitor = Object()
    private val threadName = threadName

    private var surfaceView: FlintGameSurfaceView? = null
    private var surfaceReady = false
    private var frameRequested = false
    private var rendering = false
    private var stopping = false
    private var renderThread: Thread? = null

    /** Start the dedicated render thread and associate it with one game surface. */
    fun attach(view: FlintGameSurfaceView) {
        synchronized(monitor) {
            check(renderThread == null) { "Render session is already attached" }
            surfaceView = view
            surfaceReady = view.isEglWindowReady
            stopping = false
            renderThread = Thread(::runLoop, threadName).apply {
                isDaemon = true
                start()
            }
        }
    }

    /** Called by the SurfaceView after EGL window creation succeeds. */
    fun onSurfaceAvailable(view: FlintGameSurfaceView) {
        synchronized(monitor) {
            if (surfaceView !== view || stopping) return
            surfaceReady = true
            monitor.notifyAll()
        }
    }

    /**
     * Quiesce the render thread before the UI thread temporarily binds EGL to
     * apply a Surface resize. This prevents competing eglMakeCurrent calls.
     */
    fun onSurfaceAboutToChange() {
        synchronized(monitor) {
            surfaceReady = false
            frameRequested = false
            awaitRenderCompletionLocked()
        }
    }

    /** A size change is a reason to render a frame, but not to spin continuously. */
    fun onSurfaceSizeChanged(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        synchronized(monitor) {
            if (stopping) return
            surfaceReady = true
            frameRequested = true
            monitor.notifyAll()
        }
    }

    /** Queue one frame. Multiple requests before a frame are coalesced. */
    fun requestFrame(): Boolean = synchronized(monitor) {
        if (stopping || !surfaceReady || renderThread == null) {
            false
        } else {
            frameRequested = true
            monitor.notifyAll()
            true
        }
    }

    /**
     * Called synchronously before native EGL teardown. It waits for any in-flight
     * frame to release the context on its owning render thread.
     */
    fun onSurfaceDestroyed() {
        synchronized(monitor) {
            surfaceReady = false
            frameRequested = false
            monitor.notifyAll()
            awaitRenderCompletionLocked()
        }
    }

    /** Wait uninterruptibly so teardown can never race a context-owning frame. */
    private fun awaitRenderCompletionLocked() {
        var interrupted = false
        while (rendering) {
            try {
                monitor.wait()
            } catch (_: InterruptedException) {
                interrupted = true
            }
        }
        if (interrupted) Thread.currentThread().interrupt()
    }

    private fun runLoop() {
        while (true) {
            val view = synchronized(monitor) {
                while (!stopping && !(surfaceReady && frameRequested)) {
                    try {
                        monitor.wait()
                    } catch (_: InterruptedException) {
                        if (stopping) return@synchronized null
                    }
                }
                if (stopping) return
                frameRequested = false
                rendering = true
                surfaceView
            } ?: break

            try {
                if (view.makeEglContextCurrent()) {
                    try {
                        // GLFW's C ABI owns event-queue draining. Do not consume
                        // events here or LWJGL's glfwPollEvents would see an empty queue.
                        renderFrame(view)
                        view.swapEglBuffers()
                    } finally {
                        // EGL context ownership is released by the same thread
                        // that acquired it, even if renderer code throws.
                        view.releaseEglContext()
                    }
                }
            } catch (error: Throwable) {
                try {
                    onRenderError(error)
                } catch (_: Throwable) {
                    // Error reporting must not kill the lifecycle thread.
                }
            } finally {
                synchronized(monitor) {
                    rendering = false
                    monitor.notifyAll()
                }
            }
        }
    }

    /** Stop the worker and wait until it has left the render loop. */
    override fun close() {
        val thread = synchronized(monitor) {
            stopping = true
            surfaceReady = false
            frameRequested = false
            monitor.notifyAll()
            renderThread
        }
        if (thread != null && thread !== Thread.currentThread()) {
            try {
                thread.join()
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        synchronized(monitor) {
            if (renderThread === thread && (thread == null || !thread.isAlive)) {
                renderThread = null
                surfaceView = null
                rendering = false
            }
        }
    }
}
