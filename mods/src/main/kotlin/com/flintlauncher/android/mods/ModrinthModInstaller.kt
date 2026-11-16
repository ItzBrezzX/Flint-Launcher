package com.flintlauncher.android.mods

import com.flintlauncher.android.download.DownloadManager
import com.flintlauncher.android.download.DownloadProgress
import com.flintlauncher.android.download.DownloadRequest
import kotlinx.coroutines.flow.StateFlow
import com.flintlauncher.android.download.ExpectedHash
import com.flintlauncher.android.download.HashAlgorithm
import com.flintlauncher.android.profiles.Profile
import com.flintlauncher.android.profiles.ProfileDirs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant

/**
 * Installs verified JARs from Modrinth only. It never resolves a file through third-party
 * mirrors; the URL must point directly at cdn.modrinth.com and the Modrinth checksum is
 * required before DownloadManager promotes the partial file into the profile's mods folder.
 */
class ModrinthModInstaller(
    private val downloads: DownloadManager,
    private val launcherRoot: File,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    val downloadProgress: StateFlow<DownloadProgress> get() = downloads.progress

    suspend fun install(
        profile: Profile,
        project: ModrinthProject,
        version: ModrinthVersion,
    ): InstalledMod {
        require(project.projectType == "mod") { "Only Modrinth mod projects can be installed" }
        require(project.projectId == version.projectId) { "The version does not belong to this Modrinth project" }
        require(profile.id.isNotBlank()) { "Select or create a profile before installing a mod" }
        require(profile.loader != "vanilla") { "Mods require Fabric, Quilt, Forge, or NeoForge" }
        require(profile.minecraftVersion in version.gameVersions) {
            "This mod version does not support Minecraft ${profile.minecraftVersion}"
        }
        require(profile.loader in version.loaders) {
            "This mod version does not support ${profile.loader}"
        }

        val file = version.files.firstOrNull { it.primary && it.filename.endsWith(".jar", ignoreCase = true) }
            ?: version.files.firstOrNull { it.filename.endsWith(".jar", ignoreCase = true) }
            ?: throw IOException("This Modrinth version does not contain a JAR file")
        val url = file.url.toHttpUrlOrNull()
            ?: throw IOException("Modrinth supplied an invalid download URL")
        if (url.scheme != "https" || url.host != MODRINTH_CDN_HOST ||
            url.username.isNotEmpty() || url.password.isNotEmpty()
        ) {
            throw IOException("Blocked non-Modrinth download URL")
        }
        if (file.size <= 0L) throw IOException("Modrinth supplied an invalid file size")

        val hash = when {
            file.hashes["sha512"]?.matches(SHA512) == true ->
                ExpectedHash(HashAlgorithm.SHA512, file.hashes.getValue("sha512"))
            file.hashes["sha1"]?.matches(SHA1) == true ->
                ExpectedHash(HashAlgorithm.SHA1, file.hashes.getValue("sha1"))
            else -> throw IOException("Modrinth did not provide a usable SHA-512 or SHA-1 checksum")
        }

        val rawName = file.filename.substringAfterLast('/').substringAfterLast('\\')
        if (rawName.isBlank() || rawName == "." || rawName == "..") {
            throw IOException("Modrinth supplied an unsafe filename")
        }
        val safeName = rawName.replace(UNSAFE_FILENAME, "_").take(120)
        val destinationName = "${project.projectId}-${version.id}-$safeName"
        val dirs = ProfileDirs(launcherRoot, profile.id)
        dirs.createAll()

        val request = DownloadRequest(
            id = "modrinth:${project.projectId}:${version.id}",
            url = url.toString(),
            destination = File(dirs.mods, destinationName),
            sizeBytes = file.size,
            hash = hash,
        )
        val result = downloads.run(listOf(request))
        if (result.cancelled) throw CancellationException("Mod installation was cancelled")
        if (result.failed.isNotEmpty()) {
            throw IOException("Modrinth download failed: ${result.failed.first().reason}")
        }

        val installed = InstalledMod(
            projectId = project.projectId,
            projectTitle = project.title,
            versionId = version.id,
            versionNumber = version.versionNumber,
            fileName = destinationName,
            installedAt = Instant.now().toString(),
        )
        withContext(Dispatchers.IO) { saveInstalled(dirs, installed) }
        return installed
    }

    suspend fun installed(profile: Profile): List<InstalledMod> = withContext(Dispatchers.IO) {
        require(profile.id.isNotBlank()) { "Select a profile first" }
        val dirs = ProfileDirs(launcherRoot, profile.id)
        loadInstalled(dirs).filter { safeModFile(dirs, it.fileName)?.isFile == true }
    }

    suspend fun uninstall(profile: Profile, projectId: String): Boolean = withContext(Dispatchers.IO) {
        require(profile.id.isNotBlank()) { "Select a profile first" }
        val dirs = ProfileDirs(launcherRoot, profile.id)
        val records = loadInstalled(dirs)
        val target = records.firstOrNull { it.projectId == projectId } ?: return@withContext false
        val file = safeModFile(dirs, target.fileName) ?: return@withContext false
        val deleted = !file.exists() || file.delete()
        if (deleted) saveInstalled(dirs, null, removeProjectId = projectId)
        deleted
    }

    private fun safeModFile(dirs: ProfileDirs, filename: String): File? {
        if (filename.isBlank() || filename != File(filename).name) return null
        val root = dirs.mods.canonicalFile
        val file = File(root, filename).canonicalFile
        return file.takeIf { it.parentFile == root }
    }

    private fun loadInstalled(dirs: ProfileDirs): List<InstalledMod> {
        val file = manifestFile(dirs)
        if (!file.isFile) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(InstalledMod.serializer()), file.readText())
        }.getOrDefault(emptyList())
    }

    private fun saveInstalled(
        dirs: ProfileDirs,
        item: InstalledMod?,
        removeProjectId: String? = null,
    ) {
        val current = loadInstalled(dirs).filterNot {
            it.projectId == item?.projectId || (removeProjectId != null && it.projectId == removeProjectId)
        }
        val updated = if (item == null) current else current + item
        val destination = manifestFile(dirs)
        destination.parentFile?.mkdirs()
        val temporary = File(destination.path + ".tmp")
        temporary.writeText(json.encodeToString(ListSerializer(InstalledMod.serializer()), updated))
        try {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun manifestFile(dirs: ProfileDirs) = File(File(dirs.gameDir, ".flint"), "modrinth-mods.json")

    companion object {
        private const val MODRINTH_CDN_HOST = "cdn.modrinth.com"
        private val SHA512 = Regex("^[0-9a-fA-F]{128}$")
        private val SHA1 = Regex("^[0-9a-fA-F]{40}$")
        private val UNSAFE_FILENAME = Regex("[^A-Za-z0-9._-]")
    }
}
