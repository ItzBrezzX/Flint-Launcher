package com.flintlauncher.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GameFilesRepositoryTest {
    @Test
    fun listsOnlySupportedFilesInsideKnownGameFolders() {
        val root = createTempDir(prefix = "flint-game-files")
        try {
            File(root, "screenshots").mkdirs()
            File(root, "logs").mkdirs()
            File(root, "crash-reports").mkdirs()
            File(root, "screenshots/world.png").writeBytes(byteArrayOf(1, 2, 3))
            File(root, "logs/latest.log").writeText("log")
            File(root, "crash-reports/crash.txt").writeText("crash")
            File(root, "screenshots/ignore.exe").writeText("no")
            File(root, "options.txt").writeText("not listed")
            val entries = GameFilesRepository(root).list()
            assertEquals(3, entries.size)
            assertEquals(
                setOf(GameFileKind.SCREENSHOT, GameFileKind.LOG, GameFileKind.CRASH_REPORT),
                entries.map { it.kind }.toSet(),
            )
            assertTrue(entries.all { it.file.canonicalPath.startsWith(root.canonicalPath + File.separator) })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun refusesFilesOutsideKnownDirectories() {
        val root = createTempDir(prefix = "flint-game-root")
        val outside = File(root.parentFile, "outside-${System.nanoTime()}.txt")
        try {
            outside.writeText("keep")
            assertEquals(null, GameFilesRepository(root).resolveSupportedFile(outside.absolutePath))
            assertFalse(GameFilesRepository(root).delete(outside.absolutePath))
            assertTrue(outside.exists())
        } finally {
            outside.delete()
            root.deleteRecursively()
        }
    }

    @Test
    fun deletesSupportedFileWithinGameRoot() {
        val root = createTempDir(prefix = "flint-game-delete")
        try {
            val logs = File(root, "logs").apply { mkdirs() }
            val log = File(logs, "latest.log").apply { writeText("log") }
            assertTrue(GameFilesRepository(root).delete(log.absolutePath))
            assertFalse(log.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
