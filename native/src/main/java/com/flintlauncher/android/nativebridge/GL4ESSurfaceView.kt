package com.flintlauncher.android.nativebridge

import android.content.Context
import android.graphics.PixelFormat
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.io.File
import java.util.concurrent.locks.LockSupport

/**
 * Owns a real Android Surface and a dedicated EGL render thread.
 *
 * EGL creation, GL4ES initialization, rendering, buffer swaps and teardown
 * all happen on this one thread. This is a renderer/window integration test
 * surface; Minecraft's LWJGL window callbacks are a separate integration step.
 */
class GL4ESSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    @Volatile
    var rendererStatus: String = "Waiting for Android surface"
        private set

    @Volatile
    var onStatusChanged: ((String) -> Unit)? = null

    @Volatile private var renderRequested = false
    @Volatile private var surfaceWidth = 0
    @Volatile private var surfaceHeight = 0
    private var renderThread: Thread? = null

    init {
        holder.setFormat(PixelFormat.RGBA_8888)
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (renderThread?.isAlive == true) return
        renderRequested = true
        val thread = Thread({ renderLoop(holder) }, "Flint-GL4ES-Render")
        renderThread = thread
        thread.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        renderRequested = false
        renderThread?.interrupt()
        try {
            renderThread?.join(3000)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        renderThread = null
        updateStatus("Android surface destroyed")
    }

    private fun renderLoop(holder: SurfaceHolder) {
        var eglCreated = false
        try {
            val eglStatus = NativeBridge.nativeCreateWindowSurface(holder.surface)
            if (eglStatus != "egl-window-ready") {
                updateStatus("EGL window creation failed: $eglStatus")
                return
            }
            eglCreated = true
            updateStatus("EGL window ready; loading GL4ES")

            val glLibrary = File(context.applicationInfo.nativeLibraryDir, "libGL.so")
            if (!glLibrary.isFile) {
                updateStatus("GL4ES library missing: ${glLibrary.absolutePath}")
                return
            }
            if (!NativeBridge.nativeLoadGL4ES(glLibrary.absolutePath)) {
                updateStatus("GL4ES initialization failed; inspect FlintNative logcat")
                return
            }

            val version = NativeBridge.nativeGL4ESVersion()
            if (version.startsWith("gl4es-")) {
                updateStatus("GL4ES version query failed: $version")
                return
            }
            updateStatus("GL4ES active · $version")

            var lastWidth = 0
            var lastHeight = 0
            while (renderRequested && holder.surface.isValid) {
                val width = surfaceWidth
                val height = surfaceHeight
                if (width > 0 && height > 0 && (width != lastWidth || height != lastHeight)) {
                    if (!NativeBridge.nativeResizeWindowSurface(width, height)) {
                        updateStatus("EGL viewport update failed")
                        break
                    }
                    lastWidth = width
                    lastHeight = height
                }

                if (!NativeBridge.nativeGL4ESClear()) {
                    updateStatus("GL4ES draw call failed")
                    break
                }
                if (!NativeBridge.nativeSwapWindowBuffers()) {
                    updateStatus("EGL buffer swap failed")
                    break
                }
                LockSupport.parkNanos(FRAME_INTERVAL_NS)
                if (Thread.interrupted() && !renderRequested) break
            }
        } catch (error: Throwable) {
            updateStatus("Renderer thread failed: ${error.message ?: error.javaClass.simpleName}")
            Log.e(TAG, "GL4ES render loop failed", error)
        } finally {
            if (eglCreated) {
                runCatching { NativeBridge.nativeDestroyWindowSurface() }
                    .onFailure { Log.e(TAG, "EGL teardown failed", it) }
            }
        }
    }

    private fun updateStatus(status: String) {
        rendererStatus = status
        Log.i(TAG, status)
        onStatusChanged?.invoke(status)
    }

    private companion object {
        private const val TAG = "FlintGL4ES"
        private const val FRAME_INTERVAL_NS = 16_666_667L
    }
}
