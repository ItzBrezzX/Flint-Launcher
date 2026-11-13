package com.flintlauncher.android.ui.mods

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.flintlauncher.android.ui.state.InstalledModUi
import com.flintlauncher.android.ui.state.Loadable
import com.flintlauncher.android.ui.state.ModFilterOption
import com.flintlauncher.android.ui.state.ModProjectUi
import com.flintlauncher.android.ui.state.ModVersionUi
import com.flintlauncher.android.ui.state.ModsActions
import com.flintlauncher.android.ui.state.ModsUiState
import com.flintlauncher.android.ui.theme.FlintColors

@Composable
fun ModsScreen(
    state: ModsUiState,
    actions: ModsActions,
    modifier: Modifier = Modifier,
) {
    var createProfileOpen by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF06140B))) {
        val wide = maxWidth >= 760.dp
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(horizontal = if (wide) 16.dp else 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            // The reference Library uses resource categories as its first navigation row.
            // This row is backed by the real Modrinth category facets returned by the API.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = state.selectedCategory.isBlank(),
                    onClick = { actions.onCategoryChange?.invoke("") },
                    enabled = actions.onCategoryChange != null,
                    label = { Text("All", fontSize = 11.sp) },
                )
                state.categories.take(8).forEach { category ->
                    FilterChip(
                        selected = state.selectedCategory == category.value,
                        onClick = { actions.onCategoryChange?.invoke(category.value) },
                        enabled = actions.onCategoryChange != null,
                        label = { Text(category.label, fontSize = 11.sp) },
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { actions.onRefresh?.invoke() },
                    enabled = actions.onRefresh != null,
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh Modrinth catalog", tint = FlintColors.Ember)
                }
            }

            if (wide) {
                Row(
                    Modifier.fillMaxWidth().height(158.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LibraryHero(Modifier.weight(3.2f).fillMaxHeight())
                    FeaturedProjects(
                        projects = (state.results as? Loadable.Content)?.value.orEmpty().take(3),
                        onSelect = { actions.onSelectProject?.invoke(it) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            } else {
                LibraryHero(Modifier.fillMaxWidth().height(118.dp))
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(9.dp),
                color = Color(0xFF0B2111),
                border = BorderStroke(1.dp, Color(0xFF194526)),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("FILTERS", color = FlintColors.TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Spacer(Modifier.weight(1f))
                        Text("MODRINTH CATALOG", color = FlintColors.Ember, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterDropdown(
                            label = "Mod Loader",
                            selected = state.loaders.firstOrNull { it.value == state.selectedLoader }?.label
                                ?: state.selectedLoader.ifBlank { "All Loaders" },
                            options = listOf(ModFilterOption("", "All Loaders")) + state.loaders,
                            onSelected = { actions.onLoaderChange?.invoke(it) },
                        )
                        FilterDropdown(
                            label = "Minecraft Version",
                            selected = state.gameVersions.firstOrNull { it.value == state.selectedGameVersion }?.label
                                ?: state.selectedGameVersion.ifBlank { "All Versions" },
                            options = listOf(ModFilterOption("", "All Versions")) + state.gameVersions,
                            onSelected = { actions.onGameVersionChange?.invoke(it) },
                        )
                        FilterDropdown(
                            label = "Sort By",
                            selected = sortOptions().firstOrNull { it.value == state.sortIndex }?.label ?: "Most downloaded",
                            options = sortOptions(),
                            onSelected = { actions.onSortChange?.invoke(it) },
                        )
                        SideFilter("Client", state.clientSide, actions.onClientSideChange)
                        SideFilter("Server", state.serverSide, actions.onServerSideChange)
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF0B2111),
                border = BorderStroke(1.dp, Color(0xFF194526)),
            ) {
                ProfileSelector(
                    state = state,
                    onSelect = actions.onSelectProfile,
                    onCreate = { createProfileOpen = true },
                )
            }

            if (state.selectedProfile == null) {
                MessageCard("Create or select a mod-loader profile before installing a compatible Modrinth project.")
            } else {
                InstalledModsSection(state.installed, actions.onUninstall)
            }
            state.errorMessage?.let { MessageCard(it) }
            state.notice?.let { MessageCard(it) }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("DISCOVER", color = FlintColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    state.totalHits.toString() + " results",
                    color = FlintColors.TextMuted,
                    fontSize = 9.sp,
                )
            }

            when (val results = state.results) {
                Loadable.Loading -> Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = FlintColors.Ember)
                }
                is Loadable.Error -> Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(results.message, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { actions.onRefresh?.invoke() }, enabled = actions.onRefresh != null) {
                        Text("Retry")
                    }
                }
                is Loadable.Content -> {
                    if (results.value.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("No projects match these filters.", color = FlintColors.TextMuted, fontSize = 11.sp)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(if (wide) 2 else 1),
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(bottom = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(results.value, key = { it.id }) { project ->
                                ModProjectCard(project) { actions.onSelectProject?.invoke(project.id) }
                            }
                            if (state.offset + state.pageLimit < state.totalHits) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    OutlinedButton(
                                        onClick = { actions.onLoadMore?.invoke() },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = actions.onLoadMore != null,
                                    ) { Text("Load more") }
                                }
                            }
                        }
                    }
                }
            }

            InstallStatusBar(
                busyProject = state.busyProjectId,
                installedCount = state.installed.size,
            )
        }
    }

    if (createProfileOpen) {
        CreateProfileDialog(
            initialVersion = state.selectedGameVersion,
            options = state.gameVersions,
            onDismiss = { createProfileOpen = false },
            onCreate = { name, version, loader ->
                createProfileOpen = false
                actions.onCreateProfile?.invoke(name, version, loader)
            },
            enabled = actions.onCreateProfile != null,
        )
    }

    state.selectedProject?.let { project ->
        ModDetailDialog(
            project = project,
            versions = state.selectedVersions,
            loading = state.detailLoading,
            busy = state.busyProjectId == project.id,
            installed = state.installed.any { it.projectId == project.id },
            onDismiss = { actions.onCloseProject?.invoke() },
            onInstall = { versionId -> actions.onInstallVersion?.invoke(project.id, versionId) },
            onUninstall = { actions.onUninstall?.invoke(project.id) },
            canInstall = actions.onInstallVersion != null && state.selectedProfile != null,
            canUninstall = actions.onUninstall != null,
        )
    }
}

