package com.flintlauncher.android.runtime

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream

/**
 * Minimal, safety-focused ustar/GNU tar.gz extractor for mobile JRE packages.
 *
 * Guarantees:
 * - All extracted paths stay under [destDir] (rejects `..` and absolute paths).
 * - Symbolic links are skipped (not followed or created).
 * - Hard links are skipped.
 * - File size is capped per entry and total.
 * - Incomplete extraction cleans up the destination directory.
 *
 * Not a general-purpose tar implementation. Sufficient for OpenJDK JRE layouts.
 */
object SafeTarGzExtractor {

    private const val MAX_ENTRY_BYTES = 256L * 1024 * 1024 // 256 MiB single file
    private const val MAX_TOTAL_BYTES = 1024L * 1024 * 1024 // 1 GiB total
    private const val BLOCK = 512

    data class Result(val filesWritten: Int, val bytesWritten: Long)

    fun extract(tarGz: File, destDir: File): Result {
        require(tarGz.isFile) { "Archive not found: $tarGz" }
        destDir.mkdirs()
        val destCanonical = destDir.canonicalFile
        var files = 0
        var total = 0L
        try {
            GZIPInputStream(BufferedInputStream(FileInputStream(tarGz))).use { gzip ->
                val buf = ByteArray(BLOCK)
                while (true) {
                    val header = readFully(gzip, buf) ?: break
                    if (isZeroBlock(header)) {
                        // possible end-of-archive; read one more zero block if present
                        readFully(gzip, buf)
                        break
                    }
                    val name = parseName(header)
                    val size = parseOctal(header, 124, 12)
                    val typeFlag = header[156].toInt().toChar()
                    val prefix = parseString(header, 345, 155)
                    val fullName = if (prefix.isNotEmpty()) "$prefix/$name" else name

                    // Consume entry data (even if we skip writing)
                    val data = if (size > 0) {
                        if (size > MAX_ENTRY_BYTES) throw IOException("Entry too large: $fullName ($size)")
                        val bytes = ByteArray(size.toInt())
                        readFullyOrThrow(gzip, bytes)
                        // pad to 512
                        val pad = ((size + BLOCK - 1) / BLOCK) * BLOCK - size
                        if (pad > 0) {
                            val skip = ByteArray(pad.toInt())
                            readFullyOrThrow(gzip, skip)
                        }
                        bytes
                    } else {
                        ByteArray(0)
                    }

                    when (typeFlag) {
                        '0', '\u0000' -> { // regular file
                            val outFile = resolveSafe(destCanonical, fullName)
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { it.write(data) }
                            files++
                            total += data.size
                            if (total > MAX_TOTAL_BYTES) throw IOException("Archive exceeds total size limit")
                        }
                        '5' -> { // directory
                            resolveSafe(destCanonical, fullName).mkdirs()
                        }
                        '1', '2' -> {
                            // hard link / symlink — skip for safety
                        }
                        else -> {
                            // other types ignored
                        }
                    }
                }
            }
        } catch (e: Exception) {
            destDir.deleteRecursively()
            throw e
        }
        return Result(files, total)
    }

    private fun resolveSafe(root: File, relative: String): File {
        if (relative.isBlank()) throw IOException("Empty entry name")
        if (relative.startsWith("/") || relative.startsWith("\\")) {
            throw IOException("Absolute path rejected: $relative")
        }
        val normalized = relative.replace('\\', '/')
        if (normalized.split('/').any { it == ".." }) {
            throw IOException("Path traversal rejected: $relative")
        }
        val candidate = File(root, normalized).canonicalFile
        if (!candidate.path.startsWith(root.path)) {
            throw IOException("Entry escapes destination: $relative")
        }
        return candidate
    }

    private fun parseName(header: ByteArray): String {
        // support ustar prefix already handled; name at 0..99
        return parseString(header, 0, 100)
    }

    private fun parseString(header: ByteArray, off: Int, len: Int): String {
        var end = off
        val limit = off + len
        while (end < limit && header[end] != 0.toByte()) end++
        return String(header, off, end - off, Charsets.UTF_8)
    }

    private fun parseOctal(header: ByteArray, off: Int, len: Int): Long {
        var value = 0L
        for (i in off until off + len) {
            val c = header[i].toInt()
            if (c == 0 || c == ' '.code) break
            if (c < '0'.code || c > '7'.code) break
            value = value * 8 + (c - '0'.code)
        }
        return value
    }

    private fun isZeroBlock(block: ByteArray): Boolean = block.all { it == 0.toByte() }

    private fun readFully(input: java.io.InputStream, buf: ByteArray): ByteArray? {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) return if (off == 0) null else throw IOException("Truncated tar stream")
            off += n
        }
        return buf
    }

    private fun readFullyOrThrow(input: java.io.InputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) throw IOException("Truncated tar entry")
            off += n
        }
    }
}
