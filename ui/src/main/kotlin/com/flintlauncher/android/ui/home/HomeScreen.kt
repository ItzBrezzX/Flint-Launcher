package com.flintlauncher.android.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flintlauncher.android.ui.state.HomeActions
import com.flintlauncher.android.ui.state.HomeUiState
import com.flintlauncher.android.ui.theme.FlintColors

/**
 * PLAY dashboard. Android-native adaptation of the desktop layout:
 * a single scrolling column on phones, and a main column plus a partners
 * column on wide screens (>= 840dp). It renders [state] as given; empty,
 * loading and error states are shown as such, never filled with sample data.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 540.dp
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
private fun EmptyHeroBanner(height: androidx.compose.ui.unit.Dp) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(height),
        color = FlintColors.Panel,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, FlintColors.PanelRaised),
    ) { }
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
    EmptyHeroBanner(if (compactLandscape) 132.dp else 190.dp)
    LaunchButton(state, actions)
    if (state.launchBusy) LaunchProgressCard(state)
    state.launchMessage?.let { LaunchMessageCard(it, actions) }
    ProfileSlots(state, actions)
    NewsSection(state.news, actions)
    FriendsSection(state.friends)
}
