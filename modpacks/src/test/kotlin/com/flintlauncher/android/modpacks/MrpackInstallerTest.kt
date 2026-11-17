package com.flintlauncher.android.modpacks

import com.flintlauncher.android.download.DownloadConfig
import com.flintlauncher.android.download.DownloadManager
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MrpackInstallerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val sampleBytes = "sample-mod-jar".toByteArray()
    private val sha1 = MessageDigest.getInstance("SHA-1")
        .digest(sampleBytes).joinToString("") { "%02x".format(it) }

    private fun archive(index: String, overrides: Map<String, ByteArray> = emptyMap()): File {
        val file = File(tmp.root, "fixture.mrpack")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(MrpackInstaller.INDEX_JSON))
            zip.write(index.toByteArray())
            zip.closeEntry()
            overrides.forEach { (path, body) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(body)
                zip.closeEntry()
            }
        }
        return file
    }

    private fun manifest(files: String = "") = """{
        "formatVersion":1,
        "game":"minecraft",
        "versionId":"test-pack",
        "name":"Test Pack",
        "summary":"Fixture",
        "dependencies":{"minecraft":"1.20.4","fabric-loader":"0.15.7"},
        "files":[$files]
    }"""

    @Test
    fun parsesManifestAndLoaderMetadata() {
        val pack = archive(manifest())
        val parsed = MrpackInstaller().parse(pack)
        assertEquals("Test Pack", parsed.name)
        assertEquals("1.20.4", parsed.dependencies.minecraft)
        assertEquals("fabric", parsed.dependencies.loader)
        assertEquals("0.15.7", parsed.dependencies.loaderVersion)
    }

    @Test
    fun planBuildsHashVerifiedDownloadsAndSkipsClientUnsupportedFiles() {
        val files = """
            {"path":"mods/client.jar","hashes":{"sha1":"$sha1"},"env":{"client":"required"},
             "downloads":["https://cdn.modrinth.com/client.jar"],"fileSize":14},
            {"path":"mods/server.jar","hashes":{"sha1":"$sha1"},"env":{"client":"unsupported"},
             "downloads":["https://cdn.modrinth.com/server.jar"],"fileSize":14}
        """
        val pack = archive(manifest(files))
        val gameDir = File(tmp.root, "profile")
        val plan = MrpackInstaller().plan(MrpackInstaller().parse(pack), pack, gameDir)
        assertEquals(1, plan.downloads.size)
        assertEquals(1, plan.skippedClientUnsupported)
        assertEquals("https://cdn.modrinth.com/client.jar", plan.downloads.single().url)
        assertEquals(File(gameDir, "mods/client.jar").canonicalPath, plan.downloads.single().destination.canonicalPath)
        assertEquals(sha1, plan.downloads.single().hash!!.hex)
    }

    @Test
    fun extractsOverridesWhenPackHasNoExternalDownloads() = runBlocking {
        val pack = archive(
            manifest(),
            overrides = mapOf(
                "overrides/config/mod.toml" to "config-data".toByteArray(),
                "overrides/resourcepacks/sample.zip" to "resource-pack".toByteArray(),
            ),
        )
        val gameDir = File(tmp.root, "profile")
        val downloads = DownloadManager(
            OkHttpClient(),
            DownloadConfig(concurrency = 1, maxAttempts = 1, backoffBaseMillis = 0),
        )

        val result = MrpackInstaller().install(pack, gameDir, downloads)

        val success = result as? MrpackInstallResult.Success
            ?: throw AssertionError("expected successful mrpack install: $result")
        assertEquals(2, success.summary.extractedOverrides)
        assertEquals("config-data", File(gameDir, "config/mod.toml").readText())
        assertEquals("resource-pack", File(gameDir, "resourcepacks/sample.zip").readText())
    }

    @Test
    fun rejectsZipSlipAndWindowsStyleOverridePaths() {
        for (path in listOf("../escape.jar", "mods/../../escape.jar", "mods\\\\escape.jar")) {
            try {
                MrpackInstaller.safeResolve(File(tmp.root, "profile"), path)
                throw AssertionError("expected unsafe path rejection for $path")
            } catch (e: MrpackFormatException) {
                assertTrue(e.message!!.contains("invalid") || e.message!!.contains("escapes"))
            }
        }
    }

    @Test
    fun rejectsHttpDownloadUrlsAndDuplicateDestinations() {
        val httpFile = """{"path":"mods/a.jar","hashes":{"sha1":"$sha1"},
            "downloads":["http://127.0.0.1/private"],"fileSize":1}"""
        val httpPack = archive(manifest(httpFile))
        try {
            MrpackInstaller().plan(MrpackInstaller().parse(httpPack), httpPack, File(tmp.root, "profile-a"))
            throw AssertionError("expected HTTPS-only URL rejection")
        } catch (e: MrpackFormatException) {
            assertTrue(e.message!!.contains("HTTPS"))
        }

        val duplicate = """
            {"path":"mods/a.jar","hashes":{"sha1":"$sha1"},"downloads":["https://cdn.modrinth.com/a"],"fileSize":1},
            {"path":"mods/a.jar","hashes":{"sha1":"$sha1"},"downloads":["https://cdn.modrinth.com/b"],"fileSize":1}
        """
        val duplicatePack = archive(manifest(duplicate))
        try {
            MrpackInstaller().plan(MrpackInstaller().parse(duplicatePack), duplicatePack, File(tmp.root, "profile-b"))
            throw AssertionError("expected duplicate destination rejection")
        } catch (e: MrpackFormatException) {
            assertTrue(e.message!!.contains("duplicate"))
        }
    }

    @Test
    fun invalidOrUnsupportedManifestsAreRejected() {
        val invalidJson = archive("{not-json")
        try {
            MrpackInstaller().parse(invalidJson)
            throw AssertionError("expected invalid JSON rejection")
        } catch (e: MrpackFormatException) {
            assertTrue(e.message!!.contains("parse"))
        }

        val nonMinecraft = archive("""{"formatVersion":1,"game":"other","versionId":"1","name":"x","dependencies":{"minecraft":"1.20.4"},"files":[]}""")
        try {
            MrpackInstaller().parse(nonMinecraft)
            throw AssertionError("expected non-Minecraft rejection")
        } catch (e: MrpackFormatException) {
            assertTrue(e.message!!.contains("Minecraft"))
        }
    }

    @Test
    fun installReturnsCategorizedErrorForInvalidArchive() = runBlocking {
        val invalid = File(tmp.root, "not-a-pack.mrpack").apply { writeText("not a zip") }
        val downloads = DownloadManager(OkHttpClient(), DownloadConfig(maxAttempts = 1))
        val result = MrpackInstaller().install(invalid, File(tmp.root, "profile"), downloads)
        assertTrue(result is MrpackInstallResult.Failure)
        assertTrue((result as MrpackInstallResult.Failure).message.contains("Could not read"))
    }

    @Test
    fun rejectsMissingDeclaredDownloadSize() {
        val file = """{"path":"mods/a.jar","hashes":{"sha1":"$sha1"},"downloads":["https://cdn.modrinth.com/a.jar"]}"""
        val pack = archive(manifest(file))
        try {
            MrpackInstaller().plan(MrpackInstaller().parse(pack), pack, File(tmp.root, "missing-size-profile"))
            throw AssertionError("expected missing fileSize rejection")
        } catch (error: MrpackFormatException) {
            assertTrue(error.message.orEmpty().contains("file size"))
        }
    }

    @Test
    fun rejectsOversizedOverrideBeforeExtractingIt() {
        val pack = archive(manifest(), overrides = mapOf("overrides/large.bin" to byteArrayOf(1)))
        patchCentralDirectoryUncompressedSize(pack, "overrides/large.bin", 256L * 1024 * 1024 + 1)
        try {
            MrpackInstaller().plan(MrpackInstaller().parse(pack), pack, File(tmp.root, "large-profile"))
            throw AssertionError("expected oversized override rejection")
        } catch (error: MrpackFormatException) {
            assertTrue(error.message.orEmpty().contains("large") || error.message.orEmpty().contains("too large"))
        }
    }

    @Test
    fun rejectsSymlinkComponentsInOverrideDestination() {
        val profile = File(tmp.root, "symlink-profile").apply { mkdirs() }
        val outside = File(tmp.root, "outside").apply { mkdirs() }
        java.nio.file.Files.createSymbolicLink(File(profile, "config").toPath(), outside.toPath())
        try {
            MrpackInstaller.safeResolve(profile, "config/mod.toml")
            throw AssertionError("expected symbolic-link path rejection")
        } catch (error: MrpackFormatException) {
            assertTrue(error.message.orEmpty().contains("symbolic link"))
        }
        assertFalse(File(outside, "mod.toml").exists())
    }

    @Test
    fun overrideExtractionDoesNotFollowPredictableTemporarySymlink() = runBlocking {
        val pack = archive(
            manifest(),
            overrides = mapOf("overrides/config/mod.toml" to "safe-content".toByteArray()),
        )
        val gameDir = File(tmp.root, "temporary-link-profile").apply { mkdirs() }
        val configDir = File(gameDir, "config").apply { mkdirs() }
        val outside = File(tmp.root, "outside-file.txt").apply { writeText("unchanged") }
        java.nio.file.Files.createSymbolicLink(File(configDir, "mod.toml.flint-part").toPath(), outside.toPath())
        val downloads = DownloadManager(OkHttpClient(), DownloadConfig(concurrency = 1, maxAttempts = 1))

        val result = MrpackInstaller().install(pack, gameDir, downloads)

        assertTrue(result is MrpackInstallResult.Success)
        assertEquals("unchanged", outside.readText())
        assertEquals("safe-content", File(configDir, "mod.toml").readText())
    }

    private fun patchCentralDirectoryUncompressedSize(file: File, entryName: String, size: Long) {
        val bytes = file.readBytes()
        var offset = 0
        while (offset <= bytes.size - 46) {
            if (readLittleEndianInt(bytes, offset) == 0x02014b50L) {
                val nameLength = readLittleEndianShort(bytes, offset + 28)
                val extraLength = readLittleEndianShort(bytes, offset + 30)
                val commentLength = readLittleEndianShort(bytes, offset + 32)
                val name = String(bytes, offset + 46, nameLength, Charsets.UTF_8)
                if (name == entryName) {
                    val value = size.toInt()
                    for (index in 0 until 4) bytes[offset + 24 + index] = (value ushr (index * 8)).toByte()
                    file.writeBytes(bytes)
                    return
                }
                offset += 46 + nameLength + extraLength + commentLength
            } else {
                offset++
            }
        }
        throw AssertionError("Could not find central directory entry: " + entryName)
    }

    private fun readLittleEndianShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun readLittleEndianInt(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xff) or
            ((bytes[offset + 1].toLong() and 0xff) shl 8) or
            ((bytes[offset + 2].toLong() and 0xff) shl 16) or
            ((bytes[offset + 3].toLong() and 0xff) shl 24)
}
