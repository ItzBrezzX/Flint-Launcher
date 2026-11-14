package com.flintlauncher.android.ui.mods

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flintlauncher.android.ui.state.*

private val FlintGreen = Color(0xFF35D12C)
private val Screen = Color(0xFF07110B)
private val Panel = Color(0xFF101B14)
private val Muted = Color(0xFF9AA99D)

@Composable
fun ModsScreen(state: ModsUiState, actions: ModsActions, modifier: Modifier = Modifier) {
    var query by remember(state.query) { mutableStateOf(state.query) }
    Column(modifier.fillMaxSize().background(Screen).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("MODRINTH LIBRARY", color = FlintGreen, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Katalog dan unduhan resmi Modrinth", color = Muted, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(query, { query = it; actions.onQueryChange?.invoke(it) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Cari mod") })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { actions.onSearch?.invoke() }, enabled = actions.onSearch != null) { Text("Search") }
            TextButton(onClick = { actions.onRefresh?.invoke() }, enabled = actions.onRefresh != null) { Text("Refresh") }
            Spacer(Modifier.weight(1f))
            Text(state.totalHits.toString() + " hasil", color = Muted)
        }
        FilterMenu("Profile", state.profiles.map { ModFilterOption(it.id, it.name + " · " + it.minecraftVersion + " · " + it.loader) }, state.selectedProfileId, { actions.onSelectProfile?.invoke(it) }, actions.onSelectProfile != null)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { FilterMenu("Category", state.categories, state.selectedCategory, { actions.onCategoryChange?.invoke(it) }, actions.onCategoryChange != null) }
            Box(Modifier.weight(1f)) { FilterMenu("Loader", state.loaders, state.selectedLoader, { actions.onLoaderChange?.invoke(it) }, actions.onLoaderChange != null) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { FilterMenu("Minecraft", state.gameVersions, state.selectedGameVersion, { actions.onGameVersionChange?.invoke(it) }, actions.onGameVersionChange != null) }
            Box(Modifier.weight(1f)) {
                FilterMenu("Sort", listOf(ModFilterOption("downloads","Most downloaded"),ModFilterOption("relevance","Relevance"),ModFilterOption("follows","Most followed"),ModFilterOption("newest","Newest"),ModFilterOption("updated","Recently updated")), state.sortIndex, { actions.onSortChange?.invoke(it) }, actions.onSortChange != null)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { FilterMenu("Client side", sideOptions(), state.clientSide, { actions.onClientSideChange?.invoke(it) }, actions.onClientSideChange != null) }
            Box(Modifier.weight(1f)) { FilterMenu("Server side", sideOptions(), state.serverSide, { actions.onServerSideChange?.invoke(it) }, actions.onServerSideChange != null) }
        }
        state.errorMessage?.let { MessagePanel(it) { actions.onDismissNotice?.invoke() } }
        state.notice?.let { MessagePanel(it) { actions.onDismissNotice?.invoke() } }
        when (val results = state.results) {
            Loadable.Loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FlintGreen) }
            is Loadable.Error -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(results.message, color = Color.White)
                    TextButton(onClick = { actions.onRefresh?.invoke() }) { Text("Try again", color = FlintGreen) }
                }
            }
            is Loadable.Content -> if (results.value.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("Tidak ada mod yang cocok.", color = Muted) }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results.value, key = { it.id }) { project ->
                        ProjectCard(project, state.busyProjectId == project.id) { actions.onSelectProject?.invoke(project.id) }
                    }
                    if (state.offset + state.pageLimit < state.totalHits) item {
                        OutlinedButton(onClick = { actions.onLoadMore?.invoke() }, enabled = !state.loading && actions.onLoadMore != null, modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.loading) "Loading…" else "Load more")
                        }
                    }
                }
            }
        }
    }
    state.selectedProject?.let { project ->
        ProjectDetailsDialog(project, state.selectedVersions, state.detailLoading, state.busyProjectId == project.id,
            { actions.onCloseProject?.invoke() }, { actions.onInstallVersion?.invoke(project.id, it) })
    }
}

@Composable
private fun FilterMenu(title: String, options: List<ModFilterOption>, selected: String, onSelect: (String) -> Unit, enabled: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(title, color = Muted, style = MaterialTheme.typography.labelSmall)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(options.firstOrNull { it.value == selected }?.label ?: if (selected.isBlank()) "All" else selected, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded, { expanded = false }) {
                DropdownMenuItem(text = { Text("All") }, onClick = { expanded = false; onSelect("") })
                options.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { expanded = false; onSelect(option.value) }) }
            }
        }
    }
}

@Composable
private fun ProjectCard(project: ModProjectUi, busy: Boolean, onOpen: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onOpen), color = Panel, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(project.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("by " + project.author.ifBlank { "Unknown author" }, color = Muted, style = MaterialTheme.typography.labelSmall)
                }
                Text(if (project.installed) "INSTALLED" else if (busy) "WORKING" else "DETAILS →", color = FlintGreen, style = MaterialTheme.typography.labelSmall)
            }
            Text(project.description, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            Row {
                Text(project.downloads.toString() + " downloads", color = Muted, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Text(project.categories.take(4).joinToString(" · "), color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ProjectDetailsDialog(project: ModProjectUi, versions: List<ModVersionUi>, loading: Boolean, busy: Boolean, onClose: () -> Unit, onInstall: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(project.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(project.description, style = MaterialTheme.typography.bodySmall)
                Text("Client: " + project.clientSide + " · Server: " + project.serverSide, style = MaterialTheme.typography.labelSmall)
                when {
                    loading -> CircularProgressIndicator(color = FlintGreen)
                    versions.isEmpty() -> Text("Tidak ada versi kompatibel untuk profile ini.", color = Muted)
                    else -> LazyColumn(Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(versions, key = { it.id }) { version ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(version.versionNumber)
                                    Text(version.gameVersions.joinToString(), color = Muted, style = MaterialTheme.typography.labelSmall)
                                }
                                Button(onClick = { onInstall(version.id) }, enabled = !busy) { Text(if (busy) "Installing…" else "Install") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}

@Composable
private fun MessagePanel(message: String, onDismiss: () -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, color = Color.White, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onDismiss) { Text("OK", color = FlintGreen) }
        }
    }
}

private fun sideOptions() = listOf(
    ModFilterOption("required", "Required"),
    ModFilterOption("optional", "Optional"),
    ModFilterOption("unsupported", "Unsupported"),
    ModFilterOption("unknown", "Unknown"),
)
