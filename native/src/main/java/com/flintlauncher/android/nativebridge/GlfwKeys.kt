package com.flintlauncher.android.nativebridge

import android.view.KeyEvent

/** GLFW key/button/modifier constants and the Android KeyEvent -> GLFW key mapping. */
object GlfwKeys {
    const val RELEASE = 0
    const val PRESS = 1
    const val REPEAT = 2

    const val MOD_SHIFT = 0x0001
    const val MOD_CONTROL = 0x0002
    const val MOD_ALT = 0x0004
    const val MOD_SUPER = 0x0008

    const val MOUSE_LEFT = 0
    const val MOUSE_RIGHT = 1
    const val MOUSE_MIDDLE = 2

    const val KEY_SPACE = 32
    const val KEY_APOSTROPHE = 39
    const val KEY_COMMA = 44
    const val KEY_MINUS = 45
    const val KEY_PERIOD = 46
    const val KEY_SLASH = 47
    const val KEY_0 = 48
    const val KEY_SEMICOLON = 59
    const val KEY_EQUAL = 61
    const val KEY_A = 65
    const val KEY_D = 68
    const val KEY_E = 69
    const val KEY_Q = 81
    const val KEY_S = 83
    const val KEY_T = 84
    const val KEY_W = 87
    const val KEY_LEFT_BRACKET = 91
    const val KEY_BACKSLASH = 92
    const val KEY_RIGHT_BRACKET = 93
    const val KEY_GRAVE_ACCENT = 96
    const val KEY_ESCAPE = 256
    const val KEY_ENTER = 257
    const val KEY_TAB = 258
    const val KEY_BACKSPACE = 259
    const val KEY_INSERT = 260
    const val KEY_DELETE = 261
    const val KEY_RIGHT = 262
    const val KEY_LEFT = 263
    const val KEY_DOWN = 264
    const val KEY_UP = 265
    const val KEY_PAGE_UP = 266
    const val KEY_PAGE_DOWN = 267
    const val KEY_HOME = 268
    const val KEY_END = 269
    const val KEY_CAPS_LOCK = 280
    const val KEY_F1 = 290
    const val KEY_F5 = 294
    const val KEY_F11 = 300
    const val KEY_LEFT_SHIFT = 340
    const val KEY_LEFT_CONTROL = 341
    const val KEY_LEFT_ALT = 342
    const val KEY_RIGHT_SHIFT = 344
    const val KEY_RIGHT_CONTROL = 345
    const val KEY_RIGHT_ALT = 346

    /** Returns the GLFW key for an Android key code, or -1 when it has no equivalent. */
    fun fromAndroid(keyCode: Int): Int = when (keyCode) {
        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> KEY_A + (keyCode - KeyEvent.KEYCODE_A)
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> KEY_0 + (keyCode - KeyEvent.KEYCODE_0)
        in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 -> KEY_F1 + (keyCode - KeyEvent.KEYCODE_F1)
        KeyEvent.KEYCODE_SPACE -> KEY_SPACE
        KeyEvent.KEYCODE_APOSTROPHE -> KEY_APOSTROPHE
        KeyEvent.KEYCODE_COMMA -> KEY_COMMA
        KeyEvent.KEYCODE_MINUS -> KEY_MINUS
        KeyEvent.KEYCODE_PERIOD -> KEY_PERIOD
        KeyEvent.KEYCODE_SLASH -> KEY_SLASH
        KeyEvent.KEYCODE_SEMICOLON -> KEY_SEMICOLON
        KeyEvent.KEYCODE_EQUALS -> KEY_EQUAL
        KeyEvent.KEYCODE_LEFT_BRACKET -> KEY_LEFT_BRACKET
        KeyEvent.KEYCODE_BACKSLASH -> KEY_BACKSLASH
        KeyEvent.KEYCODE_RIGHT_BRACKET -> KEY_RIGHT_BRACKET
        KeyEvent.KEYCODE_GRAVE -> KEY_GRAVE_ACCENT
        KeyEvent.KEYCODE_ESCAPE -> KEY_ESCAPE
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> KEY_ENTER
        KeyEvent.KEYCODE_TAB -> KEY_TAB
        KeyEvent.KEYCODE_DEL -> KEY_BACKSPACE
        KeyEvent.KEYCODE_FORWARD_DEL -> KEY_DELETE
        KeyEvent.KEYCODE_INSERT -> KEY_INSERT
        KeyEvent.KEYCODE_DPAD_RIGHT -> KEY_RIGHT
        KeyEvent.KEYCODE_DPAD_LEFT -> KEY_LEFT
        KeyEvent.KEYCODE_DPAD_DOWN -> KEY_DOWN
        KeyEvent.KEYCODE_DPAD_UP -> KEY_UP
        KeyEvent.KEYCODE_PAGE_UP -> KEY_PAGE_UP
        KeyEvent.KEYCODE_PAGE_DOWN -> KEY_PAGE_DOWN
        KeyEvent.KEYCODE_MOVE_HOME -> KEY_HOME
        KeyEvent.KEYCODE_MOVE_END -> KEY_END
        KeyEvent.KEYCODE_CAPS_LOCK -> KEY_CAPS_LOCK
        KeyEvent.KEYCODE_SHIFT_LEFT -> KEY_LEFT_SHIFT
        KeyEvent.KEYCODE_SHIFT_RIGHT -> KEY_RIGHT_SHIFT
        KeyEvent.KEYCODE_CTRL_LEFT -> KEY_LEFT_CONTROL
        KeyEvent.KEYCODE_CTRL_RIGHT -> KEY_RIGHT_CONTROL
        KeyEvent.KEYCODE_ALT_LEFT -> KEY_LEFT_ALT
        KeyEvent.KEYCODE_ALT_RIGHT -> KEY_RIGHT_ALT
        else -> -1
    }

    fun modsFrom(metaState: Int): Int {
        var mods = 0
        if (metaState and KeyEvent.META_SHIFT_ON != 0) mods = mods or MOD_SHIFT
        if (metaState and KeyEvent.META_CTRL_ON != 0) mods = mods or MOD_CONTROL
        if (metaState and KeyEvent.META_ALT_ON != 0) mods = mods or MOD_ALT
        if (metaState and KeyEvent.META_META_ON != 0) mods = mods or MOD_SUPER
        return mods
    }
}