@Composable
private fun LibraryHero(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF0D2E17),
        border = BorderStroke(1.dp, Color(0xFF1C4B27)),
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF123A1B), Color(0xFF07170C), Color(0xFF16451F)),
                ),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("MINECRAFT", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                Text("MOD MENU", color = FlintColors.Ember, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 1.8.sp)
                Text("Discover mods, shaders, resource packs and more", color = Color(0xFFD1E3D3), fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun FeaturedProjects(
    projects: List<ModProjectUi>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(9.dp),
        color = Color(0xFF0D2413),
        border = BorderStroke(1.dp, Color(0xFF194526)),
    ) {
        Column(Modifier.fillMaxSize().padding(9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("FEATURED", color = FlintColors.TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Black)
            if (projects.isEmpty()) {
                Text("Search results will appear here.", color = FlintColors.TextMuted, fontSize = 9.sp)
            } else {
                projects.forEach { project ->
                    Row(
                        Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(5.dp))
                            .clickable { onSelect(project.id) }.background(Color(0xFF142D1A)).padding(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val iconUrl = remember(project.iconUrl) { trustedModrinthIcon(project.iconUrl) }
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF203A26)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (iconUrl != null) {
                                AsyncImage(iconUrl, contentDescription = project.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Icon(Icons.Filled.Build, contentDescription = null, tint = FlintColors.Ember)
                            }
                        }
                        Column(Modifier.padding(start = 7.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(project.title, color = FlintColors.TextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(compactCount(project.downloads) + " downloads", color = FlintColors.TextMuted, fontSize = 8.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallStatusBar(busyProject: String?, installedCount: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF0B2111),
        border = BorderStroke(1.dp, Color(0xFF1B6429)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Build, contentDescription = null, tint = FlintColors.Ember, modifier = Modifier.size(17.dp))
            Text("INSTALL STATUS", color = FlintColors.Ember, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            Spacer(Modifier.weight(1f))
            Text(
                busyProject?.let { "Installing " + it } ?: (installedCount.toString() + " installed"),
                color = FlintColors.TextMuted,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ModSearchBar(
    state: ModsUiState,
    actions: ModsActions,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { actions.onQueryChange?.invoke(it) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("Search mods, authors...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            enabled = actions.onQueryChange != null,
        )
        Button(
            onClick = { actions.onSearch?.invoke() },
            enabled = actions.onSearch != null,
        ) { Text("Search") }
    }
}

private fun sortOptions() = listOf(
    ModFilterOption("downloads", "Most downloaded"),
    ModFilterOption("relevance", "Relevance"),
    ModFilterOption("follows", "Most followed"),
    ModFilterOption("newest", "Newest"),
    ModFilterOption("updated", "Recently updated"),
)

@Composable
private fun ProfileSelector(
    state: ModsUiState,
    onSelect: ((String) -> Unit)?,
    onCreate: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("ACTIVE PROFILE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                state.selectedProfile?.let { "${it.name}  ·  Minecraft ${it.minecraftVersion}  ·  ${it.loader}" }
                    ?: "No profile",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.profiles.isNotEmpty()) {
            Box {
                OutlinedButton(onClick = { expanded = true }) { Text("Change") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    state.profiles.forEach { profile ->
                        DropdownMenuItem(
                            text = { Text("${profile.name} · ${profile.minecraftVersion} · ${profile.loader}") },
                            onClick = {
                                expanded = false
                                onSelect?.invoke(profile.id)
                            },
                        )
                    }
                }
            }
        }
        OutlinedButton(onClick = onCreate) { Text(if (state.profiles.isEmpty()) "Create" else "New") }
    }
}

@Composable
private fun FilterDropdown(
    label: String,
    selected: String,
    options: List<ModFilterOption>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("$label: $selected", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.take(100).forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = { expanded = false; onSelected(option.value) },
                )
            }
        }
    }
}

@Composable
private fun SideFilter(
    label: String,
    selected: String,
    onSelected: ((String) -> Unit)?,
) {
    val options = listOf(
        ModFilterOption("", "Any"),
        ModFilterOption("required", "Required"),
        ModFilterOption("optional", "Optional"),
        ModFilterOption("unsupported", "Unsupported"),
        ModFilterOption("unknown", "Unknown"),
    )
    FilterDropdown(
        label = label,
        selected = options.firstOrNull { it.value == selected }?.label ?: "Any",
        options = options,
        onSelected = { onSelected?.invoke(it) },
    )
}

@Composable
private fun ModProjectCard(project: ModProjectUi, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = FlintColors.Panel),
        border = BorderStroke(1.dp, FlintColors.PanelRaised),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val trustedIconUrl = remember(project.iconUrl) { trustedModrinthIcon(project.iconUrl) }
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(FlintColors.PanelRaised),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Build,
                    contentDescription = null,
                    tint = FlintColors.Ember,
                    modifier = Modifier.size(30.dp),
                )
                if (trustedIconUrl != null) {
                    AsyncImage(
                        model = trustedIconUrl,
                        contentDescription = project.title + " icon",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f).padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        project.title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (project.installed) {
                        Text(
                            "INSTALLED",
                            color = FlintColors.Ember,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Text(
                    project.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = FlintColors.TextMuted,
                )
                Text(
                    "${project.author} · ${compactCount(project.downloads)} downloads",
                    style = MaterialTheme.typography.labelSmall,
                    color = FlintColors.TextMuted,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    project.categories.take(3).forEach { category ->
                        AssistChip(
                            onClick = onClick,
                            label = { Text(category, fontSize = 10.sp) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "CLIENT ${project.clientSide.uppercase()} · SERVER ${project.serverSide.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = FlintColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = onClick) { Text("Versions") }
                }
            }
        }
    }
}

@Composable
private fun InstalledModsSection(
    mods: List<InstalledModUi>,
    onUninstall: ((String) -> Unit)?,
) {
    if (mods.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            "INSTALLED IN THIS PROFILE",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = FlintColors.TextMuted,
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            mods.forEach { mod ->
                FilterChip(
                    selected = false,
                    onClick = { onUninstall?.invoke(mod.projectId) },
                    enabled = onUninstall != null,
                    label = { Text("${mod.title} ${mod.versionNumber} ×", maxLines = 1, fontSize = 11.sp) },
                )
            }
        }
    }
}

@Composable
private fun MessageCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = FlintColors.Panel,
        border = BorderStroke(1.dp, FlintColors.PanelRaised),
    ) {
        Text(
            message,
            modifier = Modifier.padding(10.dp),
            color = FlintColors.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun CreateProfileDialog(
    initialVersion: String,
    options: List<ModFilterOption>,
    onDismiss: () -> Unit,
    onCreate: (String, String, String) -> Unit,
    enabled: Boolean,
) {
    var name by remember { mutableStateOf("My Minecraft") }
    var version by remember { mutableStateOf(initialVersion) }
    var loader by remember { mutableStateOf("fabric") }
    var loaderExpanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create mod profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Profile name") }, singleLine = true)
                if (options.isNotEmpty()) {
                    FilterDropdown(
                        label = "Minecraft",
                        selected = version.ifBlank { "Choose version" },
                        options = options,
                        onSelected = { version = it },
                    )
                } else {
                    OutlinedTextField(value = version, onValueChange = { version = it }, label = { Text("Minecraft version") }, singleLine = true)
                }
                Box {
                    OutlinedButton(onClick = { loaderExpanded = true }) { Text("Loader: $loader") }
                    DropdownMenu(expanded = loaderExpanded, onDismissRequest = { loaderExpanded = false }) {
                        listOf("fabric", "quilt", "forge", "neoforge").forEach { value ->
                            DropdownMenuItem(text = { Text(value) }, onClick = { loader = value; loaderExpanded = false })
                        }
                    }
                }
                Text("Mods are stored separately for each profile.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name.trim(), version.trim(), loader) },
                enabled = enabled && name.isNotBlank() && version.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ModDetailDialog(
    project: ModProjectUi,
    versions: List<ModVersionUi>,
    loading: Boolean,
    busy: Boolean,
    installed: Boolean,
    onDismiss: () -> Unit,
    onInstall: (String) -> Unit,
    onUninstall: () -> Unit,
    canInstall: Boolean,
    canUninstall: Boolean,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(project.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(project.description, style = MaterialTheme.typography.bodySmall)
                Text(
                    "Client: ${project.clientSide} · Server: ${project.serverSide}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("COMPATIBLE VERSIONS", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                when {
                    loading -> CircularProgressIndicator(Modifier.size(22.dp))
                    versions.isEmpty() -> Text("No compatible release was found for this profile.")
                    else -> Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        versions.forEach { version -> VersionRow(version, busy || !canInstall) { onInstall(version.id) } }
                    }
                }
            }
        },
        confirmButton = {
            if (installed) {
                TextButton(onClick = onUninstall, enabled = canUninstall && !busy) { Text("Uninstall") }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = { if (installed) TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun VersionRow(version: ModVersionUi, disabled: Boolean, onInstall: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(version.versionNumber, fontWeight = FontWeight.Bold)
                Text(
                    "${version.gameVersions.joinToString()} · ${version.loaders.joinToString()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("${compactCount(version.downloads)} downloads", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onInstall, enabled = !disabled) { Text("Install") }
        }
    }
}

private fun compactCount(value: Long): String = when {
    value >= 1_000_000 -> "${"%.1f".format(value / 1_000_000.0)}M"
    value >= 1_000 -> "${"%.1f".format(value / 1_000.0)}K"
    else -> value.toString()
}

/** Only fetch icon images from Modrinth's official HTTPS CDN. */
private fun trustedModrinthIcon(value: String?): String? {
    if (value.isNullOrBlank()) return null
    val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return null
    return value.takeIf { uri.scheme.equals("https", ignoreCase = true) && uri.host.equals("cdn.modrinth.com", ignoreCase = true) }
}
