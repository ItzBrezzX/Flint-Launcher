package com.flintlauncher.android.ui.files
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flintlauncher.android.ui.state.*
import java.text.DateFormat
import java.util.Date
@Composable fun GameFilesScreen(state:GameFilesUiState,actions:GameFilesActions,modifier:Modifier=Modifier) {
 val visible=state.items.filter { state.category==GameFileCategory.ALL || it.category==state.category }
 Column(modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
   listOf("All" to GameFileCategory.ALL,"Screenshots" to GameFileCategory.SCREENSHOTS,"Logs" to GameFileCategory.LOGS,"Crashes" to GameFileCategory.CRASH_REPORTS).forEach { (label,cat) ->
    if(state.category==cat) Button(onClick={actions.onCategoryChange?.invoke(cat)}) { Text(label) }
    else OutlinedButton(onClick={actions.onCategoryChange?.invoke(cat)}) { Text(label) }
   }
  }
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
   Text("${visible.size} files",style=MaterialTheme.typography.titleMedium)
   OutlinedButton(onClick={actions.onRefresh?.invoke()}) { Text("Refresh") }
  }
  state.message?.let { Text(it,color=MaterialTheme.colorScheme.error) }
  if(state.loading) CircularProgressIndicator()
  else if(visible.isEmpty()) Text("No files found. Screenshots, logs and crash reports appear here after Minecraft creates them.")
  else LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
   items(visible,key={it.id}) { item ->
    Card(Modifier.fillMaxWidth()) {
     Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
      Text(item.name,style=MaterialTheme.typography.titleSmall)
      Text("${item.category.name.replace('_',' ')} · ${formatSize(item.sizeBytes)} · ${DateFormat.getDateTimeInstance().format(Date(item.modifiedAt))}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
      Text(item.path,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
      Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
       OutlinedButton(onClick={actions.onOpen?.invoke(item.path)},enabled=actions.onOpen!=null) { Text("Open") }\n       OutlinedButton(onClick={actions.onShare?.invoke(item.path)},enabled=actions.onShare!=null) { Text("Share") }
       OutlinedButton(onClick={actions.onDelete?.invoke(item.path)},enabled=actions.onDelete!=null) { Text("Delete") }
      }
     }
    }
   }
  }
 }
}
private fun formatSize(bytes:Long)=when { bytes<1024 -> "${bytes} B"; bytes<1024*1024 -> "%.1f KB".format(bytes/1024.0); else -> "%.1f MB".format(bytes/(1024.0*1024.0)) }
