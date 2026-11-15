package com.flintlauncher.android

/** Pure mapping from a normalized virtual-joystick vector to GLFW movement keys. */
internal object FlintTouchInputMapper {
    const val KEY_A = 65
    const val KEY_D = 68
    const val KEY_S = 83
    const val KEY_W = 87

    fun keysForVector(x: Float, y: Float, deadZone: Float = 0.35f): Set<Int> {
        require(deadZone in 0f..1f) { "deadZone must be between 0 and 1" }
        return buildSet {
            if (x <= -deadZone) add(KEY_A)
            if (x >= deadZone) add(KEY_D)
            if (y <= -deadZone) add(KEY_W)
            if (y >= deadZone) add(KEY_S)
        }
    }
}
