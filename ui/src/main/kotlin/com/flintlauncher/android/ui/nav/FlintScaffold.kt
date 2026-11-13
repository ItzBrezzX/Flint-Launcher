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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
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
import com.flintlauncher.android.ui.theme.FlintColors

/** Top-level launcher destinations. */
enum class Destination(val label: String, val icon: ImageVector) {
    PLAY("HOME", Icons.Filled.PlayArrow),
    PROFILES("INSTANCES", Icons.Filled.AccountCircle),
    MODS("LIBRARY", Icons.Filled.Build),
    SETTINGS("SETTINGS", Icons.Filled.Settings),
    ABOUT("ABOUT", Icons.Filled.Info),
}

private val LauncherBackground = FlintColors.Charcoal
private val LauncherPanel = FlintColors.Panel
private val LauncherGreen = FlintColors.Ember
private val LauncherText = FlintColors.TextPrimary
private val LauncherMuted = FlintColors.TextMuted

/**
 * Landscape/tablet launcher shell inspired by the supplied Minecraft Launcher reference:
 * fixed left navigation, account block and a wide content viewport.
 * The activity is configured to open in landscape on phones.
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
        if (maxWidth >= 560.dp) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier.width(164.dp).fillMaxHeight().background(LauncherPanel),
                ) {
                    Column(
                        Modifier.fillMaxWidth().height(88.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(LauncherGreen),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("F", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                            }
                            Column(Modifier.padding(start = 10.dp)) {
                                Text("FLINT LAUNCHER", color = LauncherText, fontSize = 12.sp, fontWeight = FontWeight.Black)
                                Text("MINECRAFT JAVA", color = LauncherMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
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
                                 .clip(RoundedCornerShape(8.dp))
                                .background(if (active) LauncherGreen.copy(alpha = 0.14f) else Color.Transparent)
                                .clickable { onSelect(destination) }
                                .padding(horizontal = 12.dp, vertical = 15.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(destination.icon, contentDescription = null,
                                tint = if (active) LauncherGreen else LauncherMuted)
                            Text(
                                destination.label,
                                modifier = Modifier.padding(start = 12.dp),
                                color = if (active) LauncherGreen else LauncherMuted,
                                fontSize = 12.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("WHAT'S NEW", color = LauncherMuted, fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(Destination.PLAY) }
                            .padding(horizontal = 16.dp, vertical = 16.dp))
                    Text("ANDROID EDITION", color = LauncherMuted, fontSize = 10.sp,
                        modifier = Modifier.padding(start = 14.dp, bottom = 8.dp))
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(
                        Modifier.fillMaxWidth().height(64.dp).background(LauncherPanel).padding(horizontal = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(selected.label, color = LauncherText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("FLINT  /  MINECRAFT JAVA", color = LauncherMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
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
