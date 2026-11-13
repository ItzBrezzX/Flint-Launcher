package com.flintlauncher.android.ui.versions

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    @Test
    fun bytesUseTheRightUnit() {
        assertEquals("512 B", Formatters.bytes(512))
        assertEquals("1.5 KB", Formatters.bytes(1536))
        assertEquals("128.5 MB", Formatters.bytes((128.5 * 1024 * 1024).toLong()))
        assertEquals("1.25 GB", Formatters.bytes((1.25 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun speedAddsPerSecond() {
        assertEquals("8.4 MB/s", Formatters.speed((8.4 * 1024 * 1024).toLong()))
    }

    @Test
    fun durationMatchesTheBriefsExampleAndHandlesUnknown() {
        assertEquals("00:42", Formatters.duration(42))
        assertEquals("02:05", Formatters.duration(125))
        assertEquals("1:01:01", Formatters.duration(3661))
        assertEquals("--:--", Formatters.duration(null))
    }

    @Test
    fun percentIsClampedAndUnknownIsADash() {
        assertEquals("37.5%", Formatters.percent(37.5))
        assertEquals("100.0%", Formatters.percent(100.4))
        assertEquals("--", Formatters.percent(null))
    }
}
