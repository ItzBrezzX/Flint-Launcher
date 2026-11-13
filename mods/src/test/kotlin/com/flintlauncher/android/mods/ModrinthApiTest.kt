package com.flintlauncher.android.mods

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ModrinthApiTest {
    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun api() = ModrinthApi(OkHttpClient(), server.url("/v2/"))

    @Test
    fun searchAlwaysRestrictsResultsToModsAndAppliesDocumentedFacets() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "hits": [{
                    "project_id": "sodium-id",
                    "project_type": "mod",
                    "title": "Sodium",
                    "description": "Rendering optimization",
                    "author": "JellySquid",
                    "categories": ["optimization", "fabric"],
                    "display_categories": ["optimization"],
                    "versions": ["1.21.1"],
                    "downloads": 1200,
                    "follows": 50,
                    "icon_url": null,
                    "client_side": "required",
                    "server_side": "optional"
                  }],
                  "offset": 0,
                  "limit": 20,
                  "total_hits": 1
                }
                """.trimIndent(),
            ),
        )

        val result = api().search(
            ModrinthFilters(
                query = "sodium",
                category = "optimization",
                loader = "fabric",
                gameVersion = "1.21.1",
                clientSide = ModrinthSideSupport.REQUIRED,
                serverSide = ModrinthSideSupport.OPTIONAL,
            ),
        )

        assertEquals(1, result.totalHits)
        assertEquals("Sodium", result.hits.single().title)
        assertEquals("required", result.hits.single().clientSide)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/v2/search", request.requestUrl?.encodedPath)
        assertEquals("sodium", request.requestUrl?.queryParameter("query"))
        val facets = Json.parseToJsonElement(request.requestUrl!!.queryParameter("facets")!!).jsonArray
            .flatMap { it.jsonArray }
            .map { it.jsonPrimitive.content }
            .toSet()
        assertTrue("project_type:mod" in facets)
        assertTrue("categories:optimization" in facets)
        assertTrue("categories:fabric" in facets)
        assertTrue("versions:1.21.1" in facets)
        assertTrue("client_side:required" in facets)
        assertTrue("server_side:optional" in facets)
    }

    @Test
    fun tagsAreFilteredToModCategoriesAndLoaders() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                [
                  {"name":"technology","header":"categories","project_type":"mod"},
                  {"name":"adventure","header":"categories","project_type":"mod"},
                  {"name":"resource-pack-category","header":"categories","project_type":"resourcepack"}
                ]
                """.trimIndent(),
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """
                [
                  {"name":"fabric","supported_project_types":["mod","modpack"]},
                  {"name":"datapack-loader","supported_project_types":["datapack"]}
                ]
                """.trimIndent(),
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """
                [
                  {"version":"1.21.1","version_type":"release","date":"2024-09-19T00:00:00Z","major":true},
                  {"version":"24w40a","version_type":"snapshot","date":"2024-10-01T00:00:00Z","major":false}
                ]
                """.trimIndent(),
            ),
        )

        val tags = api().tags()

        assertEquals(listOf("adventure", "technology"), tags.categories.map { it.name })
        assertEquals(listOf("fabric"), tags.loaders.map { it.name })
        assertEquals(listOf("1.21.1", "24w40a"), tags.gameVersions.map { it.version })
        assertEquals("/v2/tag/category", server.takeRequest().requestUrl?.encodedPath)
        assertEquals("/v2/tag/loader", server.takeRequest().requestUrl?.encodedPath)
        assertEquals("/v2/tag/game_version", server.takeRequest().requestUrl?.encodedPath)
    }

    @Test
    fun projectVersionsRequestUsesJsonArrayFiltersAndChecksReturnedCompatibility() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                [
                  {
                    "id":"fabric-version",
                    "project_id":"demo-mod",
                    "name":"Fabric release",
                    "version_number":"2.1.0",
                    "dependencies":[],
                    "game_versions":["1.21.1"],
                    "version_type":"release",
                    "loaders":["fabric"],
                    "date_published":"2026-01-01T00:00:00Z",
                    "downloads":100,
                    "files":[]
                  }
                ]
                """.trimIndent(),
            ),
        )

        val versions = api().projectVersions("demo-mod", "1.21.1", "fabric")

        assertEquals(1, versions.size)
        assertEquals("fabric-version", versions.single().id)
        val request = server.takeRequest()
        assertEquals("/v2/project/demo-mod/version", request.requestUrl?.encodedPath)
        assertEquals("[\"1.21.1\"]", request.requestUrl?.queryParameter("game_versions"))
        assertEquals("[\"fabric\"]", request.requestUrl?.queryParameter("loaders"))
    }

    @Test
    fun nonSuccessfulApiResponsesAreReported() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("rate limited"))

        try {
            api().search(ModrinthFilters())
            throw AssertionError("Expected HTTP 429 to be reported")
        } catch (e: java.io.IOException) {
            assertTrue(e.message.orEmpty().contains("429"))
        }
    }
}
