package com.flintlauncher.android
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintlauncher.android.ui.state.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
class GameFilesViewModel(private val repository:GameFilesRepository):ViewModel() {
 private val _state=MutableStateFlow(GameFilesUiState(loading=true)); val state=_state.asStateFlow()
 init { refresh() }
 fun setCategory(category:GameFileCategory) { _state.value=_state.value.copy(category=category) }
 fun refresh() { viewModelScope.launch {
  _state.value=_state.value.copy(loading=true)
  val result=runCatching { withContext(Dispatchers.IO) { repository.list() } }
  _state.value=_state.value.copy(loading=false,items=result.getOrDefault(emptyList()).map { e ->
   GameFileItemUi(e.id,e.name,e.file.absolutePath,when(e.kind) {
    GameFileKind.SCREENSHOT -> GameFileCategory.SCREENSHOTS
    GameFileKind.LOG -> GameFileCategory.LOGS
    GameFileKind.CRASH_REPORT -> GameFileCategory.CRASH_REPORTS
   },e.sizeBytes,e.modifiedAt)
  },message=result.exceptionOrNull()?.message)
 } }
 fun delete(path:String) { viewModelScope.launch {
  val entry=withContext(Dispatchers.IO) { repository.list().firstOrNull { it.file.absolutePath==path } }
  val ok=entry!=null && withContext(Dispatchers.IO) { repository.delete(entry) }
  _state.value=_state.value.copy(message=if(ok) "File deleted." else "Could not delete this file.")
  refresh()
 } }
}
