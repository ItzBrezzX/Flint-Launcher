package com.flintlauncher.android.runtime

import com.flintlauncher.android.minecraft.version.MavenCoordinate
import com.flintlauncher.android.minecraft.version.VersionJson

/**
 * API compatibility line required by a Minecraft version's resolved metadata.
 * Alpha/beta IDs and snapshots do not follow the release-number format.
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

object RendererCompatibilityPolicy {
    private val releasePattern = Regex("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:[-+].*)?$")

    /**
     * Prefer the resolved, merged version JSON. This is the reliable route for
     * release, snapshot, alpha/beta and mod-loader profiles.
     */
    fun planFor(version: VersionJson): RendererCompatibilityPlan? {
        val coordinates = version.libraries.mapNotNull { MavenCoordinate.parse(it.name) }
        val lwjgl2 = coordinates.any {
            it.group == "org.lwjgl.lwjgl" && it.artifact == "lwjgl"
        }
        val lwjgl3 = coordinates.any {
            it.group == "org.lwjgl" && it.artifact == "lwjgl"
        }

        val line = when {
            lwjgl2 && !lwjgl3 -> LwjglCompatibilityLine.LWJGL2
            lwjgl3 && !lwjgl2 -> LwjglCompatibilityLine.LWJGL3
            else -> return planFor(version.id)
        }
        return createPlan(version.id, line, "Detected ${line.name} from resolved version libraries.")
    }

    /**
     * Fallback for normal release IDs before the version JSON is available.
     * Snapshot and alpha/beta identifiers must be resolved from their libraries.
     */
    fun planFor(minecraftVersion: String): RendererCompatibilityPlan? {
        val id = minecraftVersion.trim()
        val match = releasePattern.matchEntire(id) ?: return null
        val major = match.groupValues[1].toIntOrNull() ?: return null
        val minor = match.groupValues[2].toIntOrNull() ?: return null
        val line = if (major == 1 && minor < 13) {
            LwjglCompatibilityLine.LWJGL2
        } else {
            LwjglCompatibilityLine.LWJGL3
        }
        return createPlan(
            id,
            line,
            "Inferred from the Minecraft release boundary; resolved libraries remain authoritative.",
        )
    }

    private fun createPlan(
        version: String,
        line: LwjglCompatibilityLine,
        explanation: String,
    ) = RendererCompatibilityPlan(
        minecraftVersion = version,
        renderer = RendererKind.GL4ES,
        lwjglLine = line,
        rendererId = "gl4es",
        explanation = explanation,
    )
}
