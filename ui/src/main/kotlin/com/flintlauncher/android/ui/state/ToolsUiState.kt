package com.flintlauncher.android.ui.state

import com.flintlauncher.android.GameLogReader
import com.flintlauncher.android.WorldManager

data class LogViewerUiState(
    val logs: List<GameLogReader.LogFile> = emptyList(),
    val selectedLogName: String? = null,
    val content: String = "",
    val loading: Boolean = false,
    val message: String? = null,
)

data class LogViewerActions(
    val onSelectLog: ((String) -> Unit)? = null,
    val onRefresh: (() -> Unit)? = null,
    val onCopy: (() -> Unit)? = null,
    val onDismiss: (() -> Unit)? = null,
)

data class WorldManagerUiState(
    val worlds: List<WorldManager.WorldSave> = emptyList(),
    val backups: List<WorldManager.BackupFile> = emptyList(),
    val message: String? = null,
    val busy: Boolean = false,
)

data class WorldManagerActions(
    val onBackup: ((String) -> Unit)? = null,
    val onRestore: ((String) -> Unit)? = null,
    val onDeleteBackup: ((String) -> Unit)? = null,
    val onRefresh: (() -> Unit)? = null,
    val onDismiss: (() -> Unit)? = null,
)

data class ProfileImportExportUiState(
    val profiles: List<String> = emptyList(),
    val exportedJson: String? = null,
    val importText: String = "",
    val importResult: String? = null,
    val droppedArgs: List<String> = emptyList(),
    val message: String? = null,
)

data class ProfileImportExportActions(
    val onExport: ((String) -> Unit)? = null,
    val onImportTextChange: ((String) -> Unit)? = null,
    val onImport: (() -> Unit)? = null,
    val onCopyExport: (() -> Unit)? = null,
    val onDismiss: (() -> Unit)? = null,
)
