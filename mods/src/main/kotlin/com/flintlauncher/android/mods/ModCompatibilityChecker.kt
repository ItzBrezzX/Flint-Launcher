package com.flintlauncher.android.mods

/**
 * Checks a Modrinth version against a profile's Minecraft version + loader before any
 * download. A version is compatible only when it explicitly supports both. Modrinth
 * versions list exact game versions and loaders; wildcard support is never assumed.
 */
data class CompatibilityReport(
    val compatible: Boolean,
    val reasons: List<String>,
) {
    companion object {
        val OK = CompatibilityReport(true, emptyList())
    }
}

object ModCompatibilityChecker {
    fun check(version: ModrinthVersion, minecraftVersion: String, loader: String): CompatibilityReport {
        val reasons = mutableListOf<String>()
        if (minecraftVersion !in version.gameVersions) {
            reasons += "This mod version does not support Minecraft $minecraftVersion " +
                "(supports: ${version.gameVersions.take(5).joinToString()}${if (version.gameVersions.size > 5) ", …" else ""})"
        }
        if (loader !in version.loaders.map { it.lowercase() }) {
            reasons += "This mod version does not support the $loader loader " +
                "(supports: ${version.loaders.joinToString()})"
        }
        if (version.files.none { it.filename.endsWith(".jar", ignoreCase = true) }) {
            reasons += "This mod version contains no downloadable JAR file"
        }
        return if (reasons.isEmpty()) CompatibilityReport.OK else CompatibilityReport(false, reasons)
    }

    /** Convenience: the first compatible version for a project, newest first. */
    fun pickCompatible(
        versions: List<ModrinthVersion>,
        minecraftVersion: String,
        loader: String,
    ): ModrinthVersion? = versions.firstOrNull { check(it, minecraftVersion, loader).compatible }
}
