package com.flintlauncher.android

import com.flintlauncher.android.download.BatchState
import com.flintlauncher.android.download.DownloadProgress
import com.flintlauncher.android.installer.InstallStage
import com.flintlauncher.android.minecraft.manifest.VersionManifestParser
import com.flintlauncher.android.ui.state.InstallPhase
import com.flintlauncher.android.ui.state.VersionFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.util.Locale

class VersionRowsTest {
    // Synthetic manifest: real structure, made-up hashes and URLs.
    private val manifest = VersionManifestParser.parse(
        """
        {"latest":{"release":"26.2","snapshot":"26.3-snapshot-9"},"versions":[
          {"id":"26.3-snapshot-9","type":"snapshot","url":"https://x.invalid/a","time":"2026-08-17T10:00:00+00:00","releaseTime":"2026-08-17T10:00:00+00:00"},
          {"id":"26.2","type":"release","url":"https://x.invalid/b","time":"2026-06-16T09:00:00+00:00","releaseTime":"2026-06-16T09:00:00+00:00"},
          {"id":"1.21.11","type":"release","url":"https://x.invalid/c","time":"2025-12-09T09:00:00+00:00","releaseTime":"2025-12-09T09:00:00+00:00"},
          {"id":"b1.7.3","type":"old_beta","url":"https://x.invalid/d","time":"2011-07-08T00:00:00+00:00","releaseTime":"2011-07-08T00:00:00+00:00"},
          {"id":"a1.0.0","type":"old_alpha","url":"https://x.invalid/e","time":"2010-06-30T00:00:00+00:00","releaseTime":"2010-06-30T00:00:00+00:00"}]}
        """.trimIndent(),
    )

    private fun rows(filter: VersionFilter, query: String = "", newest: Boolean = true, installed: Set<String> = emptySet()) =
        VersionRows.build(manifest, filter, query, newest, { it in installed }, ZoneOffset.UTC, Locale.US)

    @Test
    fun filtersByCategory() {
        assertEquals(listOf("26.2", "1.21.11"), rows(VersionFilter.RELEASE).map { it.id })
        assertEquals(listOf("26.3-snapshot-9"), rows(VersionFilter.SNAPSHOT).map { it.id })
        assertEquals(listOf("b1.7.3", "a1.0.0"), rows(VersionFilter.BETA).map { it.id })
    }

    @Test
    fun sortsNewestOrOldestFirst() {
        assertEquals(listOf("1.21.11", "26.2"), rows(VersionFilter.RELEASE, newest = false).map { it.id })
    }

    @Test
    fun searchIgnoresCaseAndSurroundingSpaces() {
        assertEquals(listOf("1.21.11"), rows(VersionFilter.RELEASE, query = "  1.21 ").map { it.id })
        assertEquals(listOf("26.3-snapshot-9"), rows(VersionFilter.SNAPSHOT, query = "SNAPSHOT").map { it.id })
        assertTrue(rows(VersionFilter.RELEASE, query = "nothing").isEmpty())
    }

    @Test
    fun rowsCarryTypeDateAndInstalledStatus() {
        val row = rows(VersionFilter.RELEASE, installed = setOf("26.2")).first()
        assertEquals("26.2", row.id)
        assertEquals("Release", row.typeLabel)
        assertEquals("16 Jun 2026", row.dateLabel)
        assertTrue(row.installed)
        assertFalse(rows(VersionFilter.RELEASE, installed = setOf("26.2")).last().installed)
    }
}

class InstallPanelMapperTest {
    @Test
    fun idleHasNoPanel() {
        assertNull(InstallPanelMapper.map(InstallState()))
    }

    @Test
    fun runningStateExposesTheRealCounters() {
        val progress = DownloadProgress(
            state = BatchState.RUNNING, totalFiles = 347, completedFiles = 128, downloadedBytes = 1000, totalBytes = 4000,
            speedBytesPerSecond = 50, averageBytesPerSecond = 40, etaSeconds = 60, activeFiles = listOf("client.jar"),
        )
        val panel = InstallPanelMapper.map(
            InstallState("1.21.11", InstallState.Phase.RUNNING, InstallStage.DOWNLOADING_ASSETS, progress),
        )
        assertNotNull(panel)
        assertEquals(InstallPhase.RUNNING, panel!!.phase)
        assertEquals("Downloading assets", panel.stageLabel)
        assertEquals(1000L, panel.downloadedBytes)
        assertEquals(25.0, panel.percent!!, 0.001)
        assertEquals(128, panel.completedFiles)
        assertEquals(347, panel.totalFiles)
        assertEquals("client.jar", panel.activeFile)
        assertFalse(panel.paused)
    }

    @Test
    fun aPausedBatchIsShownAsPaused() {
        val panel = InstallPanelMapper.map(
            InstallState("1.0", InstallState.Phase.RUNNING, InstallStage.DOWNLOADING_ASSETS, DownloadProgress(state = BatchState.PAUSED)),
        )
        assertTrue(panel!!.paused)
    }

    @Test
    fun outcomesMapToTheirPhasesWithMessages() {
        val failed = InstallPanelMapper.map(InstallState("1.0", InstallState.Phase.FAILED, message = "lib.jar: HTTP 404"))
        assertEquals(InstallPhase.FAILED, failed!!.phase)
        assertEquals("lib.jar: HTTP 404", failed.message)
        assertEquals("Preparing\u2026", failed.stageLabel)
        assertEquals(InstallPhase.CANCELLED, InstallPanelMapper.map(InstallState("1.0", InstallState.Phase.CANCELLED))!!.phase)
        assertEquals(InstallPhase.SUCCEEDED, InstallPanelMapper.map(InstallState("1.0", InstallState.Phase.SUCCEEDED))!!.phase)
    }
}
