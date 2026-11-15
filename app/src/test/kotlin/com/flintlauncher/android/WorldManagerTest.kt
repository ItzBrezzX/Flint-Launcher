package com.flintlauncher.android

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorldManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var savesDir: File
    private lateinit var backupsDir: File

    @Before
    fun setUp() {
        savesDir = tmp.newFolder("saves")
        backupsDir = tmp.newFolder("backups")
    }

    @Test
    fun listWorldsIncludesSubdirectories() {
        val world = File(savesDir, "New World").apply { mkdirs() }
        File(world, "level.dat").writeText("data")
        val worlds = WorldManager.listWorlds(savesDir)
        assertEquals(1, worlds.size)
        assertEquals("New World", worlds[0].name)
    }

    @Test
    fun backupThenRestoreRoundTrip() {
        val world = File(savesDir, "MyWorld").apply { mkdirs() }
        File(world, "level.dat").writeText("level-data")
        File(world, "region/r.0.0.mca").apply { parentFile?.mkdirs(); writeText("region-data") }

        val backup = WorldManager.backup(savesDir, backupsDir, "MyWorld")
        assertTrue(backup.isFile)
        assertTrue(backup.name.endsWith(".zip"))

        // Restore into a fresh saves directory.
        val freshSaves = tmp.newFolder("saves2")
        val restored = WorldManager.restore(backup, freshSaves)
        assertEquals("MyWorld", restored.name)
        assertEquals("level-data", File(restored, "level.dat").readText())
        assertEquals("region-data", File(restored, "region/r.0.0.mca").readText())
    }

    @Test
    fun pathTraversalZipEntryIsRejected() {
        val malicious = File(backupsDir, "evil.zip")
        ZipOutputStream(FileOutputStream(malicious)).use { zip ->
            zip.putNextEntry(ZipEntry("MyWorld/../../escape.txt"))
            zip.write("evil".toByteArray())
            zip.closeEntry()
        }
        try {
            WorldManager.restore(malicious, savesDir)
            fail("Expected path-traversal rejection")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Refusing") == true)
        }
    }

    @Test
    fun listBackupsSortedNewestFirst() {
        val world = File(savesDir, "W").apply { mkdirs(); File(this, "level.dat").writeText("x") }
        val b1 = WorldManager.backup(savesDir, backupsDir, "W")
        Thread.sleep(10)
        val b2 = WorldManager.backup(savesDir, backupsDir, "W")
        val backups = WorldManager.listBackups(backupsDir)
        assertEquals(2, backups.size)
        assertEquals(b2.name, backups[0].name)
        assertEquals(b1.name, backups[1].name)
    }
}
