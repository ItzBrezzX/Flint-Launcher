package com.flintlauncher.android.installer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NativesExtractorAndroidTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun extractsResourcesButSkipsLinuxSharedObjects() {
        val archive = File(temporary.root, "natives-linux.jar")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
            zip.write("Manifest-Version: 1.0".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("lib/liblwjgl.so"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("lib/libopenal.so.1"))
            zip.write(byteArrayOf(4, 5, 6))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("org/lwjgl/system/linux/liblwjgl.so"))
            zip.write(byteArrayOf(7, 8, 9))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("org/lwjgl/system/linux/bridge.dat"))
            zip.write(byteArrayOf(10, 11, 12))
            zip.closeEntry()
        }

        val output = File(temporary.root, "android-natives")
        val extracted = NativesExtractor.extract(listOf(archive), output)

        assertEquals(1, extracted.size)
        assertEquals("bridge.dat", extracted.single().name)
        assertTrue(extracted.single().isFile)
        assertFalse(File(output, "lib/liblwjgl.so").exists())
        assertFalse(File(output, "lib/libopenal.so.1").exists())
        assertFalse(File(output, "META-INF/MANIFEST.MF").exists())
    }

    @Test
    fun canOptInToExtractingSharedObjectsForNonAndroidCallers() {
        val archive = File(temporary.root, "native.jar")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("libexample.so"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
        }

        val output = File(temporary.root, "desktop-natives")
        val extracted = NativesExtractor.extract(
            jars = listOf(archive),
            target = output,
            excludeSharedObjects = false,
        )

        assertEquals(listOf("libexample.so"), extracted.map { it.name })
    }
}
