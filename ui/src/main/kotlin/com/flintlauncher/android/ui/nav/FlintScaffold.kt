package com.flintlauncher.android.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
enum class Destination(val label: String, val icon: ImageVector) {
    PLAY("Home", Icons.Filled.Home),
    PROFILES("Instances", Icons.Filled.SportsEsports),
    MODS("Mod Menu", Icons.Filled.Extension),
    SETTINGS("Settings", Icons.Filled.Settings),
    ABOUT("About", Icons.Filled.Info),
}

// Colors 1:1
private val Bg = Color(0xFF07110B)
private val SidebarBg = Color(0xFF0A140E)
private val Panel = Color(0xFF0B1410)
private val CardBg = Color(0xFF101B14)
private val Accent = Color(0xFF35D12C)
private val TextPrimary = Color(0xFFE8F0E8)
private val TextMuted = Color(0xFF8A9A8E)
private val Border = Color(0xFF1A2A1E)
private val ActiveBg = Color(0x2935D12C)
private val DarkText = Color(0xFF07110B)

private val Round8 = RoundedCornerShape(8.dp)
private val Round9 = RoundedCornerShape(9.dp)
private val Round10 = RoundedCornerShape(10.dp)
private val Round12 = RoundedCornerShape(12.dp)
private val DotShape = RoundedCornerShape(50)

@Composable
fun FlintScaffold(
    selected: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Bg),
    ) {
        if (maxWidth >= 560.dp) {
            Row(Modifier.fillMaxSize()) {
                // ===== LEFT SIDEBAR (fixed width, never clipped) =====
                Column(
                    Modifier
                        .width(210.dp)
                        .fillMaxHeight()
                        .background(SidebarBg)
                        .padding(vertical = 6.dp),
                ) {
                    // Logo
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(Round9)
                                .background(Accent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("F", color = DarkText, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        }
                        Column(Modifier.padding(start = 9.dp)) {
                            Text(
                                "Flint-Launcher",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "Play • Mod • Explore",
                                color = TextMuted,
                                fontSize = 9.sp,
                                maxLines = 1,
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Nav
                    Destination.entries.forEach { dest ->
                        val active = selected == dest
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 1.dp)
                                .clip(Round10)
                                .background(if (active) ActiveBg else Color.Transparent)
                                .clickable(role = Role.Tab) { onSelect(dest) }
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = dest.icon,
                                contentDescription = dest.label,
                                tint = if (active) Accent else TextMuted,
                                modifier = Modifier.size(19.dp),
                            )
                            Text(
                                text = dest.label,
                                color = if (active) Accent else TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(start = 11.dp),
                                maxLines = 1,
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // Version card
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .clip(Round12)
                            .background(CardBg)
                            .border(1.dp, Border, Round12)
                            .padding(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(Round8)
                                    .background(Color(0xFF1E2E24)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("📦", fontSize = 13.sp)
                            }
                            Column(Modifier.padding(start = 8.dp)) {
                                Text("Minecraft", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                Text("1.21.10", color = TextMuted, fontSize = 10.sp, maxLines = 1)
                            }
                        }
                        Spacer(Modifier.height(7.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).clip(DotShape).background(Accent))
                            Text(
                                "Online  •  Ready to play",
                                color = TextMuted,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(start = 6.dp),
                                maxLines = 1,
                            )
                        }
                    }
                }

                // ===== CONTENT + TOP BAR =====
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Bg),
                ) {
                    TopBar()
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        content()
                    }
                }
            }
        } else {
            // Phone
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(SidebarBg)
                        .navigationBarsPadding()
                        .padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Destination.entries.forEach { dest ->
                        val active = selected == dest
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable { onSelect(dest) }
                                .padding(vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(dest.icon, null, tint = if (active) Accent else TextMuted, modifier = Modifier.size(20.dp))
                            Text(dest.label, color = if (active) Accent else TextMuted, fontSize = 9.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar() {
    var search by remember { mutableStateOf("") }

    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Panel)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = {
                Text("Search mods, shaders, resource packs and more...", color = TextMuted, fontSize = 12.sp, maxLines = 1)
            },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            shape = Round12,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = Border,
                focusedContainerColor = CardBg,
                unfocusedContainerColor = CardBg,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = Accent,
            ),
            modifier = Modifier.weight(1f).height(40.dp),
        )

        Spacer(Modifier.width(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(Round10)
                .background(CardBg)
                .border(1.dp, Border, Round10)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Box(
                Modifier.size(24.dp).clip(Round8).background(Accent),
                contentAlignment = Alignment.Center,
            ) {
                Text("K", color = DarkText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Text(
                "KINGBREZZ",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 6.dp),
                maxLines = 1,
            )
            Icon(Icons.Default.ArrowDropDown, null, tint = TextMuted, modifier = Modifier.size(16.dp))
        }
    }
}
