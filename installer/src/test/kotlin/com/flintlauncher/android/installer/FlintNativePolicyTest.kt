package com.flintlauncher.android.installer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FlintNativePolicyTest {
    @Test
    fun lwjglGlfwOpenalAndJemallocAreReplaced() {
        assertTrue(FlintNativePolicy.isReplacedByFlint("liblwjgl.so"))
        assertTrue(FlintNativePolicy.isReplacedByFlint("liblwjgl_opengl.so"))
        assertTrue(FlintNativePolicy.isReplacedByFlint("liblwjgl_stb.so"))
        assertTrue(FlintNativePolicy.isReplacedByFlint("libglfw.so"))
        assertTrue(FlintNativePolicy.isReplacedByFlint("libopenal.so"))
        assertTrue(FlintNativePolicy.isReplacedByFlint("libopenal64.so"))
        assertTrue(FlintNativePolicy.isReplacedByFlint("libjemalloc.so"))
        assertTrue(FlintNativePolicy.isReplacedByFlint("linux/x86_64/libglfw.so"))
    }

    @Test
    fun unrelatedNativesAreKept() {
        assertFalse(FlintNativePolicy.isReplacedByFlint("libtwitchsdk.so"))
        assertFalse(FlintNativePolicy.isReplacedByFlint("libjdvp.so"))
        assertFalse(FlintNativePolicy.isReplacedByFlint("libfmod.so"))
        // Non-.so entries are never natives (e.g. META-INF is handled separately).
        assertFalse(FlintNativePolicy.isReplacedByFlint("liblwjgl.dll"))
        assertFalse(FlintNativePolicy.isReplacedByFlint("liblwjgl.dylib"))
    }
}

class NativesExtractorFilteredTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun zip(name: String, entries: Map<String, String>): File {
        val file = File(tmp.root, name)
        ZipOutputStream(file.outputStream()).use { out ->
            for ((entryName, content) in entries) {
                out.putNextEntry(ZipEntry(entryName))
                out.write(content.toByteArray())
                out.closeEntry()
            }
        }
        return file
    }

    @Test
    fun filteredExtractionSkipsFlintReplacedNativesAndReportsThem() {
        val jar = zip("natives.jar", mapOf(
            "liblwjgl.so" to "elf-lwjgl",
            "libglfw.so" to "elf-glfw",
            "libtwitchsdk.so" to "elf-twitch",
            "META-INF/MANIFEST.MF" to "x",
        ))
        val target = File(tmp.root, "extracted")
        val result = NativesExtractor.extractFiltered(listOf(jar), target) { FlintNativePolicy.isReplacedByFlint(it) }
        assertEquals(listOf("libtwitchsdk.so"), result.extracted.map { it.name })
        assertEquals(listOf("libglfw.so", "liblwjgl.so"), result.skippedFlintReplaced)
        assertTrue(File(target, "libtwitchsdk.so").isFile)
        assertFalse(File(target, "liblwjgl.so").exists())
    }

    @Test
    fun legacyExtractDelegateStillWorks() {
        val jar = zip("n.jar", mapOf("libfoo.so" to "x", "META-INF/MANIFEST.MF" to "y"))
        val target = File(tmp.root, "out")
        val extracted = NativesExtractor.extract(listOf(jar), target)
        assertEquals(listOf("libfoo.so"), extracted.map { it.name })
    }
}
