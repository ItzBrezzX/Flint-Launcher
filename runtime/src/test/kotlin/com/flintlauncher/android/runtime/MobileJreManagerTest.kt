package com.flintlauncher.android.runtime

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

class MobileJreManagerTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private fun createRuntime(root: File, id: String, version: String): File {
        val home = File(root, id).apply { mkdirs() }
        File(home, "release").writeText(
            "JAVA_VERSION=\"$version\"\nOS_ARCH=\"aarch64\"\n",
        )
        File(home, "lib/libjli.so").apply {
            parentFile.mkdirs()
            writeBytes(byteArrayOf(1))
        }
        File(home, "lib/server/libjvm.so").apply {
            parentFile.mkdirs()
            writeBytes(byteArrayOf(1))
        }
        return home
    }

    @Test
    fun listingAndSelectionDoNotLoadOrPinAnyJvmInTheAppProcess() = runBlocking {
        val runtimes = temporary.newFolder("runtimes")
        val downloads = temporary.newFolder("downloads")
        createRuntime(runtimes, "jre17-arm64", "17.0.11")
        createRuntime(runtimes, "jre21-arm64", "21.0.3")

        val manager = MobileJreManager(
            runtimesDir = runtimes,
            downloadDir = downloads,
            deviceArch = "arm64",
            downloads = DownloadManager(OkHttpClient()),
        )

        assertEquals(listOf(17, 21), manager.listInstalled().map { it.majorVersion })
        assertEquals(21, manager.findCompatible(21, "arm64")?.majorVersion)
    }

    @Test
    fun localImportRequiresChecksumAndRejectsTraversalRuntimeIds() = runBlocking {
        val runtimes = temporary.newFolder("import-runtimes")
        val downloads = temporary.newFolder("import-downloads")
        val archive = temporary.newFile("untrusted.tar.gz").apply { writeText("not a valid archive") }
        val manager = MobileJreManager(
            runtimesDir = runtimes,
            downloadDir = downloads,
            deviceArch = "arm64",
            downloads = DownloadManager(OkHttpClient()),
        )

        val missingChecksum = manager.importLocalArchive(archive, "local-jre", 17)
        assertTrue(missingChecksum.isFailure)
        assertTrue(missingChecksum.exceptionOrNull()?.message.orEmpty().contains("SHA-256"))

        val traversalId = manager.importLocalArchive(archive, "../outside", 17, "0".repeat(64))
        assertTrue(traversalId.isFailure)
        assertTrue(traversalId.exceptionOrNull()?.message.orEmpty().contains("Runtime ID"))
        assertFalse(File(runtimes.parentFile, "outside").exists())
    }

    @Test
    fun failedLocalImportKeepsExistingRuntimeDirectory() = runBlocking {
        val runtimes = temporary.newFolder("existing-runtimes")
        val downloads = temporary.newFolder("existing-downloads")
        val target = File(runtimes, "local-jre").apply { mkdirs() }
        val marker = File(target, "keep.txt").apply { writeText("previous-runtime") }
        val archive = temporary.newFile("broken.tar.gz").apply { writeText("not a valid archive") }
        val checksum = java.security.MessageDigest.getInstance("SHA-256")
            .digest(archive.readBytes()).joinToString("") { "%02x".format(it) }
        val manager = MobileJreManager(
            runtimesDir = runtimes,
            downloadDir = downloads,
            deviceArch = "arm64",
            downloads = DownloadManager(OkHttpClient()),
        )

        val result = manager.importLocalArchive(archive, "local-jre", 17, checksum)

        assertTrue(result.isFailure)
        assertTrue(marker.isFile)
        assertEquals("previous-runtime", marker.readText())
    }
}
