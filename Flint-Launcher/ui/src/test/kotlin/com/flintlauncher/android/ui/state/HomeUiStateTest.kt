package com.flintlauncher.android.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeUiStateTest {
    private val fabric = InstallationUi("a", "Flint Fabric", "1.21.11", "Fabric", 21)
    private val vanilla = InstallationUi("b", "Vanilla", "26.2", "Vanilla", 25)

    @Test
    fun selectedFollowsSelectedId() {
        val state = HomeUiState(installations = listOf(fabric, vanilla), selectedInstallationId = "b")
        assertEquals(vanilla, state.selected)
    }

    @Test
    fun noSelectionWhenIdIsMissingOrUnknown() {
        assertNull(HomeUiState(installations = listOf(fabric)).selected)
        assertNull(HomeUiState(installations = listOf(fabric), selectedInstallationId = "zzz").selected)
    }

    @Test
    fun summaryShowsVersionLoaderAndJava() {
        assertEquals("1.21.11 \u00B7 Fabric \u00B7 Java 21", fabric.summary)
    }

    @Test
    fun defaultsNeverClaimOnlinePresenceOrServerStatus() {
        val state = HomeUiState()
        assertEquals(FriendsUi.Unavailable, state.friends)
        assertNull(state.account)
    }
}
