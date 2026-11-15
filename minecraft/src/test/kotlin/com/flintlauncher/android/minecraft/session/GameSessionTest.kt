package com.flintlauncher.android.minecraft.session

import com.flintlauncher.android.core.FlintError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GameSessionTest {
    @Test
    fun normalLifecycleTransitionsAreAllowed() {
        val s = GameSession("1.21", clock = { 1000L })
        s.transition(SessionState.PREPARING)
        s.transition(SessionState.LAUNCHING)
        s.transition(SessionState.RUNNING)
        assertEquals(1000L, s.startedAt)
        assertTrue(s.state.isAlive)
        s.transition(SessionState.STOPPING)
        assertNull(s.reportExit(0))
        assertEquals(SessionState.EXITED_CLEAN, s.state)
        assertTrue(s.state.isTerminal)
        assertNotNull(s.durationMillis)
    }

    @Test
    fun nonzeroExitIsCategorizedAsCrash() {
        val s = GameSession("1.20.4")
        s.transition(SessionState.RUNNING)
        val err = s.reportExit(1, "Exception in thread \"main\" java.lang.RuntimeException")
        assertTrue(err is FlintError.Crash)
        assertEquals(1, (err as FlintError.Crash).exitCode)
        assertEquals(SessionState.EXITED_CRASH, s.state)
        assertEquals(err, s.error)
    }

    @Test
    fun signalExitsAreTreatedAsCleanUserStops() {
        val sigterm = GameSession("1.21")
        sigterm.transition(SessionState.RUNNING)
        assertNull(sigterm.reportExit(143)) // SIGTERM
        assertEquals(SessionState.EXITED_CLEAN, sigterm.state)

        val sigkill = GameSession("1.21")
        sigkill.transition(SessionState.RUNNING)
        assertNull(sigkill.reportExit(137)) // SIGKILL
        assertEquals(SessionState.EXITED_CLEAN, sigkill.state)
    }

    @Test
    fun configFailuresAreDetectedFromLogSnippet() {
        val badMemory = ExitCodeClassifier.classify("1.21", 1, "Invalid maximum heap size: -Xmx99999G")
        assertTrue(badMemory is FlintError.ConfigError)
        assertTrue((badMemory as FlintError.ConfigError).message.contains("memory"))

        val badMain = ExitCodeClassifier.classify("1.21", 1, "Error: Could not find or load main class net.minecraft.client.main.Main")
        assertTrue(badMain is FlintError.ConfigError)
        assertTrue(badMain.message.contains("main class"))

        val oldJava = ExitCodeClassifier.classify("1.21", 1, "UnsupportedClassVersionError: Preview features")
        assertTrue(oldJava is FlintError.ConfigError)
        assertTrue(oldJava.message.contains("Java runtime"))

        val nativeFail = ExitCodeClassifier.classify("1.21", 1, "java.lang.UnsatisfiedLinkError: no lwjgl in java.library.path")
        assertTrue(nativeFail is FlintError.ConfigError)
        assertTrue(nativeFail.message.contains("native library"))
    }

    @Test
    fun outOfMemoryCrashCarriesAMemoryHint() {
        val err = ExitCodeClassifier.classify("1.12.2", -1, "java.lang.OutOfMemoryError: Java heap space")
        assertTrue(err is FlintError.Crash)
        assertTrue(err.message.contains("increasing allocated memory"))
    }

    @Test
    fun terminalStatesCannotBeLeftExceptByReset() {
        val s = GameSession("1.21")
        s.transition(SessionState.RUNNING)
        s.reportExit(1)
        try {
            s.transition(SessionState.RUNNING)
            fail("expected IllegalStateException from terminal state")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("terminal"))
        }
        s.reset()
        assertEquals(SessionState.IDLE, s.state)
        assertNull(s.exitCode)
        assertNull(s.error)
    }

    @Test
    fun preLaunchFailuresLandInFailedState() {
        val s = GameSession("1.21")
        s.fail(FlintError.MissingRuntime("need java 21", 21))
        assertEquals(SessionState.FAILED, s.state)
        assertTrue(s.error is FlintError.MissingRuntime)
        assertTrue(s.state.isTerminal)
    }
}
