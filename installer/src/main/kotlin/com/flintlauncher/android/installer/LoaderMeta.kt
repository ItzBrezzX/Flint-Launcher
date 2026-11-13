package com.flintlauncher.android.installer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

enum class LoaderKind { VANILLA, FABRIC, QUILT, FORGE, NEOFORGE }

data class LoaderVersion(val version: String, val stable: Boolean)

/** Result of checking a loader against a Minecraft version before anything is downloaded. */
sealed interface LoaderCompatibility {
    data class Compatible(val versions: List<LoaderVersion>) : LoaderCompatibility
    data class Incompatible(val reason: String) : LoaderCompatibility
    /** Known loader, but this launcher cannot install it yet. */
    data class Unsupported(val reason: String) : LoaderCompatibility
}

@Serializable
private data class LoaderEntry(val loader: LoaderInfo)

@Serializable
private data class LoaderInfo(val version: String, val stable: Boolean = false)

/**
 * Fabric and Quilt expose the same metadata shape:
 * `GET {base}/versions/loader/{game}` and `.../{game}/{loader}/profile/json`.
 * An empty list means the loader does not support that Minecraft version.
 */
class FabricStyleMeta(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loaderVersions(gameVersion: String): List<LoaderVersion> {
        val url = baseUrl.newBuilder().addPathSegments("versions/loader").addPathSegment(gameVersion).build()
        val body = get(url)
        return json.decodeFromString(ListSerializer(LoaderEntry.serializer()), body)
            .map { LoaderVersion(it.loader.version, it.loader.stable) }
    }

    suspend fun profileJson(gameVersion: String, loaderVersion: String): String {
        val url = baseUrl.newBuilder()
            .addPathSegments("versions/loader")
            .addPathSegment(gameVersion)
            .addPathSegment(loaderVersion)
            .addPathSegments("profile/json")
            .build()
        return get(url)
    }

    private suspend fun get(url: HttpUrl): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Loader metadata request failed: HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Empty loader metadata response")
        }
    }

    companion object {
        val FABRIC_BASE: HttpUrl = "https://meta.fabricmc.net/v2/".toHttpUrl()
        val QUILT_BASE: HttpUrl = "https://meta.quiltmc.org/v3/".toHttpUrl()
    }
}

class LoaderCompatibilityChecker(
    private val fabric: FabricStyleMeta,
    private val quilt: FabricStyleMeta,
) {
    /** Network failures surface as IOException so callers can tell them apart from "incompatible". */
    suspend fun check(loader: LoaderKind, gameVersion: String): LoaderCompatibility = when (loader) {
        LoaderKind.VANILLA -> LoaderCompatibility.Compatible(emptyList())
        LoaderKind.FABRIC -> fromMeta("Fabric", fabric, gameVersion)
        LoaderKind.QUILT -> fromMeta("Quilt", quilt, gameVersion)
        LoaderKind.FORGE, LoaderKind.NEOFORGE -> LoaderCompatibility.Unsupported(
            "${loader.name.lowercase().replaceFirstChar { it.uppercase() }} installers run on the Java runtime, which is not implemented yet",
        )
    }

    private suspend fun fromMeta(name: String, meta: FabricStyleMeta, gameVersion: String): LoaderCompatibility {
        val versions = meta.loaderVersions(gameVersion)
        return if (versions.isEmpty()) {
            LoaderCompatibility.Incompatible("$name has no loader for Minecraft $gameVersion")
        } else {
            LoaderCompatibility.Compatible(versions)
        }
    }

    companion object {
        /** The newest stable loader, or the newest of any kind when none is marked stable. */
        fun recommended(versions: List<LoaderVersion>): LoaderVersion? =
            versions.firstOrNull { it.stable } ?: versions.firstOrNull()
    }
}
