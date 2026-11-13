package com.flintlauncher.android.installer

import com.flintlauncher.android.minecraft.version.Environment
import com.flintlauncher.android.minecraft.version.VersionJsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class InstallPlannerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val linux = Environment(osName = "linux", osArch = "arm64")
    private val hashA = "a".repeat(40)
    private val hashB = "b".repeat(40)

    // Synthetic version JSON: made-up hashes and URLs, real structure.
    private fun version() = VersionJsonParser.parse(
        """
        {
          "id": "test", "mainClass": "M",
          "downloads": { "client": { "sha1": "$hashA", "size": 1000, "url": "https://dl.example.invalid/client.jar" } },
          "assetIndex": { "id": "idx", "sha1": "$hashB", "size": 50, "url": "https://dl.example.invalid/idx.json" },
          "libraries": [
            { "name": "com.example:one:1.0", "downloads": { "artifact": {
              "path": "com/example/one/1.0/one-1.0.jar", "sha1": "$hashA", "size": 10, "url": "https://dl.example.invalid/one.jar" } } },
            { "name": "com.example:nat:1.0", "natives": { "linux": "natives-linux" },
              "downloads": { "classifiers": { "natives-linux": { "sha1": "$hashB", "size": 5, "url": "https://dl.example.invalid/nat.jar" } } } }
          ]
        }
        """.trimIndent(),
    )

    @Test
    fun plansClientLibrariesAndAssetIndexWithHashes() {
        val layout = GameLayout(tmp.root)
        val plan = InstallPlanner.planCore(version(), "test", layout, linux)

        assertEquals(
            listOf("client-test", "com/example/one/1.0/one-1.0.jar", "com/example/nat/1.0/nat-1.0-natives-linux.jar", "assetindex-idx"),
            plan.requests.map { it.id },
        )
        assertEquals(1000L, plan.requests.first().sizeBytes)
        assertTrue(plan.requests.all { it.hash != null })
        assertEquals(layout.versionJar("test"), plan.requests.first().destination)
    }

    @Test
    fun classpathHasLibrariesThenTheClientJarAndKeepsNativesSeparate() {
        val layout = GameLayout(tmp.root)
        val plan = InstallPlanner.planCore(version(), "test", layout, linux)

        assertEquals(listOf(layout.library("com/example/one/1.0/one-1.0.jar"), layout.versionJar("test")), plan.classpath)
        assertEquals(listOf(layout.library("com/example/nat/1.0/nat-1.0-natives-linux.jar")), plan.nativeJars)
        assertTrue(plan.unresolvedLibraries.isEmpty())
    }

    @Test
    fun assetsAreDeduplicatedByHashAndUseTheContentAddressedUrl() {
        val index = VersionJsonParser.parseAssetIndex(
            """{"objects":{
                "a/one.png":{"hash":"$hashA","size":3},
                "b/same-file.png":{"hash":"$hashA","size":3},
                "c/two.ogg":{"hash":"$hashB","size":7}}}""",
        )
        val layout = GameLayout(tmp.root)

        val requests = InstallPlanner.planAssets(index, layout)

        assertEquals(2, requests.size)
        assertEquals("https://resources.download.minecraft.net/aa/$hashA", requests[0].url)
        assertEquals(layout.assetObject(hashA), requests[0].destination)
        assertEquals(File(tmp.root, "assets/objects/bb/$hashB").canonicalFile, requests[1].destination.canonicalFile)
    }

    @Test
    fun legacyAssetIndexesAreRejectedClearly() {
        val index = VersionJsonParser.parseAssetIndex("""{"objects":{},"virtual":true}""")
        try {
            InstallPlanner.planAssets(index, GameLayout(tmp.root))
            fail("expected InstallPlanException")
        } catch (e: InstallPlanException) {
            assertTrue(e.message!!.contains("Legacy"))
        }
    }

    @Test
    fun maliciousMetadataCannotEscapeTheGameDirectory() {
        val layout = GameLayout(tmp.root)
        try {
            layout.library("../../etc/passwd")
            fail("expected InstallPlanException")
        } catch (e: InstallPlanException) {
            assertTrue(e.message!!.contains("outside"))
        }
        val badIndex = VersionJsonParser.parseAssetIndex("""{"objects":{"x":{"hash":"../../evil","size":1}}}""")
        try {
            InstallPlanner.planAssets(badIndex, layout)
            fail("expected InstallPlanException")
        } catch (e: InstallPlanException) {
            assertTrue(e.message!!.contains("Invalid asset hash"))
        }
    }

    @Test
    fun missingClientDownloadIsAClearError() {
        val noClient = VersionJsonParser.parse("""{"id":"x","assetIndex":{"id":"i","url":"https://x.invalid/i"}}""")
        try {
            InstallPlanner.planCore(noClient, "x", GameLayout(tmp.root), linux)
            fail("expected InstallPlanException")
        } catch (e: InstallPlanException) {
            assertTrue(e.message!!.contains("no client download"))
        }
    }
}

class LoaderMetaTest {
    private val server = MockWebServer()

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun meta(): FabricStyleMeta {
        server.start()
        return FabricStyleMeta(OkHttpClient(), server.url("/v2/"))
    }

    @Test
    fun parsesLoaderVersionsAndPicksTheNewestStable() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """[{"loader":{"version":"0.20.0-beta","stable":false},"intermediary":{"version":"1.21"}},
                    {"loader":{"version":"0.19.2","stable":true}},
                    {"loader":{"version":"0.19.1","stable":true}}]""",
            ),
        )
        val versions = meta().loaderVersions("1.21.11")

        assertEquals(3, versions.size)
        assertEquals("0.19.2", LoaderCompatibilityChecker.recommended(versions)!!.version)
        assertEquals("/v2/versions/loader/1.21.11", server.takeRequest().path)
    }

    @Test
    fun profileJsonUsesTheProfileEndpoint() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":"fabric-loader-0.19.2-1.21.11"}"""))
        val body = meta().profileJson("1.21.11", "0.19.2")

        assertTrue(body.contains("fabric-loader"))
        assertEquals("/v2/versions/loader/1.21.11/0.19.2/profile/json", server.takeRequest().path)
    }

    @Test
    fun emptyListMeansIncompatibleNotAnError() = runBlocking {
        server.enqueue(MockResponse().setBody("[]"))
        val checker = LoaderCompatibilityChecker(meta(), meta2())

        val result = checker.check(LoaderKind.FABRIC, "1.0")

        assertTrue(result is LoaderCompatibility.Incompatible)
    }

    @Test
    fun forgeAndNeoForgeAreReportedAsUnsupportedNotFaked() = runBlocking {
        val checker = LoaderCompatibilityChecker(meta(), meta2())

        assertTrue(checker.check(LoaderKind.FORGE, "1.21.11") is LoaderCompatibility.Unsupported)
        assertTrue(checker.check(LoaderKind.NEOFORGE, "1.21.11") is LoaderCompatibility.Unsupported)
        assertEquals(LoaderCompatibility.Compatible(emptyList()), checker.check(LoaderKind.VANILLA, "1.21.11"))
    }

    @Test
    fun serverErrorsSurfaceAsIoExceptions() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        try {
            meta().loaderVersions("1.21.11")
            fail("expected IOException")
        } catch (e: java.io.IOException) {
            assertTrue(e.message!!.contains("503"))
        }
    }

    // A second meta pointing at the same (already started) server; the Quilt side is unused in these tests.
    private fun meta2() = FabricStyleMeta(OkHttpClient(), server.url("/v3/"))
}
