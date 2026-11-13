package com.flintlauncher.android.ui.versions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flintlauncher.android.ui.state.InstallPanelState
import com.flintlauncher.android.ui.versions.InstallProgressScreen
import com.flintlauncher.android.ui.state.InstallPhase
import com.flintlauncher.android.ui.state.Loadable
import com.flintlauncher.android.ui.state.VersionFilter
import com.flintlauncher.android.ui.state.VersionRow
import com.flintlauncher.android.ui.state.VersionsActions
import com.flintlauncher.android.ui.state.VersionsUiState
import com.flintlauncher.android.ui.theme.FlintColors

private val CardShape = RoundedCornerShape(16.dp)

/** Version catalog (Release / Snapshot / Beta), search, sort, install, and the Download Manager panel. */
@Composable
fun InstallationsScreen(
    state: VersionsUiState,
    actions: VersionsActions,
    modifier: Modifier = Modifier,
) {
    val busy = state.install?.phase == InstallPhase.RUNNING
    LazyColumn(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "INSTALLATIONS",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        state.install?.let { panel -> item { DownloadPanel(panel, actions) } }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = { actions.onQueryChange?.invoke(it) },
                enabled = actions.onQueryChange != null,
                label = { Text("Search versions") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VersionFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { actions.onFilterChange?.invoke(filter) },
                        enabled = actions.onFilterChange != null,
                        label = { Text(filter.label) },
                    )
                }
                TextButton(onClick = { actions.onToggleSort?.invoke() }, enabled = actions.onToggleSort != null) {
                    Text(if (state.newestFirst) "Newest" else "Oldest")
                }
            }
        }
        when (val catalog = state.catalog) {
            Loadable.Loading -> item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Loading versions\u2026", color = FlintColors.TextMuted)
                }
            }
            is Loadable.Error -> item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(catalog.message, color = FlintColors.TextMuted)
                    TextButton(onClick = { actions.onRetryCatalog?.invoke() }, enabled = actions.onRetryCatalog != null) { Text("Retry") }
                }
            }
            is Loadable.Content -> if (catalog.value.isEmpty()) {
                item { Text("No versions match.", color = FlintColors.TextMuted) }
            } else {
                items(catalog.value, key = { it.id }) { row -> VersionItem(row, busy, actions) }
            }
        }
        item { Text(" ", style = MaterialTheme.typography.labelSmall) } // breathing room above the nav bar
    }
}

@Composable
private fun VersionItem(row: VersionRow, busy: Boolean, actions: VersionsActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(FlintColors.Panel)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.id, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${row.typeLabel} \u00B7 ${row.dateLabel}", style = MaterialTheme.typography.labelSmall, color = FlintColors.TextMuted)
        }
        if (row.installed) {
            Text("Installed", style = MaterialTheme.typography.labelMedium, color = FlintColors.StatusOnline, modifier = Modifier.padding(end = 8.dp))
            OutlinedButton(onClick = { actions.onInstall?.invoke(row.id) }, enabled = !busy && actions.onInstall != null) { Text("Repair") }
        } else {
            Button(
                onClick = { actions.onInstall?.invoke(row.id) },
                enabled = !busy && actions.onInstall != null,
                colors = ButtonDefaults.buttonColors(containerColor = FlintColors.Ember, contentColor = FlintColors.OnEmber),
            ) { Text("Install") }
        }
    }
}

@Composable
private fun DownloadPanel(panel: InstallPanelState, actions: VersionsActions) {
    // Full dedicated UI for the active installation
    InstallProgressScreen(panel = panel, actions = actions)
    return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(FlintColors.PanelRaised)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("FLINT DOWNLOAD MANAGER", style = MaterialTheme.typography.labelMedium, color = FlintColors.Ember, fontWeight = FontWeight.Black)
        Text("Minecraft ${panel.versionId}", style = MaterialTheme.typography.titleMedium)
        when (panel.phase) {
            InstallPhase.RUNNING -> RunningDetails(panel, actions)
            InstallPhase.SUCCEEDED -> {
                Text(panel.message ?: "Installed", color = FlintColors.StatusOnline)
                TextButton(onClick = { actions.onDismissResult?.invoke() }, enabled = actions.onDismissResult != null) { Text("Done") }
            }
            InstallPhase.FAILED, InstallPhase.CANCELLED -> {
                Text(panel.message ?: "Stopped", color = FlintColors.StatusOffline)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (panel.phase == InstallPhase.FAILED) {
                        Button(
                            onClick = { actions.onInstall?.invoke(panel.versionId) },
                            enabled = actions.onInstall != null,
                            colors = ButtonDefaults.buttonColors(containerColor = FlintColors.Ember, contentColor = FlintColors.OnEmber),
                        ) { Text("Retry") }
                    }
                    TextButton(onClick = { actions.onDismissResult?.invoke() }, enabled = actions.onDismissResult != null) { Text("Dismiss") }
                }
            }
        }
    }
}

@Composable
private fun RunningDetails(panel: InstallPanelState, actions: VersionsActions) {
    val total = panel.totalBytes?.let { Formatters.bytes(it) } ?: "unknown"
    Text(panel.stageLabel, style = MaterialTheme.typography.labelMedium, color = FlintColors.TextMuted)
    Text("Downloaded: ${Formatters.bytes(panel.downloadedBytes)} / $total")
    Text("Progress: ${Formatters.percent(panel.percent)}")
    if (panel.paused) {
        Text("Paused", color = FlintColors.Ember)
    } else {
        Text("Speed: ${Formatters.speed(panel.speedBytesPerSecond)}  \u00B7  Average: ${Formatters.speed(panel.averageBytesPerSecond)}")
        Text("Remaining: ${Formatters.duration(panel.etaSeconds)}")
    }
    Text("Files: ${panel.completedFiles} / ${panel.totalFiles}" + if (panel.failedFiles > 0) "  \u00B7  ${panel.failedFiles} failed" else "")
    panel.activeFile?.let {
        Text(it, style = MaterialTheme.typography.labelSmall, color = FlintColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val fraction = panel.percent?.let { (it / 100.0).toFloat().coerceIn(0f, 1f) }
    if (fraction != null) {
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(), color = FlintColors.Ember)
    } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = FlintColors.Ember)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (panel.paused) {
            Button(
                onClick = { actions.onResume?.invoke() },
                enabled = actions.onResume != null,
                colors = ButtonDefaults.buttonColors(containerColor = FlintColors.Ember, contentColor = FlintColors.OnEmber),
            ) { Text("RESUME") }
        } else {
            OutlinedButton(onClick = { actions.onPause?.invoke() }, enabled = actions.onPause != null) { Text("PAUSE") }
        }
        OutlinedButton(onClick = { actions.onCancel?.invoke() }, enabled = actions.onCancel != null) { Text("CANCEL") }
    }
}
