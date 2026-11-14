package com.flintlauncher.android.runtime

/**
 * Selects the graphics/API compatibility line required by a Minecraft release.
 *
 * This is Flint-owned policy code. It does not imply that a renderer binary,
 * LWJGL native library, or Android window bridge is installed or usable.
 */
enum class LwjglCompatibilityLine {
    LWJGL2,
    LWJGL3,
}

data class RendererCompatibilityPlan(
    val minecraftVersion: String,
    val renderer: RendererKind,
    val lwjglLine: LwjglCompatibilityLine,
    val rendererId: String,
    val explanation: String,
)

/**
 * Version-aware policy based on the compatibility split used by established
 * Android Java launchers: pre-1.13 clients use the LWJGL 2 API line, while
 * 1.13+ clients use LWJGL 3. GL4ES is the initial OpenGL translation target.
 *
 * The policy is deliberately separate from device probing. A plan is not a
 * claim that GL4ES has been packaged, loaded, or tested on the current GPU.
 */
object RendererCompatibilityPolicy {
    private val releasePattern = Regex("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:[-+].*)?$")

    fun planFor(minecraftVersion: String): RendererCompatibilityPlan? {
        val match = releasePattern.matchEntire(minecraftVersion.trim()) ?: return null
        val major = match.groupValues[1].toIntOrNull() ?: return null
        val minor = match.groupValues[2].toIntOrNull() ?: return null

        val lwjglLine = if (major == 1 && minor < 13) {
            LwjglCompatibilityLine.LWJGL2
        } else {
            LwjglCompatibilityLine.LWJGL3
        }

        val explanation = when (lwjglLine) {
            LwjglCompatibilityLine.LWJGL2 ->
                "Legacy Minecraft uses the LWJGL 2 API line; GL4ES is the initial OpenGL translation target."
            LwjglCompatibilityLine.LWJGL3 ->
                "This release uses the LWJGL 3 API line; GL4ES remains the initial vanilla renderer target."
        }

        return RendererCompatibilityPlan(
            minecraftVersion = minecraftVersion,
            renderer = RendererKind.GL4ES,
            lwjglLine = lwjglLine,
            rendererId = "gl4es",
            explanation = explanation,
        )
    }
}
