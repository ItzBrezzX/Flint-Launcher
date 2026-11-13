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
import androidx.compose.material3.LinearProgressIndicator
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
    val libraryBg = FlintColors.Charcoal
    val libraryPanel = FlintColors.Panel
    val libraryBorder = FlintColors.PanelRaised
    val libraryGreen = FlintColors.Ember
    val featuredProject = (state.results as? Loadable.Content)?.value?.firstOrNull()

    BoxWithConstraints(modifier.fillMaxSize().background(libraryBg)) {
        val wide = maxWidth >= 760.dp
        Column(
            Modifier.fillMaxSize().padding(horizontal = if (wide) 16.dp else 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            ModSearchBar(state, actions)
            Surface(
                modifier = Modifier.fillMaxWidth().height(if (wide) 148.dp else 116.dp),
                shape = RoundedCornerShape(10.dp),
                color = libraryPanel,
                border = BorderStroke(1.dp, libraryBorder),
            ) {
                Row(
                    Modifier.fillMaxSize().background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(
                                androidx.compose.ui.graphics.Color(0xFF173D20),
                                libraryPanel,
                                androidx.compose.ui.graphics.Color(0xFF0A1710),
                            ),
                        ),
                    ).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text("FLINT  /  COMMUNITY CONTENT", color = libraryGreen, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp)
                        Text("RESOURCE LIBRARY", color = androidx.compose.ui.graphics.Color.White, fontSize = if (wide) 23.sp else 18.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp)
                        Text("Mods  ·  Shaders  ·  Packs  ·  Worlds", color = FlintColors.TextMuted, fontSize = 10.sp)
                        Text(
                            "${state.totalHits} MODRINTH RESULTS  ·  JAVA EDITION",
                            color = libraryGreen,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (featuredProject != null) {
                        Box(
                            Modifier.size(if (wide) 104.dp else 78.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(libraryBorder),
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(
                                model = trustedModrinthIcon(featuredProject.iconUrl),
                                contentDescription = "${featuredProject.title} preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    } else {
                        Box(
                            Modifier.size(if (wide) 104.dp else 78.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(libraryBorder),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("MC", color = libraryGreen, fontSize = 30.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val resourceTypes = listOf(
                    "mod" to "Mods",
                    "shader" to "Shaders",
                    "resourcepack" to "Resource Packs",
                    "modpack" to "Add-ons",
                    "datapack" to "Data Packs",
                    "world" to "Maps",
                )
                FilterChip(
                    selected = state.selectedProjectType == "all",
                    onClick = { actions.onProjectTypeChange?.invoke("all") },
                    enabled = actions.onProjectTypeChange != null,
                    label = { Text("All", fontSize = 10.sp) },
                )
                resourceTypes.forEach { (type, label) ->
                    FilterChip(
                        selected = state.selectedProjectType == type,
                        onClick = { actions.onProjectTypeChange?.invoke(type) },
                        enabled = actions.onProjectTypeChange != null,
                        label = { Text(label, fontSize = 10.sp) },
                    )
                }
                var moreExpanded by remember { mutableStateOf(false) }
                Box {
                    FilterChip(
                        selected = state.selectedProjectType == "plugin",
                        onClick = { moreExpanded = true },
                        label = { Text("More ▾", fontSize = 10.sp) },
                    )
                    DropdownMenu(expanded = moreExpanded, onDismissRequest = { moreExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Plugins") },
                            onClick = {
                                moreExpanded = false
                                actions.onProjectTypeChange?.invoke("plugin")
                            },
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = libraryPanel,
                border = BorderStroke(1.dp, libraryBorder),
            ) {
                Column(Modifier.fillMaxWidth().padding(9.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("FILTERS", color = FlintColors.TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Spacer(Modifier.weight(1f))
                        Text("SOURCE  /  MODRINTH", color = libraryGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        androidx.compose.material3.IconButton(onClick = { actions.onRefresh?.invoke() }, enabled = actions.onRefresh != null) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh Modrinth catalog", tint = libraryGreen)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("PLATFORM · JAVA EDITION", color = libraryGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        if (state.selectedProjectType == "mod") {
                            FilterDropdown(
                                label = "Feature",
                                selected = state.categories.firstOrNull { it.value == state.selectedCategory }?.label ?: "All",
                                options = listOf(ModFilterOption("", "All")) + state.categories,
                                onSelected = { actions.onCategoryChange?.invoke(it) },
                            )
                            FilterDropdown(
                                label = "Loader",
                                selected = state.loaders.firstOrNull { it.value == state.selectedLoader }?.label ?: state.selectedLoader.ifBlank { "All" },
                                options = listOf(ModFilterOption("", "All")) + state.loaders,
                                onSelected = { actions.onLoaderChange?.invoke(it) },
                            )
                            SideFilter("Client", state.clientSide, actions.onClientSideChange)
                            SideFilter("Server", state.serverSide, actions.onServerSideChange)
                        }
                        FilterDropdown(
                            label = "Version",
                            selected = state.gameVersions.firstOrNull { it.value == state.selectedGameVersion }?.label ?: state.selectedGameVersion.ifBlank { "All" },
                            options = listOf(ModFilterOption("", "All")) + state.gameVersions,
                            onSelected = { actions.onGameVersionChange?.invoke(it) },
                        )
                        FilterDropdown(
                            label = "Sort",
                            selected = sortOptions().firstOrNull { it.value == state.sortIndex }?.label ?: "Most downloaded",
                            options = sortOptions(),
                            onSelected = { actions.onSortChange?.invoke(it) },
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = libraryPanel,
                border = BorderStroke(1.dp, libraryBorder),
            ) {
                ProfileSelector(state, actions.onSelectProfile) { createProfileOpen = true }
            }

            if (state.selectedProfile == null) {
                MessageCard("Select or create a compatible profile before installing projects.")
            } else {
                InstalledModsSection(state.installed, actions.onUninstall)
            }
            state.errorMessage?.let { MessageCard(it) }
            state.notice?.let { MessageCard(it) }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("BROWSE RESOURCES", color = FlintColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
                Spacer(Modifier.weight(1f))
                Text("${state.totalHits} results", color = FlintColors.TextMuted, fontSize = 9.sp)
            }

            when (val results = state.results) {
                Loadable.Loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = libraryGreen)
                }
                is Loadable.Error -> Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(results.message, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { actions.onRefresh?.invoke() }, enabled = actions.onRefresh != null) { Text("Retry") }
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    "FEATURED ON MODRINTH",
                                    color = libraryGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                )
                            }
                            item(span = { GridItemSpan(maxLineSpan) }, key = "featured-${results.value.first().id}") {
                                ModProjectCard(results.value.first()) {
                                    actions.onSelectProject?.invoke(results.value.first().id)
                                }
                            }
                            items(results.value.drop(1), key = { it.id }) { project ->
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

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(7.dp),
                color = libraryPanel,
                border = BorderStroke(1.dp, libraryBorder),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("DOWNLOAD QUEUE", color = libraryGreen, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.9.sp)
                        Spacer(Modifier.weight(1f))
                        val active = state.downloadState == "RUNNING" || state.downloadState == "PAUSED"
                        Text(
                            when {
                                state.busyProjectId != null -> "Installing ${state.busyProjectId}"
                                active -> state.downloadState.lowercase().replaceFirstChar { it.uppercase() }
                                else -> "${state.installed.size} installed"
                            },
                            color = FlintColors.TextMuted,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (state.busyProjectId != null || state.downloadState == "RUNNING" || state.downloadState == "PAUSED") {
                        val percent = state.downloadPercent
                        if (percent == null) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(
                                progress = (percent / 100f).coerceIn(0f, 1f),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        val total = state.downloadTotalBytes
                        val bytes = state.downloadBytes
                        val size = if (total != null && total > 0) {
                            "%.1f / %.1f MB".format(bytes / 1_048_576f, total / 1_048_576f)
                        } else {
                            "%.1f MB".format(bytes / 1_048_576f)
                        }
                        val speed = if (state.downloadSpeedBytesPerSecond > 0) {
                            " · %.2f MB/s".format(state.downloadSpeedBytesPerSecond / 1_048_576f)
                        } else ""
                        Text(
                            (percent?.let { "${it.toInt()}% · " } ?: "") + size + speed,
                            color = FlintColors.TextMuted,
                            fontSize = 9.sp,
                        )
                        state.downloadActiveFiles.firstOrNull()?.let {
                            Text(it, color = FlintColors.TextMuted, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
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
            canInstall = actions.onInstallVersion != null && state.selectedProfile != null && project.projectType == "mod",
            canUninstall = actions.onUninstall != null,
        )
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
                Text(
                    project.projectType.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = FlintColors.Ember,
                    fontWeight = FontWeight.Bold,
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
                    if (project.projectType == "mod") {
                        Text(
                            "CLIENT ${project.clientSide.uppercase()} · SERVER ${project.serverSide.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = FlintColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Text("Minecraft Java", style = MaterialTheme.typography.labelSmall, color = FlintColors.TextMuted)
                    }
                    TextButton(onClick = onClick) { Text(if (project.projectType == "mod") "Versions" else "Details") }
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
                    "Type: ${project.projectType}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (project.projectType != "mod") {
                    Text(
                        "Browsing is available from Modrinth. Direct installation for this resource type is not connected yet.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
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
