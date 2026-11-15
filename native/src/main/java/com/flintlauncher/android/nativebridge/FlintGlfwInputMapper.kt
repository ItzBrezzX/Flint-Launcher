package com.flintlauncher.android.nativebridge

import android.view.KeyEvent

/**
 * Converts Android physical-key events into GLFW 3 key and modifier values.
 * Unknown Android keys are intentionally dropped instead of leaking Android
 * key-code numbers into the GLFW event stream.
 */
object FlintGlfwInputMapper {
    // GLFW key values from glfw3.h (ASCII keys retain their ASCII values).
    fun key(androidKeyCode: Int): Int? {
        if (androidKeyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
            return GLFW_KEY_A + androidKeyCode - KeyEvent.KEYCODE_A
        }
        if (androidKeyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            return GLFW_KEY_0 + androidKeyCode - KeyEvent.KEYCODE_0
        }
        if (androidKeyCode in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12) {
            return GLFW_KEY_F1 + androidKeyCode - KeyEvent.KEYCODE_F1
        }
        if (androidKeyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9) {
            return GLFW_KEY_KP_0 + androidKeyCode - KeyEvent.KEYCODE_NUMPAD_0
        }

        return when (androidKeyCode) {
            KeyEvent.KEYCODE_SPACE -> 32
            KeyEvent.KEYCODE_APOSTROPHE -> 39
            KeyEvent.KEYCODE_COMMA -> 44
            KeyEvent.KEYCODE_MINUS -> 45
            KeyEvent.KEYCODE_PERIOD -> 46
            KeyEvent.KEYCODE_SLASH -> 47
            KeyEvent.KEYCODE_NUMPAD_DOT -> GLFW_KEY_KP_DECIMAL
            KeyEvent.KEYCODE_NUMPAD_DIVIDE -> GLFW_KEY_KP_DIVIDE
            KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> GLFW_KEY_KP_SUBTRACT
            KeyEvent.KEYCODE_NUMPAD_COMMA -> return null
            KeyEvent.KEYCODE_SEMICOLON -> 59
            KeyEvent.KEYCODE_EQUALS -> 61
            KeyEvent.KEYCODE_NUMPAD_EQUALS -> GLFW_KEY_KP_EQUAL
            KeyEvent.KEYCODE_LEFT_BRACKET -> 91
            KeyEvent.KEYCODE_BACKSLASH -> 92
            KeyEvent.KEYCODE_RIGHT_BRACKET -> 93
            KeyEvent.KEYCODE_GRAVE -> 96
            KeyEvent.KEYCODE_ESCAPE -> GLFW_KEY_ESCAPE
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> GLFW_KEY_ENTER
            KeyEvent.KEYCODE_TAB -> GLFW_KEY_TAB
            KeyEvent.KEYCODE_DEL -> GLFW_KEY_BACKSPACE
            KeyEvent.KEYCODE_FORWARD_DEL -> GLFW_KEY_DELETE
            KeyEvent.KEYCODE_INSERT -> GLFW_KEY_INSERT
            KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_HOME -> GLFW_KEY_HOME
            KeyEvent.KEYCODE_MOVE_END, KeyEvent.KEYCODE_END -> GLFW_KEY_END
            KeyEvent.KEYCODE_PAGE_UP -> GLFW_KEY_PAGE_UP
            KeyEvent.KEYCODE_PAGE_DOWN -> GLFW_KEY_PAGE_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> GLFW_KEY_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> GLFW_KEY_RIGHT
            KeyEvent.KEYCODE_DPAD_UP -> GLFW_KEY_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> GLFW_KEY_DOWN
            KeyEvent.KEYCODE_SHIFT_LEFT -> GLFW_KEY_LEFT_SHIFT
            KeyEvent.KEYCODE_SHIFT_RIGHT -> GLFW_KEY_RIGHT_SHIFT
            KeyEvent.KEYCODE_CTRL_LEFT -> GLFW_KEY_LEFT_CONTROL
            KeyEvent.KEYCODE_CTRL_RIGHT -> GLFW_KEY_RIGHT_CONTROL
            KeyEvent.KEYCODE_ALT_LEFT -> GLFW_KEY_LEFT_ALT
            KeyEvent.KEYCODE_ALT_RIGHT -> GLFW_KEY_RIGHT_ALT
            KeyEvent.KEYCODE_META_LEFT -> GLFW_KEY_LEFT_SUPER
            KeyEvent.KEYCODE_META_RIGHT -> GLFW_KEY_RIGHT_SUPER
            KeyEvent.KEYCODE_CAPS_LOCK -> GLFW_KEY_CAPS_LOCK
            KeyEvent.KEYCODE_SCROLL_LOCK -> GLFW_KEY_SCROLL_LOCK
            KeyEvent.KEYCODE_NUM_LOCK -> GLFW_KEY_NUM_LOCK
            KeyEvent.KEYCODE_SYSRQ -> GLFW_KEY_PRINT_SCREEN
            KeyEvent.KEYCODE_BREAK -> GLFW_KEY_PAUSE
            KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> GLFW_KEY_KP_MULTIPLY
            KeyEvent.KEYCODE_NUMPAD_ADD -> GLFW_KEY_KP_ADD
            else -> null
        }
    }

