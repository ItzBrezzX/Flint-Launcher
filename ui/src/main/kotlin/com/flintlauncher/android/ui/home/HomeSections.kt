package com.flintlauncher.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flintlauncher.android.ui.state.AccountKind
import com.flintlauncher.android.ui.state.FriendsUi
import com.flintlauncher.android.ui.state.HomeActions
import com.flintlauncher.android.ui.state.HomeUiState
import com.flintlauncher.android.ui.state.InstallationUi
import com.flintlauncher.android.ui.state.Loadable
import com.flintlauncher.android.ui.state.NewsCardUi
import com.flintlauncher.android.ui.state.PartnerServerUi
import com.flintlauncher.android.ui.state.ServerEdition
import com.flintlauncher.android.ui.state.ServerStatus
import com.flintlauncher.android.ui.theme.FlintColors

private const val SLOT_COUNT = 3
private val PanelShape = RoundedCornerShape(16.dp)

// ---------------------------------------------------------------- top bar

@Composable
internal fun HomeTopBar(state: HomeUiState, actions: HomeActions) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(FlintColors.Ember))
            Text(
                text = "FLINT",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
            )
        }
        AccountChip(state, actions)
    }
}

@Composable
private fun AccountChip(state: HomeUiState, actions: HomeActions) {
    val account = state.account
    val onClick = actions.onOpenAccounts
    if (account == null) {
        OutlinedButton(onClick = { onClick?.invoke() }, enabled = onClick != null) {
            Icon(Icons.Filled.Person, contentDescription = null)
            Text("Add account", modifier = Modifier.padding(start = 8.dp))
        }
        return
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(FlintColors.Panel)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(FlintColors.PanelRaised),
            contentAlignment = Alignment.Center,
        ) {
            Text(account.username.take(1).uppercase(), style = MaterialTheme.typography.labelLarge)
        }
        Column {
            Text(account.username, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Offline profiles are always labelled; they are never presented as premium accounts.
            Text(
                text = if (account.kind == AccountKind.OFFLINE) "OFFLINE" else "MICROSOFT",
                style = MaterialTheme.typography.labelSmall,
                color = if (account.kind == AccountKind.OFFLINE) FlintColors.Ember else FlintColors.TextMuted,
            )
        }
        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Accounts")
    }
}

// ------------------------------------------------------- player showcase

@Composable
internal fun PlayerShowcase(state: HomeUiState) {
    val account = state.account
    val selected = state.selected
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF2B2420), FlintColors.Charcoal))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = account?.username ?: "No account",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(FlintColors.Panel)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
            // Neutral silhouette. A real skin render comes from the skin manager, not from here.
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(FlintColors.PanelRaised), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = FlintColors.TextMuted)
            }
            Box(Modifier.width(88.dp).height(96.dp).clip(RoundedCornerShape(8.dp)).background(FlintColors.Panel))
            Text(
                text = if (account == null) "Add an account to show your player" else "Skin preview is not implemented yet",
                style = MaterialTheme.typography.labelMedium,
                color = FlintColors.TextMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip(selected?.minecraftVersion ?: "No version")
                InfoChip(selected?.loader ?: "No loader")
                InfoChip(selected?.name ?: "No profile")
            }
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(FlintColors.Panel)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

// ---------------------------------------------------------- launch button

@Composable
internal fun LaunchButton(state: HomeUiState, actions: HomeActions) {
    val selected = state.selected
    val canLaunch = selected != null && actions.onLaunch != null
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(PanelShape),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .alpha(if (canLaunch) 1f else 0.5f)
                .background(Brush.horizontalGradient(listOf(FlintColors.EmberDark, FlintColors.Ember)))
                .clickable(enabled = canLaunch) { actions.onLaunch?.invoke() },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("LAUNCH GAME", color = FlintColors.OnEmber, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = selected?.summary ?: "No installation selected",
                    color = FlintColors.OnEmber,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            modifier = Modifier
                .width(56.dp)
                .fillMaxHeight()
                .alpha(if (state.installations.isNotEmpty()) 1f else 0.5f)
                .background(FlintColors.EmberDark)
                .clickable(enabled = state.installations.isNotEmpty()) { menuOpen = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose installation", tint = FlintColors.OnEmber)
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                state.installations.forEach { item ->
                    DropdownMenuItem(
                        text = { Text("${item.name}  \u00B7  ${item.summary}") },
                        enabled = actions.onSelectInstallation != null,
                        onClick = {
                            menuOpen = false
                            actions.onSelectInstallation?.invoke(item.id)
                        },
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------- profile slots

@Composable
internal fun ProfileSlots(state: HomeUiState, actions: HomeActions) {
    val shown = state.installations.take(SLOT_COUNT)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(SLOT_COUNT) { index ->
                val item = shown.getOrNull(index)
                Box(Modifier.weight(1f)) {
                    if (item != null) {
                        FilledSlot(item, item.id == state.selectedInstallationId, actions)
                    } else {
                        AddSlot(actions)
                    }
                }
            }
        }
        val extra = state.installations.size - SLOT_COUNT
        if (extra > 0) {
            Text(
                text = "+$extra more in Profiles",
                style = MaterialTheme.typography.labelMedium,
                color = FlintColors.TextMuted,
            )
        }
    }
}

@Composable
private fun FilledSlot(item: InstallationUi, isSelected: Boolean, actions: HomeActions) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(PanelShape)
            .background(if (isSelected) FlintColors.PanelRaised else FlintColors.Panel)
            .border(1.dp, if (isSelected) FlintColors.Ember else Color.Transparent, PanelShape)
            .clickable(enabled = actions.onSelectInstallation != null) { actions.onSelectInstallation?.invoke(item.id) },
    ) {
        Column(Modifier.padding(start = 12.dp, top = 12.dp, end = 36.dp)) {
            Text(item.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${item.minecraftVersion} \u00B7 ${item.loader}", style = MaterialTheme.typography.labelSmall, color = FlintColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.align(Alignment.TopEnd)) {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Profile options for ${item.name}")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    enabled = actions.onEditProfile != null,
                    onClick = { menuOpen = false; actions.onEditProfile?.invoke(item.id) },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    enabled = actions.onDeleteProfile != null,
                    onClick = { menuOpen = false; confirmDelete = true },
                )
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete profile?") },
            text = { Text("\"${item.name}\" will be removed from the launcher.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; actions.onDeleteProfile?.invoke(item.id) }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AddSlot(actions: HomeActions) {
    val enabled = actions.onAddProfile != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(PanelShape)
            .background(FlintColors.Panel)
            .clickable(enabled = enabled) { actions.onAddProfile?.invoke() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text("Add profile", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
    }
}

// -------------------------------------------------------------------- news

@Composable
internal fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.5.sp,
    )
}

@Composable
private fun MutedText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = FlintColors.TextMuted)
}

@Composable
internal fun NewsSection(news: Loadable<List<NewsCardUi>>, actions: HomeActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("NEWS")
        when (news) {
            Loadable.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                MutedText("Loading news\u2026")
            }
            is Loadable.Error -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MutedText(news.message)
                TextButton(onClick = { actions.onRetryNews?.invoke() }, enabled = actions.onRetryNews != null) { Text("Retry") }
            }
            is Loadable.Content -> if (news.value.isEmpty()) {
                MutedText("No news yet. No news source is configured.")
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(news.value, key = { it.id }) { card -> NewsCard(card, actions) }
                }
            }
        }
    }
}

