package com.flintlauncher.android.ui.home

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flintlauncher.android.ui.state.FriendsUi
import com.flintlauncher.android.ui.state.HomeActions
import com.flintlauncher.android.ui.state.HomeUiState
import com.flintlauncher.android.ui.state.Loadable
import com.flintlauncher.android.ui.state.ServerStatus

private object HomePalette {
    val Background = Color(0xFF0B1116)
    val Rail = Color(0xFF101820)
    val Panel = Color(0xFF151E26)
    val PanelRaised = Color(0xFF202B35)
    val Accent = Color(0xFFFF8A1F)
    val AccentDeep = Color(0xFFD96B12)
    val Text = Color(0xFFF4F6F8)
    val Muted = Color(0xFFA7B0B9)
    val Online = Color(0xFF42D477)
    val Offline = Color(0xFFE05B5B)
    val Unknown = Color(0xFF89939D)
}

private val HomeShape = RoundedCornerShape(9.dp)

@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(HomePalette.Background)) {
        val viewportHeight = maxHeight
        val landscape = maxWidth >= 700.dp && viewportHeight >= 400.dp
        if (landscape) {
            Row(
                Modifier.fillMaxSize().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    Modifier.weight(3.15f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MainHero(
                        state = state,
                        actions = actions,
                        height = (viewportHeight - 132.dp).coerceIn(300.dp, 430.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        NewsPanel(state, actions, Modifier.weight(1.35f))
                        FriendsPanel(state, Modifier.weight(1f))
                    }
                    Text(
                        "Flint Launcher  •  " + state.launcherVersion.ifBlank { "development" },
                        color = HomePalette.Muted,
                        fontSize = 9.sp,
                        modifier = Modifier.align(Alignment.End),
                    )
                }
                PartnerRail(
                    state = state,
                    actions = actions,
                    modifier = Modifier.weight(1.08f).fillMaxHeight(),
                )
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MainHero(state, actions, 330.dp)
                NewsPanel(state, actions, Modifier.fillMaxWidth())
                FriendsPanel(state, Modifier.fillMaxWidth())
                PartnerRail(state, actions, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun MainHero(state: HomeUiState, actions: HomeActions, height: Dp) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(height),
        shape = HomeShape,
        color = HomePalette.Panel,
        border = BorderStroke(1.dp, HomePalette.PanelRaised),
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1B2932),
                        Color(0xFF111B23),
                        Color(0xFF202A2D),
                        Color(0xFF111820),
                    ),
                ),
            ),
        ) {
            // Restrained block-like panels echo the Minecraft artwork without using
            // fabricated account, player, or server data.
            Row(
                Modifier.fillMaxWidth().height(74.dp).align(Alignment.TopStart),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF24333A).copy(alpha = 0.36f)))
                Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF0B151C).copy(alpha = 0.28f)))
                Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF344239).copy(alpha = 0.23f)))
            }
            Column(
                Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    state.account?.username ?: "GUEST",
                    color = HomePalette.Text,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                )
                Text(
                    "MINECRAFT JAVA EDITION",
                    color = HomePalette.Text.copy(alpha = 0.94f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    state.selected?.let { it.minecraftVersion + "  ·  " + it.loader + "  ·  Java " + it.javaMajor }
                        ?: "Select an installation to get started",
                    color = HomePalette.Muted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Button(
                    onClick = { actions.onLaunch?.invoke() },
                    enabled = state.selected != null && actions.onLaunch != null,
                    shape = RoundedCornerShape(7.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HomePalette.Accent,
                        contentColor = Color(0xFF17120D),
                        disabledContainerColor = HomePalette.PanelRaised,
                        disabledContentColor = HomePalette.Muted,
                    ),
                    modifier = Modifier.width(228.dp).height(54.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("LAUNCH GAME", fontSize = 15.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Select installation")
                }
                InstallationMenu(state, actions)
                state.launchMessage?.let { message ->
                    Surface(
                        color = Color(0xDD101820),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, HomePalette.Accent.copy(alpha = 0.55f)),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                message,
                                color = HomePalette.Text,
                                fontSize = 9.sp,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = { actions.onDismissLaunchMessage?.invoke() },
                                enabled = actions.onDismissLaunchMessage != null,
                            ) { Text("DISMISS", color = HomePalette.Accent, fontSize = 9.sp) }
                        }
                    }
                }
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(3) { index ->
                    val installation = state.installations.getOrNull(index)
                    val selected = installation?.id == state.selectedInstallationId
                    Surface(
                        modifier = Modifier.weight(1f).height(48.dp).clickable {
                            if (installation != null) actions.onSelectInstallation?.invoke(installation.id)
                            else actions.onOpenInstances?.invoke()
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = if (selected) Color(0xE52B3136) else Color(0xE511171D),
                        border = BorderStroke(
                            1.dp,
                            if (selected) HomePalette.Accent else HomePalette.PanelRaised,
                        ),
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            if (installation == null) {
                                Icon(Icons.Filled.Add, contentDescription = null, tint = HomePalette.Muted)
                                Spacer(Modifier.width(5.dp))
                                Text("Add a Profile", color = HomePalette.Text, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        installation.name,
                                        color = HomePalette.Text,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        installation.minecraftVersion + " · " + installation.loader,
                                        color = HomePalette.Muted,
                                        fontSize = 8.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallationMenu(state: HomeUiState, actions: HomeActions) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            enabled = state.installations.isNotEmpty() && actions.onSelectInstallation != null,
        ) {
            Text(
                if (state.installations.size > 1) "CHANGE PROFILE" else "PROFILE OPTIONS",
                color = HomePalette.Text,
                fontSize = 9.sp,
                letterSpacing = 0.7.sp,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.installations.forEach { installation ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(installation.name)
                            Text(
                                installation.minecraftVersion + " · " + installation.loader,
                                color = HomePalette.Muted,
                                fontSize = 11.sp,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        actions.onSelectInstallation?.invoke(installation.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun NewsPanel(state: HomeUiState, actions: HomeActions, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = HomeShape,
        color = HomePalette.Panel,
        border = BorderStroke(1.dp, HomePalette.PanelRaised),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("NEWS", color = HomePalette.Text, fontSize = 12.sp, fontWeight = FontWeight.Black)
            when (val news = state.news) {
                Loadable.Loading -> Text("Loading news…", color = HomePalette.Muted, fontSize = 10.sp)
                is Loadable.Error -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(news.message, color = HomePalette.Muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { actions.onRetryNews?.invoke() }, enabled = actions.onRetryNews != null) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Retry news", tint = HomePalette.Accent)
                    }
                }
                is Loadable.Content -> {
                    if (news.value.isEmpty()) {
                        Text("No news available.", color = HomePalette.Muted, fontSize = 10.sp)
                    } else {
                        news.value.take(2).forEach { item ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable(
                                    enabled = actions.onOpenNews != null,
                                    onClick = { actions.onOpenNews?.invoke(item.id) },
                                ),
                                shape = RoundedCornerShape(6.dp),
                                color = HomePalette.PanelRaised,
                            ) {
                                Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(item.category.uppercase() + "  ·  " + item.dateLabel, color = HomePalette.Accent, fontSize = 8.sp)
                                    Text(item.title, color = HomePalette.Text, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(item.summary, color = HomePalette.Muted, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendsPanel(state: HomeUiState, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = HomeShape,
        color = HomePalette.Panel,
        border = BorderStroke(1.dp, HomePalette.PanelRaised),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("FRIENDS", color = HomePalette.Text, fontSize = 12.sp, fontWeight = FontWeight.Black)
            when (val friends = state.friends) {
                FriendsUi.Unavailable -> Text(
                    "Friends are not connected yet.",
                    color = HomePalette.Muted,
                    fontSize = 10.sp,
                )
                is FriendsUi.Available -> {
                    if (friends.names.isEmpty()) {
                        Text("No friends yet. Find a player by Minecraft name or friend code.", color = HomePalette.Muted, fontSize = 10.sp)
                    } else {
                        friends.names.take(5).forEach { name ->
                            Text(name, color = HomePalette.Text, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PartnerRail(state: HomeUiState, actions: HomeActions, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = HomeShape,
        color = HomePalette.Panel,
        border = BorderStroke(1.dp, HomePalette.PanelRaised),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text("PARTNER SERVERS", color = HomePalette.Text, fontSize = 12.sp, fontWeight = FontWeight.Black)
            when (val partners = state.partners) {
                Loadable.Loading -> Text("Checking server status…", color = HomePalette.Muted, fontSize = 10.sp)
                is Loadable.Error -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(partners.message, color = HomePalette.Muted, fontSize = 10.sp)
                    TextButton(onClick = { actions.onRefreshPartners?.invoke() }, enabled = actions.onRefreshPartners != null) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh servers", tint = HomePalette.Accent)
                        Text("Refresh", color = HomePalette.Accent)
                    }
                }
                is Loadable.Content -> {
                    if (partners.value.isEmpty()) {
                        Text("No partner servers configured.", color = HomePalette.Muted, fontSize = 10.sp)
                    } else {
                        partners.value.forEach { server ->
                            val statusColor = when (server.status) {
                                is ServerStatus.Online -> HomePalette.Online
                                ServerStatus.Offline -> HomePalette.Offline
                                ServerStatus.Unknown -> HomePalette.Unknown
                            }
                            Surface(
                                shape = RoundedCornerShape(7.dp),
                                color = HomePalette.PanelRaised,
                                border = BorderStroke(1.dp, HomePalette.PanelRaised),
                            ) {
                                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(server.name, color = HomePalette.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("●", color = statusColor, fontSize = 10.sp)
                                    }
                                    Text(server.address, color = HomePalette.Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        when (val status = server.status) {
                                            is ServerStatus.Online -> "Online" + (status.players?.let { " · " + it + " players" } ?: "")
                                            ServerStatus.Offline -> "Offline"
                                            ServerStatus.Unknown -> "Status unknown"
                                        },
                                        color = statusColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        TextButton(
                                            onClick = { actions.onCopyAddress?.invoke(server.address) },
                                            enabled = actions.onCopyAddress != null,
                                        ) {
                                            Text("COPY", color = HomePalette.Muted, fontSize = 8.sp)
                                        }
                                        TextButton(
                                            onClick = { server.website?.let { actions.onVisitServer?.invoke(it) } },
                                            enabled = server.website != null && actions.onVisitServer != null,
                                        ) { Text("VISIT", color = HomePalette.Accent, fontSize = 8.sp) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(1.dp))
            Text(
                "Server counts and presence are shown only when a live status result is available.",
                color = HomePalette.Muted,
                fontSize = 8.sp,
                lineHeight = 11.sp,
            )
        }
    }
}


@Composable
fun PartnersScreen(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(HomePalette.Background)) {
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("PARTNER SERVERS", color = HomePalette.Text, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text(
                "Live status is shown only when the server status service has returned a result.",
                color = HomePalette.Muted,
                fontSize = 10.sp,
            )
            PartnerRail(
                state = state,
                actions = actions,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}
