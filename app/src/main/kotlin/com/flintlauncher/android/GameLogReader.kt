package com.flintlauncher.android

import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Reads Minecraft log files from a profile's game directory. Supports both plain
 * `latest.log` and rotated `*.log.gz` files (gunzip via GZIPInputStream). Reads are
 * bounded so a multi-megabyte log cannot exhaust memory; the caller asks for a tail.
 */
object GameLogReader {

    data class LogFile(
        val name: String,
        val path: File,
        val compressed: Boolean,
        val lastModifiedMillis: Long,
        val sizeBytes: Long,
    )

    private const val GZIP_MAGIC = 0x1f8b

    /** List log files in <gameDir>/logs, newest first. Returns empty if the logs dir does not exist. */
    fun listLogs(gameDir: File): List<LogFile> {
        val logsDir = File(gameDir, "logs")
        if (!logsDir.isDirectory) return emptyList()
        return logsDir.listFiles { f ->
            f.isFile && (f.name == "latest.log" || f.name.endsWith(".log.gz") || f.name.endsWith(".log"))
        }?.map { f ->
            LogFile(
                name = f.name,
                path = f,
                compressed = isGzip(f),
                lastModifiedMillis = f.lastModified(),
                sizeBytes = f.length(),
            )
        }?.sortedByDescending { it.lastModifiedMillis } ?: emptyList()
    }

    /** Read up to [maxBytes] of decoded log text. For compressed files this is the decompressed limit. */
    fun readTail(file: LogFile, maxBytes: Long = 512L * 1024): String {
        if (!file.path.isFile) return ""
        val stream: InputStream = if (file.compressed) {
            file.path.inputStream().buffered().let { GZIPInputStream(it) }
        } else {
            file.path.inputStream().buffered()
        }
        return stream.use { input ->
            val buffer = ByteArray(8192)
            val out = StringBuilder()
            var total = 0L
            while (total < maxBytes) {
                val toRead = minOf(buffer.size.toLong(), maxBytes - total).toInt()
                val read = input.read(buffer, 0, toRead)
                if (read <= 0) break
                out.append(String(buffer, 0, read, Charsets.UTF_8))
                total += read
            }
            out.toString()
        }
    }

    /** Last [lines] lines of the log, bounded by [maxBytes] of decoded input. */
    fun tailLines(file: LogFile, lines: Int = 200, maxBytes: Long = 512L * 1024): String {
        val text = readTail(file, maxBytes)
        val all = text.lines()
        return if (all.size <= lines) text else all.takeLast(lines).joinToString("\n")
    }

    private fun isGzip(file: File): Boolean {
        if (file.name.endsWith(".gz")) return true
        return runCatching {
            file.inputStream().use { input ->
                val b1 = input.read()
                val b2 = input.read()
                val magic = (b1 shl 8) or b2
                magic == GZIP_MAGIC
            }
        }.getOrDefault(false)
    }
}
