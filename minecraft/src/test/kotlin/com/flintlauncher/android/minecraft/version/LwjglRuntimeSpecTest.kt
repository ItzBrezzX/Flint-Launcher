package com.flintlauncher.android.minecraft.version

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LwjglRuntimeSpecTest {
    private val androidLinux = Environment(osName = "linux", osArch = "arm64")

    @Test
    fun detectsLwjgl3FromResolvedCoreAndModules() {
        val result = LwjglRuntimeSpec.resolve(
            listOf(
                Library("org.lwjgl:lwjgl:3.3.1"),
                Library("org.lwjgl:lwjgl-glfw:3.3.1"),
                Library("org.lwjgl:lwjgl-opengl:3.3.1"),
            ),
            androidLinux,
        )!!

        assertEquals(3, result.major)
        assertEquals("3.3.1", result.version)
        assertTrue(result.isModern3)
        assertFalse(result.isLegacy2)
        assertEquals(3, result.artifacts.size)
    }

    @Test
    fun detectsLegacyLwjgl2Coordinates() {
        val result = LwjglRuntimeSpec.resolve(
            listOf(Library("org.lwjgl.lwjgl:lwjgl:2.9.4-nightly-20150209")),
            androidLinux,
        )!!

        assertEquals(2, result.major)
        assertTrue(result.isLegacy2)
    }

    @Test
    fun ignoresLibrariesExcludedByPlatformRules() {
        val result = LwjglRuntimeSpec.resolve(
            listOf(
                Library(
                    "org.lwjgl:lwjgl:3.3.1",
                    rules = listOf(Rule("allow", os = OsRule(name = "windows"))),
                ),
            ),
            androidLinux,
        )
        assertNull(result)
    }

    @Test
    fun returnsNullWhenProfileDoesNotDeclareLwjgl() {
        assertNull(LwjglRuntimeSpec.resolve(listOf(Library("org.ow2.asm:asm:9.6")), androidLinux))
    }

    @Test
    fun rejectsMixedLwjglMavenGroupsEvenWhenVersionsMatch() {
        try {
            LwjglRuntimeSpec.resolve(
                listOf(
                    Library("org.lwjgl.lwjgl:lwjgl:3.3.1"),
                    Library("org.lwjgl:lwjgl-glfw:3.3.1"),
                ),
                androidLinux,
            )
            fail("Expected mixed LWJGL Maven groups to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("Mixed LWJGL Maven groups"))
        }
    }

    @Test
    fun rejectsMixedLwjglModuleVersions() {
        try {
            LwjglRuntimeSpec.resolve(
                listOf(
                    Library("org.lwjgl:lwjgl:3.3.1"),
                    Library("org.lwjgl:lwjgl-glfw:3.2.3"),
                ),
                androidLinux,
            )
            fail("Expected mixed LWJGL versions to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("Mixed LWJGL versions"))
        }
    }

    @Test
    fun rejectsUnsupportedMajorVersion() {
        try {
            LwjglRuntimeSpec.resolve(listOf(Library("org.lwjgl:lwjgl:4.0.0")), androidLinux)
            fail("Expected unsupported LWJGL major to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("Unsupported LWJGL major"))
        }
    }
}
