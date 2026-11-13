package com.flintlauncher.android.download

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
private data class PersistedRequest(
    val id: String,
    val url: String,
    val path: String,
    val sizeBytes: Long? = null,
    val hashAlgorithm: HashAlgorithm? = null,
    val hashHex: String? = null,
)

/**
 * Remembers the pending batch across app restarts. After a restart the caller loads
 * the list and runs it again: valid files are skipped and `.part` files resume.
 * A missing or corrupt store simply yields an empty queue.
 */
class FileQueueStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }

    fun save(requests: List<DownloadRequest>) {
        val persisted = requests.map {
            PersistedRequest(it.id, it.url, it.destination.path, it.sizeBytes, it.hash?.algorithm, it.hash?.hex)
        }
        file.absoluteFile.parentFile?.mkdirs()
        val temp = File(file.path + ".tmp")
        temp.writeText(json.encodeToString(persisted))
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    fun load(): List<DownloadRequest> {
        if (!file.isFile) return emptyList()
        return try {
            json.decodeFromString<List<PersistedRequest>>(file.readText()).map {
                val hash = if (it.hashAlgorithm != null && it.hashHex != null) ExpectedHash(it.hashAlgorithm, it.hashHex) else null
                DownloadRequest(it.id, it.url, File(it.path), it.sizeBytes, hash)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clear() {
        file.delete()
    }
}