@Composable
private fun NewsCard(card: NewsCardUi, actions: HomeActions) {
    Column(
        modifier = Modifier
            .width(250.dp)
            .clip(PanelShape)
            .background(Brush.linearGradient(listOf(FlintColors.PanelRaised, FlintColors.Panel)))
            .clickable(enabled = actions.onOpenNews != null) { actions.onOpenNews?.invoke(card.id) }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(card.category.uppercase(), style = MaterialTheme.typography.labelSmall, color = FlintColors.Ember, fontWeight = FontWeight.Bold)
        Text(card.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(card.summary, style = MaterialTheme.typography.bodySmall, color = FlintColors.TextMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(card.dateLabel, style = MaterialTheme.typography.labelSmall, color = FlintColors.TextMuted)
    }
}

// ----------------------------------------------------------------- friends

@Composable
internal fun FriendsSection(friends: FriendsUi) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("FRIENDS")
        when (friends) {
            FriendsUi.Unavailable -> MutedText("Friends need an online service that is not configured, so there is no friends list or online status.")
            is FriendsUi.Available -> if (friends.names.isEmpty()) {
                MutedText("No friends yet. Find a player by Minecraft name or friend code.")
            } else {
                friends.names.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

// ---------------------------------------------------------------- partners

@Composable
internal fun PartnersSection(partners: Loadable<List<PartnerServerUi>>, actions: HomeActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SectionHeader("SERVERS")
            IconButton(
                onClick = { actions.onRefreshPartners?.invoke() },
                enabled = actions.onRefreshPartners != null && partners !is Loadable.Loading,
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh server status")
            }
        }
        Text(
            text = "Public servers. Flint is not affiliated with them. Status comes from mcstatus.io.",
            style = MaterialTheme.typography.labelSmall,
            color = FlintColors.TextMuted,
        )
        when (partners) {
            Loadable.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                MutedText("Checking servers\u2026")
            }
            is Loadable.Error -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MutedText(partners.message)
                TextButton(onClick = { actions.onRefreshPartners?.invoke() }, enabled = actions.onRefreshPartners != null) { Text("Retry") }
            }
            is Loadable.Content -> if (partners.value.isEmpty()) {
                MutedText("No servers are configured.")
            } else {
                partners.value.forEach { PartnerCard(it, actions) }
            }
        }
    }
}

@Composable
private fun PartnerCard(server: PartnerServerUi, actions: HomeActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(FlintColors.Panel)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(server.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = if (server.edition == ServerEdition.JAVA) "JAVA" else "BEDROCK",
                style = MaterialTheme.typography.labelSmall,
                color = FlintColors.TextMuted,
            )
        }
        Text(server.address, style = MaterialTheme.typography.bodySmall, color = FlintColors.TextMuted)
        StatusRow(server.status)
        Row {
            TextButton(onClick = { actions.onCopyAddress?.invoke(server.address) }, enabled = actions.onCopyAddress != null) { Text("Copy IP") }
            TextButton(
                onClick = { server.website?.let { actions.onVisitServer?.invoke(it) } },
                enabled = actions.onVisitServer != null && server.website != null,
            ) { Text("Visit") }
        }
    }
}

@Composable
private fun StatusRow(status: ServerStatus) {
    val (color, label) = when (status) {
        ServerStatus.Unknown -> FlintColors.StatusUnknown to "Status unknown"
        ServerStatus.Offline -> FlintColors.StatusOffline to "Offline"
        is ServerStatus.Online -> FlintColors.StatusOnline to
            (if (status.players != null) "Online \u00B7 ${status.players} players" else "Online")
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
