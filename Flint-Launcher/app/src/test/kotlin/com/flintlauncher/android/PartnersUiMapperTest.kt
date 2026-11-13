package com.flintlauncher.android

import com.flintlauncher.android.partners.Edition
import com.flintlauncher.android.partners.ServerEntry
import com.flintlauncher.android.partners.ServerStatusResult
import com.flintlauncher.android.partners.ServerWithStatus
import com.flintlauncher.android.ui.state.ServerEdition
import com.flintlauncher.android.ui.state.ServerStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class PartnersUiMapperTest {
    private val entry = ServerEntry("Test", "mc.example.invalid", Edition.JAVA, "https://example.invalid")

    @Test
    fun onlineKeepsTheRealPlayerCount() {
        val ui = ServerWithStatus(entry, ServerStatusResult.Online(players = 42, max = 100)).toUi()
        assertEquals(ServerStatus.Online(42), ui.status)
        assertEquals("https://example.invalid", ui.website)
        assertEquals(ServerEdition.JAVA, ui.edition)
    }

    @Test
    fun missingPlayerCountStaysMissing() {
        val ui = ServerWithStatus(entry, ServerStatusResult.Online(players = null, max = null)).toUi()
        assertEquals(ServerStatus.Online(null), ui.status)
    }

    @Test
    fun unknownNeverBecomesOnlineOrOffline() {
        val ui = ServerWithStatus(entry, ServerStatusResult.Unknown("Rate limited")).toUi()
        assertEquals(ServerStatus.Unknown, ui.status)
    }

    @Test
    fun offlineAndBedrockMap() {
        val ui = ServerWithStatus(entry.copy(edition = Edition.BEDROCK), ServerStatusResult.Offline).toUi()
        assertEquals(ServerStatus.Offline, ui.status)
        assertEquals(ServerEdition.BEDROCK, ui.edition)
    }
}
