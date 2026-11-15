package com.flintlauncher.android.nativebridge

/**
 * Narrow Android-to-native boundary. The consumer (the Flint GLFW adapter)
 * drains events on the game/window thread; Android callbacks only enqueue.
 */
object FlintNativeWindowBridge {
    fun enqueue(
        type: Int,
        code: Int = 0,
        action: Int = 0,
        modifiers: Int = 0,
        x: Double = 0.0,
        y: Double = 0.0,
    ) {
        check(NativeBridge.isNativeLoaded) {
            "Flint native library is not loaded: ${NativeBridge.nativeLoadError}"
        }
        NativeBridge.nativePushInputEvent(type, code, action, modifiers, x, y)
    }

    fun poll(maximum: Int = 256): List<FlintInputEvent> {
        require(maximum in 1..1024) { "maximum must be between 1 and 1024" }
        check(NativeBridge.isNativeLoaded) {
            "Flint native library is not loaded: ${NativeBridge.nativeLoadError}"
        }
        return FlintInputEvent.decode(NativeBridge.nativePollInputEvents(maximum))
    }

    fun droppedEventCount(): Long {
        check(NativeBridge.isNativeLoaded) {
            "Flint native library is not loaded: ${NativeBridge.nativeLoadError}"
        }
        return NativeBridge.nativeDroppedInputEventCount()
    }
}
