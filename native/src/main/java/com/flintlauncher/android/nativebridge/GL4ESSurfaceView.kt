package com.flintlauncher.android.nativebridge

import android.content.Context
import android.graphics.PixelFormat
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
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
        isFocusable = true
        isFocusableInTouchMode = true
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
        enqueueInput(FlintInputEvent.WINDOW_SIZE, x = width.toDouble(), y = height.toDouble())
        enqueueInput(FlintInputEvent.FRAMEBUFFER_SIZE, x = width.toDouble(), y = height.toDouble())
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // GLFW has one cursor. The first touch pointer acts as the
                // left mouse button; secondary fingers must not create extra
                // presses/releases that would cancel the primary click.
                enqueueInput(
                    FlintInputEvent.CURSOR,
                    x = event.getX(0).toDouble(),
                    y = event.getY(0).toDouble(),
                )
                enqueueInput(
                    FlintInputEvent.MOUSE_BUTTON,
                    code = 0,
                    action = 1,
                    modifiers = FlintGlfwInputMapper.modifiers(event.metaState),
                    x = event.getX(0).toDouble(),
                    y = event.getY(0).toDouble(),
                )
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount > 0) {
                    enqueueInput(
                        FlintInputEvent.CURSOR,
                        x = event.getX(0).toDouble(),
                        y = event.getY(0).toDouble(),
                    )
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                enqueueInput(
                    FlintInputEvent.MOUSE_BUTTON,
                    code = 0,
                    action = 0,
                    modifiers = FlintGlfwInputMapper.modifiers(event.metaState),
                    x = event.getX(0).toDouble(),
                    y = event.getY(0).toDouble(),
                )
            }
        }
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(android.view.InputDevice.SOURCE_MOUSE)) {
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_MOVE -> {
                    enqueueInput(FlintInputEvent.CURSOR, x = event.x.toDouble(), y = event.y.toDouble())
                    return true
                }
                MotionEvent.ACTION_SCROLL -> {
                    enqueueInput(
                        FlintInputEvent.SCROLL,
                        x = event.getAxisValue(MotionEvent.AXIS_HSCROLL).toDouble(),
                        y = event.getAxisValue(MotionEvent.AXIS_VSCROLL).toDouble(),
                    )
                    return true
                }
                MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE -> {
                    val pressed = event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS
                    val mapping = mapOf(
                        MotionEvent.BUTTON_PRIMARY to 0,
                        MotionEvent.BUTTON_SECONDARY to 1,
                        MotionEvent.BUTTON_TERTIARY to 2,
                        MotionEvent.BUTTON_BACK to 3,
                        MotionEvent.BUTTON_FORWARD to 4,
                    )
                    mapping[event.actionButton]?.let { button ->
                        enqueueInput(
                            FlintInputEvent.MOUSE_BUTTON,
                            code = button,
                            action = if (pressed) 1 else 0,
                            modifiers = FlintGlfwInputMapper.modifiers(event.metaState),
                            x = event.x.toDouble(),
                            y = event.y.toDouble(),
                        )
                    }
                    return true
                }
            }
        }
        return super.onGenericMotionEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP) {
            val glfwKey = FlintGlfwInputMapper.key(event.keyCode)
            if (glfwKey != null) {
                val action = when {
                    event.action == KeyEvent.ACTION_UP -> 0
                    event.repeatCount > 0 -> 2
                    else -> 1
                }
                enqueueInput(
                    FlintInputEvent.KEY,
                    code = glfwKey,
                    action = action,
                    modifiers = FlintGlfwInputMapper.modifiers(event.metaState),
                )
            }
            if (event.action == KeyEvent.ACTION_DOWN) {
                val codePoint = event.unicodeChar
                if (codePoint != 0) {
                    enqueueInput(FlintInputEvent.CHARACTER, code = codePoint)
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        enqueueInput(FlintInputEvent.FOCUS, action = if (hasWindowFocus) 1 else 0)
    }

    private fun enqueueInput(
        type: Int,
        code: Int = 0,
        action: Int = 0,
        modifiers: Int = 0,
        x: Double = 0.0,
        y: Double = 0.0,
    ) {
        if (!NativeBridge.isNativeLoaded) return
        runCatching {
            FlintNativeWindowBridge.enqueue(type, code, action, modifiers, x, y)
        }.onFailure { Log.w(TAG, "Could not enqueue input event", it) }
    }

    private fun renderLoop(holder: SurfaceHolder) {
        var eglCreated = false
        var glfwCreated = false
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

            val surfaceFrame = holder.surfaceFrame
            val initialWidth = (surfaceWidth.takeIf { it > 0 } ?: surfaceFrame.width()).coerceAtLeast(1)
            val initialHeight = (surfaceHeight.takeIf { it > 0 } ?: surfaceFrame.height()).coerceAtLeast(1)
            if (!FlintGlfwBackend.initialize(initialWidth, initialHeight, "Flint Renderer Preview")) {
                updateStatus("Flint GLFW backend initialization failed; inspect FlintNative logcat")
                return
            }
            glfwCreated = true
            updateStatus("${FlintGlfwBackend.version()} active · native input queue connected")

            var lastWidth = 0
            var lastHeight = 0
            while (renderRequested && holder.surface.isValid) {
                FlintGlfwBackend.pollEvents()
                if (FlintGlfwBackend.shouldClose()) {
                    updateStatus("Flint GLFW window close requested")
                    break
                }

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
                if (!FlintGlfwBackend.swapBuffers()) {
                    updateStatus("Flint GLFW buffer swap failed")
                    break
                }
                LockSupport.parkNanos(FRAME_INTERVAL_NS)
                if (Thread.interrupted() && !renderRequested) break
            }
        } catch (error: Throwable) {
            updateStatus("Renderer thread failed: ${error.message ?: error.javaClass.simpleName}")
            Log.e(TAG, "GL4ES render loop failed", error)
        } finally {
            if (glfwCreated) {
                runCatching { FlintGlfwBackend.destroy() }
                    .onFailure { Log.e(TAG, "Flint GLFW teardown failed", it) }
            }
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
