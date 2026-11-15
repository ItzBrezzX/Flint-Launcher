package com.flintlauncher.android.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flintlauncher.android.ui.state.ProfileImportExportActions
import com.flintlauncher.android.ui.state.ProfileImportExportUiState

private val Bg = Color(0xFF07110B)
private val Panel = Color(0xFF101B14)
private val Accent = Color(0xFF35D12C)
private val MainText = Color(0xFFE8F0E8)
private val Muted = Color(0xFF8A9A8E)
private val Border = Color(0xFF1A2A1E)

/**
 * Profile import/export UI. The JSON format and risky-argument filtering live in
 * :profiles ProfileRepository (logic shard); this screen only drives export -> copy
 * and paste -> import, and reports dropped risky arguments honestly.
 */
@Composable
fun ProfileImportExportScreen(
    state: ProfileImportExportUiState,
    actions: ProfileImportExportActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().background(Bg).padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Profiles: import / export", color = MainText, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { actions.onDismiss?.invoke() }, enabled = actions.onDismiss != null) { Text("Close") }
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel)
                .border(1.dp, Border, RoundedCornerShape(12.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Export a profile", color = MainText, fontWeight = FontWeight.SemiBold)
            if (state.profiles.isEmpty()) {
                Text("No profiles to export.", color = Muted, fontSize = 12.sp)
            } else {
                state.profiles.forEach { name ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, color = MainText, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { actions.onExport?.invoke(name) }, enabled = actions.onExport != null) {
                            Text("Export", fontSize = 11.sp)
                        }
                    }
                }
            }
            state.exportedJson?.let { json ->
                Text("Exported JSON (${json.length} chars). Copy it to share or back up.", color = Muted, fontSize = 11.sp)
                Text(
                    json.take(400) + if (json.length > 400) "…" else "",
                    color = MainText, fontSize = 9.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Bg)
                        .padding(8.dp),
                )
                Button(
                    onClick = { actions.onCopyExport?.invoke() },
                    enabled = actions.onCopyExport != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Copy exported JSON") }
            }
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel)
                .border(1.dp, Border, RoundedCornerShape(12.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Import a profile", color = MainText, fontWeight = FontWeight.SemiBold)
            Text(
                "Paste an exported Flint profile JSON. Risky JVM arguments (javaagent, bootclasspath, etc.) are dropped and reported — never applied.",
                color = Muted, fontSize = 11.sp,
            )
            OutlinedTextField(
                value = state.importText,
                onValueChange = { actions.onImportTextChange?.invoke(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Paste profile JSON") },
                minLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
            )
            Button(
                onClick = { actions.onImport?.invoke() },
                enabled = actions.onImport != null && state.importText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Import") }
            state.importResult?.let { Text(it, color = Accent, fontSize = 12.sp) }
            if (state.droppedArgs.isNotEmpty()) {
                Text("Dropped risky arguments:", color = Color(0xFFFFC27A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                state.droppedArgs.forEach { Text("  $it", color = Color(0xFFFFC27A), fontSize = 10.sp) }
            }
            state.message?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp) }
        }
    }
}
