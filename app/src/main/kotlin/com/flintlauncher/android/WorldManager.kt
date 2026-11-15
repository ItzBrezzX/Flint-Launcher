package com.flintlauncher.android

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * World/save management: list saves, back up a world to a zip, restore from a zip.
 * All zip paths are validated against path-traversal (an entry may never escape the
 * target saves directory). Backup zips are written to <backupsDir> with a timestamp.
 */
object WorldManager {

    data class WorldSave(
        val name: String,
        val dir: File,
        val lastModifiedMillis: Long,
        val sizeBytes: Long,
    )

    data class BackupFile(
        val name: String,
        val file: File,
        val createdMillis: Long,
        val sizeBytes: Long,
    )

    /** List world directories in <savesDir>. A world is any subdirectory (level.dat presence is not required). */
    fun listWorlds(savesDir: File): List<WorldSave> {
        if (!savesDir.isDirectory) return emptyList()
        return savesDir.listFiles { it.isDirectory }?.map { dir ->
            WorldSave(
                name = dir.name,
                dir = dir,
                lastModifiedMillis = dir.lastModified(),
                sizeBytes = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() },
            )
        }?.sortedByDescending { it.lastModifiedMillis } ?: emptyList()
    }

    fun listBackups(backupsDir: File): List<BackupFile> {
        if (!backupsDir.isDirectory) return emptyList()
        return backupsDir.listFiles { it.isFile && it.name.endsWith(".zip") }?.map { f ->
            BackupFile(f.name, f, f.lastModified(), f.length())
        }?.sortedByDescending { it.createdMillis } ?: emptyList()
    }

    /** Zip <savesDir>/<worldName> into <backupsDir>. Returns the created backup file. */
    fun backup(savesDir: File, backupsDir: File, worldName: String): File {
        val source = File(savesDir, worldName)
        require(source.isDirectory) { "World does not exist: $worldName" }
        backupsDir.mkdirs()
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        val safeName = worldName.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val target = File(backupsDir, "$safeName-$timestamp.zip")
        ZipOutputStream(FileOutputStream(target).buffered()).use { zip ->
            source.walkTopDown().filter { it.isFile }.forEach { file ->
                val entryName = source.toPath().relativize(file.toPath()).toString()
                zip.putNextEntry(ZipEntry("$worldName/$entryName"))
                FileInputStream(file).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return target
    }

    /**
     * Unzip [backup] into <savesDir>. If [targetName] is null the world name is taken
     * from the top-level zip entry. Refuses entries that escape the saves directory.
     * Returns the restored world directory.
     */
    fun restore(backup: File, savesDir: File, targetName: String? = null): File {
        require(backup.isFile) { "Backup file does not exist: ${backup.path}" }
        savesDir.mkdirs()
        var resolvedName: String? = targetName
        val canonicalSaves = savesDir.canonicalFile
        ZipInputStream(FileInputStream(backup).buffered()).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val entryPath = entry.name
                check(!entryPath.contains("..")) { "Refusing path-traversal entry: $entryPath" }
                val firstSegment = entryPath.substringBefore('/')
                val worldName = resolvedName ?: firstSegment.also { resolvedName = it }
                val relative = if (entryPath.startsWith("$firstSegment/")) entryPath.removePrefix("$firstSegment/") else entryPath
                if (relative.isBlank()) { zip.closeEntry(); entry = zip.nextEntry; continue }
                val target = File(File(canonicalSaves, worldName), relative).canonicalFile
                check(target.path.startsWith(canonicalSaves.path + File.separator)) {
                    "Refusing zip entry outside saves directory: $entryPath"
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { zip.copyTo(it) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val name = requireNotNull(resolvedName) { "Backup zip contained no world entries." }
        return File(savesDir, name)
    }

    fun deleteBackup(file: File): Boolean = file.delete()
}
