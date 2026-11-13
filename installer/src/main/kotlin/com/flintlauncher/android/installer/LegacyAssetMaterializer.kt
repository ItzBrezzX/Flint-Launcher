package com.flintlauncher.android.installer

import com.flintlauncher.android.minecraft.version.AssetIndex
import java.io.File
import java.security.MessageDigest

/**
 * Recreates the path-based asset layouts expected by pre-1.7 Minecraft clients.
 *
 * Modern clients read assets/objects/<prefix>/<sha1> through the index. Older
 * snapshots require copies at assets/virtual/legacy/<name> or resources/<name>.
 * The content-addressed source has already been downloaded and SHA-1 verified.
 */
object LegacyAssetMaterializer {
    private val sha1Pattern = Regex("^[0-9a-fA-F]{40}$")

    fun materialize(index: AssetIndex, layout: GameLayout): List<File> {
        if (!index.virtual && !index.mapToResources) return emptyList()

        val created = mutableListOf<File>()
        for ((name, asset) in index.objects) {
            if (name.isBlank() || name.contains('\\')) {
                throw InstallPlanException("Invalid legacy asset path: $name")
            }
            if (!sha1Pattern.matches(asset.hash)) {
                throw InstallPlanException("Invalid asset hash in legacy index: ${asset.hash}")
            }

            val source = layout.assetObject(asset.hash)
            if (!source.isFile || source.length() != asset.size) {
                throw InstallPlanException("Legacy asset source is missing or incomplete: ${asset.hash}")
            }

            val destination = if (index.mapToResources) {
                layout.mappedResource(name)
            } else {
                layout.legacyVirtualAsset(name)
            }
            if (destination.isFile && destination.length() == asset.size && sha1Matches(destination, asset.hash)) {
                continue
            }

            destination.parentFile?.mkdirs()
            source.copyTo(destination, overwrite = true)
            if (destination.length() != asset.size || !sha1Matches(destination, asset.hash)) {
                destination.delete()
                throw InstallPlanException("Could not materialize legacy asset: $name")
            }
            created += destination
        }
        return created
    }

    private fun sha1Matches(file: File, expected: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.equals(expected, ignoreCase = true)
    }
}
