package com.flintlauncher.android.installer

import java.io.File
import java.util.zip.ZipFile

/**
 * Extracts resources from Minecraft's platform-classified native jars.
 *
 * Desktop callers can keep extracting shared objects (the default). Android
 * launch must opt out because Mojang's natives-linux .so files use GLIBC and
 * cannot be loaded by Android's Bionic linker; Flint stages its own Bionic
 * libraries instead.
 */
object NativesExtractor {
    fun extract(
        jars: List<File>,
        target: File,
        excludePrefixes: List<String> = listOf("META-INF/"),
        excludeSharedObjects: Boolean = false,
    ): List<File> {
        if (!target.isDirectory && !target.mkdirs()) {
            throw IllegalStateException("Could not create native extraction directory: ${target.absolutePath}")
        }
        val extracted = mutableListOf<File>()
        for (jar in jars) {
            ZipFile(jar).use { zip ->
                for (entry in zip.entries()) {
                    if (entry.isDirectory) continue

                    // Validate every archive path before filtering its type. Otherwise
                    // a traversal entry ending in .so could bypass the path guard.
                    val out = GameLayout.safeChild(target, entry.name)
                    if (excludePrefixes.any { entry.name.startsWith(it) }) continue
                    if (excludeSharedObjects && isSharedObject(entry.name)) continue

                    out.parentFile?.let { parent ->
                        if (!parent.isDirectory && !parent.mkdirs()) {
                            throw IllegalStateException("Could not create native extraction directory: ${parent.absolutePath}")
                        }
                    }
                    zip.getInputStream(entry).use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                    extracted += out
                }
            }
        }
        return extracted
    }

    private fun isSharedObject(entryName: String): Boolean {
        val filename = entryName.substringAfterLast('/')
        return filename.endsWith(".so", ignoreCase = true) ||
            Regex("""\.so\.\d+(?:\.\d+)*$""", RegexOption.IGNORE_CASE)
                .containsMatchIn(filename)
    }
}
