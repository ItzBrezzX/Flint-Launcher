package com.flintlauncher.android.runtime

import com.flintlauncher.android.download.DownloadManager
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
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
}
