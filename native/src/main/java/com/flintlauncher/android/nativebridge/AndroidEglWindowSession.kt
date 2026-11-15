package com.flintlauncher.android.nativebridge

import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns the native EGL window on one dedicated thread.
 *
 * Android Surface attach, buffer presentation and teardown are serialized on
 * this thread because an EGL context is thread-affine. Callbacks also execute
 * on the render thread; dispatch them to the UI thread when updating Compose.
 *
 * This is the Android window backend only. It does not provide GL4ES, GLFW,
 * LWJGL or a Minecraft render loop by itself.
 */
class AndroidEglWindowSession(
    threadName: String = "Flint-EGL",
) : Closeable {
    private val thread = HandlerThread(threadName).apply { start() }
    private val handler = Handler(thread.looper)
    private val closed = AtomicBoolean(false)
    private val attached = AtomicBoolean(false)

    /** Attach an Android Surface and create its EGL window/context. */
    fun attach(surface: Surface, onResult: (String) -> Unit): Boolean {
        if (closed.get() || !surface.isValid) return false
        return handler.post {
            if (closed.get() || !surface.isValid) {
                onResult("window-surface-invalid")
            } else {
                val result = runCatching {
                    NativeBridge.nativeCreateWindowSurface(surface)
                }.getOrElse { "egl-window-exception: ${it.message ?: it.javaClass.simpleName}" }
                attached.set(result == "egl-window-ready")
                onResult(result)
            }
        }
    }

    /** Apply the latest Android surface dimensions on the EGL owner thread. */
    fun resize(width: Int, height: Int, onResult: (Boolean) -> Unit = {}): Boolean {
        if (closed.get() || !attached.get() || width <= 0 || height <= 0) return false
        return handler.post {
            val resized = !closed.get() && attached.get() &&
                runCatching { NativeBridge.nativeResizeWindowSurface(width, height) }.getOrDefault(false)
            onResult(resized)
        }
    }

    /** Draw and present one visible diagnostic frame; never used by the Minecraft renderer. */
    fun renderTestFrame(onResult: (Boolean) -> Unit = {}): Boolean {
        if (closed.get() || !attached.get()) return false
        return handler.post {
            val rendered = !closed.get() && attached.get() &&
                runCatching { NativeBridge.nativeRenderWindowTestFrame() }.getOrDefault(false)
            onResult(rendered)
        }
    }

    /** Present one GLES frame. Invoke from the game/render loop, not every UI frame. */
    fun present(onResult: (Boolean) -> Unit = {}): Boolean {
        if (closed.get() || !attached.get()) return false
        return handler.post {
            val presented = !closed.get() && attached.get() &&
                runCatching { NativeBridge.nativeSwapWindowBuffers() }.getOrDefault(false)
            onResult(presented)
        }
    }

    /** Release EGL resources while still on the owning render thread. */
    fun detach(onComplete: () -> Unit = {}): Boolean {
        if (closed.get()) return false
        return handler.post {
            if (attached.getAndSet(false)) {
                runCatching { NativeBridge.nativeDestroyWindowSurface() }
            }
            onComplete()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        handler.post {
            if (attached.getAndSet(false)) {
                runCatching { NativeBridge.nativeDestroyWindowSurface() }
            }
            thread.quitSafely()
        }
    }
}
