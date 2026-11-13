package com.flintlauncher.android.installer

import java.io.File

class InstallPlanException(message: String) : IllegalStateException(message)

/**
 * Shared game files (versions, libraries, assets) live under [root], like the official
 * launcher. Per-profile data (mods, saves, config) lives in the profile's own directory.
 * Every path built from remote metadata is checked so it cannot escape [root].
 */
class GameLayout(root: File) {
    val root: File = root.absoluteFile
    val versionsDir = File(this.root, "versions")
    val librariesDir = File(this.root, "libraries")
    val assetsDir = File(this.root, "assets")

    fun versionJar(id: String): File = safeChild(versionsDir, "$id/$id.jar")
    fun versionJson(id: String): File = safeChild(versionsDir, "$id/$id.json")
    fun library(path: String): File = safeChild(librariesDir, path)
    fun assetIndex(id: String): File = safeChild(assetsDir, "indexes/$id.json")
    /** Written only after an install fully succeeded; its presence means "installed". */
    fun installMarker(id: String): File = safeChild(versionsDir, "$id/.flint-installed")
    fun logConfig(id: String): File = safeChild(assetsDir, "log_configs/$id")
    fun assetObject(hash: String): File = safeChild(assetsDir, "objects/${hash.take(2)}/$hash")

    companion object {
        fun safeChild(base: File, relative: String): File {
            val baseCanonical = base.canonicalFile
            val target = File(baseCanonical, relative).canonicalFile
            if (!target.path.startsWith(baseCanonical.path + File.separator)) {
                throw InstallPlanException("Refusing path outside the game directory: $relative")
            }
            return target
        }
    }
}
