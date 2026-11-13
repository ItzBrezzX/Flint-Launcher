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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

/** Top-level launcher destinations. */
enum class Destination(val label: String, val icon: ImageVector) {
    PLAY("HOME", Icons.Filled.PlayArrow),
    PROFILES("JAVA EDITION", Icons.Filled.AccountCircle),
    MODS("LIBRARY", Icons.Filled.Build),
    PARTNERS("SERVERS", Icons.Filled.Star),
    SETTINGS("SETTINGS", Icons.Filled.Settings),
}

private val LauncherBackground = Color(0xFF171615)
private val LauncherPanel = Color(0xFF252423)
private val LauncherGreen = Color(0xFF3C8527)
private val LauncherText = Color(0xFFE8E6E3)
private val LauncherMuted = Color(0xFFAAA7A2)

/**
 * Landscape/tablet launcher shell inspired by the supplied Minecraft Launcher reference:
 * fixed left navigation, account block, top play bar, and a wide content viewport.
 * Portrait phones retain compact bottom navigation.
 */
@Composable
fun FlintScaffold(
    selected: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(LauncherBackground),
    ) {
        if (maxWidth >= 600.dp) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier.width(184.dp).fillMaxHeight().background(LauncherPanel),
                ) {
                    Column(
                        Modifier.fillMaxWidth().height(88.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = LauncherText)
                            Column(Modifier.padding(start = 10.dp)) {
                                Text("FLINT ACCOUNT", color = LauncherText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Local profile", color = LauncherMuted, fontSize = 11.sp)
                            }
                        }
                    }
                    Surface(color = Color(0xFF10100F), modifier = Modifier.fillMaxWidth().height(1.dp)) {}
                    Spacer(Modifier.height(8.dp))
                    Destination.entries.forEach { destination ->
                        val active = selected == destination
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (active) Color(0xFF393735) else Color.Transparent)
                                .clickable { onSelect(destination) }
                                .padding(horizontal = 12.dp, vertical = 15.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(destination.icon, contentDescription = null,
                                tint = if (active) LauncherText else LauncherMuted)
                            Text(
                                destination.label,
                                modifier = Modifier.padding(start = 12.dp),
                                color = if (active) LauncherText else LauncherMuted,
                                fontSize = 12.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("WHAT'S NEW", color = LauncherMuted, fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(Destination.PLAY) }
                            .padding(horizontal = 16.dp, vertical = 16.dp))
                    Text("v${BuildConfigPlaceholder.version}", color = LauncherMuted, fontSize = 10.sp,
                        modifier = Modifier.padding(start = 14.dp, bottom = 8.dp))
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(
                        Modifier.fillMaxWidth().height(64.dp).background(LauncherPanel).padding(horizontal = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Minecraft Launcher", color = LauncherText, fontSize = 14.sp)
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = { onSelect(Destination.PROFILES) },
                            colors = ButtonDefaults.buttonColors(containerColor = LauncherGreen, contentColor = Color.White),
                            shape = RoundedCornerShape(3.dp),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Text("PLAY JAVA", fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                    Box(
                        Modifier.weight(1f).fillMaxWidth().navigationBarsPadding().background(LauncherBackground),
                    ) { content() }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                Row(
                    Modifier.fillMaxWidth().background(LauncherPanel).padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Destination.entries.forEach { destination ->
                        Column(
                            Modifier.weight(1f).clickable { onSelect(destination) }.padding(vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(destination.icon, contentDescription = null,
                                tint = if (selected == destination) LauncherText else LauncherMuted)
                            Text(destination.label, color = if (selected == destination) LauncherText else LauncherMuted,
                                fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }
}

private object BuildConfigPlaceholder {
    const val version = "Flint"
}
