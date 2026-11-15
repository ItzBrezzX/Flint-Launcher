package com.flintlauncher.android.installer

import java.io.File
import java.util.zip.ZipFile

/**
 * Extracts non-native resources from Minecraft's platform-classified native jars.
 * Android builds use Flint's own Bionic .so files, so Linux/GLIBC shared objects
 * from Mojang's "natives-linux" archives must never enter the Android load path.
 */
object NativesExtractor {
    fun extract(
        jars: List<File>,
        target: File,
        excludePrefixes: List<String> = listOf("META-INF/"),
        excludeSharedObjects: Boolean = true,
    ): List<File> {
        target.mkdirs()
        val extracted = mutableListOf<File>()
        for (jar in jars) {
            ZipFile(jar).use { zip ->
                for (entry in zip.entries()) {
                    if (entry.isDirectory) continue
                    if (excludePrefixes.any { entry.name.startsWith(it) }) continue
                    if (excludeSharedObjects && isSharedObject(entry.name)) continue
                    val out = GameLayout.safeChild(target, entry.name)
                    out.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                    extracted += out
                }
            }
        }
        return extracted
    }

    private fun isSharedObject(entryName: String): Boolean {
        val filename = entryName.substringAfterLast('/')
        return filename.endsWith(".so", ignoreCase = true) ||
            Regex("""\\.so\\.\\d+(?:\\.\\d+)*$""", RegexOption.IGNORE_CASE).containsMatchIn(filename)
    }
}
