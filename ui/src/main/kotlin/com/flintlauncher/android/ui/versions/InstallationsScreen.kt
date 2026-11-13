package com.flintlauncher.android.ui.versions

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import com.flintlauncher.android.ui.state.InstallPhase
import com.flintlauncher.android.ui.state.Loadable
import com.flintlauncher.android.ui.state.VersionFilter
import com.flintlauncher.android.ui.state.VersionRow
import com.flintlauncher.android.ui.state.VersionsActions
import com.flintlauncher.android.ui.state.VersionsUiState
import com.flintlauncher.android.ui.theme.FlintColors
import com.flintlauncher.android.ui.versions.InstallProgressScreen

private val CardShape = RoundedCornerShape(16.dp)

/** Landscape-first version catalog with full-width controls and two-column results. */
@Composable
fun InstallationsScreen(
    state: VersionsUiState,
    actions: VersionsActions,
    modifier: Modifier = Modifier,
) {
    val busy = state.install?.phase == InstallPhase.RUNNING
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wide = maxWidth >= 540.dp
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (wide) 2 else 1),
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "INSTANCES",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                )
            }
            state.install?.let { panel ->
                item(span = { GridItemSpan(maxLineSpan) }) { DownloadPanel(panel, actions) }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { actions.onQueryChange?.invoke(it) },
                    enabled = actions.onQueryChange != null,
                    label = { Text("Search versions") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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
                Loadable.Loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = FlintColors.Ember)
                        Text("Loading versions…", color = FlintColors.TextMuted)
                    }
                }
                is Loadable.Error -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(catalog.message, color = FlintColors.TextMuted)
                        TextButton(onClick = { actions.onRetryCatalog?.invoke() }, enabled = actions.onRetryCatalog != null) { Text("Retry") }
                    }
                }
                is Loadable.Content -> if (catalog.value.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { Text("No versions match.", color = FlintColors.TextMuted) }
                } else {
                    items(catalog.value, key = { it.id }) { row -> VersionItem(row, busy, actions) }
                }
            }
        }
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
            Text("${row.typeLabel} · ${row.dateLabel}", style = MaterialTheme.typography.labelSmall, color = FlintColors.TextMuted)
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
    InstallProgressScreen(panel = panel, actions = actions)
}
