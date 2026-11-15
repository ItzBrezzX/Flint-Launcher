package com.flintlauncher.android.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProfileSetCodecTest {
    private fun profile(name: String, jvm: List<String> = emptyList()) = Profile(
        name = name, minecraftVersion = "1.21", jvmArguments = jvm,
    )

    @Test
    fun roundTripPreservesProfilesAndStripsIdsAndDefaults() {
        val original = listOf(
            profile("One").copy(id = "a", isDefault = true),
            profile("Two", jvm = listOf("-Xmx4G")).copy(id = "b"),
        )
        val text = ProfileSetCodec.exportAll(original)
        assertTrue(text.contains("\"format\": ${ProfileSetCodec.FORMAT_VERSION}"))
        val imported = ProfileSetCodec.importAll(text)
        assertEquals(2, imported.profiles.size)
        assertTrue(imported.profiles.all { it.id.isEmpty() })
        assertTrue(imported.profiles.none { it.isDefault })
        assertEquals(listOf("-Xmx4G"), imported.profiles[1].jvmArguments)
        assertTrue(imported.droppedArguments.isEmpty())
    }

    @Test
    fun riskyArgumentsAreDroppedAndReported() {
        val original = listOf(profile("Evil", jvm = listOf("-javaagent:evil.jar", "-Xmx2G")))
        val imported = ProfileSetCodec.importAll(ProfileSetCodec.exportAll(original))
        assertEquals(listOf("-Xmx2G"), imported.profiles.single().jvmArguments)
        assertEquals(listOf("-javaagent:evil.jar"), imported.droppedArguments)
    }

    @Test
    fun invalidJsonIsRejected() {
        try {
            ProfileSetCodec.importAll("{not json")
            fail("expected ProfileValidationException")
        } catch (e: ProfileValidationException) {
            assertTrue(e.errors.single().contains("valid Flint profile set"))
        }
    }

    @Test
    fun unsupportedFormatVersionIsRejected() {
        val text = """{"format": 99, "app": "flint", "profiles": []}"""
        try {
            ProfileSetCodec.importAll(text)
            fail("expected ProfileValidationException")
        } catch (e: ProfileValidationException) {
            assertTrue(e.errors.single().contains("Unsupported"))
        }
    }
}
