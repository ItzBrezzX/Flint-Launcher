package com.flintlauncher.android.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
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
 * PLAY dashboard. Phone layouts use lazy item composition so off-screen sections
 * are not composed and measured until needed. Wide layouts retain the two-column view.
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(
                horizontal = if (wide) 18.dp else 16.dp,
                vertical = if (compactLandscape) 8.dp else 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(if (compactLandscape) 10.dp else 16.dp),
        ) {
            item { HomeTopBar(state, actions) }
            if (wide) {
                item {
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
                }
            } else {
                item { EmptyHeroBanner(if (compactLandscape) 132.dp else 190.dp) }
                item { LaunchButton(state, actions) }
                state.launchMessage?.let { message -> item { LaunchMessageCard(message, actions) } }
                item { ProfileSlots(state, actions) }
                item { NewsSection(state.news, actions) }
                item { FriendsSection(state.friends) }
                item { PartnersSection(state.partners, actions) }
            }
            if (state.launcherVersion.isNotBlank()) {
                item {
                    Text(
                        text = "v${state.launcherVersion}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
private fun MainSections(state: HomeUiState, actions: HomeActions, compactLandscape: Boolean) {
    EmptyHeroBanner(if (compactLandscape) 132.dp else 190.dp)
    LaunchButton(state, actions)
    state.launchMessage?.let { LaunchMessageCard(it, actions) }
    ProfileSlots(state, actions)
    NewsSection(state.news, actions)
    FriendsSection(state.friends)
}
