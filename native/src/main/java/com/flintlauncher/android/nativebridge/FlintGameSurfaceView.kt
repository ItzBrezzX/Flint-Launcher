package com.flintlauncher.android.nativebridge

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * Owns the Android Surface lifecycle used by the native game renderer.
 *
 * This view currently establishes and clears a real EGL window surface. It is
 * deliberately not advertised as a Minecraft renderer: GL4ES/LWJGL must attach
 * their rendering loop to this EGL context in a later integration step.
 */
class FlintGameSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    @Volatile
    var isEglWindowReady: Boolean = false
        private set

    var onEglWindowReadyChanged: ((Boolean) -> Unit)? = null

    init {
        holder.addCallback(this)
        holder.setFormat(android.graphics.PixelFormat.OPAQUE)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    /**
     * Acquire the EGL context on the caller's render thread. Pair every successful
     * acquisition with [releaseEglContext] on that same thread.
     */
    fun makeEglContextCurrent(): Boolean =
        isEglWindowReady && NativeBridge.isNativeLoaded &&
            runCatching { NativeBridge.nativeSurfaceMakeCurrent() }.getOrDefault(false)

    /** Swap the EGL window buffer from the thread that currently owns the context. */
    fun swapEglBuffers(): Boolean =
        isEglWindowReady && NativeBridge.isNativeLoaded &&
            runCatching { NativeBridge.nativeSurfaceSwapBuffers() }.getOrDefault(false)

    /** Release EGL context ownership from the same thread that acquired it. */
    fun releaseEglContext() {
        if (NativeBridge.isNativeLoaded) {
            runCatching { NativeBridge.nativeSurfaceReleaseCurrent() }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val ready = NativeBridge.isNativeLoaded &&
            runCatching { NativeBridge.nativeSurfaceCreated(holder.surface) }.getOrDefault(false)
        updateReady(ready)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        val ready = width > 0 && height > 0 && NativeBridge.isNativeLoaded &&
            runCatching { NativeBridge.nativeSurfaceChanged(width, height) }.getOrDefault(false)
        updateReady(ready)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (NativeBridge.isNativeLoaded) {
            runCatching { NativeBridge.nativeSurfaceDestroyed() }
        }
        updateReady(false)
    }

    private fun updateReady(ready: Boolean) {
        isEglWindowReady = ready
        post { onEglWindowReadyChanged?.invoke(ready) }
    }
}
