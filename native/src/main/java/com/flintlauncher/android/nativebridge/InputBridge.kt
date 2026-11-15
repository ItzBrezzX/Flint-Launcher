package com.flintlauncher.android.nativebridge

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View

/**
 * Kotlin side of the Flint input bridge. Receives Android input events from a
 * game View (touch, hardware keyboard, mouse, trackball) and injects them into
 * the native GLFW event queue via NativeBridge. Also owns the virtual-joystick
 * → mouse/key mapping and touch-button → key mapping configuration.
 *
 * Threading: all nativeInject* calls are thread-safe and non-blocking; they may
 * be called from the Android UI thread (input callbacks) or a dedicated input
 * thread. The native queue is drained by glfwPollEvents on the render/main thread.
 */
class InputBridge {

    /** GLFW action constants (mirror FLINT_GLFW_* in glfw_stub.h). */
    object Action {
        const val RELEASE = 0
        const val PRESS = 1
        const val REPEAT = 2
    }

    /** GLFW modifier bits. */
    object Mods {
        const val SHIFT = 0x0001
        const val CONTROL = 0x0002
        const val ALT = 0x0004
        const val SUPER = 0x0008
    }

    /** GLFW cursor modes. */
    object CursorMode {
        const val NORMAL = 0x00034001
        const val HIDDEN = 0x00034002
        const val DISABLED = 0x00034003
        const val CAPTURED = 0x00034004 // Flint extension (relative mouse)
    }

    /** Joystick stick ids. */
    object Stick {
        const val LEFT = 0  // movement → WASD
        const val RIGHT = 1 // camera look → mouse delta
    }

    /** Joystick mode for injectJoystickAxis. */
    object StickMode {
        const val LOOK = 0     // emit cursor delta (camera)
        const val MOVEMENT = 1 // emit synthetic WASD key events
    }

    /** Default touch-action → GLFW key mapping (overridable via setTouchKeyMapping). */
    object DefaultTouchMap {
        const val JUMP = "jump"
        const val SNEAK = "sneak"
        const val SPRINT = "sprint"
        const val INVENTORY = "inventory"
        const val DROP = "drop"
        const val CHAT = "chat"
        const val ATTACK = "attack"   // mouse button 0
        const val USE = "use"         // mouse button 1
        const val PICK = "pick"       // mouse button 2
    }

    var lookSensitivity: Double = 1.0
    var invertY: Boolean = false

