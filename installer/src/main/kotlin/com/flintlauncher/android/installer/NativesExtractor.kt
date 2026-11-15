package com.flintlauncher.android.installer

import java.io.File
import java.util.zip.ZipFile

/**
 * Extracts shared libraries from legacy native jars (versions before 1.19 ship natives as
 * separate jars). Entries that would land outside [target] abort the extraction.
 */
object NativesExtractor {
    fun extract(jars: List<File>, target: File, excludePrefixes: List<String> = listOf("META-INF/")): List<File> =
        extractFiltered(jars, target, excludePrefixes) { false }.extracted

    /**
     * Extracts natives, skipping META-INF and any entry [skipEntry] returns true for
     * (used to drop LWJGL/GLFW/OpenAL desktop natives that Flint replaces). Entries that
     * would land outside [target] abort the extraction (zip-slip protection, inherited
     * from [GameLayout.safeChild]). Skipped entries are reported, not silently dropped.
     */
    fun extractFiltered(
        jars: List<File>,
        target: File,
        excludePrefixes: List<String> = listOf("META-INF/"),
        skipEntry: (String) -> Boolean,
    ): NativeExtractionResult {
        target.mkdirs()
        val extracted = mutableListOf<File>()
        val skipped = mutableListOf<String>()
        for (jar in jars) {
            ZipFile(jar).use { zip ->
                for (entry in zip.entries()) {
                    if (entry.isDirectory) continue
                    if (excludePrefixes.any { entry.name.startsWith(it) }) continue
                    if (skipEntry(entry.name)) {
                        skipped += entry.name
                        continue
                    }
                    val out = GameLayout.safeChild(target, entry.name)
                    out.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                    extracted += out
                }
            }
        }
        return NativeExtractionResult(extracted, skipped.distinct().sorted())
    }
}
