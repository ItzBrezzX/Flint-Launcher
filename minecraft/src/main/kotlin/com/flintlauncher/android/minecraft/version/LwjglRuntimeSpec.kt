package com.flintlauncher.android.minecraft.version

/**
 * Identifies the LWJGL generation declared by the resolved Minecraft libraries.
 * Metadata is authoritative: do not guess from Minecraft's version string because
 * mod loaders and patched manifests can override native dependencies.
 */
data class LwjglRuntimeSpec(
    val major: Int,
    val version: String,
    val artifacts: List<MavenCoordinate>,
) {
    val isLegacy2: Boolean get() = major == 2
    val isModern3: Boolean get() = major == 3

    companion object {
        private val supportedVersion = Regex("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:[-+].*)?$")

        fun resolve(libraries: List<Library>, environment: Environment): LwjglRuntimeSpec? {
            val coordinates = libraries.asSequence()
                .filter { RuleEvaluator.allows(it.rules, environment) }
                .mapNotNull { MavenCoordinate.parse(it.name) }
                .filter { coordinate ->
                    (coordinate.group == "org.lwjgl" &&
                        (coordinate.artifact == "lwjgl" || coordinate.artifact.startsWith("lwjgl-"))) ||
                        (coordinate.group == "org.lwjgl.lwjgl" &&
                            (coordinate.artifact == "lwjgl" || coordinate.artifact == "lwjgl-platform"))
                }
                .toList()

            if (coordinates.isEmpty()) return null

            val core = coordinates.filter { it.artifact == "lwjgl" }
            val selected = (core.ifEmpty { coordinates }).first()
            val parsed = supportedVersion.matchEntire(selected.version)
                ?: throw IllegalArgumentException("Unsupported LWJGL version: ${selected.version}")
            val major = parsed.groupValues[1].toIntOrNull()
                ?: throw IllegalArgumentException("Invalid LWJGL major version: ${selected.version}")
            if (major != 2 && major != 3) {
                throw IllegalArgumentException("Unsupported LWJGL major version: $major")
            }

            val conflicting = coordinates.filter { it.version != selected.version }
            if (conflicting.isNotEmpty()) {
                val details = conflicting.joinToString { "${it.artifact}:${it.version}" }
                throw IllegalArgumentException(
                    "Mixed LWJGL versions: core is ${selected.version}, but $details are declared",
                )
            }
            if (major == 2 && selected.group != "org.lwjgl.lwjgl") {
                throw IllegalArgumentException("LWJGL 2 must use the org.lwjgl.lwjgl Maven group")
            }
            if (major == 3 && selected.group != "org.lwjgl") {
                throw IllegalArgumentException("LWJGL 3 must use the org.lwjgl Maven group")
            }

            return LwjglRuntimeSpec(major, selected.version, coordinates)
        }
    }
}
