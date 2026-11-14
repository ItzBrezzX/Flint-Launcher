package com.flintlauncher.android.minecraft.version

/** `group:artifact:version[:classifier][@extension]` */
data class MavenCoordinate(
    val group: String,
    val artifact: String,
    val version: String,
    val classifier: String? = null,
    val extension: String = "jar",
) {
    /** Repository-relative path, which is also the path under the `libraries` directory. */
    val path: String
        get() {
            val suffix = if (classifier != null) "-$classifier" else ""
            return "${group.replace('.', '/')}/$artifact/$version/$artifact-$version$suffix.$extension"
        }

    /** Identity ignoring the version: two libraries with the same key conflict. */
    val key: String get() = "$group:$artifact" + (classifier?.let { ":$it" } ?: "")

    companion object {
        fun parse(name: String): MavenCoordinate? {
            val extension = name.substringAfter('@', "").ifEmpty { "jar" }
            val parts = name.substringBefore('@').split(':')
            if (parts.size < 3 || parts.any { it.isEmpty() }) return null
            return MavenCoordinate(parts[0], parts[1], parts[2], parts.getOrNull(3), extension)
        }
    }
}

data class LibraryFile(
    val key: String,
    val path: String,
    val url: String,
    val sha1: String?,
    val size: Long?,
    val isNative: Boolean,
)

data class LibraryResolution(
    val files: List<LibraryFile>,
    /** Libraries that apply to this OS but have no usable download information. */
    val unresolved: List<String>,
)

object LibraryResolver {
    fun resolve(libraries: List<Library>, env: Environment): LibraryResolution {
        val files = mutableListOf<LibraryFile>()
        val unresolved = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        for (library in libraries) {
            if (!RuleEvaluator.allows(library.rules, env)) continue
            val coordinate = MavenCoordinate.parse(library.name)
            // A loader's library listed first wins over the same group:artifact further down.
            if (coordinate != null && !seen.add(coordinate.key + "|" + (library.natives?.get(env.osName) ?: ""))) continue

            var resolvedAny = false

            val nativeClassifier = library.natives?.get(env.osName)?.replace("\${arch}", env.bitness)
            if (nativeClassifier != null) {
                val info = library.downloads?.classifiers?.get(nativeClassifier)
                if (info == null || coordinate == null) {
                    // A native declared for this platform is required. Do not silently
                    // accept the library just because its ordinary artifact exists.
                    unresolved += "${library.name} (missing native classifier $nativeClassifier)"
                } else {
                    val path = info.path ?: coordinate.copy(classifier = nativeClassifier).path
                    files += LibraryFile(coordinate.key + ":" + nativeClassifier, path, info.url, info.sha1, info.size, isNative = true)
                    resolvedAny = true
                }
            }

            val artifact = library.downloads?.artifact
            if (artifact != null && coordinate != null) {
                files += LibraryFile(coordinate.key, artifact.path ?: coordinate.path, artifact.url, artifact.sha1, artifact.size, isNative = false)
                resolvedAny = true
            } else if (library.downloads == null && library.url != null && coordinate != null) {
                val base = if (library.url.endsWith("/")) library.url else library.url + "/"
                files += LibraryFile(coordinate.key, coordinate.path, base + coordinate.path, null, null, isNative = false)
                resolvedAny = true
            }

            // A natives-only library with no entry for this OS is simply not needed here.
            if (!resolvedAny && nativeClassifier == null && library.natives == null) unresolved += library.name
        }
        return LibraryResolution(files, unresolved)
    }
}
