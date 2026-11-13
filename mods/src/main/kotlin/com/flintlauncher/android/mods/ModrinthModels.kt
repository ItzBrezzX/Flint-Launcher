package com.flintlauncher.android.mods

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModrinthSearchResult(
    val hits: List<ModrinthProject> = emptyList(),
    val offset: Int = 0,
    val limit: Int = 10,
    @SerialName("total_hits") val totalHits: Int = 0,
)

@Serializable
data class ModrinthProject(
    @SerialName("project_id") val projectId: String,
    @SerialName("project_type") val projectType: String,
    val title: String,
    val description: String = "",
    val author: String = "",
    val categories: List<String> = emptyList(),
    @SerialName("display_categories") val displayCategories: List<String> = emptyList(),
    val versions: List<String> = emptyList(),
    val downloads: Long = 0,
    val follows: Long = 0,
    @SerialName("icon_url") val iconUrl: String? = null,
    val slug: String? = null,
    val environment: List<String> = emptyList(),
    @SerialName("latest_version") val latestVersion: String? = null,
)

@Serializable
data class ModrinthVersion(
    val id: String,
    @SerialName("project_id") val projectId: String,
    val name: String = "",
    @SerialName("version_number") val versionNumber: String,
    val changelog: String? = null,
    val dependencies: List<ModrinthDependency> = emptyList(),
    @SerialName("game_versions") val gameVersions: List<String> = emptyList(),
    @SerialName("version_type") val versionType: String = "release",
    val loaders: List<String> = emptyList(),
    @SerialName("date_published") val datePublished: String = "",
    val downloads: Long = 0,
    val files: List<ModrinthFile> = emptyList(),
)

@Serializable
data class ModrinthFile(
    val hashes: Map<String, String> = emptyMap(),
    val url: String,
    val filename: String,
    val primary: Boolean = false,
    val size: Long = 0,
)

@Serializable
data class ModrinthDependency(
    @SerialName("version_id") val versionId: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("dependency_type") val dependencyType: String = "optional",
)

@Serializable
data class ModrinthCategory(
    val name: String,
    val header: String = "",
    @SerialName("project_type") val projectType: String = "mod",
)

@Serializable
data class ModrinthLoader(
    val name: String,
    @SerialName("supported_project_types") val supportedProjectTypes: List<String> = emptyList(),
)

@Serializable
data class ModrinthGameVersion(
    val version: String,
    @SerialName("version_type") val versionType: String = "release",
    val date: String = "",
    val major: Boolean = false,
)

/** Modrinth's environment facet describes where a mod is intended to run. */
enum class ModrinthEnvironment(val facetValue: String, val label: String) {
    CLIENT_AND_SERVER("client_and_server", "Client + Server"),
    CLIENT_ONLY("client_only", "Client only"),
    CLIENT_OPTIONAL_SERVER("client_only_server_optional", "Client (server optional)"),
    SINGLEPLAYER_ONLY("singleplayer_only", "Single-player"),
    SERVER_ONLY("server_only", "Server only"),
    SERVER_OPTIONAL_CLIENT("server_only_client_optional", "Server (client optional)"),
    DEDICATED_SERVER_ONLY("dedicated_server_only", "Dedicated server"),
    CLIENT_OR_SERVER("client_or_server", "Client or server"),
    ANY("unknown", "Not specified"),
}

data class ModrinthFilters(
    val query: String = "",
    val category: String? = null,
    val environment: ModrinthEnvironment? = null,
    val gameVersion: String? = null,
    val loader: String? = null,
    val index: String = "relevance",
    val offset: Int = 0,
    val limit: Int = 20,
)

data class ModrinthTags(
    val categories: List<ModrinthCategory>,
    val loaders: List<ModrinthLoader>,
    val gameVersions: List<ModrinthGameVersion>,
)

@Serializable
data class InstalledMod(
    val projectId: String,
    val projectTitle: String,
    val versionId: String,
    val versionNumber: String,
    val fileName: String,
    val installedAt: String,
)