    // ------------------------------------------------------------------
    // Hardware keyboard (Android KeyEvent → GLFW key code).
    // Minimal mapping for the keys Minecraft uses; unknown keys pass through
    // with keyCode+0 (Spike 0). Full mapping table documented in progress doc.
    // ------------------------------------------------------------------
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (!NativeBridge.isNativeLoaded) return false
        val action = when (event.action) {
            KeyEvent.ACTION_DOWN -> Action.PRESS
            KeyEvent.ACTION_UP -> Action.RELEASE
            KeyEvent.ACTION_MULTIPLE -> Action.REPEAT
            else -> return false
        }
        val glfwKey = androidKeyToGlfw(event.keyCode)
        val mods = androidModsToGlfw(event.metaState)
        NativeBridge.nativeInjectKey(glfwKey, event.scanCode, action, mods)
        // Also inject char for text input (chat / text fields).
        if (action == Action.PRESS || action == Action.REPEAT) {
            val unicode = event.unicodeChar
            if (unicode != 0 && unicode != 0xffff && unicode >= 32) {
                NativeBridge.nativeInjectChar(unicode)
            }
        }
        return true
    }

    // ------------------------------------------------------------------
    // Touch / mouse. Single-finger drag = mouse move + (when captured) look.
    // Tap = left click. Two-finger vertical drag = scroll.
    // ------------------------------------------------------------------
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var touchTracking = false

    fun onTouchEvent(view: View, event: MotionEvent): Boolean {
        if (!NativeBridge.isNativeLoaded) return false
        val captured = NativeBridge.nativeGetMouseCapture() == CursorMode.DISABLED ||
            NativeBridge.nativeGetMouseCapture() == CursorMode.CAPTURED
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                touchTracking = true
                if (!captured) {
                    NativeBridge.nativeInjectCursorPos(event.x.toDouble(), event.y.toDouble())
                }
                NativeBridge.nativeInjectMouseButton(0, Action.PRESS, 0)
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    // Two-finger drag → scroll.
                    val dy = event.y - lastTouchY
                    NativeBridge.nativeInjectScroll(0.0, -dy / 50.0)
                } else if (captured) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    val sy = if (invertY) -dy else dy
                    NativeBridge.nativeInjectCursorDelta(dx * lookSensitivity, sy * lookSensitivity)
                } else {
                    NativeBridge.nativeInjectCursorPos(event.x.toDouble(), event.y.toDouble())
                }
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                NativeBridge.nativeInjectMouseButton(0, Action.RELEASE, 0)
                touchTracking = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Second finger down → right click (use item).
                if (event.pointerCount == 2) {
                    NativeBridge.nativeInjectMouseButton(1, Action.PRESS, 0)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount == 2) {
                    NativeBridge.nativeInjectMouseButton(1, Action.RELEASE, 0)
                }
            }
        }
        return true
    }

    /** Hardware mouse / trackball. */
    fun onMouseEvent(event: MotionEvent) {
        if (!NativeBridge.isNativeLoaded) return
        NativeBridge.nativeInjectCursorPos(event.x.toDouble(), event.y.toDouble())
    }

    /** Generic scroll wheel / hover scroll. */
    fun onScrollEvent(dx: Float, dy: Float) {
        if (!NativeBridge.isNativeLoaded) return
        NativeBridge.nativeInjectScroll(dx.toDouble(), dy.toDouble())
    }

    // ------------------------------------------------------------------
    // Virtual joystick (called by the on-screen control overlay).
    // ------------------------------------------------------------------
    fun onJoystickAxis(stick: Int, axisX: Float, axisY: Float) {
        if (!NativeBridge.isNativeLoaded) return
        val mode = if (stick == Stick.LEFT) StickMode.MOVEMENT else StickMode.LOOK
        val sy = if (stick == Stick.RIGHT && invertY) -axisY else axisY
        NativeBridge.nativeInjectJoystickAxis(stick, axisX.toDouble(), sy.toDouble(), lookSensitivity, mode)
    }

    // ------------------------------------------------------------------
    // Touch buttons (jump / sneak / sprint / etc.).
    // ------------------------------------------------------------------
    fun onTouchButton(action: String, pressed: Boolean) {
        if (!NativeBridge.isNativeLoaded) return
        NativeBridge.nativeInjectTouchAction(action, pressed)
    }

    fun setTouchKeyMapping(action: String, glfwKey: Int) {
        if (NativeBridge.isNativeLoaded) NativeBridge.nativeSetTouchKeyMapping(action, glfwKey)
    }

    fun getTouchKeyMapping(action: String): Int =
        if (NativeBridge.isNativeLoaded) NativeBridge.nativeGetTouchKeyMapping(action) else -1

    // ------------------------------------------------------------------
    // Mouse capture (pointer lock). Minecraft calls glfwSetInputMode.
    // ------------------------------------------------------------------
    fun setMouseCapture(mode: Int) {
        if (NativeBridge.isNativeLoaded) NativeBridge.nativeSetMouseCapture(mode)
    }

    fun getMouseCapture(): Int =
        if (NativeBridge.isNativeLoaded) NativeBridge.nativeGetMouseCapture() else CursorMode.NORMAL

    fun reset() {
        if (NativeBridge.isNativeLoaded) NativeBridge.nativeResetInput()
    }

    companion object {
        /** Minimal Android keyCode → GLFW key code mapping. */
        fun androidKeyToGlfw(keyCode: Int): Int {
            return when (keyCode) {
                KeyEvent.KEYCODE_SPACE -> 32
                KeyEvent.KEYCODE_APOSTROPHE -> 39
                KeyEvent.KEYCODE_COMMA -> 44
                KeyEvent.KEYCODE_MINUS -> 45
                KeyEvent.KEYCODE_PERIOD -> 46
                KeyEvent.KEYCODE_SLASH -> 47
                in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> 48 + (keyCode - KeyEvent.KEYCODE_0)
                KeyEvent.KEYCODE_SEMICOLON -> 59
                KeyEvent.KEYCODE_EQUALS -> 61
                in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> 65 + (keyCode - KeyEvent.KEYCODE_A)
                KeyEvent.KEYCODE_LEFT_BRACKET -> 91
                KeyEvent.KEYCODE_BACKSLASH -> 92
                KeyEvent.KEYCODE_RIGHT_BRACKET -> 93
                KeyEvent.KEYCODE_GRAVE -> 96
                KeyEvent.KEYCODE_ESCAPE -> 256
                KeyEvent.KEYCODE_ENTER -> 257
                KeyEvent.KEYCODE_TAB -> 258
                KeyEvent.KEYCODE_DEL -> 259
                KeyEvent.KEYCODE_INSERT -> 260
                KeyEvent.KEYCODE_FORWARD_DEL -> 261
                KeyEvent.KEYCODE_DPAD_RIGHT -> 262
                KeyEvent.KEYCODE_DPAD_LEFT -> 263
                KeyEvent.KEYCODE_DPAD_DOWN -> 264
                KeyEvent.KEYCODE_DPAD_UP -> 265
                KeyEvent.KEYCODE_PAGE_UP -> 266
                KeyEvent.KEYCODE_PAGE_DOWN -> 267
                KeyEvent.KEYCODE_MOVE_HOME -> 268
                KeyEvent.KEYCODE_MOVE_END -> 269
                KeyEvent.KEYCODE_CAPS_LOCK -> 280
                KeyEvent.KEYCODE_SCROLL_LOCK -> 281
                KeyEvent.KEYCODE_NUM_LOCK -> 282
                KeyEvent.KEYCODE_SYSRQ -> 283
                KeyEvent.KEYCODE_BREAK -> 284
                in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 -> 290 + (keyCode - KeyEvent.KEYCODE_F1)
                KeyEvent.KEYCODE_SHIFT_LEFT -> 340
                KeyEvent.KEYCODE_CTRL_LEFT -> 341
                KeyEvent.KEYCODE_ALT_LEFT -> 342
                KeyEvent.KEYCODE_META_LEFT -> 343
                KeyEvent.KEYCODE_SHIFT_RIGHT -> 344
                KeyEvent.KEYCODE_CTRL_RIGHT -> 345
                KeyEvent.KEYCODE_ALT_RIGHT -> 346
                KeyEvent.KEYCODE_META_RIGHT -> 347
                KeyEvent.KEYCODE_MENU -> 348
                else -> -1 // GLFW_KEY_UNKNOWN
            }
        }

        fun androidModsToGlfw(metaState: Int): Int {
            var mods = 0
            if (metaState and (KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON or KeyEvent.META_SHIFT_RIGHT_ON) != 0) mods = mods or Mods.SHIFT
            if (metaState and (KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_CTRL_RIGHT_ON) != 0) mods = mods or Mods.CONTROL
            if (metaState and (KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON or KeyEvent.META_ALT_RIGHT_ON) != 0) mods = mods or Mods.ALT
            if (metaState and (KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON or KeyEvent.META_META_RIGHT_ON) != 0) mods = mods or Mods.SUPER
            return mods
        }
    }
}
