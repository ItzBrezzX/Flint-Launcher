package com.flintlauncher.android.minecraft.version

import com.flintlauncher.android.minecraft.manifest.JavaVersionSpec
import com.flintlauncher.android.minecraft.manifest.MojangJson
import com.flintlauncher.android.minecraft.manifest.VersionType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class DownloadInfo(
    val sha1: String? = null,
    val size: Long? = null,
    val url: String,
    val path: String? = null,
)

@Serializable
data class VersionDownloads(
    val client: DownloadInfo? = null,
    val server: DownloadInfo? = null,
)

@Serializable
data class AssetIndexRef(
    val id: String,
    val sha1: String? = null,
    val size: Long? = null,
    val totalSize: Long? = null,
    val url: String,
)

@Serializable
data class OsRule(
    val name: String? = null,
    val arch: String? = null,
    val version: String? = null,
)

@Serializable
data class Rule(
    val action: String,
    val os: OsRule? = null,
    val features: Map<String, Boolean>? = null,
)

@Serializable
data class LibraryDownloads(
    val artifact: DownloadInfo? = null,
    val classifiers: Map<String, DownloadInfo>? = null,
)

@Serializable
data class Library(
    val name: String,
    val downloads: LibraryDownloads? = null,
    val rules: List<Rule>? = null,
    val natives: Map<String, String>? = null,
    /** Maven repository base URL, used by loader libraries that have no `downloads` block. */
    val url: String? = null,
)

@Serializable
data class LoggingFile(
    val id: String,
    val sha1: String? = null,
    val size: Long? = null,
    val url: String,
)

/** The log4j configuration Mojang ships to neutralise the Log4Shell lookup vulnerability. */
@Serializable
data class LoggingClient(
    /** JVM argument template, containing the placeholder for the config file path. */
    val argument: String,
    val file: LoggingFile,
    val type: String? = null,
)

@Serializable
data class LoggingConfig(val client: LoggingClient? = null)

/** Raw entries: each is a string or an object with `rules` and `value` (string or array). */
@Serializable
data class Arguments(
    val game: List<JsonElement> = emptyList(),
    val jvm: List<JsonElement> = emptyList(),
)

@Serializable
data class VersionJson(
    val id: String,
    val type: VersionType = VersionType.UNKNOWN,
    val mainClass: String? = null,
    val inheritsFrom: String? = null,
    val assets: String? = null,
    val assetIndex: AssetIndexRef? = null,
    val downloads: VersionDownloads? = null,
    val libraries: List<Library> = emptyList(),
    val javaVersion: JavaVersionSpec? = null,
    val logging: LoggingConfig? = null,
    val arguments: Arguments? = null,
    /** Pre-1.13 versions use one space-separated string instead of `arguments`. */
    val minecraftArguments: String? = null,
) {
    /**
     * Applies this (loader) profile on top of its vanilla [parent]. Loader values win;
     * libraries and arguments from the loader come first, then the parent's.
     */
    fun mergedOnto(parent: VersionJson): VersionJson = copy(
        mainClass = mainClass ?: parent.mainClass,
        assets = assets ?: parent.assets,
        assetIndex = assetIndex ?: parent.assetIndex,
        downloads = downloads ?: parent.downloads,
        javaVersion = javaVersion ?: parent.javaVersion,
        logging = logging ?: parent.logging,
        libraries = libraries + parent.libraries,
        arguments = when {
            arguments == null -> parent.arguments
            parent.arguments == null -> arguments
            else -> Arguments(
                game = parent.arguments.game + arguments.game,
                jvm = parent.arguments.jvm + arguments.jvm,
            )
        },
        minecraftArguments = minecraftArguments ?: parent.minecraftArguments,
        inheritsFrom = null,
    )
}

@Serializable
data class AssetObject(val hash: String, val size: Long)

@Serializable
data class AssetIndex(
    val objects: Map<String, AssetObject> = emptyMap(),
    /** Legacy indexes (about 1.7 and older) require materializing objects into their virtual or resources paths. */
    val virtual: Boolean = false,
    @SerialName("map_to_resources") val mapToResources: Boolean = false,
)

object VersionJsonParser {
    fun parse(text: String): VersionJson = MojangJson.decodeFromString(VersionJson.serializer(), text)
    fun parseAssetIndex(text: String): AssetIndex = MojangJson.decodeFromString(AssetIndex.serializer(), text)
}
