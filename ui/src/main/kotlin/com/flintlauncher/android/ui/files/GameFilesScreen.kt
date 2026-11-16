package com.flintlauncher.android.ui.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flintlauncher.android.ui.state.GameFileCategory
import com.flintlauncher.android.ui.state.GameFilesActions
import com.flintlauncher.android.ui.state.GameFilesUiState
import java.text.DateFormat
import java.util.Date

@Composable
fun GameFilesScreen(
    state: GameFilesUiState,
    actions: GameFilesActions,
    modifier: Modifier = Modifier,
) {
    val visible = state.items.filter {
        state.category == GameFileCategory.ALL || it.category == state.category
    }
    Column(
        modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("GAME FILES", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "All" to GameFileCategory.ALL,
                "Screenshots" to GameFileCategory.SCREENSHOTS,
                "Logs" to GameFileCategory.LOGS,
                "Crashes" to GameFileCategory.CRASH_REPORTS,
            ).forEach { (label, category) ->
                if (state.category == category) {
                    Button(onClick = { actions.onCategoryChange?.invoke(category) }, enabled = actions.onCategoryChange != null) {
                        Text(label)
                    }
                } else {
                    OutlinedButton(onClick = { actions.onCategoryChange?.invoke(category) }, enabled = actions.onCategoryChange != null) {
                        Text(label)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${visible.size} files", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { actions.onRefresh?.invoke() }, enabled = actions.onRefresh != null) {
                Text("Refresh")
            }
        }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        when {
            state.loading -> CircularProgressIndicator()
            visible.isEmpty() -> Text(
                "No files found. Screenshots, logs and crash reports appear here after Minecraft creates them.",
                style = MaterialTheme.typography.bodyMedium,
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visible, key = { it.id }) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(item.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${item.category.name.replace('_', ' ')} · ${formatSize(item.sizeBytes)} · " +
                                    DateFormat.getDateTimeInstance().format(Date(item.modifiedAt)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(item.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { actions.onOpen?.invoke(item.path) }, enabled = actions.onOpen != null) {
                                    Text("Open")
                                }
                                OutlinedButton(onClick = { actions.onShare?.invoke(item.path) }, enabled = actions.onShare != null) {
                                    Text("Share")
                                }
                                OutlinedButton(onClick = { actions.onDelete?.invoke(item.path) }, enabled = actions.onDelete != null) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
