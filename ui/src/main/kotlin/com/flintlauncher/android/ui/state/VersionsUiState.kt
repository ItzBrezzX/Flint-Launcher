package com.flintlauncher.android.ui.state

import com.flintlauncher.android.installer.InstallStage

enum class VersionFilter(val label: String) {
    RELEASE("Release"),
    SNAPSHOT("Snapshot"),
    BETA("Beta"),
}

data class VersionRow(
    val id: String,
    val typeLabel: String,
    val dateLabel: String,
    val installed: Boolean,
    val readyToPlay: Boolean = false,
)

enum class InstallPhase { RUNNING, SUCCEEDED, FAILED, CANCELLED }

/**
 * Everything the dedicated real-time download / installation UI shows.
 * Every numeric value must come from real DownloadManager / installer counters.
 * Never hardcode sizes, speeds, or progress.
 */
data class InstallPanelState(
    val versionId: String,
    val versionTypeLabel: String = "Release",
    val phase: InstallPhase,
    val stage: InstallStage = InstallStage.RESOLVING_METADATA,
    val stageLabel: String = InstallStage.RESOLVING_METADATA.label,
    val downloadedBytes: Long = 0,
    /** Null when at least one required file has unknown size. */
    val totalBytes: Long? = null,
    val percent: Double? = null,
    val speedBytesPerSecond: Long = 0,
    val averageBytesPerSecond: Long = 0,
    val etaSeconds: Long? = null,
    val completedFiles: Int = 0,
    val totalFiles: Int = 0,
    val failedFiles: Int = 0,
    val activeFile: String? = null,
    val paused: Boolean = false,
    val message: String? = null,
    /** True only after full verification + profile registration. */
    val readyToPlay: Boolean = false,
    val knownSizeFiles: Int = 0,
    val unknownSizeFiles: Int = 0,
)

data class VersionsUiState(
    val catalog: Loadable<List<VersionRow>> = Loadable.Loading,
    val query: String = "",
    val filter: VersionFilter = VersionFilter.RELEASE,
    val newestFirst: Boolean = true,
    val install: InstallPanelState? = null,
)

/** A null callback means the capability does not exist; the control is shown disabled. */
data class VersionsActions(
    val onQueryChange: ((String) -> Unit)? = null,
    val onFilterChange: ((VersionFilter) -> Unit)? = null,
    val onToggleSort: (() -> Unit)? = null,
    val onInstall: ((String) -> Unit)? = null,
    val onPause: (() -> Unit)? = null,
    val onResume: (() -> Unit)? = null,
    val onCancel: (() -> Unit)? = null,
    val onRetryCatalog: (() -> Unit)? = null,
    val onDismissResult: (() -> Unit)? = null,
    val onPlayNow: ((String) -> Unit)? = null,
    val onViewInstallation: ((String) -> Unit)? = null,
    val onManageMods: ((String) -> Unit)? = null,
    val onRepair: ((String) -> Unit)? = null,
)

sealed class Loadable<out T> {
    data object Loading : Loadable<Nothing>()
    data class Ready<T>(val value: T) : Loadable<T>()
    data class Error(val message: String) : Loadable<Nothing>()
}
