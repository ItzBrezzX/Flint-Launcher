package com.flintlauncher.android.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Manages mobile OpenJDK runtimes for Android ARM64 (Pojav-style layout).
 *
 * Artifact policy (Spike 0.2):
 * - The upstream android-openjdk-build-multiarch repo is archived; GitHub Actions
 *   artifacts are not stable public URLs with published SHA-256.
 * - Therefore Flint does NOT hardcode invented checksums or example.invalid URLs.
 * - Operators supply a local .tar.gz / .tar.xz via [importLocalArchive], or configure
 *   [JreArtifact] with a URL+SHA256 they have verified themselves.
 *
 * License: OpenJDK builds are GPLv2 with Classpath Exception. Only redistribute
 * binaries you are allowed to redistribute; prefer user-side install.
 */
data class JreArtifact(
    val id: String,
    val major: Int,
    val arch: String,
    val url: String?,
    val sha256: String?,
    val sizeBytes: Long? = null,
    val sourceNote: String = "",
)

object KnownJreArtifacts {
    /**
     * Catalog entries without download URLs. Use importLocalArchive() after the user
     * obtains a mobile JRE (e.g. from a Pojav-compatible build they trust).
     */
    val ARM64_TEMPLATES = listOf(
        JreArtifact(
            id = "jre17-arm64",
            major = 17,
            arch = "aarch64",
            url = null,
            sha256 = null,
            sourceNote = "Mobile OpenJDK 17 aarch64 — supply local tar.gz from android-openjdk-build-multiarch lineage or successor",
        ),
        JreArtifact(
            id = "jre21-arm64",
            major = 21,
            arch = "aarch64",
            url = null,
            sha256 = null,
            sourceNote = "Mobile OpenJDK 21 aarch64 — supply local tar.gz",
        ),
        JreArtifact(
            id = "jre8-arm64",
            major = 8,
            arch = "aarch64",
            url = null,
            sha256 = null,
            sourceNote = "Mobile OpenJDK 8 aarch64 — for older Minecraft versions",
        ),
    )
}

class MobileJreManager(
    private val runtimesDir: File,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build(),
) : JavaRuntimeManager {

    override suspend fun listInstalled(): List<JavaRuntimeInfo> = withContext(Dispatchers.IO) {
        if (!runtimesDir.isDirectory) return@withContext emptyList()
        runtimesDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { probe(it) }
            ?: emptyList()
    }

    override suspend fun findCompatible(requiredMajor: Int, preferArch: String): JavaRuntimeInfo? {
        return listInstalled()
            .filter { it.arch.contains("aarch64") || it.arch.contains("arm64") || preferArch in it.arch }
            .filter { it.majorVersion >= requiredMajor && it.verified }
            .maxByOrNull { it.majorVersion }
    }

    override suspend fun install(major: Int): Result<JavaRuntimeInfo> {
        return Result.failure(
            IllegalStateException(
                "Remote install is disabled until a stable, SHA-256-verified mobile OpenJDK " +
                    "URL is configured. Use importLocalArchive(file, id, major) after obtaining " +
                    "a trusted aarch64 JRE tarball. See docs/SPIKE0.md.",
            ),
        )
    }

    /**
     * Import a user-supplied or CI-built mobile JRE archive (.tar.gz).
     * Optional [expectedSha256] is verified when non-null.
     */
    suspend fun importLocalArchive(
        archive: File,
        id: String,
        major: Int,
        expectedSha256: String? = null,
    ): Result<JavaRuntimeInfo> = withContext(Dispatchers.IO) {
        runCatching {
            if (!archive.isFile) error("Archive not found: $archive")
            if (expectedSha256 != null) {
                val actual = sha256Hex(archive)
                if (!actual.equals(expectedSha256, ignoreCase = true)) {
                    error("SHA-256 mismatch: expected $expectedSha256 got $actual")
                }
            }
            runtimesDir.mkdirs()
            val home = File(runtimesDir, id)
            if (home.exists()) home.deleteRecursively()
            home.mkdirs()
            when {
                archive.name.endsWith(".tar.gz") || archive.name.endsWith(".tgz") -> {
                    SafeTarGzExtractor.extract(archive, home)
                }
                else -> error("Only .tar.gz is supported by SafeTarGzExtractor in this build (got ${archive.name})")
            }
            // If the tarball has a single top-level directory, flatten one level when needed
            flattenSingleRoot(home)
            val info = probe(home)
                ?: error("Extracted tree is not a usable mobile JRE (libjli/libjvm not found)")
            if (info.majorVersion != major && major > 0) {
                // allow mismatch but keep probed major
            }
            info
        }
    }

    override suspend fun verify(runtime: JavaRuntimeInfo): Boolean = withContext(Dispatchers.IO) {
        probe(runtime.homeDir)?.verified == true
    }

    private fun flattenSingleRoot(home: File) {
        val children = home.listFiles()?.filter { it.name != "." && it.name != ".." } ?: return
        if (children.size == 1 && children[0].isDirectory) {
            val root = children[0]
            // If root looks like a JRE (has bin/ or lib/), move contents up
            if (File(root, "lib").isDirectory || File(root, "bin").isDirectory || File(root, "release").isFile) {
                root.listFiles()?.forEach { child ->
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

    private fun probe(home: File): JavaRuntimeInfo? {
        if (!home.isDirectory) return null
        val release = File(home, "release")
        val major = parseMajor(release) ?: detectMajorFromPath(home) ?: return null
        val jli = findLib(home, "libjli.so")
        val jvm = findLib(home, "libjvm.so")
        val verified = jli != null && jvm != null
        return JavaRuntimeInfo(
            id = home.name,
            majorVersion = major,
            vendor = "OpenJDK-mobile",
            homeDir = home,
            javaExecutable = File(home, "bin/java").takeIf { it.isFile },
            arch = "aarch64",
            verified = verified,
        )
    }

    private fun parseMajor(release: File): Int? {
        if (!release.isFile) return null
        val line = release.readLines().firstOrNull { it.startsWith("JAVA_VERSION=") } ?: return null
        val ver = line.substringAfter("=").trim().trim('"')
        return ver.substringBefore('.').filter { it.isDigit() }.toIntOrNull()
    }

    private fun detectMajorFromPath(home: File): Int? =
        Regex("jre(\\d+)").find(home.name)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun findLib(home: File, name: String): File? {
        val candidates = listOf(
            File(home, "lib/$name"),
            File(home, "lib/aarch64/$name"),
            File(home, "lib/arm64/$name"),
            File(home, "lib/server/$name"),
            File(home, "lib/aarch64/server/$name"),
            File(home, "lib/jli/$name"),
        )
        candidates.firstOrNull { it.isFile }?.let { return it }
        return home.walkTopDown().maxDepth(6).firstOrNull { it.name == name && it.isFile }
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
