package com.flintlauncher.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flintlauncher.android.ui.state.HomeActions
import com.flintlauncher.android.ui.state.HomeUiState
import com.flintlauncher.android.ui.theme.FlintColors

/**
 * PLAY dashboard. Android-native adaptation of the desktop layout:
 * a single scrolling column on phones, and a main column plus a partners
 * column on landscape and wide screens (>= 600dp of content width). It renders [state] as given; empty,
 * loading and error states are shown as such, never filled with sample data.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        val compactLandscape = maxHeight < 520.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = if (wide) 18.dp else 16.dp, vertical = if (compactLandscape) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compactLandscape) 10.dp else 16.dp),
        ) {
            HomeTopBar(state, actions)
            if (wide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1.6f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) { MainSections(state, actions, compactLandscape) }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) { PartnersSection(state.partners, actions) }
                }
            } else {
                MainSections(state, actions, compactLandscape)
                PartnersSection(state.partners, actions)
            }
            if (state.launcherVersion.isNotBlank()) {
                Text(
                    text = "v${state.launcherVersion}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HomeHeroBanner(state: HomeUiState, compactLandscape: Boolean) {
    val selected = state.selected
    Surface(
        modifier = Modifier.fillMaxWidth().height(if (compactLandscape) 142.dp else 176.dp),
        color = FlintColors.Panel,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, FlintColors.PanelRaised),
    ) {
        Row(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF102C18), FlintColors.Panel, Color(0xFF0A1710)),
                ),
            ).padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "FLINT  /  JAVA EDITION",
                    color = FlintColors.Ember,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                )
                Text(
                    if (selected == null) "Your game starts here" else selected.name,
                    color = FlintColors.TextPrimary,
                    fontSize = if (compactLandscape) 21.sp else 25.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
                Text(
                    selected?.summary ?: "Create or select an instance to continue",
                    color = FlintColors.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
                Text(
                    if (selected == null) "NO INSTANCE SELECTED" else "INSTANCE SELECTED",
                    color = if (selected == null) FlintColors.TextMuted else FlintColors.Ember,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.width(if (compactLandscape) 112.dp else 148.dp).height(112.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF245B2D), Color(0xFF102519))))
                    .border(1.dp, FlintColors.Ember.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(27.dp).background(Color(0xFF73B84A)))
                        Box(Modifier.size(27.dp).background(Color(0xFF3D873C)))
                        Box(Modifier.size(27.dp).background(Color(0xFF9BBD62)))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(27.dp).background(Color(0xFF356B35)))
                        Box(Modifier.size(27.dp).background(Color(0xFF6E9B43)))
                        Box(Modifier.size(27.dp).background(Color(0xFF28552D)))
                    }
                    Text("MINECRAFT", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                }
            }
        }
    }
}

@Composable
private fun LaunchMessageCard(message: String, actions: HomeActions) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = FlintColors.Panel,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, FlintColors.PanelRaised),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = FlintColors.TextMuted,
            )
            TextButton(
                onClick = { actions.onDismissLaunchMessage?.invoke() },
                enabled = actions.onDismissLaunchMessage != null,
            ) { Text("Dismiss") }
        }
    }
}

@Composable
private fun LaunchProgressCard(state: HomeUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = FlintColors.Panel,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, FlintColors.PanelRaised),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                state.launchStage ?: "Preparing Minecraft environment",
                color = FlintColors.TextPrimary,
                style = MaterialTheme.typography.labelLarge,
            )
            val percent = state.launchProgressPercent
            if (percent == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = (percent / 100f).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val total = state.launchTotalBytes
            val downloaded = state.launchDownloadedBytes
            val size = if (total != null && total > 0) {
                "%.1f / %.1f MB".format(downloaded / 1_048_576f, total / 1_048_576f)
            } else {
                "%.1f MB".format(downloaded / 1_048_576f)
            }
            val speed = if (state.launchSpeedBytesPerSecond > 0) {
                " · %.2f MB/s".format(state.launchSpeedBytesPerSecond / 1_048_576f)
            } else ""
            Text(
                (percent?.let { "${it.toInt()}% · " } ?: "") + size + speed,
                color = FlintColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun MainSections(state: HomeUiState, actions: HomeActions, compactLandscape: Boolean) {
    HomeHeroBanner(state, compactLandscape)
    LaunchButton(state, actions)
    if (state.launchBusy) LaunchProgressCard(state)
    if (!state.launchBusy) state.launchMessage?.let { LaunchMessageCard(it, actions) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("YOUR INSTANCES", color = FlintColors.TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
        Spacer(Modifier.weight(1f))
        Text("${state.installations.size} available", color = FlintColors.TextMuted, fontSize = 9.sp)
    }
    ProfileSlots(state, actions)
    NewsSection(state.news, actions)
    FriendsSection(state.friends)
}
