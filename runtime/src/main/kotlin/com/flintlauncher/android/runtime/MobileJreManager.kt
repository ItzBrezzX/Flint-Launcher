package com.flintlauncher.android.runtime

import com.flintlauncher.android.download.DownloadManager
import com.flintlauncher.android.download.DownloadRequest
import com.flintlauncher.android.download.ExpectedHash
import com.flintlauncher.android.download.HashAlgorithm
import com.flintlauncher.android.nativebridge.NativeBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Mobile JRE catalog based on published architecture-specific AngelAuraMC
 * OpenJDK release assets. URLs, byte lengths and SHA-256 digests are pinned
 * to release metadata. Runtime packages are downloaded, not bundled in the APK.
 *
 * DownloadManager provides resumable .part downloads and validates size/hash
 * before a downloaded archive is promoted into the cache.
 */
data class JreArtifact(
    val id: String,
    val major: Int,
    val arch: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
)

object KnownJreArtifacts {
    private const val BASE = "https://github.com/AngelAuraMC/angelauramc-openjdk-build/releases/download"

    val all: List<JreArtifact> = listOf(
        JreArtifact("jre8-arm", 8, "arm", "$BASE/download_jre8/jre8-android-arm.tar.xz", "9dbee3b09af5f170e2ed9dd596bc81d0e573c88f8bf760c59c8fadbb9073d1e7", 27035664L),
        JreArtifact("jre8-arm64", 8, "arm64", "$BASE/download_jre8/jre8-android-arm64.tar.xz", "9a59124d9791957d55c68be664ab76831f336cf2e1e1cd4414220c6fdbf0e06d", 28026736L),
        JreArtifact("jre8-x86", 8, "x86", "$BASE/download_jre8/jre8-android-x86.tar.xz", "b96ce49fab52b28688dccc1a7d85dfc6d0f4048636aac826e1967524b28f75af", 27473340L),
        JreArtifact("jre8-x86_64", 8, "x86_64", "$BASE/download_jre8/jre8-android-x86_64.tar.xz", "b1fbcef4965c17925894febe8216d089c6dd47b37950b5f945a89616443c1d0e", 29132884L),

        JreArtifact("jre17-arm", 17, "arm", "$BASE/download_jre17/jre17-android-arm.tar.xz", "4a9134f1ebf6340dd855805d351712462cddab6f3c2684da7e7da10ccf06648d", 25001168L),
        JreArtifact("jre17-arm64", 17, "arm64", "$BASE/download_jre17/jre17-android-arm64.tar.xz", "e162c860fe05ee4a4e4af7606437419879f6c748386a7b09fa77d10db6a64091", 26900804L),
        JreArtifact("jre17-x86", 17, "x86", "$BASE/download_jre17/jre17-android-x86.tar.xz", "223a2d54606a9eb853c8451cf1d6bda1a8f09cb357f40b790140e57d45731ed7", 26951040L),
        JreArtifact("jre17-x86_64", 17, "x86_64", "$BASE/download_jre17/jre17-android-x86_64.tar.xz", "893e27d2aed8b40407f29fe939e2a0f193e5d55f72892a303db634b0808a2b61", 27780012L),

        JreArtifact("jre21-arm", 21, "arm", "$BASE/download_jre21/jre21-android-arm.tar.xz", "96c297487def64666e379a9a363d9955c05b1a0b091b0cf24af88359a66f394a", 26586292L),
        JreArtifact("jre21-arm64", 21, "arm64", "$BASE/download_jre21/jre21-android-arm64.tar.xz", "8d41ec401ee59f7722df60ed991f81ad146e130452804bfdd8a05d3436f7bbfe", 28675516L),
        JreArtifact("jre21-x86", 21, "x86", "$BASE/download_jre21/jre21-android-x86.tar.xz", "9b8c7d10c5f751acb3b33506593da44ece52a0fd03e0b3c283ba08a7f285a40f", 28689112L),
        JreArtifact("jre21-x86_64", 21, "x86_64", "$BASE/download_jre21/jre21-android-x86_64.tar.xz", "cb88723961f5f9ad63afa1f212eb199816c27cabfd7dc66567bde1d8fb69713b", 29662988L),

        JreArtifact("jre25-arm64", 25, "arm64", "$BASE/download_jre25/jre25-android-arm64.tar.xz", "d3eb7afe2240c26728a1bb440502c5f18ac3883e932d202dd7f0c9bcbbce4c37", 38031580L),
        JreArtifact("jre25-x86_64", 25, "x86_64", "$BASE/download_jre25/jre25-android-x86_64.tar.xz", "7fca862ee1b2d5fe23cd9c9c3d9b7ad3c241947ad1a6cc9464ef2e674867105d", 39061384L),
    )

