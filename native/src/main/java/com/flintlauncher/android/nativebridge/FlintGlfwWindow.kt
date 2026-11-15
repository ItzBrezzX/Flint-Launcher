package com.flintlauncher.android.nativebridge

/**
 * Stateful Flint-owned game-window facade over the Android EGL Surface.
 *
 * This provides the window semantics needed by the future GLFW ABI adapter:
 * dimensions, focus, close flag, event polling, and explicit frame requests.
 * It is not yet the exported C GLFW ABI consumed directly by LWJGL.
 */
class FlintGlfwWindow internal constructor(
    private val surfaceView: FlintGameSurfaceView,
) {
    data class Size(val width: Int, val height: Int)
    data class State(val size: Size, val focused: Boolean, val shouldClose: Boolean)

    fun state(): State {
        val raw = if (NativeBridge.isNativeLoaded) {
            runCatching { NativeBridge.nativeGlfwGetWindowSize() }.getOrNull()
        } else null
        val width = raw?.getOrNull(0) ?: 0
        val height = raw?.getOrNull(1) ?: 0
        val focused = (raw?.getOrNull(2) ?: 0) != 0
        return State(
            size = Size(width, height),
            focused = focused,
            shouldClose = shouldClose(),
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

    /** Request a frame after a state or input change. */
    fun requestFrame(): Boolean = surfaceView.renderSession?.requestFrame() ?: false
}
