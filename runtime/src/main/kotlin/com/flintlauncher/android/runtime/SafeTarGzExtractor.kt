package com.flintlauncher.android.runtime

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.util.zip.GZIPInputStream

/**
 * Streaming, safety-focused ustar/GNU tar.gz extractor for mobile JRE packages.
 * Regular files use a fixed-size buffer; symbolic and hard links are never created.
 */
object SafeTarGzExtractor {
    private const val MAX_ENTRY_BYTES = 256L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 1024L * 1024 * 1024
    private const val MAX_METADATA_BYTES = 16 * 1024
    private const val MAX_ENTRIES = 100_000
    private const val BLOCK = 512
    private const val BUFFER_SIZE = 64 * 1024

    data class Result(val filesWritten: Int, val bytesWritten: Long)

    fun extract(tarGz: File, destDir: File): Result {
        require(tarGz.isFile) { "Archive not found: " + tarGz }
        if (destDir.exists() && !destDir.deleteRecursively()) {
            throw IOException("Could not clear JRE staging directory")
        }
        if (!destDir.mkdirs() && !destDir.isDirectory) {
            throw IOException("Could not create JRE staging directory")
        }

        val root = destDir.canonicalFile
        var files = 0
        var writtenBytes = 0L
        var processedBytes = 0L
        var entries = 0
        var nextLongName: String? = null

        try {
            GZIPInputStream(BufferedInputStream(FileInputStream(tarGz))).use { gzip ->
                val headerBuffer = ByteArray(BLOCK)
                while (true) {
                    val header = readBlock(gzip, headerBuffer) ?: break
                    if (isZeroBlock(header)) {
                        readBlock(gzip, headerBuffer)
                        break
                    }

                    entries++
                    if (entries > MAX_ENTRIES) throw IOException("Archive contains too many entries")
                    verifyChecksum(header)

                    val shortName = parseString(header, 0, 100)
                    val prefix = parseString(header, 345, 155)
                    val headerName = if (prefix.isBlank()) shortName else prefix + "/" + shortName
                    val entryName = nextLongName ?: headerName
                    nextLongName = null
                    val size = parseOctal(header, 124, 12)
                    if (size < 0L || size > MAX_ENTRY_BYTES) {
                        throw IOException("Entry too large or invalid: " + entryName + " (" + size + ")")
                    }
                    if (size > MAX_TOTAL_BYTES - processedBytes) {
                        throw IOException("Archive exceeds total uncompressed size limit")
                    }
                    processedBytes += size
                    val typeFlag = header[156].toInt().toChar()

                    when (typeFlag) {
                        'L' -> {
                            if (size > MAX_METADATA_BYTES) throw IOException("GNU long-name metadata is too large")
                            nextLongName = readTextEntry(gzip, size)
                        }
                        'K' -> {
                            if (size > MAX_METADATA_BYTES) throw IOException("GNU long-link metadata is too large")
                            skipEntry(gzip, size)
                        }
                        'x', 'g' -> throw IOException("PAX tar metadata is not supported for local JRE imports")
                        '0', '\u0000', '7' -> {
                            val output = resolveSafe(root, entryName)
                            val parent = output.parentFile
                                ?: throw IOException("Invalid archive path: " + entryName)
                            ensureNoSymlinkParents(root, parent)
                            if (!parent.mkdirs() && !parent.isDirectory) {
                                throw IOException("Could not create archive directory: " + parent)
                            }
                            if (Files.isSymbolicLink(output.toPath())) {
                                throw IOException("Refusing to overwrite a symbolic link: " + entryName)
                            }
                            FileOutputStream(output).use { stream -> copyEntry(gzip, stream, size) }
                            skipPadding(gzip, size)
                            files++
                            writtenBytes += size
                        }
                        '5' -> {
                            val directory = resolveSafe(root, entryName)
                            ensureNoSymlinkParents(root, directory)
                            if (!directory.mkdirs() && !directory.isDirectory) {
                                throw IOException("Could not create archive directory: " + directory)
                            }
                            skipEntry(gzip, size)
                        }
                        '1', '2' -> skipEntry(gzip, size)
                        else -> skipEntry(gzip, size)
                    }
                }
            }
        } catch (error: Exception) {
            destDir.deleteRecursively()
            throw error
        }

        return Result(files, writtenBytes)
    }

