package com.flintlauncher.android

import java.io.File

/**
 * Storage migration helper: moves game files from an old root (e.g. the legacy
 * internal-files location) to a new root (e.g. app-specific external storage, which
 * has more space). A file is only deleted from the old root after the copy is
 * verified by size. A failed copy aborts that file but continues the rest; nothing
 * is deleted on failure, so a partial migration can be retried.
 */
object StorageMigration {

    data class Report(
        val migratedFiles: Int = 0,
        val skippedFiles: Int = 0,
        val failedFiles: List<String> = emptyList(),
    ) {
        val ok: Boolean get() = failedFiles.isEmpty()
    }

    /**
     * Copy every file under [oldRoot] into [newRoot] preserving relative paths, then
     * verify by size and remove the old copy. Directories are created as needed.
     * [onProgress] receives (done, total) after each file.
     */
    fun migrate(oldRoot: File, newRoot: File, onProgress: (Int, Int) -> Unit = { _, _ -> }): Report {
        if (!oldRoot.isDirectory) return Report()
        newRoot.mkdirs()
        val files = oldRoot.walkTopDown().filter { it.isFile }.toList()
        val canonicalOld = oldRoot.canonicalFile
        val canonicalNew = newRoot.canonicalFile
        check(!canonicalNew.path.startsWith(canonicalOld.path + File.separator)) {
            "Refusing migration: new root is inside the old root."
        }
        var migrated = 0
        var skipped = 0
        val failed = mutableListOf<String>()
        files.forEachIndexed { index, file ->
            try {
                val relative = canonicalOld.toPath().relativize(file.canonicalFile.toPath()).toString()
                val target = File(canonicalNew, relative)
                if (target.isFile && target.length() == file.length()) {
                    skipped++
                    file.delete()
                } else {
                    target.parentFile?.mkdirs()
                    file.copyTo(target, overwrite = true)
                    check(target.length() == file.length()) { "Size mismatch after copy: ${file.path}" }
                    file.delete()
                    migrated++
                }
            } catch (e: Exception) {
                failed += file.path
            }
            onProgress(index + 1, files.size)
        }
        // Remove now-empty old directories, best-effort.
        oldRoot.walkBottomUp().filter { it.isDirectory && it != oldRoot }.forEach { runCatching { it.delete() } }
        return Report(migrated, skipped, failed)
    }
}
