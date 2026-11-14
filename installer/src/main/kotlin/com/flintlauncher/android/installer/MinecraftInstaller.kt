package com.flintlauncher.android.installer

import com.flintlauncher.android.download.BatchResult
import com.flintlauncher.android.download.DownloadManager
import com.flintlauncher.android.download.DownloadProgress
import com.flintlauncher.android.download.DownloadRequest
import com.flintlauncher.android.download.FailedDownload
import com.flintlauncher.android.minecraft.manifest.VersionEntry
import com.flintlauncher.android.minecraft.manifest.VersionManifest
import com.flintlauncher.android.minecraft.manifest.VersionManifestParser
import com.flintlauncher.android.minecraft.version.Environment
import com.flintlauncher.android.minecraft.version.VersionJson
import com.flintlauncher.android.minecraft.version.VersionJsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

data class InstalledVersionSummary(
    val versionId: String,
    val requiredJavaMajor: Int,
)

data class InstalledVersion(
    val versionId: String,
    val version: VersionJson,
    val clientJar: File,
    val classpath: List<File>,
    val nativeJars: List<File>,
    val assetsRoot: File,
    val assetIndexId: String,
    /** `-Dlog4j.configurationFile=...`, or null for versions without a log config. Must be passed to the game. */
    val loggingJvmArgument: String?,
    val requiredJavaMajor: Int,
    /** Extracted Android/Linux .so files for java.library.path. */
    val nativesDirectory: File? = null,
)

sealed interface InstallResult {
    data class Success(val installed: InstalledVersion) : InstallResult
    data class Failed(val message: String, val failures: List<FailedDownload> = emptyList()) : InstallResult
    data object Cancelled : InstallResult
}

private class MetadataHttpException(val code: Int) : IOException("HTTP $code")

/**
 * Installs one Minecraft version the way the official and third-party launchers do:
 * manifest, version JSON (SHA-1 checked), asset index, then client jar, libraries, log config
 * and every asset object through the verified, resumable [DownloadManager]. It is idempotent:
 * running it again downloads only what is missing or corrupt, which is also how an interrupted
 * install resumes. Expected problems come back as [InstallResult.Failed] with a readable message.
 */