    private fun resolveSafe(root: File, relative: String): File {
        if (relative.isBlank() || relative.startsWith('/') || relative.startsWith('\\')) {
            throw IOException("Absolute or empty archive path rejected: " + relative)
        }
        val normalized = relative.replace('\\', '/').trimEnd('/')
        if (normalized.isBlank()) return root
        val segments = normalized.split('/')
        if (segments.any { it.isEmpty() || it == ".." }) {
            throw IOException("Archive path traversal or empty segment rejected: " + relative)
        }
        val safeSegments = segments.filterNot { it == "." }
        if (safeSegments.isEmpty()) return root
        val candidate = File(root, safeSegments.joinToString(File.separator)).canonicalFile
        val rootPath = root.path
        if (candidate.path != rootPath && !candidate.path.startsWith(rootPath + File.separator)) {
            throw IOException("Archive entry escapes destination: " + relative)
        }
        return candidate
    }

    private fun ensureNoSymlinkParents(root: File, path: File) {
        var current: File? = path
        while (current != null && current != root) {
            if (Files.isSymbolicLink(current.toPath())) {
                throw IOException("Archive writes through a symbolic link: " + current)
            }
            current = current.parentFile
        }
    }

    private fun verifyChecksum(header: ByteArray) {
        val expected = parseOctal(header, 148, 8)
        var actual = 0L
        header.forEachIndexed { index, byte ->
            actual += if (index in 148..155) ' '.code.toLong() else (byte.toInt() and 0xff).toLong()
        }
        if (expected != actual) throw IOException("Invalid tar header checksum")
    }

    private fun parseString(header: ByteArray, offset: Int, length: Int): String {
        var end = offset
        val limit = offset + length
        while (end < limit && header[end] != 0.toByte()) end++
        return String(header, offset, end - offset, Charsets.UTF_8)
    }

    private fun parseOctal(header: ByteArray, offset: Int, length: Int): Long {
        var value = 0L
        var seenDigit = false
        var terminated = false
        for (index in offset until offset + length) {
            val character = header[index].toInt() and 0xff
            if (character == 0 || character == ' '.code) {
                if (seenDigit) terminated = true
                continue
            }
            if (terminated || character !in '0'.code..'7'.code) {
                throw IOException("Invalid octal value in tar header")
            }
            val digit = (character - '0'.code).toLong()
            if (value > (Long.MAX_VALUE - digit) / 8L) {
                throw IOException("Tar header numeric value overflow")
            }
            value = value * 8L + digit
            seenDigit = true
        }
        return value
    }

    private fun readTextEntry(input: java.io.InputStream, size: Long): String {
        val bytes = ByteArray(size.toInt())
        readFully(input, bytes)
        skipPadding(input, size)
        return String(bytes, Charsets.UTF_8).trimEnd('\u0000', '\n', '\r')
    }

    private fun copyEntry(input: java.io.InputStream, output: FileOutputStream, size: Long) {
        var remaining = size
        val buffer = ByteArray(BUFFER_SIZE)
        while (remaining > 0L) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (count < 0) throw IOException("Truncated tar entry")
            output.write(buffer, 0, count)
            remaining -= count.toLong()
        }
    }

    private fun skipEntry(input: java.io.InputStream, size: Long) {
        var remaining = size
        val buffer = ByteArray(BUFFER_SIZE)
        while (remaining > 0L) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (count < 0) throw IOException("Truncated tar entry")
            remaining -= count.toLong()
        }
        skipPadding(input, size)
    }

    private fun skipPadding(input: java.io.InputStream, size: Long) {
        val padding = ((size + BLOCK - 1) / BLOCK) * BLOCK - size
        if (padding > 0L) readFully(input, ByteArray(padding.toInt()))
    }

    private fun isZeroBlock(block: ByteArray): Boolean = block.all { it == 0.toByte() }

    private fun readBlock(input: java.io.InputStream, buffer: ByteArray): ByteArray? {
        var offset = 0
        while (offset < buffer.size) {
            val count = input.read(buffer, offset, buffer.size - offset)
            if (count < 0) {
                if (offset == 0) return null
                throw IOException("Truncated tar header")
            }
            offset += count
        }
        return buffer
    }

    private fun readFully(input: java.io.InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val count = input.read(buffer, offset, buffer.size - offset)
            if (count < 0) throw IOException("Truncated tar stream")
            offset += count
        }
    }
}
