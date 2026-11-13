package com.flintlauncher.android.minecraft.manifest

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.OffsetDateTime

/** Release channel of a Mojang version entry. Unknown values never crash parsing. */
@Serializable
enum class VersionType {
    @SerialName("release") RELEASE,
    @SerialName("snapshot") SNAPSHOT,
    @SerialName("old_beta") OLD_BETA,
    @SerialName("old_alpha") OLD_ALPHA,
    UNKNOWN,
}

@Serializable
data class LatestVersions(
    val release: String,
    val snapshot: String,
)

/**
 * One row of `version_manifest_v2.json`. `sha1` and `complianceLevel` exist in
 * the v2 manifest only, so they are nullable.
 */
@Serializable
data class VersionEntry(
    val id: String,
    val type: VersionType = VersionType.UNKNOWN,
    val url: String,
    val time: String,
    val releaseTime: String,
    val sha1: String? = null,
    val complianceLevel: Int? = null,
) {
    /** Mojang timestamps use an explicit offset (e.g. `+00:00`), which OffsetDateTime handles. */
    val releaseInstant: Instant
        get() = OffsetDateTime.parse(releaseTime).toInstant()
}

@Serializable
data class VersionManifest(
    val latest: LatestVersions,
    val versions: List<VersionEntry>,
) {
    fun ofType(vararg types: VersionType): List<VersionEntry> =
        versions.filter { it.type in types }

    fun newestFirst(): List<VersionEntry> = versions.sortedByDescending { it.releaseInstant }

    fun oldestFirst(): List<VersionEntry> = versions.sortedBy { it.releaseInstant }

    fun find(id: String): VersionEntry? = versions.firstOrNull { it.id == id }
}
