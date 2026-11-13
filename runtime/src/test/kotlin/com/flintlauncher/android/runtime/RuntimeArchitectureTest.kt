package com.flintlauncher.android.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeArchitectureTest {

    @Test
    fun normalizesAndroidAbiNames() {
        assertEquals("arm64", RuntimeArchitecture.normalize("arm64-v8a"))
        assertEquals("arm", RuntimeArchitecture.normalize("armeabi-v7a"))
        assertEquals("x86", RuntimeArchitecture.normalize("x86"))
        assertEquals("x86_64", RuntimeArchitecture.normalize("x86_64"))
        assertNull(RuntimeArchitecture.normalize("mips"))
    }

    @Test
    fun choosesAbiForTheActualAppProcessBitness() {
        val abis = listOf("arm64-v8a", "armeabi-v7a")
        assertEquals("arm64", RuntimeArchitecture.fromSupportedAbis(abis, true))
        assertEquals("arm", RuntimeArchitecture.fromSupportedAbis(abis, false))
    }

    @Test
    fun publishedJavaMajorsAreFilteredByArchitecture() {
        assertEquals(listOf(8, 17, 21, 25), KnownJreArtifacts.majorsFor("arm64-v8a"))
        assertEquals(listOf(8, 17, 21), KnownJreArtifacts.majorsFor("armeabi-v7a"))
    }

    @Test
    fun selectsLowestPublishedRuntimeAtOrAboveTheMinecraftRequirement() {
        assertEquals(17, KnownJreArtifacts.compatibleMajor(16, "arm64-v8a"))
        assertEquals(21, KnownJreArtifacts.compatibleMajor(21, "arm64-v8a"))
        assertNull(KnownJreArtifacts.compatibleMajor(26, "arm64-v8a"))
        assertNull(KnownJreArtifacts.compatibleMajor(25, "armeabi-v7a"))
    }

    @Test
    fun parsesLegacyJavaEightReleaseStrings() {
        assertEquals(8, parseMobileJavaMajor("1.8.0_432"))
        assertEquals(8, parseMobileJavaMajor("1.8.0_432-b06"))
        assertEquals(17, parseMobileJavaMajor("17.0.9"))
        assertEquals(21, parseMobileJavaMajor("21.0.1+12"))
        assertEquals(25, parseMobileJavaMajor("25.0.0"))
        assertNull(parseMobileJavaMajor("not-a-version"))
    }
}
