package com.flintlauncher.android.runtime

import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Safety-focused extractor for the .tar.xz mobile JRE packages published by
 * AngelAuraMC's OpenJDK build project.
 *
 * Regular files are extracted before links are created. Absolute paths and
 * traversal are rejected, and links must resolve inside the JRE directory.
 */
object SafeTarXzExtractor {
    private const val BLOCK = 512
    private const val MAX_ENTRY_BYTES = 256L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 1024L * 1024 * 1024
    private const val MAX_METADATA_BYTES = 16 * 1024

    data class Result(val filesWritten: Int, val bytesWritten: Long)

    private data class PendingLink(
        val path: Path,
        val target: String,
        val symbolic: Boolean,
    )

    fun extract(archive: File, destination: File): Result {
        require(archive.isFile) { "JRE archive not found: $archive" }
        if (destination.exists()) destination.deleteRecursively()
        if (!destination.mkdirs() && !destination.isDirectory) {
            throw IOException("Could not create JRE staging directory")
        }

        val root = destination.canonicalFile.toPath()
        val links = mutableListOf<PendingLink>()
        var filesWritten = 0
        var totalBytes = 0L
        var nextLongName: String? = null
        var nextLongLink: String? = null

        try {
            XZInputStream(BufferedInputStream(FileInputStream(archive))).use { input ->
                val header = ByteArray(BLOCK)
                while (true) {
                    readBlock(input, header) ?: break
                    if (header.all { it == 0.toByte() }) {
                        readBlock(input, header)
                        break
                    }

                    val shortName = parseString(header, 0, 100)
                    val prefix = parseString(header, 345, 155)
                    val headerName = if (prefix.isBlank()) shortName else "$prefix/$shortName"
                    val name = nextLongName ?: headerName
                    nextLongName = null
                    val size = parseOctal(header, 124, 12)
                    if (size < 0 || size > MAX_ENTRY_BYTES) {
                        throw IOException("JRE archive entry has an invalid size: $name")
                    }
                    val type = header[156].toInt().toChar()
                    val linkName = nextLongLink ?: parseString(header, 157, 100)
                    nextLongLink = null

                    when (type) {
                        'L' -> {
                            if (size > MAX_METADATA_BYTES) throw IOException("JRE path metadata is too large")
                            nextLongName = readTextEntry(input, size)
                        }
                        'K' -> {
                            if (size > MAX_METADATA_BYTES) throw IOException("JRE link metadata is too large")
                            nextLongLink = readTextEntry(input, size)
                        }
                        'x', 'g' -> {
                            throw IOException("Unsupported PAX metadata in JRE archive")
                        }
                        '0', '\u0000', '7' -> {
                            val output = resolveSafe(root, name)
                            ensureNoSymlinkParents(root, output.parent)
                            output.parent?.let { Files.createDirectories(it) }
                            FileOutputStream(output.toFile()).use { stream ->
                                totalBytes += copyEntry(input, stream, size)
                            }
                            filesWritten++
                            val mode = parseOctal(header, 100, 8).toInt()
                            if (mode and 0b001001001 != 0) output.toFile().setExecutable(true, false)
                            if (totalBytes > MAX_TOTAL_BYTES) {
                                throw IOException("JRE archive exceeds the extraction size limit")
                            }
                            skipPadding(input, size)
                        }
                        '5' -> {
                            val directory = resolveSafe(root, name)
                            ensureNoSymlinkParents(root, directory.parent)
                            Files.createDirectories(directory)
                            skipEntry(input, size)
                        }
                        '2' -> {
                            val linkPath = resolveSafe(root, name)
                            validateLinkTarget(root, linkPath, linkName, symbolic = true)
                            links += PendingLink(linkPath, linkName, symbolic = true)
                            skipEntry(input, size)
                        }
                        '1' -> {
                            val linkPath = resolveSafe(root, name)
                            validateLinkTarget(root, linkPath, linkName, symbolic = false)
                            links += PendingLink(linkPath, linkName, symbolic = false)
                            skipEntry(input, size)
                        }
                        else -> {
                            skipEntry(input, size)
                        }
                    }
                }
            }

            links.filterNot { it.symbolic }.forEach { link ->
                ensureNoSymlinkParents(root, link.path.parent)
                val target = resolveSafe(root, link.target)
                if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                    throw IOException("JRE hard-link target is missing: " + link.target)
                }
                Files.createDirectories(link.path.parent)
                Files.createLink(link.path, target)
            }
            links.filter { it.symbolic }.forEach { link ->
                ensureNoSymlinkParents(root, link.path.parent)
                val rawTarget = Paths.get(link.target.replace('\\', '/'))
                if (rawTarget.isAbsolute) throw IOException("Absolute JRE symlink target rejected")
                Files.createDirectories(link.path.parent)
                Files.createSymbolicLink(link.path, rawTarget)
            }
        } catch (error: Exception) {
            destination.deleteRecursively()
            throw error
        }

