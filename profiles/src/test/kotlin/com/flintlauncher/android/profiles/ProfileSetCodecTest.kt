package com.flintlauncher.android.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSetCodecTest {
    private fun profile(name: String, jvm: List<String> = emptyList(), game: List<String> = emptyList()) =
        Profile(name = name, minecraftVersion = "1.21", jvmArguments = jvm, gameArguments = game)

    @Test
    fun roundTripPreservesProfilesAndStripsIdsAndDefaultFlags() {
        val original = listOf(
            profile("One").copy(id = "profile-a", isDefault = true),
            profile("Two", jvm = listOf("-Xmx4G")).copy(id = "profile-b"),
        )
        val imported = ProfileSetCodec.importAll(ProfileSetCodec.exportAll(original))
        assertEquals(2, imported.profiles.size)
        assertTrue(imported.profiles.all { it.id.isEmpty() })
        assertTrue(imported.profiles.none { it.isDefault })
        assertEquals(listOf("-Xmx4G"), imported.profiles[1].jvmArguments)
        assertTrue(imported.droppedArguments.isEmpty())
    }

    @Test
    fun riskyArgumentsAreDroppedAndReportedFromBothArgumentLists() {
        val original = listOf(profile(
            "Evil",
            jvm = listOf("-javaagent:evil.jar", "-Xmx2G"),
            game = listOf("@/sdcard/args.txt", "--safe"),
        ))
        val imported = ProfileSetCodec.importAll(ProfileSetCodec.exportAll(original))
        assertEquals(listOf("-Xmx2G"), imported.profiles.single().jvmArguments)
        assertEquals(listOf("--safe"), imported.profiles.single().gameArguments)
        assertEquals(listOf("-javaagent:evil.jar", "@/sdcard/args.txt"), imported.droppedArguments)
    }

    @Test
    fun invalidJsonIsRejected() {
        try {
            ProfileSetCodec.importAll("{not json")
            throw AssertionError("expected ProfileValidationException")
        } catch (e: ProfileValidationException) {
            assertTrue(e.errors.single().contains("valid Flint profile set"))
        }
    }

    @Test
    fun unsupportedFormatAndWrongAppAreRejected() {
        val wrongVersion = """{"format":99,"app":"flint","profiles":[]}"""
        try {
            ProfileSetCodec.importAll(wrongVersion)
            throw AssertionError("expected unsupported format")
        } catch (e: ProfileValidationException) {
            assertTrue(e.errors.single().contains("Unsupported"))
        }

        val wrongApp = """{"format":1,"app":"other","profiles":[]}"""
        try {
            ProfileSetCodec.importAll(wrongApp)
            throw AssertionError("expected wrong app")
        } catch (e: ProfileValidationException) {
            assertTrue(e.errors.single().contains("not a Flint"))
        }
    }

    @Test
    fun invalidProfileDraftIsRejected() {
        val invalid = """{"format":1,"app":"flint","profiles":[{"name":"","minecraftVersion":"","loader":"unknown","memoryMb":0}]}"""
        try {
            ProfileSetCodec.importAll(invalid)
            throw AssertionError("expected invalid profile")
        } catch (e: ProfileValidationException) {
            assertTrue(e.errors.any { it.contains("Name is required") })
        }
    }
}
