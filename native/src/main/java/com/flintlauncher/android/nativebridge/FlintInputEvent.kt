package com.flintlauncher.android.nativebridge

/**
 * Platform-neutral event record exchanged between Android and the future
 * LWJGL/GLFW platform adapter. Key codes are Android KeyEvent codes until
 * translated by the GLFW adapter; action values follow Android conventions.
 */
data class FlintInputEvent(
    val type: Int,
    val code: Int,
    val action: Int,
    val modifiers: Int,
    val x: Double,
    val y: Double,
) {
    companion object {
        const val KEY = 1
        const val CHARACTER = 2
        const val CURSOR = 3
        const val MOUSE_BUTTON = 4
        const val SCROLL = 5
        const val FOCUS = 6
        const val WINDOW_SIZE = 7
        const val FRAMEBUFFER_SIZE = 8

        internal fun decode(packed: LongArray): List<FlintInputEvent> {
            require(packed.size % 6 == 0) { "Malformed native input event buffer" }
            return packed.asList().chunked(6).map { fields ->
                FlintInputEvent(
                    type = fields[0].toInt(),
                    code = fields[1].toInt(),
                    action = fields[2].toInt(),
                    modifiers = fields[3].toInt(),
                    x = java.lang.Double.longBitsToDouble(fields[4]),
                    y = java.lang.Double.longBitsToDouble(fields[5]),
                )
            }
        }
    }
}
