package com.flintlauncher.android.installer

import com.flintlauncher.android.download.DownloadRequest
import com.flintlauncher.android.download.ExpectedHash
import com.flintlauncher.android.download.HashAlgorithm
import com.flintlauncher.android.minecraft.version.AssetIndex
import com.flintlauncher.android.minecraft.version.Environment
import com.flintlauncher.android.minecraft.version.LibraryResolver
import com.flintlauncher.android.minecraft.version.VersionJson
import java.io.File

/** Log4j configuration file plus the JVM argument template that points the game at it. */
data class LogConfig(val file: File, val argument: String) {
    /** Ready-to-use JVM argument, e.g. `-Dlog4j.configurationFile=/path/client-1.12.xml`. */
    val jvmArgument: String get() = argument.replace("\${path}", file.absolutePath)
}

data class InstallPlan(
    val requests: List<DownloadRequest>,
    /** Libraries that apply here but have no download information. Launch would fail without them. */
    val unresolvedLibraries: List<String>,
    /** Library jars in classpath order, then the client jar. */
    val classpath: List<File>,
    val nativeJars: List<File>,
    val logConfig: LogConfig? = null,
)

object InstallPlanner {
    const val DEFAULT_ASSET_BASE_URL = "https://resources.download.minecraft.net/"
    private val sha1Hex = Regex("^[0-9a-f]{40}$")

    /**
     * Client jar, libraries and the asset index file, for a version JSON that has already been
     * merged with its vanilla parent. [vanillaId] names the vanilla version whose jar is used.
     */
    fun planCore(version: VersionJson, vanillaId: String, layout: GameLayout, env: Environment): InstallPlan {
        val client = version.downloads?.client
            ?: throw InstallPlanException("Version ${version.id} has no client download")
        val assetIndex = version.assetIndex
            ?: throw InstallPlanException("Version ${version.id} has no asset index")

        val clientJar = layout.versionJar(vanillaId)
        val requests = mutableListOf<DownloadRequest>()
        requests += DownloadRequest("client-$vanillaId", client.url, clientJar, client.size, sha1(client.sha1))

        val resolution = LibraryResolver.resolve(version.libraries, env)
        val classpath = mutableListOf<File>()
        val natives = mutableListOf<File>()
        for (library in resolution.files) {
            val file = layout.library(library.path)
            requests += DownloadRequest(library.path, library.url, file, library.size, sha1(library.sha1))
            if (library.isNative) natives += file else classpath += file
        }
        classpath += clientJar

        requests += DownloadRequest(
            "assetindex-${assetIndex.id}", assetIndex.url, layout.assetIndex(assetIndex.id), assetIndex.size, sha1(assetIndex.sha1),
        )

        var logConfig: LogConfig? = null
        val logging = version.logging?.client
        if (logging != null) {
            val logFile = layout.logConfig(logging.file.id)
            requests += DownloadRequest("logconfig-${logging.file.id}", logging.file.url, logFile, logging.file.size, sha1(logging.file.sha1))
            logConfig = LogConfig(logFile, logging.argument)
        }

        return InstallPlan(
            requests = requests.distinctBy { it.destination },
            unresolvedLibraries = resolution.unresolved,
            classpath = classpath,
            nativeJars = natives,
            logConfig = logConfig,
        )
    }

    /** One request per distinct asset hash (many asset names share a file). */
    fun planAssets(index: AssetIndex, layout: GameLayout, baseUrl: String = DEFAULT_ASSET_BASE_URL): List<DownloadRequest> {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return index.objects.values.distinctBy { it.hash }.map { asset ->
            if (!sha1Hex.matches(asset.hash)) throw InstallPlanException("Invalid asset hash in index: ${asset.hash}")
            DownloadRequest(
                id = asset.hash,
                url = "$base${asset.hash.take(2)}/${asset.hash}",
                destination = layout.assetObject(asset.hash),
                sizeBytes = asset.size,
                hash = ExpectedHash(HashAlgorithm.SHA1, asset.hash),
            )
        }
    }

    private fun sha1(value: String?): ExpectedHash? =
        value?.takeIf { it.isNotBlank() }?.let { ExpectedHash(HashAlgorithm.SHA1, it) }
}
