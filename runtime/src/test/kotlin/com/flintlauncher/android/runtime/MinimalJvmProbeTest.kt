package com.flintlauncher.android.runtime

import com.flintlauncher.android.runtime.testsupport.MinimalJvmProbe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MinimalJvmProbeTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun writesAValidJavaEightClassFileWithoutJavac() {
        val outputDir = temporaryFolder.newFolder("probe")
        val classFile = MinimalJvmProbe.writeProbeClass(outputDir)
        val bytes = classFile.readBytes()

        assertTrue(classFile.isFile)
        assertEquals("FlintJvmProbe.class", classFile.name)
        assertEquals(0xCA, bytes[0].toInt() and 0xFF)
        assertEquals(0xFE, bytes[1].toInt() and 0xFF)
        assertEquals(0xBA, bytes[2].toInt() and 0xFF)
        assertEquals(0xBE, bytes[3].toInt() and 0xFF)
        assertEquals(0, bytes[4].toInt() and 0xFF)
        assertEquals(52, bytes[7].toInt() and 0xFF)
    }

    @Test
    fun probeClassCanBeRegeneratedDeterministically() {
        val outputDir = temporaryFolder.newFolder("repeat")
        val first = MinimalJvmProbe.writeProbeClass(outputDir).readBytes()
        val second = MinimalJvmProbe.writeProbeClass(outputDir).readBytes()

        assertTrue(first.contentEquals(second))
    }
}
