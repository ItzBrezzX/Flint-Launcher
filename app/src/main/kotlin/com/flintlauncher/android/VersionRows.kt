package com.flintlauncher.android

import com.flintlauncher.android.download.BatchState
import com.flintlauncher.android.installer.InstallStage
import com.flintlauncher.android.minecraft.manifest.VersionManifest
import com.flintlauncher.android.minecraft.manifest.VersionType
import com.flintlauncher.android.ui.state.InstallPanelState
import com.flintlauncher.android.ui.state.InstallPhase
import com.flintlauncher.android.ui.state.VersionFilter
import com.flintlauncher.android.ui.state.VersionRow
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object VersionRows {
    fun build(
        manifest: VersionManifest,
        filter: VersionFilter,
        query: String,
        newestFirst: Boolean,
        isInstalled: (String) -> Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): List<VersionRow> {
        val types = when (filter) {
            VersionFilter.RELEASE -> arrayOf(VersionType.RELEASE)
            VersionFilter.SNAPSHOT -> arrayOf(VersionType.SNAPSHOT)
            VersionFilter.BETA -> arrayOf(VersionType.OLD_BETA, VersionType.OLD_ALPHA)
        }
        val needle = query.trim()
        val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", locale).withZone(zone)
        val ordered = if (newestFirst) manifest.newestFirst() else manifest.oldestFirst()
        return ordered
            .filter { it.type in types }
            .filter { needle.isEmpty() || it.id.contains(needle, ignoreCase = true) }
            .map {
                VersionRow(
                    id = it.id,
                    typeLabel = typeLabel(it.type),
                    dateLabel = formatter.format(it.releaseInstant),
                    installed = isInstalled(it.id),
                )
            }
    }

    private fun typeLabel(type: VersionType) = when (type) {
        VersionType.RELEASE -> "Release"
        VersionType.SNAPSHOT -> "Snapshot"
        VersionType.OLD_BETA -> "Beta"
        VersionType.OLD_ALPHA -> "Alpha"
        VersionType.UNKNOWN -> "Other"
    }
}

object InstallPanelMapper {
    fun map(state: InstallState): InstallPanelState? {
        val id = state.versionId ?: return null
        if (state.phase == InstallState.Phase.IDLE) return null
        val p = state.progress
        return InstallPanelState(
            versionId = id,
            phase = when (state.phase) {
                InstallState.Phase.SUCCEEDED -> InstallPhase.SUCCEEDED
                InstallState.Phase.FAILED -> InstallPhase.FAILED
                InstallState.Phase.CANCELLED -> InstallPhase.CANCELLED
                else -> InstallPhase.RUNNING
            },
            stage = state.stage ?: InstallStage.RESOLVING_METADATA,
            stageLabel = state.stage?.label ?: "Preparing\u2026",
            readyToPlay = state.phase == InstallState.Phase.SUCCEEDED,
            knownSizeFiles = if (p.totalBytes != null) p.totalFiles else 0,
            unknownSizeFiles = if (p.totalBytes == null) p.totalFiles else 0,
            downloadedBytes = p.downloadedBytes,
            totalBytes = p.totalBytes,
            percent = p.percent,
            speedBytesPerSecond = p.speedBytesPerSecond,
            averageBytesPerSecond = p.averageBytesPerSecond,
            etaSeconds = p.etaSeconds,
            completedFiles = p.completedFiles,
            totalFiles = p.totalFiles,
            failedFiles = p.failedFiles,
            activeFile = p.activeFiles.firstOrNull(),
            paused = p.state == BatchState.PAUSED,
            message = state.message,
        )
    }
}