    fun modifiers(metaState: Int): Int {
        var result = 0
        if (metaState and (KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON or KeyEvent.META_SHIFT_RIGHT_ON) != 0) {
            result = result or GLFW_MOD_SHIFT
        }
        if (metaState and (KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_CTRL_RIGHT_ON) != 0) {
            result = result or GLFW_MOD_CONTROL
        }
        if (metaState and (KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON or KeyEvent.META_ALT_RIGHT_ON) != 0) {
            result = result or GLFW_MOD_ALT
        }
        if (metaState and (KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON or KeyEvent.META_META_RIGHT_ON) != 0) {
            result = result or GLFW_MOD_SUPER
        }
        if (metaState and KeyEvent.META_CAPS_LOCK_ON != 0) result = result or GLFW_MOD_CAPS_LOCK
        if (metaState and KeyEvent.META_NUM_LOCK_ON != 0) result = result or GLFW_MOD_NUM_LOCK
        return result
    }

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
    const val GLFW_KEY_CAPS_LOCK = 280
    const val GLFW_KEY_SCROLL_LOCK = 281
    const val GLFW_KEY_NUM_LOCK = 282
    const val GLFW_KEY_PRINT_SCREEN = 283
    const val GLFW_KEY_PAUSE = 284
    const val GLFW_KEY_F1 = 290
    const val GLFW_KEY_KP_0 = 320
    const val GLFW_KEY_KP_DECIMAL = 330
    const val GLFW_KEY_KP_DIVIDE = 331
    const val GLFW_KEY_KP_MULTIPLY = 332
    const val GLFW_KEY_KP_SUBTRACT = 333
    const val GLFW_KEY_KP_ADD = 334
    const val GLFW_KEY_KP_EQUAL = 336
    const val GLFW_KEY_LEFT_SHIFT = 340
    const val GLFW_KEY_LEFT_CONTROL = 341
    const val GLFW_KEY_LEFT_ALT = 342
    const val GLFW_KEY_LEFT_SUPER = 343
    const val GLFW_KEY_RIGHT_SHIFT = 344
    const val GLFW_KEY_RIGHT_CONTROL = 345
    const val GLFW_KEY_RIGHT_ALT = 346
    const val GLFW_KEY_RIGHT_SUPER = 347

    const val GLFW_MOD_SHIFT = 0x0001
    const val GLFW_MOD_CONTROL = 0x0002
    const val GLFW_MOD_ALT = 0x0004
    const val GLFW_MOD_SUPER = 0x0008
    const val GLFW_MOD_CAPS_LOCK = 0x0010
    const val GLFW_MOD_NUM_LOCK = 0x0020
}