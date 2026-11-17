package com.flintlauncher.android

import android.content.Context
import android.graphics.Color
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.text.InputType
import com.flintlauncher.android.nativebridge.FlintGlfwInputMapper
import com.flintlauncher.android.nativebridge.FlintInputEvent
import com.flintlauncher.android.nativebridge.FlintNativeWindowBridge
import com.flintlauncher.android.nativebridge.NativeBridge

/** Tiny invisible text editor that forwards Android IME input to Minecraft's GLFW queue. */
class FlintKeyboardInputView(context: Context) : View(context) {

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(Color.TRANSPARENT)
        alpha = 0.01f
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN

        return object : BaseInputConnection(this, false) {
            private val composing = StringBuilder()

            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (!text.isNullOrEmpty()) sendCharacters(text)
                composing.clear()
                return true
            }

            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                composing.clear()
                if (!text.isNullOrEmpty()) composing.append(text)
                return true
            }

            override fun finishComposingText(): Boolean {
                if (composing.isNotEmpty()) sendCharacters(composing)
                composing.clear()
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                repeat(beforeLength.coerceAtMost(8)) { sendKey(FlintGlfwInputMapper.GLFW_KEY_BACKSPACE, true); sendKey(FlintGlfwInputMapper.GLFW_KEY_BACKSPACE, false) }
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                val key = FlintGlfwInputMapper.key(event.keyCode) ?: return false
                val pressed = event.action == KeyEvent.ACTION_DOWN
                sendKey(key, pressed)
                if (pressed && event.repeatCount == 0 && event.unicodeChar != 0) {
                    enqueue(FlintInputEvent.CHARACTER, code = event.unicodeChar)
                }
                return true
            }

            override fun performEditorAction(actionCode: Int): Boolean {
                sendKey(FlintGlfwInputMapper.GLFW_KEY_ENTER, true)
                sendKey(FlintGlfwInputMapper.GLFW_KEY_ENTER, false)
                return true
            }
        }
    }

    fun showKeyboard() {
        requestFocus()
        post {
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun sendCharacters(text: CharSequence) {
        text.toString().codePoints().forEach { codePoint ->
            enqueue(FlintInputEvent.CHARACTER, code = codePoint)
        }
    }

    private fun sendKey(key: Int, pressed: Boolean) {
        enqueue(FlintInputEvent.KEY, code = key, action = if (pressed) 1 else 0)
    }

    private fun enqueue(type: Int, code: Int = 0, action: Int = 0) {
        if (!NativeBridge.isNativeLoaded) return
        runCatching {
            FlintNativeWindowBridge.enqueue(type = type, code = code, action = action)
        }.onFailure { android.util.Log.w(TAG, "Keyboard input could not be queued", it) }
    }

    private companion object {
        const val TAG = "FlintKeyboardInput"
    }
}