    fun find(major: Int, arch: String): JreArtifact? {
        val normalized = RuntimeArchitecture.normalize(arch) ?: return null
        return all.firstOrNull { it.major == major && it.arch == normalized }
    }

    fun majorsFor(arch: String): List<Int> {
        val normalized = RuntimeArchitecture.normalize(arch) ?: return emptyList()
        return all.asSequence().filter { it.arch == normalized }.map { it.major }.distinct().sorted().toList()
    }

    /** Lowest published JRE that can satisfy the Java bytecode requirement. */
    fun compatibleMajor(requiredMajor: Int, arch: String): Int? =
        majorsFor(arch).firstOrNull { it >= requiredMajor }
}

class MobileJreManager(
    private val runtimesDir: File,
    private val downloadDir: File,
    val deviceArch: String,
    private val downloads: DownloadManager,
) : JavaRuntimeManager {

    init {
        require(RuntimeArchitecture.normalize(deviceArch) != null) {
            "Unsupported Android process architecture: " + deviceArch
        }
    }

    override suspend fun listInstalled(): List<JavaRuntimeInfo> = withContext(Dispatchers.IO) {
        if (!runtimesDir.isDirectory) return@withContext emptyList()
        val candidates = runtimesDir.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .mapNotNull { directory -> runCatching { probe(directory) }.getOrNull() }
            .filter { it.verified }
            .sortedWith(compareBy<JavaRuntimeInfo> { it.majorVersion }.thenBy { it.id })
            .toList()

        // Do not expose a runtime as verified until the device's native bridge
        // can load its actual libjli and resolve JLI_Launch.
        val verified = mutableListOf<JavaRuntimeInfo>()
        for (candidate in candidates) {
            if (verify(candidate)) verified += candidate
        }
        verified.toList()
    }

    /** Selects the lowest installed Java major that satisfies the game requirement. */
    override suspend fun findCompatible(requiredMajor: Int, preferArch: String): JavaRuntimeInfo? {
        val preferred = RuntimeArchitecture.normalize(preferArch) ?: deviceArch
        return listInstalled()
            .filter { it.arch == preferred && it.majorVersion >= requiredMajor && it.verified }
            .minByOrNull { it.majorVersion }
    }

    /** Downloads and installs the exact JRE major for this Android app process architecture. */
    override suspend fun install(major: Int): Result<JavaRuntimeInfo> {
        return try {
            withContext(Dispatchers.IO) {
                val artifact = KnownJreArtifacts.find(major, deviceArch)
                    ?: throw IOException(
                        "Java " + major + " is not published for Android " + deviceArch +
                            ". Available majors: " + KnownJreArtifacts.majorsFor(deviceArch).joinToString(),
                    )
                val existing = listInstalled().firstOrNull { it.majorVersion == major && it.arch == deviceArch }
                if (existing != null) return@withContext Result.success(existing)

                runtimesDir.mkdirs()
                downloadDir.mkdirs()
                val archive = File(downloadDir, artifact.id + ".tar.xz")
                val request = DownloadRequest(
                    id = artifact.id,
                    url = artifact.url,
                    destination = archive,
                    sizeBytes = artifact.sizeBytes,
                    hash = ExpectedHash(HashAlgorithm.SHA256, artifact.sha256),
                )
                val batch = downloads.run(listOf(request))
                if (batch.cancelled) throw CancellationException("Java runtime download was cancelled")
                batch.failed.firstOrNull()?.let { throw IOException("Java " + major + " download failed: " + it.reason) }
                if (!archive.isFile || archive.length() != artifact.sizeBytes) {
                    throw IOException("Java runtime archive failed its size check")
                }

                val staging = File(runtimesDir, "." + artifact.id + ".installing")
                val target = File(runtimesDir, artifact.id)
                val backup = File(runtimesDir, "." + artifact.id + ".backup")
                staging.deleteRecursively()
                backup.deleteRecursively()
                staging.mkdirs()

                try {
                    SafeTarXzExtractor.extract(archive, staging)
                    flattenSingleRoot(staging)
                    val stagedInfo = probe(staging)
                    if (!stagedInfo.verified) throw IOException("Extracted runtime is missing libjli.so or libjvm.so")
                    if (stagedInfo.majorVersion != major) {
                        throw IOException("The archive contains Java " + stagedInfo.majorVersion + ", not Java " + major)
                    }
                    if (stagedInfo.arch != deviceArch) {
                        throw IOException("The archive is " + stagedInfo.arch + ", but this app process needs " + deviceArch)
                    }

                    if (target.exists() && !target.renameTo(backup)) {
                        throw IOException("Could not move the previous Java runtime out of the way")
                    }
                    try {
                        Files.move(staging.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    } catch (_: Exception) {
                        if (!staging.renameTo(target)) throw IOException("Could not activate the downloaded Java runtime")
                    }
                    backup.deleteRecursively()
                    Result.success(probe(target))
                } catch (error: Exception) {
                    staging.deleteRecursively()
                    if (!target.exists() && backup.exists()) backup.renameTo(target)
                    throw error
                } finally {
                    archive.delete()
                    File(archive.path + ".part").delete()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    /** Imports a local .tar.xz or .tar.gz archive when the user already has a trusted JRE. */
    suspend fun importLocalArchive(
        archive: File,
        id: String,
        major: Int,
        expectedSha256: String? = null,
    ): Result<JavaRuntimeInfo> = withContext(Dispatchers.IO) {
        try {
            if (!archive.isFile) throw IOException("Archive not found: " + archive)
            if (expectedSha256 != null) {
                val actual = sha256Hex(archive)
                if (!actual.equals(expectedSha256, ignoreCase = true)) {
                    throw IOException("SHA-256 mismatch: expected " + expectedSha256 + ", got " + actual)
                }
            }
            runtimesDir.mkdirs()
            val staging = File(runtimesDir, "." + id + ".importing")
            val target = File(runtimesDir, id)
            staging.deleteRecursively()
            staging.mkdirs()
            when {
                archive.name.endsWith(".tar.xz", ignoreCase = true) -> SafeTarXzExtractor.extract(archive, staging)
                archive.name.endsWith(".tar.gz", ignoreCase = true) || archive.name.endsWith(".tgz", ignoreCase = true) ->
                    SafeTarGzExtractor.extract(archive, staging)
                else -> throw IOException("Only .tar.xz and .tar.gz JRE archives are supported")
            }
            flattenSingleRoot(staging)
            val staged = probe(staging)
            if (!staged.verified) throw IOException("The archive is not a usable mobile JRE")
            if (staged.majorVersion != major) throw IOException("The archive contains Java " + staged.majorVersion + ", not Java " + major)
            if (staged.arch != deviceArch) throw IOException("The archive is " + staged.arch + ", but this device needs " + deviceArch)
            target.deleteRecursively()
            Files.move(staging.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            Result.success(probe(target))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    /**
     * Verify archive metadata and native JLI symbol resolution on this Android process.
     * This confirms that the mobile JVM entry point is loadable; it does NOT claim that
     * Minecraft graphics or the game window are ready.
     */
    override suspend fun verify(runtime: JavaRuntimeInfo): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val detected = probe(runtime.homeDir)
            val filesMatch = detected.verified &&
                detected.majorVersion == runtime.majorVersion &&
                detected.arch == deviceArch &&
                detected.homeDir.canonicalFile == runtime.homeDir.canonicalFile
            if (!filesMatch || !NativeBridge.isNativeLoaded) return@runCatching false

            val jliReady = NativeBridge.nativeLoadJre(runtime.homeDir.absolutePath)
            NativeBridge.nativeShutdown()
            jliReady
        }.getOrDefault(false)
    }

    private fun probe(home: File): JavaRuntimeInfo {
        if (!home.isDirectory) throw IOException("Runtime directory is missing")
        val release = File(home, "release")
        if (!release.isFile) throw IOException("Runtime release metadata is missing")
        val lines = release.readLines()
        val version = lines.firstOrNull { it.startsWith("JAVA_VERSION=") }
            ?.substringAfter('=')
            ?.trim()
            ?.trim('"')
            ?: throw IOException("JAVA_VERSION is missing from runtime metadata")
        val major = parseMobileJavaMajor(version) ?: throw IOException("Cannot parse Java version '" + version + "'")
        val osArch = lines.firstOrNull { it.startsWith("OS_ARCH=") }
            ?.substringAfter('=')
            ?.trim()
            ?.trim('"')
        val arch = RuntimeArchitecture.normalize(osArch ?: home.name.substringAfterLast('-'))
            ?: throw IOException("Cannot identify the runtime architecture")

        val jli = findLib(home, "libjli.so")
        val jvm = findLib(home, "libjvm.so")
        return JavaRuntimeInfo(
            id = home.name,
            majorVersion = major,
            vendor = "OpenJDK-mobile",
            homeDir = home,
            javaExecutable = File(home, "bin/java").takeIf { it.isFile },
            arch = arch,
            verified = jli != null && jvm != null,
        )
    }

    private fun findLib(home: File, name: String): File? {
        val paths = listOf(
            File(home, "lib/" + name),
            File(home, "lib/aarch64/" + name),
            File(home, "lib/arm64/" + name),
            File(home, "lib/arm/" + name),
            File(home, "lib/i386/" + name),
            File(home, "lib/amd64/" + name),
            File(home, "lib/x86_64/" + name),
            File(home, "lib/server/" + name),
            File(home, "lib/aarch64/server/" + name),
            File(home, "lib/arm64/server/" + name),
            File(home, "lib/jli/" + name),
        )
        paths.firstOrNull { it.isFile }?.let { return it }
        return home.walkTopDown().maxDepth(7).firstOrNull { it.name == name && it.isFile }
    }

    private fun flattenSingleRoot(home: File) {
        val children = home.listFiles()?.filter { it.name != "." && it.name != ".." } ?: return
        if (children.size == 1 && children[0].isDirectory &&
            !java.nio.file.Files.isSymbolicLink(children[0].toPath())
        ) {
            val root = children[0]
            if (File(root, "release").isFile || File(root, "lib").isDirectory) {
                root.listFiles().orEmpty().forEach { child ->
                    val target = File(home, child.name)
                    if (!child.renameTo(target)) {
                        child.copyRecursively(target, overwrite = true)
                        child.deleteRecursively()
                    }
                }
                root.deleteRecursively()
            }
        }
    }

    private fun sha256Hex(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * Understands legacy Java 8 release strings (1.8.0_*) as well as modern
 * semantic versions (17.0.9, 21.0.1, 25.0.0).
 */
internal fun parseMobileJavaMajor(version: String): Int? {
    val parts = version.split('.', '_', '+', '-')
    val first = parts.firstOrNull()?.toIntOrNull() ?: return null
    return if (first == 1) parts.getOrNull(1)?.toIntOrNull() else first
}
