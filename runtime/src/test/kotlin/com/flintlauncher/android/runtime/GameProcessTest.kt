package com.flintlauncher.android.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests (host JVM, no Android, no native) for the runtime shard's
 * non-native logic. Native-dependent paths are covered by the instrumentation
 * skeleton in :native (NativeLoadingInstrumentedTest).
 */
class GameProcessTest {

    @Test
    fun initialStateIsStarting() {
        val p = GameProcess(id = "test")
        assertEquals(GameProcess.State.STARTING, p.state)
        assertTrue(p.isAlive)
        assertNull(p.exitCode)
    }

    @Test
    fun exitCodeOnlyVisibleAfterExit() {
        val p = GameProcess(id = "test")
        p.setExitCode(0)
        p.transition(GameProcess.State.EXITED)
        assertEquals(0, p.exitCode)
        assertFalse(p.isAlive)
    }

    @Test
    fun cancelTransitionsToCancelled() {
        val p = GameProcess(id = "test")
        p.cancel()
        assertEquals(GameProcess.State.CANCELLED, p.state)
        assertFalse(p.isAlive)
    }

    @Test
    fun crashStateHoldsReport() {
        val p = GameProcess(id = "test")
        p.transition(GameProcess.State.CRASHED)
        p.crashReport = "signal=11"
        assertEquals("signal=11", p.crashReport)
        assertNull(p.exitCode)
    }
}

class NativeLibraryPathArgTest {
    @Test
    fun javaLibraryPathArgJoinsSegments() {
        val result = NativeLibraryLoader.LoadResult(
            javaLibraryPath = "/data/natives:/data/plugins",
            ldLibraryPath = "/data/natives:/data/plugins",
            preloaded = emptyList(),
            missing = emptyList(),
        )
        assertEquals(
            "-Djava.library.path=/data/natives:/data/plugins",
            NativeLibraryLoader.javaLibraryPathArg(result),
        )
        assertTrue(result.allPresent)
    }

    @Test
    fun missingReportedWhenPluginAbsent() {
        val result = NativeLibraryLoader.LoadResult(
            javaLibraryPath = "/data/natives",
            ldLibraryPath = "/data/natives",
            preloaded = emptyList(),
            missing = listOf("libGL.so"),
        )
        assertFalse(result.allPresent)
        assertEquals(1, result.missing.size)
    }
}
