package com.flintlauncher.android.installer

import java.io.File
import java.util.zip.ZipFile

/**
 * Extracts shared libraries from legacy native jars (versions before 1.19 ship natives as
 * separate jars). Entries that would land outside [target] abort the extraction.
 */
object NativesExtractor {
    fun extract(jars: List<File>, target: File, excludePrefixes: List<String> = listOf("META-INF/")): List<File> {
        if ((!target.exists() && !target.mkdirs()) || !target.isDirectory || !target.canWrite()) {
            throw InstallPlanException("Could not prepare writable native library directory: ${target.path}")
        }
        val extracted = mutableListOf<File>()
        for (jar in jars) {
            ZipFile(jar).use { zip ->
                for (entry in zip.entries()) {
                    if (entry.isDirectory) continue
                    if (excludePrefixes.any { entry.name.startsWith(it) }) continue
                    val out = GameLayout.safeChild(target, entry.name)
                    out.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                    extracted += out
                }
            }
        }
        return extracted
    }
}
