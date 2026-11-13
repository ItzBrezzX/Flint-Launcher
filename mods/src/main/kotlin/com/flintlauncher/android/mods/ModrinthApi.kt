package com.flintlauncher.android.mods

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Public, read-only Modrinth API v2 client. Search is always restricted to mods.
 * Search facets use only fields documented by Modrinth: project_type, categories,
 * versions, client_side and server_side. Loaders are a categories facet in search.
 */
class ModrinthApi(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    suspend fun search(filters: ModrinthFilters): ModrinthSearchResult = withContext(Dispatchers.IO) {
        require(filters.offset >= 0) { "Offset cannot be negative" }
        require(filters.limit in 1..100) { "Limit must be between 1 and 100" }
        require(filters.index in SORTS) { "Unsupported Modrinth sort index" }

        val url = baseUrl.newBuilder()
            .addPathSegment("search")
            .addQueryParameter("query", filters.query.trim())
            .addQueryParameter("facets", encodeFacets(buildFacets(filters)))
            .addQueryParameter("index", filters.index)
            .addQueryParameter("offset", filters.offset.toString())
            .addQueryParameter("limit", filters.limit.toString())
            .build()
        get(url, ModrinthSearchResult.serializer())
    }

    suspend fun tags(): ModrinthTags = withContext(Dispatchers.IO) {
        val categories = getArray(
            baseUrl.newBuilder().addPathSegments("tag/category").build(),
            ModrinthCategory.serializer(),
        ).filter { it.projectType == "mod" }.sortedWith(compareBy({ it.header }, { it.name }))

        val loaders = getArray(
            baseUrl.newBuilder().addPathSegments("tag/loader").build(),
            ModrinthLoader.serializer(),
        ).filter { "mod" in it.supportedProjectTypes }.sortedBy { it.name }

        val versions = getArray(
            baseUrl.newBuilder().addPathSegments("tag/game_version").build(),
            ModrinthGameVersion.serializer(),
        )
        ModrinthTags(categories, loaders, versions)
    }

    suspend fun project(projectId: String): ModrinthProjectDetails = withContext(Dispatchers.IO) {
        require(projectId.isNotBlank()) { "Project ID is required" }
        val url = baseUrl.newBuilder()
            .addPathSegment("project")
            .addPathSegment(projectId)
            .build()
        get(url, ModrinthProjectDetails.serializer()).also {
            if (it.projectType != "mod") throw IOException("Modrinth project is not a mod")
        }
    }

    suspend fun projectVersions(
        projectId: String,
        gameVersion: String? = null,
        loader: String? = null,
    ): List<ModrinthVersion> = withContext(Dispatchers.IO) {
        require(projectId.isNotBlank()) { "Project ID is required" }
        val builder = baseUrl.newBuilder()
            .addPathSegment("project")
            .addPathSegment(projectId)
            .addPathSegment("version")
        if (!gameVersion.isNullOrBlank()) {
            builder.addQueryParameter("game_versions", json.encodeToString(listOf(gameVersion)))
        }
        if (!loader.isNullOrBlank()) {
            builder.addQueryParameter("loaders", json.encodeToString(listOf(loader)))
        }
        getArray(builder.build(), ModrinthVersion.serializer())
            .filter { gameVersion.isNullOrBlank() || gameVersion in it.gameVersions }
            .filter { loader.isNullOrBlank() || loader in it.loaders }
            .sortedByDescending { it.datePublished }
    }

    suspend fun version(versionId: String): ModrinthVersion = withContext(Dispatchers.IO) {
        require(versionId.isNotBlank()) { "Version ID is required" }
        val url = baseUrl.newBuilder()
            .addPathSegment("version")
            .addPathSegment(versionId)
            .build()
        get(url, ModrinthVersion.serializer())
    }

    private fun buildFacets(filters: ModrinthFilters): List<List<String>> = buildList {
        add(listOf("project_type:mod"))
        filters.category?.takeIf { it.isNotBlank() }?.let { add(listOf("categories:$it")) }
        filters.loader?.takeIf { it.isNotBlank() }?.let { add(listOf("categories:$it")) }
        filters.gameVersion?.takeIf { it.isNotBlank() }?.let { add(listOf("versions:$it")) }
        filters.clientSide?.let { add(listOf("client_side:${it.facetValue}")) }
        filters.serverSide?.let { add(listOf("server_side:${it.facetValue}")) }
    }

    private fun encodeFacets(facets: List<List<String>>): String = json.encodeToString(facets)

    private fun <T> get(url: HttpUrl, serializer: KSerializer<T>): T {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Modrinth API returned HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("Modrinth API returned an empty response")
            return try {
                json.decodeFromString(serializer, body)
            } catch (e: Exception) {
                throw IOException("Modrinth returned unreadable data", e)
            }
        }
    }

    private fun <T> getArray(url: HttpUrl, serializer: KSerializer<T>): List<T> {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Modrinth API returned HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("Modrinth API returned an empty response")
            return try {
                json.decodeFromString(ListSerializer(serializer), body)
            } catch (e: Exception) {
                throw IOException("Modrinth returned unreadable tag data", e)
            }
        }
    }

    companion object {
        private val SORTS = setOf("relevance", "downloads", "follows", "newest", "updated")
        val DEFAULT_BASE_URL: HttpUrl = HttpUrl.get("https://api.modrinth.com/v2/")
    }
}
