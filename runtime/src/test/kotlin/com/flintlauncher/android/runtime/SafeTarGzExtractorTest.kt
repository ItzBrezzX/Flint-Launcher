package com.flintlauncher.android.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

class SafeTarGzExtractorTest {
    @Test
    fun rejectsPathTraversal() {
        val archive = buildTarGz(
            listOf(
                TarEntry("ok.txt", "hello".toByteArray()),
                TarEntry("../escape.txt", "bad".toByteArray()),
            ),
        )
        val destination = createTempDir()
        try {
            SafeTarGzExtractor.extract(archive, destination)
            fail("expected path traversal to throw")
        } catch (error: Exception) {
            assertTrue(error.message.orEmpty().contains("traversal") ||
                error.message.orEmpty().contains("escapes") || error.message.orEmpty().contains(".."))
        }
        assertFalse(File(destination, "ok.txt").exists())
    }

    @Test
    fun rejectsAbsolutePath() {
        val archive = buildTarGz(listOf(TarEntry("/tmp/evil.txt", "x".toByteArray())))
        val destination = createTempDir()
        try {
            SafeTarGzExtractor.extract(archive, destination)
            fail("expected absolute path rejection")
        } catch (error: Exception) {
            assertTrue(error.message.orEmpty().contains("Absolute") ||
                error.message.orEmpty().contains("escapes") || error.message.orEmpty().contains("rejected"))
        }
    }

    @Test
    fun extractsSafeFilesAndReportsByteCount() {
        val archive = buildTarGz(
            listOf(
                TarEntry("lib/libjli.so", "fake-jli".toByteArray()),
                TarEntry("release", "JAVA_VERSION=\"17.0.5\"\n".toByteArray()),
            ),
        )
        val destination = createTempDir()
        val result = SafeTarGzExtractor.extract(archive, destination)
        assertEquals(2, result.filesWritten)
        assertEquals("fake-jli".length.toLong() + File(destination, "release").length(), result.bytesWritten)
        assertTrue(File(destination, "lib/libjli.so").isFile)
        assertTrue(File(destination, "release").readText().contains("17"))
    }

    @Test
    fun streamsLargeEntriesWithoutChangingTheirContents() {
        val bytes = ByteArray(8 * 1024 * 1024) { (it % 251).toByte() }
        val archive = buildTarGz(listOf(TarEntry("lib/large.bin", bytes)))
        val destination = createTempDir()

        val result = SafeTarGzExtractor.extract(archive, destination)

        assertEquals(bytes.size.toLong(), result.bytesWritten)
        assertTrue(File(destination, "lib/large.bin").readBytes().contentEquals(bytes))
    }

    @Test
    fun rejectsNonEmptyDestinationWithSymlinkParents() {
        val parent = createTempDir()
        val destination = File(parent, "destination").apply { mkdirs() }
        val outside = File(parent, "outside").apply { mkdirs() }
        java.nio.file.Files.createSymbolicLink(File(destination, "link").toPath(), outside.toPath())
        val archive = buildTarGz(listOf(TarEntry("link/evil.txt", "bad".toByteArray())))

        try {
            SafeTarGzExtractor.extract(archive, destination)
            fail("expected non-empty destination to be rejected")
        } catch (error: Exception) {
            assertTrue(error.message.orEmpty().contains("empty"))
        }
        assertFalse(File(outside, "evil.txt").exists())
    }

    @Test
    fun skipsSymbolicLinksInsteadOfCreatingThem() {
        val archive = buildTarGz(
            listOf(
                TarEntry("link", ByteArray(0), typeFlag = '2', linkName = "../../outside"),
                TarEntry("link/payload.txt", "safe".toByteArray()),
            ),
        )
        val destination = createTempDir()

        SafeTarGzExtractor.extract(archive, destination)

        assertTrue(File(destination, "link/payload.txt").isFile)
        assertFalse(java.nio.file.Files.isSymbolicLink(File(destination, "link").toPath()))
    }

    private data class TarEntry(
        val name: String,
        val data: ByteArray,
        val typeFlag: Char = '0',
        val linkName: String? = null,
    )

    private fun buildTarGz(entries: List<TarEntry>): File {
        val tarBytes = ByteArrayOutputStream()
        for (entry in entries) {
            val header = ByteArray(512)
            val nameBytes = entry.name.toByteArray(Charsets.UTF_8)
            System.arraycopy(nameBytes, 0, header, 0, minOf(nameBytes.size, 99))
            writeOctal(header, 100, 8, 0b1000000000)
            writeOctal(header, 124, 12, entry.data.size.toLong())
            writeOctal(header, 136, 12, System.currentTimeMillis() / 1000)
            header[156] = entry.typeFlag.code.toByte()
            entry.linkName?.let { link ->
                val bytes = link.toByteArray(Charsets.UTF_8)
                System.arraycopy(bytes, 0, header, 157, minOf(bytes.size, 99))
            }
            val magic = "ustar\u000000".toByteArray()
            System.arraycopy(magic, 0, header, 257, 8)
            for (index in 148 until 156) header[index] = ' '.code.toByte()
            var checksum = 0
            for (byte in header) checksum += byte.toInt() and 0xff
            writeOctal(header, 148, 8, checksum.toLong())
            tarBytes.write(header)
            tarBytes.write(entry.data)
            val padding = (512 - (entry.data.size % 512)) % 512
            if (padding > 0) tarBytes.write(ByteArray(padding))
        }
        tarBytes.write(ByteArray(1024))

        val output = createTempFile(suffix = ".tar.gz")
        GZIPOutputStream(output.outputStream()).use { gzip -> gzip.write(tarBytes.toByteArray()) }
        return output
    }

    private fun writeOctal(header: ByteArray, offset: Int, length: Int, value: Long) {
        val encoded = value.toString(8).padStart(length - 1, '0').toByteArray()
        System.arraycopy(encoded, 0, header, offset, minOf(encoded.size, length - 1))
        header[offset + length - 1] = 0
    }
}
