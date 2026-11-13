package com.flintlauncher.android.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProfileRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private var counter = 0
    private fun repo(file: File = File(tmp.root, "profiles.json")) =
        ProfileRepository(file, newId = { "id-${++counter}" })

    private fun draft(name: String, version: String = "1.21.11") = Profile(name = name, minecraftVersion = version)

    @Test
    fun firstProfileBecomesTheDefault() {
        val repo = repo()
        val first = repo.create(draft("Main"))
        val second = repo.create(draft("Other"))

        assertTrue(first.isDefault)
        assertFalse(second.isDefault)
        assertEquals(first, repo.default())
    }

    @Test
    fun namesMustBeUniqueIgnoringCase() {
        val repo = repo()
        repo.create(draft("Main"))
        try {
            repo.create(draft("MAIN"))
            fail("expected ProfileValidationException")
        } catch (e: ProfileValidationException) {
            assertTrue(e.errors.single().contains("already exists"))
        }
    }

    @Test
    fun invalidProfilesReportEveryProblem() {
        val bad = Profile(name = " ", minecraftVersion = "", loader = "mystery", memoryMb = 10, jvmArguments = listOf(" "))
        val errors = ProfileValidator.validate(bad)
        assertEquals(5, errors.size)
        try {
            repo().create(bad)
            fail("expected ProfileValidationException")
        } catch (e: ProfileValidationException) {
            assertEquals(errors, e.errors)
        }
    }

    @Test
    fun updateKeepsTheDefaultFlagAndChecksNameClashesWithOthers() {
        val repo = repo()
        val a = repo.create(draft("A"))
        val b = repo.create(draft("B"))

        val renamed = repo.update(a.copy(name = "A renamed", memoryMb = 4096))
        assertTrue(renamed.isDefault)
        assertEquals(4096, repo.get(a.id)!!.memoryMb)

        try {
            repo.update(b.copy(name = "a renamed"))
            fail("expected ProfileValidationException")
        } catch (e: ProfileValidationException) {
            assertTrue(e.errors.single().contains("already exists"))
        }
    }

    @Test
    fun deletingTheDefaultPromotesAnotherProfile() {
        val repo = repo()
        val a = repo.create(draft("A"))
        val b = repo.create(draft("B"))

        assertTrue(repo.delete(a.id))

        assertEquals(b.id, repo.default()!!.id)
        assertFalse(repo.delete("missing"))
    }

    @Test
    fun cloneCopiesSettingsWithANewIdAndName() {
        val repo = repo()
        val a = repo.create(draft("A").copy(loader = Loaders.FABRIC, memoryMb = 3000))

        val copy = repo.clone(a.id)

        assertNotEquals(a.id, copy.id)
        assertEquals("A copy", copy.name)
        assertEquals(3000, copy.memoryMb)
        assertFalse(copy.isDefault)
        assertEquals("A copy 2", repo.clone(a.id).name)
    }

    @Test
    fun setDefaultMovesTheFlag() {
        val repo = repo()
        repo.create(draft("A"))
        val b = repo.create(draft("B"))

        repo.setDefault(b.id)

        assertEquals(listOf(false, true), repo.list().map { it.isDefault })
    }

    @Test
    fun changesSurviveARestart() {
        val file = File(tmp.root, "profiles.json")
        repo(file).create(draft("Persisted"))

        val reloaded = ProfileRepository(file)

        assertEquals("Persisted", reloaded.list().single().name)
    }

    @Test
    fun aCorruptFileMeansNoProfilesNotACrash() {
        val file = File(tmp.root, "profiles.json")
        file.writeText("{broken")
        assertTrue(ProfileRepository(file).list().isEmpty())
    }

    @Test
    fun exportThenImportRoundTripsWithoutTheIdOrDefaultFlag() {
        val source = repo(File(tmp.root, "a.json"))
        val original = source.create(draft("Share me").copy(loader = Loaders.QUILT, jvmArguments = listOf("-Xss1M")))
        val text = source.export(original.id)

        val target = repo(File(tmp.root, "b.json"))
        val result = target.importProfile(text)

        assertEquals("Share me", result.profile.name)
        assertEquals(Loaders.QUILT, result.profile.loader)
        assertEquals(listOf("-Xss1M"), result.profile.jvmArguments)
        assertTrue(result.droppedArguments.isEmpty())
        assertNotEquals(original.id, result.profile.id)
    }

    @Test
    fun importingDropsArgumentsThatCouldRunCode() {
        val text = """{"format":1,"profile":{"name":"Evil","minecraftVersion":"1.21.11",
            "jvmArguments":["-Xmx2G","-javaagent:/sdcard/x.jar","-XX:OnError=sh evil.sh","@/sdcard/args"],
            "gameArguments":["--fullscreen"]}}"""

        val result = repo().importProfile(text)

        assertEquals(listOf("-Xmx2G"), result.profile.jvmArguments)
        assertEquals(3, result.droppedArguments.size)
    }

    @Test
    fun importRejectsGarbageAndRenamesClashes() {
        val repo = repo()
        try {
            repo.importProfile("not a profile")
            fail("expected ProfileValidationException")
        } catch (e: ProfileValidationException) {
            assertTrue(e.errors.single().contains("valid Flint profile"))
        }
        repo.create(draft("Same"))
        val text = """{"profile":{"name":"Same","minecraftVersion":"1.21.11"}}"""
        assertEquals("Same 2", repo.importProfile(text).profile.name)
    }
}

class ProfileDirsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun eachProfileGetsItsOwnIsolatedTree() {
        val a = ProfileDirs(tmp.root, "aaa-1")
        val b = ProfileDirs(tmp.root, "bbb-2")
        a.createAll()

        assertTrue(a.mods.isDirectory)
        assertTrue(a.saves.isDirectory)
        assertNotEquals(a.gameDir, b.gameDir)
        assertFalse(b.mods.exists())
    }

    @Test
    fun idsThatCouldEscapeTheProfilesFolderAreRejected() {
        for (bad in listOf("../x", "a/b", "", "a b", "..")) {
            try {
                ProfileDirs(tmp.root, bad)
                fail("expected IllegalArgumentException for '$bad'")
            } catch (e: IllegalArgumentException) {
                assertNull(e.cause)
            }
        }
    }
}
