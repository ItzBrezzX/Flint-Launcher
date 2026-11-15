package com.flintlauncher.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintlauncher.android.profiles.ProfileRepository
import com.flintlauncher.android.ui.state.LogViewerUiState
import com.flintlauncher.android.ui.state.ProfileImportExportUiState
import com.flintlauncher.android.ui.state.WorldManagerUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Drives the in-app tools: log viewer (gzip-aware), world backup/restore (zip),
 * profile import/export (:profiles owns the JSON format + risky-arg filter).
 * Operates on the most recently used profile game directory; if none exists the
 * screens honestly show empty lists.
 */
class ToolsViewModel(
    private val gameRoot: File,
    private val profileRepository: ProfileRepository,
    private val backupsDir: File,
) : ViewModel() {

    private val _logState = MutableStateFlow(LogViewerUiState())
    val logState: StateFlow<LogViewerUiState> = _logState.asStateFlow()

    private val _worldState = MutableStateFlow(WorldManagerUiState())
    val worldState: StateFlow<WorldManagerUiState> = _worldState.asStateFlow()

    private val _profileState = MutableStateFlow(ProfileImportExportUiState())
    val profileState: StateFlow<ProfileImportExportUiState> = _profileState.asStateFlow()

    private var currentLogContent: String = ""

    /** Most recently modified profile game dir, or null. Saves/logs live per-profile. */
    private fun activeGameDir(): File? {
        val profilesDir = File(gameRoot, "profiles")
        if (!profilesDir.isDirectory) return null
        return profilesDir.listFiles { it.isDirectory }
            ?.maxByOrNull { it.lastModified() }
    }

    fun refreshLogs() {
        viewModelScope.launch {
            _logState.update { it.copy(loading = true, message = null) }
            val logs = withContext(Dispatchers.IO) {
                activeGameDir()?.let { GameLogReader.listLogs(it) } ?: emptyList()
            }
            _logState.update {
                it.copy(
                    loading = false,
                    logs = logs,
                    selectedLogName = logs.firstOrNull()?.name,
                    content = logs.firstOrNull()?.let { f -> GameLogReader.tailLines(f) }.orEmpty(),
                )
            }
            currentLogContent = _logState.value.content
        }
    }

    fun selectLog(name: String) {
        val log = _logState.value.logs.firstOrNull { it.name == name } ?: return
        viewModelScope.launch {
            val content = withContext(Dispatchers.IO) { GameLogReader.tailLines(log) }
            currentLogContent = content
            _logState.update { it.copy(selectedLogName = name, content = content) }
        }
    }

    fun currentLogText(): String = currentLogContent

    fun refreshWorlds() {
        viewModelScope.launch {
            val (worlds, backups) = withContext(Dispatchers.IO) {
                val gameDir = activeGameDir()
                val saves = gameDir?.let { File(it, "saves") }?.let { WorldManager.listWorlds(it) } ?: emptyList()
                val bk = WorldManager.listBackups(backupsDir)
                saves to bk
            }
            _worldState.update { it.copy(worlds = worlds, backups = backups, message = null) }
        }
    }

    fun backupWorld(worldName: String) {
        viewModelScope.launch {
            _worldState.update { it.copy(busy = true, message = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val gameDir = activeGameDir() ?: error("No game directory found.")
                    WorldManager.backup(File(gameDir, "saves"), backupsDir, worldName)
                }
            }
            _worldState.update {
                it.copy(
                    busy = false,
                    message = result.fold(
                        onSuccess = { "Backed up to ${it.name}" },
                        onFailure = { e -> "Backup failed: ${e.message ?: "unknown error"}" },
                    ),
                )
            }
            refreshWorlds()
        }
    }

    fun restoreBackup(backupName: String) {
        viewModelScope.launch {
            _worldState.update { it.copy(busy = true, message = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val gameDir = activeGameDir() ?: error("No game directory found.")
                    val backup = File(backupsDir, backupName)
                    WorldManager.restore(backup, File(gameDir, "saves"))
                }
            }
            _worldState.update {
                it.copy(
                    busy = false,
                    message = result.fold(
                        onSuccess = { "Restored world \"${it.name}\"" },
                        onFailure = { e -> "Restore failed: ${e.message ?: "unknown error"}" },
                    ),
                )
            }
            refreshWorlds()
        }
    }

    fun deleteBackup(backupName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { WorldManager.deleteBackup(File(backupsDir, backupName)) }
            refreshWorlds()
        }
    }

    fun refreshProfiles() {
        _profileState.update { it.copy(profiles = profileRepository.list().map { p -> "${p.name} (${p.minecraftVersion})" }) }
    }

    fun exportProfile(displayName: String) {
        val name = displayName.substringBefore(" (")
        val profile = profileRepository.list().firstOrNull { it.name == name }
        if (profile == null) {
            _profileState.update { it.copy(message = "Profile not found.") }
            return
        }
        val json = profileRepository.export(profile.id)
        _profileState.update { it.copy(exportedJson = json, message = null) }
    }

    fun exportedJson(): String? = _profileState.value.exportedJson

    fun setImportText(text: String) {
        _profileState.update { it.copy(importText = text, message = null, importResult = null, droppedArgs = emptyList()) }
    }

    fun importProfile() {
        val text = _profileState.value.importText
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { profileRepository.importProfile(text) }
            }
            _profileState.update {
                result.fold(
                    onSuccess = { imported ->
                        it.copy(
                            importResult = "Imported \"${imported.profile.name}\".",
                            droppedArgs = imported.droppedArguments,
                            importText = "",
                            message = null,
                            profiles = profileRepository.list().map { p -> "${p.name} (${p.minecraftVersion})" },
                        )
                    },
                    onFailure = { e -> it.copy(message = "Import failed: ${e.message ?: "invalid JSON"}") },
                )
            }
        }
    }
}
