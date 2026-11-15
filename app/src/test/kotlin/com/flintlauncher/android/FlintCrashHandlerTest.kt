package com.flintlauncher.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlintCrashHandlerTest {

    @Test
    fun redactsAccessRefreshAndBearerTokens() {
        val input = """
            access_token=access-secret-value
            "refreshToken":"refresh-secret-value"
            Authorization: Bearer header-secret-value
            client_secret=client-secret-value
        """.trimIndent()

        val redacted = FlintCrashHandler.redactSecrets(input)

        assertFalse(redacted.contains("access-secret-value"))
        assertFalse(redacted.contains("refresh-secret-value"))
        assertFalse(redacted.contains("header-secret-value"))
        assertFalse(redacted.contains("client-secret-value"))
        assertTrue(redacted.contains("<redacted>"))
    }

    @Test
    fun leavesOrdinaryDiagnosticTextReadable() {
        val input = "EGL window creation failed: egl-window-config-unavailable"
        assertTrue(FlintCrashHandler.redactSecrets(input).contains(input))
    }
}
