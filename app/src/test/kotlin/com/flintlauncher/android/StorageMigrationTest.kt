package com.flintlauncher.android

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StorageMigrationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var oldRoot: File
    private lateinit var newRoot: File

    @Before
    fun setUp() {
        oldRoot = tmp.newFolder("old")
        newRoot = tmp.newFolder("new")
    }

    @Test
    fun migrateCopiesVerifiesAndDeletesOld() {
        File(oldRoot, "versions/1.20/1.20.jar").apply { parentFile?.mkdirs(); writeText("jar") }
        File(oldRoot, "libraries/lib.jar").apply { parentFile?.mkdirs(); writeText("lib") }
        File(oldRoot, "options.txt").writeText("options")

        val report = StorageMigration.migrate(oldRoot, newRoot)
        assertTrue(report.ok)
        assertEquals(3, report.migratedFiles)

        // Old files removed, new files present with same content.
        assertFalse(File(oldRoot, "options.txt").exists())
        assertEquals("jar", File(newRoot, "versions/1.20/1.20.jar").readText())
        assertEquals("lib", File(newRoot, "libraries/lib.jar").readText())
        assertEquals("options", File(newRoot, "options.txt").readText())
    }

    @Test
    fun identicalExistingFileIsSkippedAndRemoved() {
        File(oldRoot, "a.txt").writeText("same")
        File(newRoot, "a.txt").apply { parentFile?.mkdirs(); writeText("same") }
        val report = StorageMigration.migrate(oldRoot, newRoot)
        assertEquals(1, report.skippedFiles)
        assertFalse(File(oldRoot, "a.txt").exists())
    }

    @Test
    fun newRootInsideOldRootIsRefused() {
        val nested = File(oldRoot, "new").apply { mkdirs() }
        File(oldRoot, "a.txt").writeText("x")
        try {
            StorageMigration.migrate(oldRoot, nested)
            org.junit.Assert.fail("Expected refusal")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Refusing") == true)
        }
    }
}
