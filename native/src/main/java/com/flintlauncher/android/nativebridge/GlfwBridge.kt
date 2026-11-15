package com.flintlauncher.android.nativebridge

import android.view.Surface

/**
 * Android-side handle on Flint's GLFW shim (libglfw.so).
 *
 * The same library file is also dlopen()ed by LWJGL inside the JVM; both resolve to one instance,
 * so what is published here (the Surface, input events) is what the game's GLFW calls consume.
 * Everything below is cheap and non-blocking: it only queues data for the game thread.
 */
object GlfwBridge {
    private var loadError: String? = null
    private var loaded = false

    init {
        try {
            System.loadLibrary("glfw")
            loaded = true
        } catch (e: UnsatisfiedLinkError) {
            loadError = e.message
        }
    }

    val isLoaded: Boolean get() = loaded
    val error: String? get() = loadError

    external fun nativeSurfaceCreated(surface: Surface, width: Int, height: Int)
    external fun nativeSurfaceChanged(width: Int, height: Int)
    external fun nativeSurfaceDestroyed()

    external fun nativeSendKey(key: Int, scancode: Int, action: Int, mods: Int)
    external fun nativeSendChar(codepoint: Int, mods: Int)
    external fun nativeSendMouseButton(button: Int, action: Int, mods: Int)
    external fun nativeSendCursorPos(x: Double, y: Double)
    external fun nativeSendCursorDelta(dx: Double, dy: Double)
    external fun nativeSendScroll(dx: Double, dy: Double)
    external fun nativeSendFocus(focused: Boolean)
    external fun nativeRequestClose()

    /** Frames presented by the game so far; 0 means Minecraft has not drawn anything yet. */
    external fun nativeFrameCount(): Long

    /** True while the game has hidden/grabbed the cursor (in-world); drags then mean "look around". */
    external fun nativeIsCursorGrabbed(): Boolean
}
