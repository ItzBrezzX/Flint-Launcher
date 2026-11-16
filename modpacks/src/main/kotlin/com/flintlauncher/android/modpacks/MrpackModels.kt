package com.flintlauncher.android.modpacks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MrpackHashes(val sha1: String? = null, val sha512: String? = null)

@Serializable
data class MrpackEnv(val client: String? = null, val server: String? = null)

@Serializable
data class MrpackFile(
    val path: String,
    val hashes: MrpackHashes,
    val env: MrpackEnv? = null,
    val downloads: List<String> = emptyList(),
    @SerialName("fileSize") val fileSize: Long? = null,
) {
    val clientUnsupported: Boolean get() = env?.client.equals("unsupported", ignoreCase = true)
}

@Serializable
data class MrpackDependencies(
    val minecraft: String? = null,
    @SerialName("fabric-loader") val fabricLoader: String? = null,
    @SerialName("quilt-loader") val quiltLoader: String? = null,
    val forge: String? = null,
    val neoforge: String? = null,
) {
    val loader: String?
        get() = when {
            fabricLoader != null -> "fabric"
            quiltLoader != null -> "quilt"
            forge != null -> "forge"
            neoforge != null -> "neoforge"
            else -> null
        }
    val loaderVersion: String? get() = fabricLoader ?: quiltLoader ?: forge ?: neoforge
}

@Serializable
data class MrpackManifest(
    @SerialName("formatVersion") val formatVersion: Int,
    val game: String,
    @SerialName("versionId") val versionId: String,
    val name: String,
    val summary: String? = null,
    val files: List<MrpackFile> = emptyList(),
    val dependencies: MrpackDependencies = MrpackDependencies(),
) {
    init {
        require(formatVersion in SUPPORTED_FORMAT_VERSIONS) {
            "Unsupported mrpack format version: $formatVersion"
        }
        require(game.equals("minecraft", ignoreCase = true)) {
            "Not a Minecraft mrpack: game=$game"
        }
        require(!dependencies.minecraft.isNullOrBlank()) {
            "mrpack does not declare a Minecraft version"
        }
        require(name.isNotBlank()) { "mrpack name is empty" }
    }

    companion object {
        val SUPPORTED_FORMAT_VERSIONS = 1..2
    }
}