        return Result(filesWritten, totalBytes)
    }

    private fun validateLinkTarget(root: Path, linkPath: Path, target: String, symbolic: Boolean) {
        if (target.isBlank() || target.startsWith("/") || target.startsWith("\\")) {
            throw IOException("Absolute or empty JRE link target rejected: $target")
        }
        val normalizedTarget = target.replace('\\', '/')
        val raw = Paths.get(normalizedTarget)
        val resolved = if (symbolic) linkPath.parent.resolve(raw).normalize() else root.resolve(raw).normalize()
        if (!resolved.startsWith(root)) throw IOException("JRE link escapes its runtime directory: $target")
    }

    private fun resolveSafe(root: Path, relative: String): Path {
        if (relative.isBlank() || relative.startsWith("/") || relative.startsWith("\\")) {
            throw IOException("Absolute or empty JRE path rejected: $relative")
        }
        val normalized = relative.replace('\\', '/')
        val segments = normalized.split('/')
        if (segments.any { it == ".." }) throw IOException("JRE path traversal rejected: $relative")
        val result = root.resolve(normalized).normalize()
        if (!result.startsWith(root)) throw IOException("JRE path escapes its runtime directory: $relative")
        return result
    }

    private fun ensureNoSymlinkParents(root: Path, parent: Path?) {
        var current = parent ?: return
        while (current.startsWith(root) && current != root) {
            if (Files.isSymbolicLink(current)) throw IOException("JRE archive writes through a symbolic link")
            current = current.parent ?: break
        }
    }

    private fun readTextEntry(input: java.io.InputStream, size: Long): String {
        val bytes = ByteArray(size.toInt())
        readFully(input, bytes)
        skipPadding(input, size)
        return String(bytes, Charsets.UTF_8).trimEnd('\u0000', '\n', '\r')
    }

    private fun copyEntry(input: java.io.InputStream, output: FileOutputStream, size: Long): Long {
        var remaining = size
        var copied = 0L
        val buffer = ByteArray(64 * 1024)
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (count < 0) throw IOException("Truncated JRE archive entry")
            output.write(buffer, 0, count)
            remaining -= count
            copied += count
        }
        return copied
    }

    private fun skipEntry(input: java.io.InputStream, size: Long) {
        var remaining = size
        val buffer = ByteArray(64 * 1024)
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (count < 0) throw IOException("Truncated JRE archive entry")
            remaining -= count
        }
        skipPadding(input, size)
    }

    private fun skipPadding(input: java.io.InputStream, size: Long) {
        val padding = ((size + BLOCK - 1) / BLOCK) * BLOCK - size
        if (padding > 0) {
            val bytes = ByteArray(padding.toInt())
            readFully(input, bytes)
        }
    }

    private fun readBlock(input: java.io.InputStream, buffer: ByteArray): ByteArray? {
        var offset = 0
        while (offset < buffer.size) {
            val count = input.read(buffer, offset, buffer.size - offset)
            if (count < 0) {
                if (offset == 0) return null
                throw IOException("Truncated JRE tar header")
            }
            offset += count
        }
        return buffer
    }

    private fun readFully(input: java.io.InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val count = input.read(buffer, offset, buffer.size - offset)
            if (count < 0) throw IOException("Truncated JRE archive")
            offset += count
        }
    }

    private fun parseString(header: ByteArray, offset: Int, length: Int): String {
        var end = offset
        val limit = offset + length
        while (end < limit && header[end] != 0.toByte()) end++
        return String(header, offset, end - offset, Charsets.UTF_8)
    }

    private fun parseOctal(header: ByteArray, offset: Int, length: Int): Long {
        var value = 0L
        for (index in offset until offset + length) {
            val character = header[index].toInt() and 0xff
            if (character == 0 || character == ' '.code) break
            if (character !in '0'.code..'7'.code) break
            value = value * 8 + (character - '0'.code)
        }
        return value
    }
}
