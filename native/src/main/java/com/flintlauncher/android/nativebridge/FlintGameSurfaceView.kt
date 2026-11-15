package com.flintlauncher.android.nativebridge

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * Owns the Android Surface lifecycle and translates Android input into Flint's
 * GLFW-compatible event protocol. It is not itself a Minecraft renderer.
 */
class FlintGameSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    @Volatile
    var isEglWindowReady: Boolean = false
        private set

    /** Assign before attaching [FlintRenderSession] to this view. */
    var renderSession: FlintRenderSession? = null

    var onEglWindowReadyChanged: ((Boolean) -> Unit)? = null

    init {
        holder.addCallback(this)
        holder.setFormat(android.graphics.PixelFormat.OPAQUE)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun makeEglContextCurrent(): Boolean =
        isEglWindowReady && NativeBridge.isNativeLoaded &&
            runCatching { NativeBridge.nativeSurfaceMakeCurrent() }.getOrDefault(false)

    fun swapEglBuffers(): Boolean =
        isEglWindowReady && NativeBridge.isNativeLoaded &&
            runCatching { NativeBridge.nativeSurfaceSwapBuffers() }.getOrDefault(false)

    fun releaseEglContext() {
        if (NativeBridge.isNativeLoaded) {
            runCatching { NativeBridge.nativeSurfaceReleaseCurrent() }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val ready = NativeBridge.isNativeLoaded &&
            runCatching { NativeBridge.nativeSurfaceCreated(holder.surface) }.getOrDefault(false)
        updateReady(ready)
        if (ready) renderSession?.onSurfaceAvailable(this)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        renderSession?.onSurfaceAboutToChange()
        val ready = width > 0 && height > 0 && NativeBridge.isNativeLoaded &&
            runCatching { NativeBridge.nativeSurfaceChanged(width, height) }.getOrDefault(false)
        updateReady(ready)
        if (ready) {
            FlintGlfwEventQueue.post(FlintGlfwEvent.windowSize(width, height))
            renderSession?.onSurfaceSizeChanged(width, height)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        renderSession?.onSurfaceDestroyed()
        if (NativeBridge.isNativeLoaded) {
            runCatching { NativeBridge.nativeSurfaceDestroyed() }
        }
        updateReady(false)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEglWindowReady) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                postInput(FlintGlfwEvent.cursor(event.getX(index), event.getY(index)))
                postInput(FlintGlfwEvent.mouseButton(index, GLFW_PRESS, modifiers(event.metaState)))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                postInput(FlintGlfwEvent.cursor(event.getX(index), event.getY(index)))
                postInput(FlintGlfwEvent.mouseButton(index, GLFW_RELEASE, modifiers(event.metaState)))
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.actionIndex.coerceIn(0, event.pointerCount - 1)
                postInput(FlintGlfwEvent.cursor(event.getX(index), event.getY(index)))
            }
            MotionEvent.ACTION_SCROLL -> {
                postInput(FlintGlfwEvent.scroll(
                    event.getAxisValue(MotionEvent.AXIS_HSCROLL),
                    event.getAxisValue(MotionEvent.AXIS_VSCROLL),
                ))
            }
            else -> return true
        }
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val key = toGlfwKey(keyCode) ?: return super.onKeyDown(keyCode, event)
        postInput(FlintGlfwEvent.key(key, event.scanCode, if (event.repeatCount > 0) GLFW_REPEAT else GLFW_PRESS, modifiers(event.metaState)))
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val key = toGlfwKey(keyCode) ?: return super.onKeyUp(keyCode, event)
        postInput(FlintGlfwEvent.key(key, event.scanCode, GLFW_RELEASE, modifiers(event.metaState)))
        return true
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        postInput(FlintGlfwEvent.focus(hasWindowFocus))
    }

    private fun postInput(event: FlintGlfwEvent) {
        FlintGlfwEventQueue.post(event)
        renderSession?.requestFrame()
    }

    private fun modifiers(metaState: Int): Int {
        var result = 0
        if (metaState and KeyEvent.META_SHIFT_ON != 0) result = result or GLFW_MOD_SHIFT
        if (metaState and KeyEvent.META_CTRL_ON != 0) result = result or GLFW_MOD_CONTROL
        if (metaState and KeyEvent.META_ALT_ON != 0) result = result or GLFW_MOD_ALT
        if (metaState and KeyEvent.META_META_ON != 0) result = result or GLFW_MOD_SUPER
        return result
    }

    /** Maps common Android physical keys to GLFW key constants. */
    private fun toGlfwKey(code: Int): Int? = when (code) {
        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> GLFW_KEY_A + (code - KeyEvent.KEYCODE_A)
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
            val digit = (code - KeyEvent.KEYCODE_0 + 1) % 10
            GLFW_KEY_0 + digit
        }
        KeyEvent.KEYCODE_SPACE -> GLFW_KEY_SPACE
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> GLFW_KEY_ENTER
        KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> GLFW_KEY_ESCAPE
        KeyEvent.KEYCODE_TAB -> GLFW_KEY_TAB
        KeyEvent.KEYCODE_DEL -> GLFW_KEY_BACKSPACE
        KeyEvent.KEYCODE_INSERT -> GLFW_KEY_INSERT
        KeyEvent.KEYCODE_FORWARD_DEL -> GLFW_KEY_DELETE
        KeyEvent.KEYCODE_DPAD_RIGHT -> GLFW_KEY_RIGHT
        KeyEvent.KEYCODE_DPAD_LEFT -> GLFW_KEY_LEFT
        KeyEvent.KEYCODE_DPAD_DOWN -> GLFW_KEY_DOWN
        KeyEvent.KEYCODE_DPAD_UP -> GLFW_KEY_UP
        KeyEvent.KEYCODE_PAGE_UP -> GLFW_KEY_PAGE_UP
        KeyEvent.KEYCODE_PAGE_DOWN -> GLFW_KEY_PAGE_DOWN
        KeyEvent.KEYCODE_MOVE_HOME -> GLFW_KEY_HOME
        KeyEvent.KEYCODE_MOVE_END -> GLFW_KEY_END
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> GLFW_KEY_LEFT_SHIFT
        KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> GLFW_KEY_LEFT_CONTROL
        KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> GLFW_KEY_LEFT_ALT
        KeyEvent.KEYCODE_F1 -> GLFW_KEY_F1
        KeyEvent.KEYCODE_F2 -> GLFW_KEY_F1 + 1
        KeyEvent.KEYCODE_F3 -> GLFW_KEY_F1 + 2
        KeyEvent.KEYCODE_F4 -> GLFW_KEY_F1 + 3
        KeyEvent.KEYCODE_F5 -> GLFW_KEY_F1 + 4
        KeyEvent.KEYCODE_F6 -> GLFW_KEY_F1 + 5
        KeyEvent.KEYCODE_F7 -> GLFW_KEY_F1 + 6
        KeyEvent.KEYCODE_F8 -> GLFW_KEY_F1 + 7
        KeyEvent.KEYCODE_F9 -> GLFW_KEY_F1 + 8
        KeyEvent.KEYCODE_F10 -> GLFW_KEY_F1 + 9
        KeyEvent.KEYCODE_F11 -> GLFW_KEY_F1 + 10
        KeyEvent.KEYCODE_F12 -> GLFW_KEY_F1 + 11
        else -> null
    }

    private fun updateReady(ready: Boolean) {
        isEglWindowReady = ready
        post { onEglWindowReadyChanged?.invoke(ready) }
    }

    private companion object {
        const val GLFW_PRESS = 1
        const val GLFW_RELEASE = 0
        const val GLFW_REPEAT = 2
        const val GLFW_MOD_SHIFT = 0x0001
        const val GLFW_MOD_CONTROL = 0x0002
        const val GLFW_MOD_ALT = 0x0004
        const val GLFW_MOD_SUPER = 0x0008
        const val GLFW_KEY_SPACE = 32
        const val GLFW_KEY_0 = 48
        const val GLFW_KEY_A = 65
        const val GLFW_KEY_ESCAPE = 256
        const val GLFW_KEY_ENTER = 257
        const val GLFW_KEY_TAB = 258
        const val GLFW_KEY_BACKSPACE = 259
        const val GLFW_KEY_INSERT = 260
        const val GLFW_KEY_DELETE = 261
        const val GLFW_KEY_RIGHT = 262
        const val GLFW_KEY_LEFT = 263
        const val GLFW_KEY_DOWN = 264
        const val GLFW_KEY_UP = 265
        const val GLFW_KEY_PAGE_UP = 266
        const val GLFW_KEY_PAGE_DOWN = 267
        const val GLFW_KEY_HOME = 268
        const val GLFW_KEY_END = 269
        const val GLFW_KEY_LEFT_SHIFT = 340
        const val GLFW_KEY_LEFT_CONTROL = 341
        const val GLFW_KEY_LEFT_ALT = 342
        const val GLFW_KEY_F1 = 290
    }
}
