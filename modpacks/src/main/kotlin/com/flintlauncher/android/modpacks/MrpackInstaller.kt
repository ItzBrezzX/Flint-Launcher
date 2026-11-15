package com.flintlauncher.android.modpacks

import com.flintlauncher.android.core.FlintError
import com.flintlauncher.android.core.Outcome
import com.flintlauncher.android.download.DownloadManager
import com.flintlauncher.android.download.DownloadRequest
import com.flintlauncher.android.download.ExpectedHash
import com.flintlauncher.android.download.HashAlgorithm
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipFile

/** Resolved pack metadata the caller turns into a Profile (modpacks module does not depend on :profiles). */
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
    /** Override entries (path inside the zip → relative path under game dir) to extract after downloads. */
    val overrideEntries: List<Pair<String, String>>,
    val skippedClientUnsupported: Int,
)

data class MrpackInstallSummary(
    val metadata: PackMetadata,
    val downloadedFiles: Int,
    val extractedOverrides: Int,
)

class MrpackFormatException(message: String) : IllegalArgumentException(message)

/**
 * Installs a Modrinth .mrpack (a zip with `modrinth.index.json` + optional `overrides/`).
 *
 * - Only client-supported files are downloaded (`env.client != "unsupported"`).
 * - Every download is checksum-verified (sha512 preferred, sha1 fallback) via DownloadManager.
 * - Overrides are extracted with zip-slip protection: any entry that would land outside the
 *   profile game dir aborts the install.
 * - Returns pack metadata; the app shard creates the Profile and runs the loader installer.
 */
class MrpackInstaller(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun parse(zipFile: File): MrpackManifest {
        ZipFile(zipFile).use { zip ->
            val entry = zip.getEntry(INDEX_JSON)
                ?: throw MrpackFormatException("mrpack is missing $INDEX_JSON")
            val text = zip.getInputStream(entry).bufferedReader().readText()
            return try {
                json.decodeFromString(MrpackManifest.serializer(), text)
            } catch (e: Exception) {
                throw MrpackFormatException("Could not parse $INDEX_JSON: ${e.message}")
            }
        }
    }

    fun plan(manifest: MrpackManifest, zipFile: File, gameDir: File): MrpackPlan {
        val requests = mutableListOf<DownloadRequest>()
        var skipped = 0
        for (file in manifest.files) {
            if (file.clientUnsupported) { skipped++; continue }
            val url = file.downloads.firstOrNull()
                ?: throw MrpackFormatException("Pack file ${file.path} has no download URL")
            val destination = safeResolve(gameDir, file.path)
            val hash = when {
                file.hashes.sha512 != null -> ExpectedHash(HashAlgorithm.SHA512, file.hashes.sha512)
                file.hashes.sha1 != null -> ExpectedHash(HashAlgorithm.SHA1, file.hashes.sha1)
                else -> throw MrpackFormatException("Pack file ${file.path} has no usable hash")
            }
            requests += DownloadRequest(
                id = "mrpack:${file.path}",
                url = url,
                destination = destination,
                sizeBytes = file.fileSize,
                hash = hash,
            )
        }
        val overrides = collectOverrideEntries(zipFile, gameDir)
        return MrpackPlan(
            metadata = PackMetadata(
                name = manifest.name,
                minecraftVersion = manifest.dependencies.minecraft!!,
                loader = manifest.dependencies.loader,
                loaderVersion = manifest.dependencies.loaderVersion,
                summary = manifest.summary,
            ),
            downloads = requests.distinctBy { it.destination.absoluteFile },
            overrideEntries = overrides,
            skippedClientUnsupported = skipped,
        )
    }

    suspend fun install(zipFile: File, gameDir: File, downloads: DownloadManager): Outcome<MrpackInstallSummary> {
        return try {
            val manifest = parse(zipFile)
            val plan = plan(manifest, zipFile, gameDir)
            if (plan.downloads.isNotEmpty()) {
                val result = downloads.run(plan.downloads)
                if (result.cancelled) return Outcome.error(FlintError.Cancelled)
                if (result.failed.isNotEmpty()) {
                    return Outcome.error(
                        FlintError.DownloadFailed(
                            "${result.failed.size} modpack file(s) failed to download",
                            result.failed.map { it.request.destination.name },
                        ),
                    )
                }
            }
            val extracted = extractOverrides(zipFile, plan.overrideEntries)
            Outcome.success(
                MrpackInstallSummary(plan.metadata, plan.downloads.size, extracted),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: MrpackFormatException) {
            Outcome.error(FlintError.ConfigError(e.message ?: "Invalid mrpack"))
        } catch (e: Exception) {
            Outcome.error(FlintError.Unknown(e.message ?: "mrpack install failed", e.javaClass.simpleName))
        }
    }

    /** Zip entry name (inside overrides/) → absolute destination path, validated zip-slip safe. */
    private fun collectOverrideEntries(zipFile: File, gameDir: File): List<Pair<String, String>> {
        val entries = mutableListOf<Pair<String, String>>()
        ZipFile(zipFile).use { zip ->
            for (entry in zip.entries().asSequence()) {
                if (entry.isDirectory) continue
                val prefix = OVERRIDE_DIRS.firstOrNull { entry.name.startsWith(it) } ?: continue
                val relative = entry.name.removePrefix(prefix).trimStart('/')
                if (relative.isEmpty()) continue
                val destination = safeResolve(gameDir, relative)
                entries += entry.name to destination.absolutePath
            }
        }
        return entries
    }

    private fun extractOverrides(zipFile: File, overrideEntries: List<Pair<String, String>>): Int {
        var extracted = 0
        ZipFile(zipFile).use { zip ->
            for ((entryName, destPath) in overrideEntries) {
                val entry = zip.getEntry(entryName) ?: continue
                val destination = File(destPath)
                destination.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input -> destination.outputStream().use { input.copyTo(it) } }
                extracted++
            }
        }
        return extracted
    }

    companion object {
        const val INDEX_JSON = "modrinth.index.json"
        val OVERRIDE_DIRS = listOf("overrides/", "client-overrides/")

        /** Zip-slip safe resolve: [relative] must stay inside [base]. Throws MrpackFormatException otherwise. */
        fun safeResolve(base: File, relative: String): File {
            if (relative.contains("..")) throw MrpackFormatException("Pack path escapes target: $relative")
            val baseCanonical = base.canonicalFile
            val target = File(baseCanonical, relative).canonicalFile
            if (target != baseCanonical && !target.path.startsWith(baseCanonical.path + File.separator)) {
                throw MrpackFormatException("Pack path escapes target: $relative")
            }
            return target
        }
    }
}
