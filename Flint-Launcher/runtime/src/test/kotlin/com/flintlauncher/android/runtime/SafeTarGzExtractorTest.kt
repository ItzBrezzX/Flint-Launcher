package com.flintlauncher.android.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

/**
 * Host-side safety tests for the tar.gz extractor.
 * These run on the JVM in CI; they do not require Android.
 */
class SafeTarGzExtractorTest {

    @Test
    fun rejectsPathTraversal() {
        val tarGz = buildTarGz(
            listOf(
                TarEntry("ok.txt", "hello".toByteArray()),
                TarEntry("../escape.txt", "bad".toByteArray()),
            ),
        )
        val dest = createTempDir()
        try {
            SafeTarGzExtractor.extract(tarGz, dest)
            fail("expected path traversal to throw")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("traversal") || e.message!!.contains("escapes") || e.message!!.contains(".."))
        }
        // dest should be cleaned on failure
        assertFalse(File(dest, "ok.txt").exists())
    }

    @Test
    fun rejectsAbsolutePath() {
        val tarGz = buildTarGz(listOf(TarEntry("/tmp/evil.txt", "x".toByteArray())))
        val dest = createTempDir()
        try {
            SafeTarGzExtractor.extract(tarGz, dest)
            fail("expected absolute path rejection")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("Absolute") || e.message!!.contains("escapes") || e.message!!.contains("rejected"))
        }
    }

    @Test
    fun extractsSafeFile() {
        val tarGz = buildTarGz(
            listOf(
                TarEntry("lib/libjli.so", "fake-jli".toByteArray()),
                TarEntry("release", "JAVA_VERSION=\"17.0.5\"\n".toByteArray()),
            ),
        )
        val dest = createTempDir()
        val result = SafeTarGzExtractor.extract(tarGz, dest)
        assertTrue(result.filesWritten >= 2)
        assertTrue(File(dest, "lib/libjli.so").isFile)
        assertTrue(File(dest, "release").readText().contains("17"))
    }

    private data class TarEntry(val name: String, val data: ByteArray)

    /** Build a minimal ustar tar.gz in memory for tests. */
    private fun buildTarGz(entries: List<TarEntry>): File {
        val tarBytes = ByteArrayOutputStream()
        for (e in entries) {
            val header = ByteArray(512)
            val nameBytes = e.name.toByteArray(Charsets.UTF_8)
            System.arraycopy(nameBytes, 0, header, 0, minOf(nameBytes.size, 99))
            // mode
            writeOctal(header, 100, 8, 0b1000000000) // 0100000-ish
            // size
            writeOctal(header, 124, 12, e.data.size.toLong())
            // mtime
            writeOctal(header, 136, 12, System.currentTimeMillis() / 1000)
            // typeflag regular file
            header[156] = '0'.code.toByte()
            // magic ustar
            val magic = "ustar\u000000".toByteArray()
            System.arraycopy(magic, 0, header, 257, 8)
            // checksum
            for (i in 148 until 156) header[i] = ' '.code.toByte()
            var sum = 0
            for (b in header) sum += (b.toInt() and 0xff)
            writeOctal(header, 148, 8, sum.toLong())
            tarBytes.write(header)
            tarBytes.write(e.data)
            val pad = (512 - (e.data.size % 512)) % 512
            if (pad > 0) tarBytes.write(ByteArray(pad))
        }
        // two zero blocks
        tarBytes.write(ByteArray(1024))

        val out = createTempFile(suffix = ".tar.gz")
        GZIPOutputStream(out.outputStream()).use { gz ->
            gz.write(tarBytes.toByteArray())
        }
        return out
    }

    private fun writeOctal(header: ByteArray, off: Int, len: Int, value: Long) {
        val s = value.toString(8).padStart(len - 1, '0')
        val bytes = s.toByteArray()
        System.arraycopy(bytes, 0, header, off, minOf(bytes.size, len - 1))
        header[off + len - 1] = 0
    }
}
