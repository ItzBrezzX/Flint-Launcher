package com.flintlauncher.android.mods

import java.io.File

/**
 * Description of a loader installer that must be run as a separate Java process
 * (Forge and NeoForge). Fabric and Quilt instead publish a ready-made profile JSON via
 * `FabricStyleMeta` in :installer and need no process.
 *
 * The contract: download the installer jar (checksum-verified via DownloadManager), then
 * run `java -jar <installer.jar> <args>` with [workingDir] set to the game root. On
 * success the installer writes a new version JSON with `inheritsFrom` pointing at the
 * vanilla version, which Flint then installs through the normal pipeline.
 *
 * Exact CLI flags are pinned to what each loader's installer actually accepts; the values
 * below match the documented installers but are re-verified at runtime (the process exit
 * code + presence of the output JSON are the source of truth). On Android the "process"
 * runs inside Flint's own JVM via the runtime shard's process-spike, not a fork.
 */
data class InstallerProcessSpec(
    val installerJar: File,
    val args: List<String>,
    val workingDir: File,
    /** The version JSON the installer is expected to create (checked after it exits). */
    val expectedVersionJson: File,
    val loaderKind: String,
)

object ForgeInstallerContract {
    const val LOADER_KIND = "forge"

    /** Maven URL for the Forge installer jar. Forge versions look like "47.1.0". */
    fun installerUrl(minecraftVersion: String, forgeVersion: String): String =
        "https://maven.minecraftforge.net/net/minecraftforge/forge/" +
            "$minecraftVersion-$forgeVersion/forge-$minecraftVersion-$forgeVersion-installer.jar"

    /** The version id the installer will create, e.g. "1.20.1-forge-47.1.0". */
    fun installedVersionId(minecraftVersion: String, forgeVersion: String): String =
        "$minecraftVersion-forge-$forgeVersion"

    fun spec(installerJar: File, gameRoot: File, minecraftVersion: String, forgeVersion: String): InstallerProcessSpec =
        InstallerProcessSpec(
            installerJar = installerJar,
            args = listOf("--installClient"),
            workingDir = gameRoot,
            expectedVersionJson = File(File(gameRoot, "versions"), "${installedVersionId(minecraftVersion, forgeVersion)}/${installedVersionId(minecraftVersion, forgeVersion)}.json"),
            loaderKind = LOADER_KIND,
        )
}

object NeoForgeInstallerContract {
    const val LOADER_KIND = "neoforge"

    /**
     * Maven URL for the NeoForge installer jar. NeoForge versions are the full artifact
     * version (e.g. "21.0.0" for MC 1.21, "20.4.0" for 1.20.4); the Minecraft version is
     * implicit in the NeoForge version line and resolved from maven-metadata.xml.
     */
    fun installerUrl(neoforgeVersion: String): String =
        "https://maven.neoforged.net/releases/net/neoforged/neoforge/" +
            "$neoforgeVersion/neoforge-$neoforgeVersion-installer.jar"

    fun installedVersionId(neoforgeVersion: String): String = "neoforge-$neoforgeVersion"

    fun spec(installerJar: File, gameRoot: File, neoforgeVersion: String): InstallerProcessSpec =
        InstallerProcessSpec(
            installerJar = installerJar,
            args = listOf("--install-client"),
            workingDir = gameRoot,
            expectedVersionJson = File(File(gameRoot, "versions"), "${installedVersionId(neoforgeVersion)}/${installedVersionId(neoforgeVersion)}.json"),
            loaderKind = LOADER_KIND,
        )
}
