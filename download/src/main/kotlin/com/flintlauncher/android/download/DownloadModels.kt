package com.flintlauncher.android.download

import java.io.File
import java.security.MessageDigest

enum class HashAlgorithm(val javaName: String) {
    SHA1("SHA-1"),
    SHA256("SHA-256"),
    SHA512("SHA-512"),
}

data class ExpectedHash(val algorithm: HashAlgorithm, val hex: String)

/** One file to fetch. [sizeBytes] and [hash] are verified when present. */
data class DownloadRequest(
    val id: String,
    val url: String,
    val destination: File,
    val sizeBytes: Long? = null,
    val hash: ExpectedHash? = null,
)

enum class BatchState { IDLE, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }

data class FailedDownload(val request: DownloadRequest, val reason: String)

data class BatchResult(
    val completed: Int,
    val failed: List<FailedDownload>,
    val cancelled: Boolean,
)

/** Snapshot for the UI. Every number comes from real byte counters, never from timers. */
data class DownloadProgress(
    val state: BatchState = BatchState.IDLE,
    val totalFiles: Int = 0,
    val completedFiles: Int = 0,
    val failedFiles: Int = 0,
    val downloadedBytes: Long = 0,
    /** Null when at least one file has an unknown size. */
    val totalBytes: Long? = null,
    val activeFiles: List<String> = emptyList(),
    val speedBytesPerSecond: Long = 0,
    val averageBytesPerSecond: Long = 0,
    val etaSeconds: Long? = null,
) {
    val pendingFiles: Int get() = totalFiles - completedFiles - failedFiles

    val percent: Double?
        get() = totalBytes?.takeIf { it > 0 }?.let { downloadedBytes * 100.0 / it }
}

data class DownloadConfig(
    val concurrency: Int = 4,
    val maxAttempts: Int = 4,
    val backoffBaseMillis: Long = 500,
    val backoffMaxMillis: Long = 8_000,
    val bufferSize: Int = 64 * 1024,
    val progressIntervalMillis: Long = 250,
    val storageMarginBytes: Long = 32L * 1024 * 1024,
)

internal object FileHasher {
    fun hex(file: File, algorithm: HashAlgorithm): String {
        val digest = MessageDigest.getInstance(algorithm.javaName)
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun matches(file: File, hash: ExpectedHash): Boolean =
        hex(file, hash.algorithm).equals(hash.hex, ignoreCase = true)
}
