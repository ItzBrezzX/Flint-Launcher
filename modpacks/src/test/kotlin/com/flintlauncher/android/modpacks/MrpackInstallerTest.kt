package com.flintlauncher.android.modpacks

import com.flintlauncher.android.download.DownloadConfig
import com.flintlauncher.android.download.DownloadManager
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MrpackInstallerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var base: String
    private val modBytes = "fake-mod-jar".toByteArray()
    private val modSha1 = sha1(modBytes)

    private fun sha1(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    @Before
    fun setUp() {
        server.start()
        base = server.url("/").toString().trimEnd('/')
    }

    @After
    fun tearDown() = runCatching { server.shutdown() }

    private fun buildMrpack(
        indexJson: String,
        overrides: Map<String, ByteArray> = emptyMap(),
    ): File {
        val file = File(tmp.root, "pack.mrpack")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("modrinth.index.json"))
            zip.write(indexJson.toByteArray())
            zip.closeEntry()
            for ((path, content) in overrides) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return file
    }

    private fun validIndex(extraFiles: String = "") = """{
        "formatVersion": 1, "game": "minecraft", "versionId": "1.0.0", "name": "Test Pack",
        "summary": "A test pack",
        "dependencies": {"minecraft": "1.20.4", "fabric-loader": "0.15.7"},
        "files": [
            {"path": "mods/testmod.jar", "hashes": {"sha1": "$modSha1"}, "env": {"client": "required"},
             "downloads": ["$base/files/testmod.jar"], "fileSize": ${modBytes.size}},
            {"path": "mods/serveronly.jar", "hashes": {"sha1": "$modSha1"}, "env": {"client": "unsupported"},
             "downloads": ["$base/files/serveronly.jar"], "fileSize": ${modBytes.size}}
            $extraFiles
        ]
    }"""

    @Test
    fun parsesValidMrpackAndExtractsMetadata() {
        val pack = buildMrpack(validIndex())
        val manifest = MrpackInstaller().parse(pack)
        assertEquals("Test Pack", manifest.name)
        assertEquals("1.20.4", manifest.dependencies.minecraft)
        assertEquals("fabric", manifest.dependencies.loader)
        assertEquals("0.15.7", manifest.dependencies.loaderVersion)
        assertEquals(2, manifest.files.size)
    }

    @Test
    fun planBuildsVerifiedDownloadRequestsAndSkipsClientUnsupported() {
        val pack = buildMrpack(validIndex())
        val gameDir = File(tmp.root, "profiles/p1")
        val plan = MrpackInstaller().plan(MrpackInstaller().parse(pack), pack, gameDir)
        assertEquals(1, plan.downloads.size) // serveronly skipped
        assertEquals(1, plan.skippedClientUnsupported)
        val req = plan.downloads.single()
        assertEquals("$base/files/testmod.jar", req.url)
        assertEquals(File(gameDir, "mods/testmod.jar").canonicalPath, req.destination.canonicalPath)
        assertEquals(modBytes.size.toLong(), req.sizeBytes)
        assertEquals(modSha1, req.hash!!.hex)
        assertEquals("fabric", plan.metadata.loader)
        assertEquals("1.20.4", plan.metadata.minecraftVersion)
    }

    @Test
    fun overridesAreExtractedIntoGameDir() = runBlocking {
        server.enqueue(MockResponse().setBody(Buffer().write(modBytes)))
        val pack = buildMrpack(
            validIndex(),
            overrides = mapOf(
                "overrides/config/mod.toml" to "config-data".toByteArray(),
                "overrides/resourcepacks/pack.zip" to "zip".toByteArray(),
            ),
        )
        val gameDir = File(tmp.root, "profiles/p2")
        val downloads = DownloadManager(OkHttpClient(), DownloadConfig(concurrency = 1, maxAttempts = 1, backoffBaseMillis = 0))
        val result = MrpackInstaller().install(pack, gameDir, downloads)
        assertTrue(result.isSuccess)
        val summary = result.getOrNull()!!
        assertEquals(2, summary.extractedOverrides)
        assertTrue(File(gameDir, "config/mod.toml").isFile)
        assertTrue(File(gameDir, "resourcepacks/pack.zip").isFile)
        assertEquals("config-data", File(gameDir, "config/mod.toml").readText())
    }

    @Test
    fun zipSlipPathInManifestIsRejected() {
        val evil = """{
            "formatVersion": 1, "game": "minecraft", "versionId": "1", "name": "Evil",
            "dependencies": {"minecraft": "1.20.4"},
            "files": [{"path": "../../etc/evil.jar", "hashes": {"sha1": "$modSha1"},
                       "downloads": ["$base/x.jar"], "fileSize": 1}]
        }"""
        val pack = buildMrpack(evil)
        try {
            MrpackInstaller().plan(MrpackInstaller().parse(pack), pack, File(tmp.root, "g"))
            org.junit.Assert.fail("expected MrpackFormatException for zip-slip path")
        } catch (e: MrpackFormatException) {
            assertTrue(e.message!!.contains("escapes"))
        }
    }

    @Test
    fun missingIndexJsonIsRejected() {
        val pack = File(tmp.root, "empty.mrpack")
        ZipOutputStream(pack.outputStream()).use { it.closeEntry() }
        try {
            MrpackInstaller().parse(pack)
            org.junit.Assert.fail("expected MrpackFormatException")
        } catch (e: MrpackFormatException) {
            assertTrue(e.message!!.contains("missing"))
        }
    }

    @Test
    fun nonMinecraftPackIsRejected() {
        val pack = buildMrpack("""{"formatVersion":1,"game":"not-mc","versionId":"1","name":"x","dependencies":{"minecraft":"1.20.4"},"files":[]}""")
        try {
            MrpackInstaller().parse(pack)
            org.junit.Assert.fail("expected MrpackFormatException")
        } catch (e: MrpackFormatException) {
            assertTrue(e.message!!.contains("Minecraft"))
        }
    }

    @Test
    fun failedDownloadIsCategorized() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        val pack = buildMrpack(validIndex())
        val downloads = DownloadManager(OkHttpClient(), DownloadConfig(concurrency = 1, maxAttempts = 1, backoffBaseMillis = 0))
        val result = MrpackInstaller().install(pack, File(tmp.root, "g3"), downloads)
        assertTrue(result.isError)
        assertTrue(result.errorOrNull() is com.flintlauncher.android.core.FlintError.DownloadFailed)
    }
}
