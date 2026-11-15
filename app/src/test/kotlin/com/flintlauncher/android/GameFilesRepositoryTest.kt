package com.flintlauncher.android

import com.flintlauncher.android.ui.state.GameFileCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GameFilesRepositoryTest {
    @Test fun discoversOnlySupportedFilesInGameFolders() {
        val root = createTempDir(prefix = "flint-files-test")
        try {
            File(root, "screenshots").mkdirs()
            File(root, "logs").mkdirs()
            File(root, "crash-reports").mkdirs()
            File(root, "screenshots/world.png").writeBytes(byteArrayOf(1, 2, 3))
            File(root, "logs/latest.log").writeText("log")
            File(root, "crash-reports/crash.txt").writeText("crash")
            File(root, "screenshots/ignore.exe").writeText("no")
            val entries = GameFilesRepository(root).list()
            assertEquals(3, entries.size)
            assertEquals(setOf(GameFileKind.SCREENSHOT, GameFileKind.LOG, GameFileKind.CRASH_REPORT), entries.map { it.kind }.toSet())
            assertTrue(entries.all { it.file.canonicalPath.startsWith(root.canonicalPath + File.separator) })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun deleteRejectsFilesOutsideGameRoot() {
        val root = createTempDir(prefix = "flint-files-root")
        val outside = File(root.parentFile, "flint-outside-${System.nanoTime()}.txt")
        try {
            outside.writeText("keep")
            val entry = GameFileEntry(outside.absolutePath, outside.name, outside, GameFileKind.LOG, outside.length(), outside.lastModified())
            assertEquals(false, GameFilesRepository(root).delete(entry))
            assertTrue(outside.exists())
        } finally {
            outside.delete()
            root.deleteRecursively()
        }
    }
}