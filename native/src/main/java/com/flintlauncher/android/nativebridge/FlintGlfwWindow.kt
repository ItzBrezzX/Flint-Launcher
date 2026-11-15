package com.flintlauncher.android.nativebridge

/**
 * Stateful Flint-owned game-window facade over the Android EGL Surface.
 *
 * LWJGL uses the exported native libglfw.so ABI for its C calls. This Kotlin
 * facade remains a diagnostics/UI view of the same Flint window state.
 */
class FlintGlfwWindow internal constructor(
    private val surfaceView: FlintGameSurfaceView,
) {
    data class Size(val width: Int, val height: Int)
    data class CursorPosition(val x: Float, val y: Float)
    data class State(
        val size: Size,
        val focused: Boolean,
        val shouldClose: Boolean,
        val cursor: CursorPosition,
    )

    fun state(): State {
        val raw = if (NativeBridge.isNativeLoaded) {
            runCatching { NativeBridge.nativeGlfwGetWindowSize() }.getOrNull()
        } else null
        val cursor = cursorPosition()
        return State(
            size = Size(raw?.getOrNull(0) ?: 0, raw?.getOrNull(1) ?: 0),
            focused = (raw?.getOrNull(2) ?: 0) != 0,
            shouldClose = shouldClose(),
            cursor = cursor,
        )
    }

    fun shouldClose(): Boolean =
        NativeBridge.isNativeLoaded &&
            runCatching { NativeBridge.nativeGlfwWindowShouldClose() }.getOrDefault(false)

    fun setShouldClose(value: Boolean) {
        if (NativeBridge.isNativeLoaded) {
            runCatching { NativeBridge.nativeGlfwSetWindowShouldClose(value) }
        }
    }

    /** GLFW-compatible key state: 0 release, 1 press, 2 repeat. */
    fun keyState(key: Int): Int =
        if (NativeBridge.isNativeLoaded && key in 0..511) {
            runCatching { NativeBridge.nativeGlfwGetKeyState(key) }.getOrDefault(0)
        } else 0

    /** GLFW-compatible mouse-button state: 0 release, 1 press. */
    fun mouseButtonState(button: Int): Int =
        if (NativeBridge.isNativeLoaded && button in 0..7) {
            runCatching { NativeBridge.nativeGlfwGetMouseButtonState(button) }.getOrDefault(0)
        } else 0

    fun cursorPosition(): CursorPosition {
        val raw = if (NativeBridge.isNativeLoaded) {
            runCatching { NativeBridge.nativeGlfwGetCursorPosition() }.getOrNull()
        } else null
        return CursorPosition(raw?.getOrNull(0) ?: 0f, raw?.getOrNull(1) ?: 0f)
    }

    /** Request a frame after a state or input change. */
    fun requestFrame(): Boolean = surfaceView.renderSession?.requestFrame() ?: false
}
