package com.flintlauncher.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintlauncher.android.ui.controls.ControlLayout
import com.flintlauncher.android.ui.controls.ControlPresets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ControlEditorUiState(
    val layout: ControlLayout = ControlPresets.defaultLayout(),
    val loaded: Boolean = false,
    val message: String? = null,
)

class ControlEditorViewModel(private val repository: ControlLayoutRepository) : ViewModel() {
    private val _state = MutableStateFlow(ControlEditorUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val layout = withContext(Dispatchers.IO) { repository.load() }
            _state.value = ControlEditorUiState(layout = layout, loaded = true)
        }
    }

    fun save(layout: ControlLayout) {
        viewModelScope.launch {
            _state.value = _state.value.copy(message = null)
            val result = runCatching { withContext(Dispatchers.IO) { repository.save(layout) } }
            _state.value = _state.value.copy(
                layout = if (result.isSuccess) layout else _state.value.layout,
                message = result.exceptionOrNull()?.message ?: "Touch control layout saved.",
            )
        }
    }

    fun dismissMessage() { _state.value = _state.value.copy(message = null) }
}
