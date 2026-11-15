package com.flintlauncher.android.nativebridge

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceView

/**
 * Input-only Android view for the production Minecraft Surface.
 *
 * This view never creates an EGL context and never owns the game thread. Android
 * callbacks publish bounded records to Flint's native FIFO; the thread running
 * JLI/LWJGL drains them through Flint's GLFW backend.
 */
class FlintGameSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs) {

    private var activeTouchPointerId = MotionEvent.INVALID_POINTER_ID
    private var touchButtonDown = false

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setOnFocusChangeListener { _, focused ->
            enqueue(FlintInputEvent.FOCUS, action = if (focused) 1 else 0)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        val modifiers = FlintGlfwInputMapper.modifiers(event.metaState)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val index = event.actionIndex
                activeTouchPointerId = event.getPointerId(index)
                enqueueCursor(event.getX(index), event.getY(index))
                enqueue(
                    FlintInputEvent.MOUSE_BUTTON,
                    code = 0,
                    action = 1,
                    modifiers = modifiers,
                    x = event.getX(index).toDouble(),
                    y = event.getY(index).toDouble(),
                )
                touchButtonDown = true
                requestFocus()
            }

            MotionEvent.ACTION_MOVE -> {
                val index = if (activeTouchPointerId == MotionEvent.INVALID_POINTER_ID) {
                    -1
                } else {
                    event.findPointerIndex(activeTouchPointerId)
                }
                if (index >= 0) enqueueCursor(event.getX(index), event.getY(index))
            }

            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val index = event.actionIndex
                if (index in 0 until event.pointerCount &&
                    event.getPointerId(index) == activeTouchPointerId
                ) {
                    enqueueCursor(event.getX(index), event.getY(index))
                    releaseTouchButton(event.getX(index), event.getY(index), modifiers)
                    activeTouchPointerId = MotionEvent.INVALID_POINTER_ID
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                val index = if (activeTouchPointerId == MotionEvent.INVALID_POINTER_ID) {
                    -1
                } else {
                    event.findPointerIndex(activeTouchPointerId)
                }
                val safeIndex = index.takeIf { it in 0 until event.pointerCount }
                if (safeIndex != null) {
                    releaseTouchButton(event.getX(safeIndex), event.getY(safeIndex), modifiers)
                } else {
                    releaseTouchButton(0f, 0f, modifiers)
                }
                activeTouchPointerId = MotionEvent.INVALID_POINTER_ID
            }
        }
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            return super.onGenericMotionEvent(event)
        }

        val modifiers = FlintGlfwInputMapper.modifiers(event.metaState)
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_MOVE -> {
                enqueueCursor(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_SCROLL -> {
                enqueue(
                    FlintInputEvent.SCROLL,
                    x = event.getAxisValue(MotionEvent.AXIS_HSCROLL).toDouble(),
                    y = event.getAxisValue(MotionEvent.AXIS_VSCROLL).toDouble(),
                )
                return true
            }

            MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE -> {
                val pressed = event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS
                val button = when (event.actionButton) {
                    MotionEvent.BUTTON_PRIMARY -> 0
                    MotionEvent.BUTTON_SECONDARY -> 1
                    MotionEvent.BUTTON_TERTIARY -> 2
                    MotionEvent.BUTTON_BACK -> 3
                    MotionEvent.BUTTON_FORWARD -> 4
                    else -> null
                }
                if (button != null) {
                    enqueueCursor(event.x, event.y)
                    enqueue(
                        FlintInputEvent.MOUSE_BUTTON,
                        code = button,
                        action = if (pressed) 1 else 0,
                        modifiers = modifiers,
                        x = event.x.toDouble(),
                        y = event.y.toDouble(),
                    )
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
                enqueue(
                    FlintInputEvent.KEY,
                    code = glfwKey,
                    action = action,
                    modifiers = FlintGlfwInputMapper.modifiers(event.metaState),
                )
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    val codePoint = event.unicodeChar
                    if (codePoint != 0) {
                        enqueue(
                            FlintInputEvent.CHARACTER,
                            code = codePoint,
                            modifiers = FlintGlfwInputMapper.modifiers(event.metaState),
                        )
                    }
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        enqueue(FlintInputEvent.FOCUS, action = if (hasWindowFocus) 1 else 0)
    }

    /** Forward Android Surface dimensions as GLFW window and framebuffer events. */
    fun notifySurfaceSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        enqueue(FlintInputEvent.WINDOW_SIZE, x = width.toDouble(), y = height.toDouble())
        enqueue(FlintInputEvent.FRAMEBUFFER_SIZE, x = width.toDouble(), y = height.toDouble())
    }

    private fun enqueueCursor(x: Float, y: Float) {
        enqueue(FlintInputEvent.CURSOR, x = x.toDouble(), y = y.toDouble())
    }

    private fun releaseTouchButton(x: Float, y: Float, modifiers: Int) {
        if (!touchButtonDown) return
        enqueue(
            FlintInputEvent.MOUSE_BUTTON,
            code = 0,
            action = 0,
            modifiers = modifiers,
            x = x.toDouble(),
            y = y.toDouble(),
        )
        touchButtonDown = false
    }

    private fun enqueue(
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
        }.onFailure { Log.w(TAG, "Could not enqueue game input event", it) }
    }

    private companion object {
        const val TAG = "FlintGameSurface"
    }
}
