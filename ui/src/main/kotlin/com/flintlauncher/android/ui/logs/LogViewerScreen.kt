package com.flintlauncher.android.ui.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flintlauncher.android.GameLogReader
import com.flintlauncher.android.ui.state.LogViewerActions
import com.flintlauncher.android.ui.state.LogViewerUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Bg = Color(0xFF07110B)
private val Panel = Color(0xFF101B14)
private val Accent = Color(0xFF35D12C)
private val MainText = Color(0xFFE8F0E8)
private val Muted = Color(0xFF8A9A8E)
private val Border = Color(0xFF1A2A1E)

private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

/** In-app log viewer. Lists plain + gzipped logs (rotated) and shows a bounded tail. */
@Composable
fun LogViewerScreen(state: LogViewerUiState, actions: LogViewerActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(Bg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Game logs", color = MainText, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { actions.onRefresh?.invoke() }, enabled = actions.onRefresh != null) { Text("Refresh") }
            TextButton(onClick = { actions.onDismiss?.invoke() }, enabled = actions.onDismiss != null) { Text("Close") }
        }
        if (state.loading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
        } else if (state.logs.isEmpty()) {
            Text("No log files found. Run Minecraft once to generate logs.", color = Muted, fontSize = 13.sp)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.logs.take(6).forEach { log ->
                    val selected = log.name == state.selectedLogName
                    Text(
                        log.name.removeSuffix(".gz").take(14),
                        color = if (selected) Bg else MainText,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Accent else Panel)
                            .border(1.dp, Border, RoundedCornerShape(8.dp))
                            .clickable(enabled = actions.onSelectLog != null) { actions.onSelectLog?.invoke(log.name) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        maxLines = 1,
                    )
                }
            }
            state.selectedLogName?.let { name ->
                state.logs.firstOrNull { it.name == name }?.let { log ->
                    Text(
                        "${log.name} · ${if (log.compressed) "gzip" else "plain"} · ${dateFormat.format(Date(log.lastModifiedMillis))} · ${log.sizeBytes} bytes",
                        color = Muted, fontSize = 11.sp,
                    )
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Panel)
                    .border(1.dp, Border, RoundedCornerShape(10.dp))
                    .padding(10.dp),
            ) {
                Text(
                    state.content.ifBlank { "(select a log to view)" },
                    color = MainText, fontSize = 10.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { actions.onCopy?.invoke() }, enabled = actions.onCopy != null && state.content.isNotBlank()) {
                    Text("Copy log")
                }
            }
        }
        state.message?.let { Text(it, color = Color(0xFFFFC27A), fontSize = 12.sp) }
    }
}
