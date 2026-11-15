package com.flintlauncher.android.ui.worlds

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.flintlauncher.android.WorldManager
import com.flintlauncher.android.ui.state.WorldManagerActions
import com.flintlauncher.android.ui.state.WorldManagerUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Bg = Color(0xFF07110B)
private val Panel = Color(0xFF101B14)
private val Accent = Color(0xFF35D12C)
private val MainText = Color(0xFFE8F0E8)
private val Muted = Color(0xFF8A9A8E)
private val Border = Color(0xFF1A2A1E)
private val Danger = Color(0xFFFF8A80)

private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

/** World/save management: list worlds, back up to zip, restore from zip, delete backups. */
@Composable
fun WorldManagerScreen(state: WorldManagerUiState, actions: WorldManagerActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(Bg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Worlds & saves", color = MainText, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { actions.onRefresh?.invoke() }, enabled = actions.onRefresh != null && !state.busy) { Text("Refresh") }
            TextButton(onClick = { actions.onDismiss?.invoke() }, enabled = actions.onDismiss != null) { Text("Close") }
        }
        if (state.busy) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Accent)
                Text("Working…", color = Muted, fontSize = 12.sp)
            }
        }
        Text("Saved worlds", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        if (state.worlds.isEmpty()) {
            Text("No worlds found. Play Minecraft and create a world first.", color = Muted, fontSize = 12.sp)
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.worlds) { world ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Panel)
                            .border(1.dp, Border, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(world.name, color = MainText, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            Text(
                                "${dateFormat.format(Date(world.lastModifiedMillis))} · ${formatMb(world.sizeBytes)}",
                                color = Muted, fontSize = 10.sp,
                            )
                        }
                        Button(
                            onClick = { actions.onBackup?.invoke(world.name) },
                            enabled = actions.onBackup != null && !state.busy,
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg),
                        ) { Text("Backup", fontSize = 11.sp) }
                    }
                }
            }
        }
        Text("Backups (zip)", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        if (state.backups.isEmpty()) {
            Text("No backups yet.", color = Muted, fontSize = 12.sp)
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.backups) { backup ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Panel)
                            .border(1.dp, Border, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(backup.name, color = MainText, fontSize = 12.sp, maxLines = 1)
                            Text(
                                "${dateFormat.format(Date(backup.createdMillis))} · ${formatMb(backup.sizeBytes)}",
                                color = Muted, fontSize = 10.sp,
                            )
                        }
                        OutlinedButton(onClick = { actions.onRestore?.invoke(backup.name) }, enabled = actions.onRestore != null && !state.busy) {
                            Text("Restore", fontSize = 11.sp)
                        }
                        TextButton(onClick = { actions.onDeleteBackup?.invoke(backup.name) }, enabled = actions.onDeleteBackup != null && !state.busy) {
                            Text("Delete", color = Danger, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        state.message?.let { Text(it, color = Color(0xFFFFC27A), fontSize = 12.sp) }
    }
}

private fun formatMb(bytes: Long): String =
    if (bytes < 1024 * 1024) "${bytes / 1024} KB" else "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
