package com.flintlauncher.android.nativebridge

/**
 * Flint's internal event protocol for the future GLFW 3 compatibility layer.
 *
 * Payloads are five 32-bit integers per event. Floating-point coordinates are
 * transported as raw IEEE-754 bits so JNI does not lose precision.
 */
data class FlintGlfwEvent(
    val type: Type,
    val a: Int,
    val b: Int,
    val c: Int,
    val d: Int,
) {
    enum class Type(val id: Int) {
        KEY(1),
        CHARACTER(2),
        CURSOR_POSITION(3),
        MOUSE_BUTTON(4),
        SCROLL(5),
        FOCUS(6),
        WINDOW_SIZE(7),
        WINDOW_CLOSE(8);

        companion object {
            fun fromId(id: Int): Type? = values().firstOrNull { it.id == id }
        }
    }

    companion object {
        fun cursor(x: Float, y: Float) = FlintGlfwEvent(
            Type.CURSOR_POSITION, x.toRawBits(), y.toRawBits(), 0, 0,
        )

        fun scroll(x: Float, y: Float) = FlintGlfwEvent(
            Type.SCROLL, x.toRawBits(), y.toRawBits(), 0, 0,
        )

        fun character(codePoint: Int) = FlintGlfwEvent(Type.CHARACTER, codePoint, 0, 0, 0)
        fun key(key: Int, scanCode: Int, action: Int, modifiers: Int) =
            FlintGlfwEvent(Type.KEY, key, scanCode, action, modifiers)
        fun mouseButton(button: Int, action: Int, modifiers: Int) =
            FlintGlfwEvent(Type.MOUSE_BUTTON, button, action, modifiers, 0)
        fun focus(focused: Boolean) = FlintGlfwEvent(Type.FOCUS, if (focused) 1 else 0, 0, 0, 0)
        fun windowSize(width: Int, height: Int) =
            FlintGlfwEvent(Type.WINDOW_SIZE, width, height, 0, 0)
        fun windowClose() = FlintGlfwEvent(Type.WINDOW_CLOSE, 0, 0, 0, 0)
    }
}

/**
 * Thread-safe JNI-backed event transport. This is an internal Flint protocol;
 * it does not yet export the GLFW C symbols expected by LWJGL.
 */
object FlintGlfwEventQueue {
    private const val STRIDE = 5

    fun post(event: FlintGlfwEvent) {
        if (!NativeBridge.isNativeLoaded) return
        NativeBridge.nativePostGlfwEvent(
            event.type.id, event.a, event.b, event.c, event.d,
        )
    }

    fun drain(): List<FlintGlfwEvent> {
        if (!NativeBridge.isNativeLoaded) return emptyList()
        val raw = NativeBridge.nativeDrainGlfwEvents() ?: return emptyList()
        if (raw.isEmpty()) return emptyList()

        val result = ArrayList<FlintGlfwEvent>(raw.size / STRIDE)
        var offset = 0
        while (offset + STRIDE <= raw.size) {
            val type = FlintGlfwEvent.Type.fromId(raw[offset])
            if (type != null) {
                result += FlintGlfwEvent(
                    type = type,
                    a = raw[offset + 1],
                    b = raw[offset + 2],
                    c = raw[offset + 3],
                    d = raw[offset + 4],
                )
            }
            offset += STRIDE
        }
        return result
    }

    fun droppedEventCount(): Long =
        if (NativeBridge.isNativeLoaded) NativeBridge.nativeGlfwDroppedEventCount() else 0L
}
