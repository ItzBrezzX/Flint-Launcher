package com.flintlauncher.android.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PojavStyleGameLauncherTest {
    @Test
    fun zeroExitIsNotReportedAsStarted() {
        val result = mapJvmExit(0, "clean shutdown")

        assertTrue(result is LaunchResult.Failed)
        assertEquals(
            "JVM exited normally. A live game session cannot be reported by the synchronous JLI_Launch bridge.",
            (result as LaunchResult.Failed).reason,
        )
        assertEquals("clean shutdown", result.logSnippet)
    }

    @Test
    fun nonZeroExitPreservesExitCodeAndLog() {
        val result = mapJvmExit(137, "process killed")

        assertTrue(result is LaunchResult.Failed)
        assertEquals("JVM exited with code 137", (result as LaunchResult.Failed).reason)
        assertEquals("process killed", result.logSnippet)
    }
}
