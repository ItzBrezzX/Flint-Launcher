package com.flintlauncher.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlintResultTest {
    @Test
    fun successExposesValueAndMaps() {
        val r: FlintResult<Int, String> = FlintResult.success(42)
        assertTrue(r.isSuccess)
        assertFalse(r.isError)
        assertEquals(42, r.getOrNull())
        assertNull(r.errorOrNull())
        assertEquals("42!", (r.map { "$it!" } as FlintResult.Success).value)
    }

    @Test
    fun errorExposesErrorAndFlatMapShortCircuits() {
        val r: FlintResult<Int, String> = FlintResult.error("boom")
        assertTrue(r.isError)
        assertEquals("boom", r.errorOrNull())
        assertNull(r.getOrNull())
        var called = false
        val mapped = r.flatMap { called = true; FlintResult.success(it + 1) }
        assertFalse(called)
        assertEquals("boom", (mapped as FlintResult.Error).error)
    }

    @Test
    fun onSuccessAndOnErrorCallbacksFireForTheRightBranch() {
        var ok: Int? = null
        var err: String? = null
        FlintResult.success<Int>(7).onSuccess { ok = it }.onError { err = it }
        assertEquals(7, ok)
        assertNull(err)

        ok = null; err = null
        FlintResult.error<String>("x").onSuccess { ok = 1 }.onError { err = it }
        assertNull(ok)
        assertEquals("x", err)
    }

    @Test
    fun flintErrorCategoriesAreDistinctAndCarryPayloads() {
        val missing: FlintError = FlintError.MissingRuntime("need java", 21)
        assertTrue(missing is FlintError.MissingRuntime)
        assertEquals(21, (missing as FlintError.MissingRuntime).requiredJavaMajor)

        val crash: FlintError = FlintError.Crash("game died", exitCode = -1, logSnippet = "Exception")
        assertEquals(-1, (crash as FlintError.Crash).exitCode)
        assertEquals("Exception", crash.logSnippet)

        assertEquals("Cancelled", FlintError.Cancelled.message)
        assertTrue(FlintError.Unknown("??", causeType = "IOException").message.contains("??"))
    }
}

class PlayerIdentityTest {
    @Test
    fun uuidStripsDashes() {
        val id = com.flintlauncher.android.core.models.PlayerIdentity(
            username = "Steve", uuid = "12345678-1234-1234-1234-123456789012",
            accessToken = "t", userType = "msa",
        )
        assertEquals("12345678123412341234123456789012", id.uuidNoDashes)
    }

    @Test
    fun offlineIdentityUsesLegacyUserTypeAndZeroToken() {
        val off = com.flintlauncher.android.core.models.PlayerIdentity.offline("Player")
        assertEquals("Player", off.username)
        assertEquals("0", off.accessToken)
        assertEquals("legacy", off.userType)
    }
}
