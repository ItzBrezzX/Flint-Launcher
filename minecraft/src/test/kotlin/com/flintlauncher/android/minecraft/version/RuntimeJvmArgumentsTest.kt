package com.flintlauncher.android.minecraft.version

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeJvmArgumentsTest {
    @Test
    fun placesManagedHeapAfterCustomArguments() {
        assertEquals(
            listOf("-Duser.language=en", "-XX:+UseG1GC", "-Xmx1024M"),
            RuntimeJvmArguments.build(1024, "-Duser.language=en -XX:+UseG1GC"),
        )
    }

    @Test
    fun preservesQuotedArgumentsAsSingleTokens() {
        assertEquals(
            listOf("-Djava.library.path=/data/user files/lib", "-Dlabel=two words", "-Xmx768M"),
            RuntimeJvmArguments.build(768, """-Djava.library.path="/data/user files/lib" '-Dlabel=two words'"""),
        )
    }

    @Test
    fun parsesEscapedWhitespaceWithoutShellExpansion() {
        assertEquals(
            listOf("-Dpath=/data/user files/\$HOME", "-Xmx512M"),
            RuntimeJvmArguments.build(512, "-Dpath=/data/user\\ files/\\\$HOME"),
        )
    }

    @Test
    fun rejectsCustomHeapAndClasspathOverrides() {
        listOf("-Xmx2G", "-Xms512M", "-cp", "--class-path", "-jar game.jar", "@/tmp/args").forEach { raw ->
            val error = runCatching { RuntimeJvmArguments.build(1024, raw) }.exceptionOrNull()
            assertTrue("Expected rejection for: $raw", error is LaunchConfigurationException)
        }
    }

    @Test
    fun rejectsMalformedQuotesAndNewlines() {
        listOf("-Dname='broken", "-Dname=value\\").forEach { raw ->
            assertTrue(runCatching { RuntimeJvmArguments.build(1024, raw) }.exceptionOrNull() is LaunchConfigurationException)
        }
        assertTrue(
            runCatching { RuntimeJvmArguments.build(1024, "-Dname=one\ntwo") }.exceptionOrNull() is LaunchConfigurationException,
        )
    }

    @Test
    fun validatesHeapAndInputLimits() {
        assertTrue(runCatching { RuntimeJvmArguments.build(128, "") }.exceptionOrNull() is LaunchConfigurationException)
        assertTrue(runCatching { RuntimeJvmArguments.build(1024, "x".repeat(2049)) }.exceptionOrNull() is LaunchConfigurationException)
    }
}
