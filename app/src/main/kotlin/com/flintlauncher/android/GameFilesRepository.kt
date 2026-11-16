package com.flintlauncher.android

import java.io.File

enum class GameFileKind { SCREENSHOT, LOG, CRASH_REPORT }

data class GameFileEntry(
    val id: String,
    val name: String,
    val file: File,
    val kind: GameFileKind,
    val sizeBytes: Long,
    val modifiedAt: Long,
)

/** Lists and deletes only supported files within known game directories. */
class GameFilesRepository(private val gameRoot: File) {
    fun list(): List<GameFileEntry> {
        val root = gameRoot.canonicalFile
        val folders = listOf(
            File(root, "screenshots") to GameFileKind.SCREENSHOT,
            File(root, "logs") to GameFileKind.LOG,
            File(root, "crash-reports") to GameFileKind.CRASH_REPORT,
        )
        return folders.flatMap { (folder, kind) ->
            if (!folder.isDirectory) emptyList() else {
                folder.walkTopDown()
                    .filter { it.isFile && it.extension.lowercase() in extensions(kind) }
                    .mapNotNull { candidate ->
                        val safe = runCatching { candidate.canonicalFile }.getOrNull()
                            ?: return@mapNotNull null
                        if (!isInside(safe, folder.canonicalFile) || !safe.canRead()) return@mapNotNull null
                        GameFileEntry(
                            id = safe.absolutePath,
                            name = safe.name,
                            file = safe,
                            kind = kind,
                            sizeBytes = safe.length(),
                            modifiedAt = safe.lastModified(),
                        )
                    }.toList()
            }
        }.sortedByDescending { it.modifiedAt }
    }

    fun resolveSupportedFile(path: String): File? {
        val root = gameRoot.canonicalFile
        val candidate = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        val kind = allowedFolderFor(root, candidate) ?: return null
        return candidate.takeIf {
            it.isFile && it.canRead() && it.extension.lowercase() in extensions(kind)
        }
    }

    fun delete(path: String): Boolean {
        val file = resolveSupportedFile(path) ?: return false
        return file.delete()
    }

    private fun allowedFolderFor(root: File, file: File): GameFileKind? {
        val folders = listOf(
            File(root, "screenshots") to GameFileKind.SCREENSHOT,
            File(root, "logs") to GameFileKind.LOG,
            File(root, "crash-reports") to GameFileKind.CRASH_REPORT,
        )
        return folders.firstOrNull { (folder, _) -> isInside(file, folder.canonicalFile) }?.second
    }

    private fun isInside(file: File, directory: File): Boolean =
        file.path.startsWith(directory.path + File.separator)

    private fun extensions(kind: GameFileKind): Set<String> = when (kind) {
        GameFileKind.SCREENSHOT -> setOf("png", "jpg", "jpeg", "webp")
        GameFileKind.LOG -> setOf("log", "txt", "gz")
        GameFileKind.CRASH_REPORT -> setOf("txt", "log")
    }
}
