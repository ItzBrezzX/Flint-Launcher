package com.flintlauncher.android.modpacks

import com.flintlauncher.android.download.DownloadManager
import com.flintlauncher.android.download.DownloadRequest
import com.flintlauncher.android.download.ExpectedHash
import com.flintlauncher.android.download.HashAlgorithm
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.CRC32
import java.util.zip.ZipFile

data class PackMetadata(
    val name: String,
    val minecraftVersion: String,
    val loader: String?,
    val loaderVersion: String?,
    val summary: String?,
)

data class MrpackPlan(
    val metadata: PackMetadata,
    val downloads: List<DownloadRequest>,
    /** Zip entry name -> relative path under the selected game directory. */
    val overrideEntries: List<Pair<String, String>>,
    val skippedClientUnsupported: Int,
)

data class MrpackInstallSummary(
    val metadata: PackMetadata,
    val downloadedFiles: Int,
    val extractedOverrides: Int,
)

sealed interface MrpackInstallResult {
    data class Success(val summary: MrpackInstallSummary) : MrpackInstallResult
    data class Failure(val message: String, val failedFiles: List<String> = emptyList()) : MrpackInstallResult
    data object Cancelled : MrpackInstallResult
}

class MrpackFormatException(message: String) : IllegalArgumentException(message)

/**
 * Imports the contents of a Modrinth .mrpack archive. Download URLs must use HTTPS,
 * archive paths are constrained to the selected game directory, and downloaded files
 * are hash-verified by DownloadManager. The app layer creates the profile and installs
 * the loader declared by the pack metadata.
 */
