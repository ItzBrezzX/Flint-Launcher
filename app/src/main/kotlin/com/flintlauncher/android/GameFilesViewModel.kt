package com.flintlauncher.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintlauncher.android.ui.state.GameFileCategory
import com.flintlauncher.android.ui.state.GameFileItemUi
import com.flintlauncher.android.ui.state.GameFilesUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GameFilesViewModel(private val repository: GameFilesRepository) : ViewModel() {
    private val _state = MutableStateFlow(GameFilesUiState(loading = true))
    val state = _state.asStateFlow()

    init { refresh() }

    fun setCategory(category: GameFileCategory) {
        _state.value = _state.value.copy(category = category)
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = null)
            val result = runCatching { withContext(Dispatchers.IO) { repository.list() } }
            _state.value = _state.value.copy(
                loading = false,
                items = result.getOrDefault(emptyList()).map { entry ->
                    GameFileItemUi(
                        id = entry.id,
                        name = entry.name,
                        path = entry.file.absolutePath,
                        category = when (entry.kind) {
                            GameFileKind.SCREENSHOT -> GameFileCategory.SCREENSHOTS
                            GameFileKind.LOG -> GameFileCategory.LOGS
                            GameFileKind.CRASH_REPORT -> GameFileCategory.CRASH_REPORTS
                        },
                        sizeBytes = entry.sizeBytes,
                        modifiedAt = entry.modifiedAt,
                    )
                },
                message = result.exceptionOrNull()?.message,
            )
        }
    }

    fun delete(path: String) {
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) { repository.delete(path) }
            _state.value = _state.value.copy(
                message = if (deleted) "File deleted." else "Could not delete this file.",
            )
            refresh()
        }
    }
}
