package com.flintlauncher.android.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private object ShellColors {
    val HomeBackground = Color(0xFF0B1116)
    val HomeRail = Color(0xFF101820)
    val HomePanel = Color(0xFF151E26)
    val HomeAccent = Color(0xFFFF8A1F)
    val LibraryBackground = Color(0xFF06140B)
    val LibraryRail = Color(0xFF071B0D)
    val LibraryPanel = Color(0xFF0D2413)
    val LibraryAccent = Color(0xFF22D52B)
    val Text = Color(0xFFF4F7F3)
    val Muted = Color(0xFFA8B3A9)
}

enum class Destination(val label: String, val icon: ImageVector) {
    PLAY("HOME", Icons.Filled.PlayArrow),
    PROFILES("INSTANCES", Icons.Filled.AccountCircle),
    MODS("LIBRARY", Icons.Filled.Build),
    PARTNERS("PARTNERS", Icons.Filled.Star),
    SETTINGS("SETTINGS", Icons.Filled.Settings),
    ABOUT("ABOUT", Icons.Filled.Info),
}

@Composable
fun FlintScaffold(
    selected: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    onSearch: (String) -> Unit = {},
    accountName: String? = null,
    instanceLabel: String? = null,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wide = maxWidth >= 560.dp
        val libraryMode = selected == Destination.MODS || selected == Destination.ABOUT
        val background = if (libraryMode) ShellColors.LibraryBackground else ShellColors.HomeBackground
        val railBackground = if (libraryMode) ShellColors.LibraryRail else ShellColors.HomeRail
        val panel = if (libraryMode) ShellColors.LibraryPanel else ShellColors.HomePanel
        val accent = if (libraryMode) ShellColors.LibraryAccent else ShellColors.HomeAccent

        Box(Modifier.fillMaxSize().background(background)) {
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.width(if (libraryMode) 156.dp else 82.dp)
                            .fillMaxHeight()
                            .background(railBackground),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (libraryMode) {
                            Row(
                                Modifier.fillMaxWidth().height(74.dp).padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(38.dp).clip(RoundedCornerShape(7.dp)).background(accent),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("F", color = Color(0xFF07130A), fontSize = 24.sp, fontWeight = FontWeight.Black)
                                }
                                Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text("FLINT", color = ShellColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Black)
                                    Text("Play · Mod · Explore", color = ShellColors.Muted, fontSize = 8.sp)
                                }
                            }
                            Surface(color = panel, modifier = Modifier.fillMaxWidth().height(1.dp)) {}
                            Text(
                                "WORKSPACE",
                                color = ShellColors.Muted,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.3.sp,
                                modifier = Modifier.align(Alignment.Start).padding(start = 14.dp, top = 18.dp, bottom = 8.dp),
                            )
                            val destinations = listOf(Destination.PLAY, Destination.PROFILES, Destination.MODS, Destination.SETTINGS, Destination.ABOUT)
                            destinations.forEach { destination ->
                                LibraryNavItem(destination, selected == destination, accent) { onSelect(destination) }
                            }
                            Spacer(Modifier.weight(1f))
                            LibraryInstanceCard(instanceLabel)
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(8.dp))
                                    .background(panel).padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(Color(0xFF32D66B)))
                                Column(Modifier.padding(start = 8.dp)) {
                                    Text("ONLINE", color = ShellColors.Text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text("Launcher ready", color = ShellColors.Muted, fontSize = 8.sp)
                                }
                            }
                        } else {
                            Box(
                                Modifier.padding(top = 13.dp, bottom = 18.dp).size(38.dp)
                                    .clip(RoundedCornerShape(8.dp)).background(accent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("F", color = Color(0xFF1A1209), fontSize = 24.sp, fontWeight = FontWeight.Black)
                            }
                            val destinations = listOf(
                                Destination.PLAY,
                                Destination.PROFILES,
                                Destination.PARTNERS,
                                Destination.MODS,
                                Destination.SETTINGS,
                            )
                            destinations.forEach { destination ->
                                HomeRailItem(
                                    destination = destination,
                                    active = selected == destination,
                                    accent = accent,
                                    label = when (destination) {
                                        Destination.PLAY -> "PLAY"
                                        Destination.PROFILES -> "PROFILES"
                                        Destination.PARTNERS -> "PARTNERS"
                                        Destination.MODS -> "STORE"
                                        else -> destination.label
                                    },
                                    onClick = { onSelect(destination) },
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                "v1.0.0",
                                color = ShellColors.Muted,
                                fontSize = 8.sp,
                                modifier = Modifier.padding(bottom = 14.dp),
                            )
                        }
                    }

                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        var query by remember { mutableStateOf("") }
                        Row(
                            Modifier.fillMaxWidth().height(54.dp).background(panel).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (libraryMode) {
                                Row(
                                    Modifier.weight(1f).widthIn(min = 130.dp, max = 620.dp).height(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(background)
                                        .padding(horizontal = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Filled.Search, contentDescription = null, tint = ShellColors.Muted, modifier = Modifier.size(18.dp))
                                    BasicTextField(
                                        value = query,
                                        onValueChange = { query = it },
                                        modifier = Modifier.weight(1f).padding(start = 9.dp),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodySmall.copy(color = ShellColors.Text),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                                        decorationBox = { inner ->
                                            Box {
                                                if (query.isEmpty()) Text("Search mods, shaders, resource packs and more…", color = ShellColors.Muted, fontSize = 11.sp)
                                                inner()
                                            }
                                        },
                                    )
                                    IconButton(onClick = { onSearch(query) }, modifier = Modifier.size(30.dp)) {
                                        Icon(Icons.Filled.Search, contentDescription = "Search", tint = accent, modifier = Modifier.size(17.dp))
                                    }
                                }
                            } else {
                                Text(
                                    when (selected) {
                                        Destination.PLAY -> "PLAY"
                                        Destination.PROFILES -> "PROFILES"
                                        Destination.PARTNERS -> "PARTNERS"
                                        Destination.MODS -> "STORE"
                                        else -> selected.label
                                    },
                                    color = ShellColors.Text,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                )
                                Spacer(Modifier.weight(1f))
                            }
                            Row(
                                Modifier.padding(start = 13.dp).clip(RoundedCornerShape(18.dp))
                                    .background(background).padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                                Text(
                                    accountName?.takeIf { it.isNotBlank() } ?: "GUEST",
                                    modifier = Modifier.padding(start = 7.dp),
                                    color = ShellColors.Text,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Box(Modifier.weight(1f).fillMaxWidth().background(background)) { content() }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    var query by remember { mutableStateOf("") }
                    if (libraryMode) {
                        Row(
                            Modifier.fillMaxWidth().background(panel).padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier.weight(1f).padding(10.dp),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = ShellColors.Text),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                                decorationBox = { inner ->
                                    Box {
                                        if (query.isEmpty()) Text("Search mods, shaders, resource packs…", color = ShellColors.Muted, fontSize = 11.sp)
                                        inner()
                                    }
                                },
                            )
                            IconButton(onClick = { onSearch(query) }) {
                                Icon(Icons.Filled.Search, contentDescription = "Search", tint = accent)
                            }
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                    val destinations = listOf(Destination.PLAY, Destination.PROFILES, Destination.MODS, Destination.PARTNERS, Destination.SETTINGS)
                    Row(
                        Modifier.fillMaxWidth().background(railBackground).padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        destinations.forEach { destination ->
                            Column(
                                Modifier.weight(1f).clickable { onSelect(destination) }.padding(vertical = 5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(destination.icon, contentDescription = null,
                                    tint = if (selected == destination) accent else ShellColors.Muted)
                                Text(
                                    if (destination == Destination.MODS) "STORE" else destination.label,
                                    color = if (selected == destination) ShellColors.Text else ShellColors.Muted,
                                    fontSize = 8.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryNavItem(
    destination: Destination,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val label = when (destination) {
        Destination.PLAY -> "Home"
        Destination.PROFILES -> "Instances"
        Destination.MODS -> "Mod Menu"
        Destination.SETTINGS -> "Settings"
        Destination.ABOUT -> "About"
        else -> destination.label
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (active) accent.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (active) {
            Box(Modifier.width(3.dp).height(21.dp).clip(RoundedCornerShape(3.dp)).background(accent))
        } else {
            Spacer(Modifier.width(3.dp))
        }
        Icon(
            destination.icon,
            contentDescription = null,
            tint = if (active) accent else ShellColors.Muted,
            modifier = Modifier.padding(start = 8.dp).size(18.dp),
        )
        Text(
            label,
            modifier = Modifier.padding(start = 11.dp),
            color = if (active) ShellColors.Text else ShellColors.Muted,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun LibraryInstanceCard(instanceLabel: String?) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ShellColors.LibraryPanel)
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text("MINECRAFT", color = ShellColors.Text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(instanceLabel?.takeIf { it.isNotBlank() } ?: "No instance selected", color = ShellColors.Muted, fontSize = 9.sp)
    }
}

@Composable
private fun HomeRailItem(
    destination: Destination,
    active: Boolean,
    accent: Color,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (active) accent.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            destination.icon,
            contentDescription = null,
            tint = if (active) accent else ShellColors.Muted,
            modifier = Modifier.size(22.dp),
        )
        Text(
            label,
            color = if (active) ShellColors.Text else ShellColors.Muted,
            fontSize = 7.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}