class MrpackInstaller(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun parse(zipFile: File): MrpackManifest {
        if (!zipFile.isFile) throw MrpackFormatException("mrpack archive does not exist")
        if (zipFile.length() > MAX_ARCHIVE_BYTES) throw MrpackFormatException("mrpack archive is too large")
        try {
            ZipFile(zipFile).use { zip ->
                val entry = zip.getEntry(INDEX_JSON)
                    ?: throw MrpackFormatException("mrpack is missing $INDEX_JSON")
                if (entry.size > MAX_INDEX_BYTES) throw MrpackFormatException("$INDEX_JSON is too large")
                val bytes = java.io.ByteArrayOutputStream()
                zip.getInputStream(entry).use { input ->
                    val buffer = ByteArray(8 * 1024)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_INDEX_BYTES) {
                            throw MrpackFormatException("$INDEX_JSON is too large")
                        }
                        bytes.write(buffer, 0, count)
                    }
                }
                val text = bytes.toString(Charsets.UTF_8.name())
                return try {
                    json.decodeFromString(MrpackManifest.serializer(), text)
                } catch (e: MrpackFormatException) {
                    throw e
                } catch (e: Exception) {
                    throw MrpackFormatException("Could not parse $INDEX_JSON: ${e.message}")
                }
            }
        } catch (e: MrpackFormatException) {
            throw e
        } catch (e: Exception) {
            throw MrpackFormatException("Could not read mrpack archive: ${e.message}")
        }
    }

    fun plan(manifest: MrpackManifest, zipFile: File, gameDir: File): MrpackPlan {
        if (!zipFile.isFile) throw MrpackFormatException("mrpack archive does not exist")
        if (zipFile.length() > MAX_ARCHIVE_BYTES) throw MrpackFormatException("mrpack archive is too large")
        if (manifest.files.size > MAX_MANIFEST_FILES) throw MrpackFormatException("mrpack contains too many downloadable files")
        val requests = mutableListOf<DownloadRequest>()
        var totalDownloadBytes = 0L
        val destinations = mutableSetOf<String>()
        var skipped = 0

        for (file in manifest.files) {
            if (file.clientUnsupported) {
                skipped++
                continue
            }
            val url = file.downloads.firstOrNull { it.isNotBlank() }
                ?: throw MrpackFormatException("Pack file ${file.path} has no download URL")
            validateDownloadUrl(url, file.path)

            val destination = safeResolve(gameDir, file.path)
            if (!destinations.add(destination.path)) {
                throw MrpackFormatException("Pack contains duplicate destination path: ${file.path}")
            }

            val hash = when {
                file.hashes.sha512?.matches(SHA512) == true ->
                    ExpectedHash(HashAlgorithm.SHA512, file.hashes.sha512.lowercase())
                file.hashes.sha1?.matches(SHA1) == true ->
                    ExpectedHash(HashAlgorithm.SHA1, file.hashes.sha1.lowercase())
                else -> throw MrpackFormatException("Pack file ${file.path} has no valid SHA-512 or SHA-1 hash")
            }
            val fileSize = file.fileSize
                ?: throw MrpackFormatException("Pack file ${file.path} is missing its declared file size")
            if (fileSize < 0L || fileSize > MAX_MOD_FILE_BYTES) {
                throw MrpackFormatException("Pack file ${file.path} has an invalid or excessive file size")
            }
            if (fileSize > MAX_MOD_TOTAL_BYTES - totalDownloadBytes) {
                throw MrpackFormatException("Modpack download size exceeds the configured safety limit")
            }
            totalDownloadBytes += fileSize

            requests += DownloadRequest(
                id = "mrpack:${file.path}",
                url = url,
                destination = destination,
                sizeBytes = fileSize,
                hash = hash,
            )
        }

        return MrpackPlan(
            metadata = PackMetadata(
                name = manifest.name,
                minecraftVersion = manifest.dependencies.minecraft
                    ?: throw MrpackFormatException("mrpack does not declare a Minecraft version"),
                loader = manifest.dependencies.loader,
                loaderVersion = manifest.dependencies.loaderVersion,
                summary = manifest.summary,
            ),
            downloads = requests,
            overrideEntries = collectOverrideEntries(zipFile, gameDir),
            skippedClientUnsupported = skipped,
        )
    }

    suspend fun install(
        zipFile: File,
        gameDir: File,
        downloads: DownloadManager,
    ): MrpackInstallResult {
        return try {
            val manifest = parse(zipFile)
            val plan = plan(manifest, zipFile, gameDir)
            if (plan.downloads.isNotEmpty()) {
                val result = downloads.run(plan.downloads)
                if (result.cancelled) return MrpackInstallResult.Cancelled
                if (result.failed.isNotEmpty()) {
                    return MrpackInstallResult.Failure(
                        "${result.failed.size} modpack file(s) failed to download",
                        result.failed.map { it.request.destination.name },
                    )
                }
            }
            val extracted = extractOverrides(zipFile, gameDir, plan.overrideEntries)
            MrpackInstallResult.Success(MrpackInstallSummary(plan.metadata, plan.downloads.size, extracted))
        } catch (e: CancellationException) {
            throw e
        } catch (e: MrpackFormatException) {
            MrpackInstallResult.Failure(e.message ?: "Invalid mrpack")
        } catch (e: Exception) {
            MrpackInstallResult.Failure(e.message ?: "mrpack install failed")
        }
    }

    private fun collectOverrideEntries(zipFile: File, gameDir: File): List<Pair<String, String>> {
        val entries = mutableListOf<Pair<String, String>>()
        val destinations = mutableSetOf<String>()
        var archiveEntryCount = 0
        var overrideBytes = 0L
        ZipFile(zipFile).use { zip ->
            for (entry in zip.entries().asSequence()) {
                archiveEntryCount++
                if (archiveEntryCount > MAX_ARCHIVE_ENTRIES) {
                    throw MrpackFormatException("mrpack contains too many ZIP entries")
                }
                if (entry.isDirectory) continue
                val prefix = OVERRIDE_DIRS.firstOrNull { entry.name.startsWith(it) } ?: continue
                val relative = entry.name.removePrefix(prefix)
                if (relative.isBlank()) throw MrpackFormatException("Override entry has an empty destination path")
                if (entries.size >= MAX_OVERRIDE_ENTRIES) {
                    throw MrpackFormatException("mrpack contains too many override files")
                }
                if (entry.size < 0L || entry.size > MAX_OVERRIDE_FILE_BYTES) {
                    throw MrpackFormatException("Override file is too large or has an unknown size: " + relative)
                }
                if (entry.size > MAX_OVERRIDE_TOTAL_BYTES - overrideBytes) {
                    throw MrpackFormatException("Override data exceeds the configured safety limit")
                }
                overrideBytes += entry.size

                val destination = safeResolve(gameDir, relative)
                if (!destinations.add(destination.path)) {
                    throw MrpackFormatException("Pack contains duplicate override path: " + relative)
                }
                entries += entry.name to relative
            }
        }
        return entries
    }

    private fun extractOverrides(
        zipFile: File,
        gameDir: File,
        overrideEntries: List<Pair<String, String>>,
    ): Int {
        if (overrideEntries.size > MAX_OVERRIDE_ENTRIES) {
            throw MrpackFormatException("mrpack contains too many override files")
        }
        var extracted = 0
        var totalWritten = 0L
        ZipFile(zipFile).use { zip ->
            for ((entryName, relativePath) in overrideEntries) {
                val entry = zip.getEntry(entryName)
                    ?: throw MrpackFormatException("Override entry disappeared from the archive: " + entryName)
                if (entry.isDirectory || entry.size < 0L || entry.size > MAX_OVERRIDE_FILE_BYTES) {
                    throw MrpackFormatException("Override entry has an invalid size or type: " + entryName)
                }
                if (entry.size > MAX_OVERRIDE_TOTAL_BYTES - totalWritten) {
                    throw MrpackFormatException("Override data exceeds the configured safety limit")
                }
                if (entry.crc < 0L) throw MrpackFormatException("Override entry has no CRC checksum: " + entryName)

                val destination = safeResolve(gameDir, relativePath)
                val parent = destination.parentFile
                    ?: throw MrpackFormatException("Invalid override path: " + relativePath)
                if (!parent.mkdirs() && !parent.isDirectory) {
                    throw MrpackFormatException("Could not create override directory: " + parent.absolutePath)
                }
                val verifiedDestination = safeResolve(gameDir, relativePath)
                val temporary = Files.createTempFile(parent.toPath(), ".flint-", ".part").toFile()
                try {
                    val written = zip.getInputStream(entry).use { input ->
                        temporary.outputStream().use { output ->
                            copyOverrideEntry(input, output, entry.size, totalWritten, entry.crc)
                        }
                    }
                    totalWritten += written
                    moveReplacing(temporary, verifiedDestination)
                    extracted++
                } finally {
                    temporary.delete()
                }
            }
        }
        return extracted
    }

    private fun copyOverrideEntry(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        expectedSize: Long,
        totalBefore: Long,
        expectedCrc: Long,
    ): Long {
        val buffer = ByteArray(64 * 1024)
        val crc = CRC32()
        var written = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            val chunk = count.toLong()
            if (written > expectedSize - chunk || written > MAX_OVERRIDE_FILE_BYTES - chunk ||
                totalBefore > MAX_OVERRIDE_TOTAL_BYTES - written - chunk
            ) {
                throw MrpackFormatException("Override stream exceeds its declared or configured size")
            }
            crc.update(buffer, 0, count)
            output.write(buffer, 0, count)
            written += chunk
        }
        if (written != expectedSize) {
            throw MrpackFormatException("Override stream size does not match its ZIP directory entry")
        }
        if (crc.value != expectedCrc) {
            throw MrpackFormatException("Override CRC checksum mismatch")
        }
        return written
    }

    companion object {
        const val INDEX_JSON = "modrinth.index.json"
        val OVERRIDE_DIRS = listOf("overrides/", "client-overrides/")
        private const val MAX_INDEX_BYTES = 4 * 1024 * 1024
        private const val MAX_ARCHIVE_BYTES = 1024L * 1024 * 1024
        private const val MAX_ARCHIVE_ENTRIES = 20_000
        private const val MAX_MANIFEST_FILES = 10_000
        private const val MAX_MOD_FILE_BYTES = 1024L * 1024 * 1024
        private const val MAX_MOD_TOTAL_BYTES = 4L * 1024 * 1024 * 1024
        private const val MAX_OVERRIDE_ENTRIES = 10_000
        private const val MAX_OVERRIDE_FILE_BYTES = 256L * 1024 * 1024
        private const val MAX_OVERRIDE_TOTAL_BYTES = 1024L * 1024 * 1024
        private val SHA512 = Regex("^[0-9a-fA-F]{128}$")
        private val SHA1 = Regex("^[0-9a-fA-F]{40}$")

        fun safeResolve(base: File, relative: String): File {
            if (relative.isBlank() || relative.startsWith('/') || relative.startsWith('\\') ||
                relative.contains('\\') || relative.contains('\u0000') ||
                relative.split('/').any { it == ".." || it == "." || it.isEmpty() }
            ) {
                throw MrpackFormatException("Pack path is invalid or escapes target: $relative")
            }
            val baseCanonical = base.canonicalFile
            val lexicalTarget = File(baseCanonical, relative)
            var current: File? = lexicalTarget
            while (current != null && current != baseCanonical) {
                if (Files.isSymbolicLink(current.toPath())) {
                    throw MrpackFormatException("Pack path traverses a symbolic link: $relative")
                }
                current = current.parentFile
            }
            val target = lexicalTarget.canonicalFile
            if (target == baseCanonical || !target.path.startsWith(baseCanonical.path + File.separator)) {
                throw MrpackFormatException("Pack path escapes target: $relative")
            }
            return target
        }

        private fun validateDownloadUrl        private fun validateDownloadUrl(url: String, path: String) {
            val uri = runCatching { URI(url) }.getOrNull()
                ?: throw MrpackFormatException("Pack file $path has an invalid download URL")
            if (!uri.scheme.equals("https", ignoreCase = true) ||
                uri.host.isNullOrBlank() ||
                uri.rawUserInfo != null ||
                uri.fragment != null
            ) {
                throw MrpackFormatException("Pack file $path must use a safe HTTPS download URL")
            }
        }

        private fun moveReplacing(source: File, destination: File) {
            try {
                Files.move(
                    source.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}
