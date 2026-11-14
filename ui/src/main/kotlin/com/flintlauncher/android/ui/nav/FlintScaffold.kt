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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Top-level launcher destinations matching the supplied Flint GUI layout. */
enum class Destination(val label: String, val icon: ImageVector) {
    PLAY("Home", Icons.Filled.PlayArrow),
    PROFILES("Instances", Icons.Filled.AccountCircle),
    MODS("Mod Menu", Icons.Filled.Build),
    SETTINGS("Settings", Icons.Filled.Settings),
    ABOUT("About", Icons.Filled.Info),
}

private val SidebarBg = Color(0xFF0B1410)
private val ContentBg = Color(0xFF07110B)
private val AccentGreen = Color(0xFF35D12C)
private val TextPrimary = Color(0xFFE8F0E8)
private val TextMuted = Color(0xFF8A9A8E)
private val CardBg = Color(0xFF101B14)

@Composable
fun FlintScaffold(
    selected: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(ContentBg),
    ) {
        if (maxWidth >= 560.dp) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier.width(220.dp).fillMaxHeight()
                        .background(SidebarBg)
                        .padding(vertical = 10.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(AccentGreen),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("F", color = Color(0xFF07110B), fontSize = 21.sp, fontWeight = FontWeight.Black)
                        }
                        Column(Modifier.padding(start = 11.dp)) {
                            Text("Flint-Launcher", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Play • Mod • Explore", color = TextMuted, fontSize = 10.sp)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Destination.entries.forEach { destination ->
                        val active = selected == destination
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) AccentGreen.copy(alpha = 0.16f) else Color.Transparent)
                                .clickable { onSelect(destination) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label,
                                tint = if (active) AccentGreen else TextMuted,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = destination.label,
                                color = if (active) AccentGreen else TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)
                            .clip(RoundedCornerShape(12.dp)).background(CardBg).padding(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(30.dp).clip(RoundedCornerShape(7.dp))
                                    .background(Color(0xFF1E2E24)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("MC", color = AccentGreen, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                            Column(Modifier.padding(start = 10.dp)) {
                                Text("Minecraft Java", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text("Selected installation", color = TextMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }

                Box(
                    Modifier.weight(1f).fillMaxHeight().background(ContentBg),
                ) {
                    content()
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                Row(
                    Modifier.fillMaxWidth().background(SidebarBg)
                        .navigationBarsPadding().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Destination.entries.forEach { destination ->
                        Column(
                            Modifier.weight(1f).clickable { onSelect(destination) }.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                destination.icon,
                                contentDescription = null,
                                tint = if (selected == destination) AccentGreen else TextMuted,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                destination.label,
                                color = if (selected == destination) AccentGreen else TextMuted,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
