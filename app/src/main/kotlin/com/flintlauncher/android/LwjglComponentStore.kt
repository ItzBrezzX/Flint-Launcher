package com.flintlauncher.android

import android.content.Context
import java.io.File

/**
 * Makes the LWJGL-for-Android jars that the CI build placed in the APK's assets/lwjgl3 available
 * as real files (the JVM classpath needs files). Copies only when missing or changed.
 */
class LwjglComponentStore(private val context: Context) {
    private val directory = File(context.filesDir, "components/lwjgl3")

    /** Jar files ready for the classpath; empty when this APK has no LWJGL components. */
    @Synchronized
    fun jars(): List<File> {
        val names = try {
            context.assets.list(ASSET_DIR)?.filter { it.endsWith(".jar") }.orEmpty()
        } catch (e: java.io.IOException) {
            emptyList()
        }
        if (names.isEmpty()) return emptyList()
        directory.mkdirs()

        val result = ArrayList<File>(names.size)
        for (name in names.sorted()) {
            val target = File(directory, name)
            val assetSize = context.assets.openFd("$ASSET_DIR/$name").use { it.length }
            if (!target.isFile || target.length() != assetSize) {
                val partial = File(directory, "$name.part")
                context.assets.open("$ASSET_DIR/$name").use { input ->
                    partial.outputStream().use { input.copyTo(it) }
                }
                if (!partial.renameTo(target)) {
                    target.delete()
                    check(partial.renameTo(target)) { "Could not install $name" }
                }
            }
            result += target
        }
        // Drop jars from older builds that are no longer shipped.
        directory.listFiles()?.filter { it.name.endsWith(".jar") && it.name !in names }?.forEach { it.delete() }
        return result
    }

    private companion object {
        const val ASSET_DIR = "lwjgl3"
    }
}
