package com.flintlauncher.android.game

import android.view.KeyCharacterMap
import android.view.KeyEvent
import com.flintlauncher.android.nativebridge.GlfwBridge
import com.flintlauncher.android.nativebridge.GlfwKeys

/** Helpers that turn Android input into the events Flint's GLFW shim hands to Minecraft. */
object GameInput {
    fun key(glfwKey: Int, action: Int, mods: Int = 0) {
        GlfwBridge.nativeSendKey(glfwKey, glfwKey, action, mods)
    }

    fun tap(glfwKey: Int) {
        key(glfwKey, GlfwKeys.PRESS)
        key(glfwKey, GlfwKeys.RELEASE)
    }

    fun typeText(text: CharSequence) {
        var index = 0
        while (index < text.length) {
            val codepoint = Character.codePointAt(text, index)
            index += Character.charCount(codepoint)
            if (codepoint == '\n'.code) tap(GlfwKeys.KEY_ENTER) else GlfwBridge.nativeSendChar(codepoint, 0)
        }
    }

    /** Hardware keyboard / IME key events. Returns false when the key has no GLFW equivalent. */
    fun forwardKeyEvent(event: KeyEvent): Boolean {
        val glfwKey = GlfwKeys.fromAndroid(event.keyCode)
        if (glfwKey < 0) return false
        val mods = GlfwKeys.modsFrom(event.metaState)
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                key(glfwKey, if (event.repeatCount > 0) GlfwKeys.REPEAT else GlfwKeys.PRESS, mods)
                val unicode = event.unicodeChar
                val printable = unicode > 0 &&
                    unicode and KeyCharacterMap.COMBINING_ACCENT == 0 &&
                    !Character.isISOControl(unicode) &&
                    mods and GlfwKeys.MOD_CONTROL == 0
                if (printable) GlfwBridge.nativeSendChar(unicode, mods)
            }
            KeyEvent.ACTION_UP -> key(glfwKey, GlfwKeys.RELEASE, mods)
            else -> return false
        }
        return true
    }
}
