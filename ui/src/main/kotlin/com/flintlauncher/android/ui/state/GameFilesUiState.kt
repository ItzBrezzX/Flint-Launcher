package com.flintlauncher.android.ui.state
enum class GameFileCategory { ALL, SCREENSHOTS, LOGS, CRASH_REPORTS }
data class GameFileItemUi(val id:String,val name:String,val path:String,val category:GameFileCategory,val sizeBytes:Long,val modifiedAt:Long)
data class GameFilesUiState(val items:List<GameFileItemUi> = emptyList(),val category:GameFileCategory = GameFileCategory.ALL,val loading:Boolean = false,val message:String? = null)
data class GameFilesActions(val onRefresh:(()->Unit)?=null,val onCategoryChange:((GameFileCategory)->Unit)?=null,val onShare:((String)->Unit)?=null,val onDelete:((String)->Unit)?=null)
