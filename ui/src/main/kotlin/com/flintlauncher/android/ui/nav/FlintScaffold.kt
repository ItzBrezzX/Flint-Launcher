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
import com.flintlauncher.android.ui.theme.FlintColors

/** Top-level launcher destinations. */
enum class Destination(val label: String, val icon: ImageVector) {
    PLAY("HOME", Icons.Filled.PlayArrow),
    PROFILES("INSTANCES", Icons.Filled.AccountCircle),
    MODS("LIBRARY", Icons.Filled.Build),
    PARTNERS("SERVERS", Icons.Filled.Star),
    SETTINGS("SETTINGS", Icons.Filled.Settings),
    ABOUT("ABOUT", Icons.Filled.Info),
}

@Composable
fun FlintScaffold(
    selected: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    onSearch: (String) -> Unit = {},
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(FlintColors.Charcoal)) {
        if (maxWidth >= 560.dp) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier.width(190.dp).fillMaxHeight().background(FlintColors.Panel),
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(82.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(FlintColors.Ember),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("F", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                        }
                        Column(Modifier.padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("FLINT", color = FlintColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                            Text("MINECRAFT JAVA", color = FlintColors.TextMuted, fontSize = 9.sp, letterSpacing = 1.sp)
                        }
                    }
                    Surface(color = FlintColors.PanelRaised, modifier = Modifier.fillMaxWidth().height(1.dp)) {}
                    Text("WORKSPACE", color = FlintColors.TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp,
                        modifier = Modifier.padding(start = 17.dp, top = 22.dp, bottom = 9.dp))
                    Destination.entries.forEach { destination ->
                        val active = selected == destination
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) FlintColors.Ember.copy(alpha = 0.13f) else Color.Transparent)
                                .clickable { onSelect(destination) }
                                .padding(horizontal = 12.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (active) Box(Modifier.width(3.dp).height(20.dp).clip(RoundedCornerShape(3.dp)).background(FlintColors.Ember))
                            else Spacer(Modifier.width(3.dp))
                            Icon(destination.icon, contentDescription = null,
                                modifier = Modifier.padding(start = 9.dp).size(18.dp),
                                tint = if (active) FlintColors.Ember else FlintColors.TextMuted)
                            Text(destination.label, modifier = Modifier.padding(start = 12.dp),
                                color = if (active) FlintColors.TextPrimary else FlintColors.TextMuted,
                                fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                letterSpacing = 0.5.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("ANDROID EDITION", color = FlintColors.TextMuted, fontSize = 9.sp, letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 16.dp, bottom = 17.dp))
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    var query by remember { mutableStateOf("") }
                    Row(
                        Modifier.fillMaxWidth().height(62.dp).background(FlintColors.Panel)
                            .padding(horizontal = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(selected.label.lowercase().replaceFirstChar { it.uppercase() },
                            color = FlintColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Row(
                            Modifier.weight(1f).widthIn(min = 130.dp, max = 250.dp).height(36.dp).clip(RoundedCornerShape(6.dp))
                                .background(FlintColors.Charcoal).padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = FlintColors.TextMuted, modifier = Modifier.size(17.dp))
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier.weight(1f).padding(start = 8.dp),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = FlintColors.TextPrimary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                                decorationBox = { inner ->
                                    Box {
                                        if (query.isEmpty()) Text("Search library…", color = FlintColors.TextMuted, fontSize = 11.sp)
                                        inner()
                                    }
                                },
                            )
                            IconButton(onClick = { onSearch(query) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Search, contentDescription = "Search library", tint = FlintColors.Ember, modifier = Modifier.size(17.dp))
                            }
                        }
                        Row(
                            Modifier.padding(start = 18.dp).clip(RoundedCornerShape(20.dp))
                                .background(FlintColors.Charcoal).padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = FlintColors.TextMuted, modifier = Modifier.size(18.dp))
                            Text("GUEST", modifier = Modifier.padding(start = 7.dp), color = FlintColors.TextMuted,
                                fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth().background(FlintColors.Charcoal)) { content() }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                Row(Modifier.fillMaxWidth().background(FlintColors.Panel).padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    Destination.entries.forEach { destination ->
                        Column(
                            Modifier.weight(1f).clickable { onSelect(destination) }.padding(vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(destination.icon, contentDescription = null,
                                tint = if (selected == destination) FlintColors.Ember else FlintColors.TextMuted)
                            Text(destination.label, color = if (selected == destination) FlintColors.TextPrimary else FlintColors.TextMuted,
                                fontSize = 8.sp)
                        }
                    }
                }
            }
        }
    }
}