class MinecraftInstaller(
    private val http: OkHttpClient,
    private val layout: GameLayout,
    private val downloads: DownloadManager,
    private val environment: Environment = Environment(),
    private val manifestUrl: String = DEFAULT_MANIFEST_URL,
    private val assetBaseUrl: String = InstallPlanner.DEFAULT_ASSET_BASE_URL,
    private val metadataRetryDelayMillis: Long = 500,
) {
    /** True only if a previous install of [versionId] ran to completion. */
    fun isInstalled(versionId: String): Boolean =
        try {
            layout.installMarker(versionId).isFile &&
                layout.versionJar(versionId).isFile &&
                layout.versionJson(versionId).isFile
        } catch (e: InstallPlanException) { false }

    /** Returns fully installed versions only; partial or failed installs are excluded. */
    fun installedVersions(): List<InstalledVersionSummary> =
        layout.versionsDir.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isDirectory }
            .mapNotNull { directory ->
                val id = directory.name
                if (!isInstalled(id)) return@mapNotNull null
                runCatching {
                    val metadata = VersionJsonParser.parse(layout.versionJson(id).readText())
                    InstalledVersionSummary(id, metadata.javaVersion?.majorVersion ?: 8)
                }.getOrNull()
            }
            .sortedByDescending { it.versionId }
            .toList()

    suspend fun fetchManifest(): VersionManifest = VersionManifestParser.parse(fetchText(manifestUrl))

    suspend fun install(
        versionId: String,
        onStage: (InstallStage) -> Unit = {},
        onProgress: (DownloadProgress) -> Unit = {},
    ): InstallResult {
        try {
            onStage(InstallStage.RESOLVING_METADATA)
            val (version, vanillaId) = resolveVersion(versionId)
            val plan = InstallPlanner.planCore(version, vanillaId, layout, environment)
            if (plan.unresolvedLibraries.isNotEmpty()) {
                return InstallResult.Failed("No download information for: ${plan.unresolvedLibraries.joinToString()}")
            }

            val indexRequest = plan.requests.first { it.id.startsWith("assetindex-") }
            onStage(InstallStage.PREPARING_QUEUE)
            val indexRun = runWithProgress(listOf(indexRequest), onProgress)
            if (indexRun.cancelled) return InstallResult.Cancelled
            if (indexRun.failed.isNotEmpty()) return InstallResult.Failed("Could not download the asset index", indexRun.failed)

            val index = VersionJsonParser.parseAssetIndex(indexRequest.destination.readText())
            val everything = plan.requests.filter { it !== indexRequest } + InstallPlanner.planAssets(index, layout, assetBaseUrl)

            onStage(InstallStage.DOWNLOADING_ASSETS)
            val run = runWithProgress(everything, onProgress)
            if (run.cancelled) return InstallResult.Cancelled
            if (run.failed.isNotEmpty()) {
                return InstallResult.Failed("${run.failed.size} file(s) failed to download", run.failed)
            }

            onStage(InstallStage.VALIDATING_INTEGRITY)
            val toValidate = everything + indexRequest
            val missing = toValidate.filter { !it.destination.isFile }
            if (missing.isNotEmpty()) {
                return InstallResult.Failed("Files missing after download: ${missing.take(3).joinToString { it.destination.name }}")
            }
            // Size check when expected size is known
            val sizeMismatches = toValidate.filter { req ->
                val expected = req.sizeBytes
                expected != null && expected > 0 && req.destination.length() != expected
            }
            if (sizeMismatches.isNotEmpty()) {
                // Quarantine mismatched files so a later repair re-downloads them
                sizeMismatches.forEach { it.destination.delete() }
                return InstallResult.Failed(
                    "Size mismatch on ${sizeMismatches.size} file(s); removed invalid copies for repair",
                    sizeMismatches.map { FailedDownload(it, "size mismatch") },
                )
            }
            // Hash check when hash is known (DownloadManager already verified on move, re-check critical client jar)
            val clientReq = toValidate.find { it.id.startsWith("client-") }
            val clientHash = clientReq?.hash
            if (clientReq != null && clientHash != null && clientReq.destination.isFile) {
                if (!com.flintlauncher.android.download.FileHasher.matches(clientReq.destination, clientHash)) {
                    clientReq.destination.delete()
                    return InstallResult.Failed("Client JAR failed integrity check", listOf(FailedDownload(clientReq, "hash mismatch")))
                }
            }

            onStage(InstallStage.GENERATING_METADATA)
            // Legacy versions do not read the content-addressed object store directly.
            // Materialize their old path-based assets before marking the version installed.
            LegacyAssetMaterializer.materialize(index, layout)
            // Native classifier JARs are separate from the Java classpath. Extract
            // them before writing the install marker so an installed version always
            // has a complete java.library.path directory.
            val nativesDirectory = layout.nativesDirectory(versionId)
            layout.installMarker(versionId).delete()
            if (nativesDirectory.exists() && !nativesDirectory.deleteRecursively()) {
                return InstallResult.Failed("Could not clear the previous native libraries for $versionId")
            }
            try {
                NativesExtractor.extract(plan.nativeJars, nativesDirectory)
            } catch (nativeError: Exception) {
                nativesDirectory.deleteRecursively()
                return InstallResult.Failed("Could not extract native libraries for $versionId: ${nativeError.message ?: nativeError.javaClass.simpleName}")
            }
            layout.installMarker(versionId).apply { parentFile?.mkdirs() }.writeText(vanillaId)
            onStage(InstallStage.FINAL_VERIFICATION)
            // Marker is written only after existence + size (+ client hash) checks pass
            onStage(InstallStage.READY)
            return InstallResult.Success(
                InstalledVersion(
                    versionId = version.id,
                    version = version,
                    clientJar = layout.versionJar(vanillaId),
                    classpath = plan.classpath,
                    nativeJars = plan.nativeJars,
                    assetsRoot = layout.assetsDir,
                    assetIndexId = version.assetIndex?.id ?: "",
                    loggingJvmArgument = plan.logConfig?.jvmArgument,
                    requiredJavaMajor = version.javaVersion?.majorVersion ?: 8,
                    nativesDirectory = nativesDirectory,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: InstallPlanException) {
            return InstallResult.Failed(e.message ?: "Invalid version data")
        } catch (e: MetadataHttpException) {
            return InstallResult.Failed("Minecraft servers answered with HTTP ${e.code}")
        } catch (e: IOException) {
            return InstallResult.Failed("Network problem: ${e.message ?: "connection failed"}")
        } catch (e: SerializationException) {
            return InstallResult.Failed("Received unreadable version data")
        } catch (e: IllegalArgumentException) {
            return InstallResult.Failed("Received unreadable version data")
        }
    }

    private suspend fun runWithProgress(
        requests: List<DownloadRequest>,
        onProgress: (DownloadProgress) -> Unit,
    ): BatchResult = coroutineScope {
        val monitor = launch {
            downloads.progress.collect(onProgress)
        }
        try {
            downloads.run(requests)
        } finally {
            monitor.cancel()
        }
    }

    // ------------------------------------------------------------ metadata

    /**
     * Resolves the complete inheritance chain, from the root vanilla version to
     * the selected loader/profile. Some loader metadata can inherit from another
     * profile, so resolving only one parent silently drops required metadata.
     */
    private suspend fun resolveVersion(id: String): Pair<VersionJson, String> {
        val manifest = fetchManifest()
        val visiting = linkedSetOf<String>()

        suspend fun resolve(currentId: String, depth: Int): Pair<VersionJson, String> {
            if (depth > MAX_VERSION_INHERITANCE_DEPTH) {
                throw InstallPlanException(
                    "Version metadata inheritance is deeper than $MAX_VERSION_INHERITANCE_DEPTH levels",
                )
            }
            if (!visiting.add(currentId)) {
                throw InstallPlanException(
                    "Version metadata inheritance cycle: " + (visiting + currentId).joinToString(" -> "),
                )
            }

            try {
                val entry = manifest.find(currentId)
                val local = layout.versionJson(currentId)
                val child = when {
                    entry != null -> fetchAndStore(entry)
                    local.isFile -> VersionJsonParser.parse(local.readText())
                    else -> throw InstallPlanException("Unknown Minecraft version: $currentId")
                }

                val parentId = child.inheritsFrom ?: return child to child.id
                val (parent, vanillaId) = resolve(parentId, depth + 1)
                return child.mergedOnto(parent) to vanillaId
            } finally {
                visiting.remove(currentId)
            }
        }

        return resolve(id, 0)
    }

    private suspend fun fetchAndStore(entry: VersionEntry): VersionJson {
        val text = fetchText(entry.url)
        val expected = entry.sha1
        if (expected != null && !sha1(text.toByteArray()).equals(expected, ignoreCase = true)) {
            throw InstallPlanException("Checksum mismatch for the version data of ${entry.id}")
        }
        val parsed = VersionJsonParser.parse(text)
        val file = layout.versionJson(entry.id)
        file.parentFile?.mkdirs()
        val temp = File(file.path + ".tmp")
        temp.writeText(text)
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        return parsed
    }

    private suspend fun fetchText(url: String): String {
        var attempt = 0
        while (true) {
            try {
                return withContext(Dispatchers.IO) {
                    http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                        if (!response.isSuccessful) throw MetadataHttpException(response.code)
                        response.body?.string() ?: throw IOException("Empty response")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                attempt++
                val retryable = e !is MetadataHttpException || e.code == 429 || e.code >= 500
                if (!retryable || attempt >= 3) throw e
                delay(metadataRetryDelayMillis * (1L shl (attempt - 1)))
            }
        }
    }

    private fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        const val MAX_VERSION_INHERITANCE_DEPTH = 16
        const val DEFAULT_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    }
}
