package com.flintlauncher.android.nativebridge

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * Lifecycle bridge from Android SurfaceHolder to the dedicated EGL owner thread.
 * This supplies a real window surface; it intentionally does not pretend to be
 * a GLFW window or a Minecraft/LWJGL renderer.
 */
class FlintGameSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private var session: AndroidEglWindowSession? = null
    private var surfaceReady = false
    var onStatus: ((String) -> Unit)? = null

    init {
        holder.addCallback(this)
        setZOrderOnTop(false)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        val activeSession = session ?: AndroidEglWindowSession().also { session = it }
        val accepted = activeSession.attach(holder.surface) { result ->
            onStatus?.invoke(result)
            if (result == "egl-window-ready" && width > 0 && height > 0) {
                activeSession.renderTestFrame { rendered ->
                    onStatus?.invoke(if (rendered) "egl-frame-presented" else "egl-frame-present-failed")
                }
                activeSession.resize(width, height)
            }
        }
        if (!accepted) onStatus?.invoke("surface-attach-rejected")
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (!surfaceReady) return
        val accepted = session?.resize(width, height) { resized ->
            onStatus?.invoke(if (resized) "egl-window-resized:${width}x${height}" else "egl-window-resize-failed")
        } ?: false
        if (!accepted) onStatus?.invoke("surface-resize-not-ready")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        session?.detach {
            onStatus?.invoke("egl-window-detached")
        }
    }

    override fun onDetachedFromWindow() {
        surfaceReady = false
        session?.close()
        session = null
        super.onDetachedFromWindow()
    }
}
