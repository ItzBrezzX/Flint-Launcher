package com.flintlauncher.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flintlauncher.android.ui.state.HomeActions
import com.flintlauncher.android.ui.state.HomeUiState
import com.flintlauncher.android.ui.theme.FlintColors

private val HomeBg = Color(0xFF080A0C)
private val HeroPanel = Color(0xFF101317)
private val HeroOrange = Color(0xFFFF8A18)
private val HeroText = Color(0xFFF4F1EC)
private val HeroMuted = Color(0xFFB7B2AA)
private val HeroShape = RoundedCornerShape(10.dp)

@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(HomeBg)) {
        val wide = maxWidth >= 700.dp
        val compact = maxHeight < 520.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = if (wide) 18.dp else 12.dp, vertical = if (compact) 8.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
        ) {
            if (wide) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(
                        Modifier.weight(1.75f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MainHero(state, actions, compact)
                        state.launchMessage?.let { LaunchMessageCard(it, actions) }
                        ProfileSlots(state, actions)
                        NewsSection(state.news, actions)
                        FriendsSection(state.friends)
                    }
                    Column(
                        Modifier.weight(0.82f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        HomeTopBar(state, actions)
                        PartnersSection(state.partners, actions)
                    }
                }
            } else {
                HomeTopBar(state, actions)
                MainHero(state, actions, compact)
                state.launchMessage?.let { LaunchMessageCard(it, actions) }
                ProfileSlots(state, actions)
                NewsSection(state.news, actions)
                FriendsSection(state.friends)
                PartnersSection(state.partners, actions)
            }
            if (state.launcherVersion.isNotBlank()) {
                Text(
                    "Flint Launcher  ·  v${state.launcherVersion}",
                    color = HeroMuted,
                    fontSize = 9.sp,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

@Composable
private fun MainHero(state: HomeUiState, actions: HomeActions, compact: Boolean) {
    val selected = state.selected
    Surface(
        modifier = Modifier.fillMaxWidth().height(if (compact) 250.dp else 330.dp),
        shape = HeroShape,
        color = HeroPanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF25282B)),
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1C2024), Color(0xFF101317), Color(0xFF090B0D)),
                ),
            ),
        ) {
            Column(
                Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    state.account?.username ?: "GUEST",
                    modifier = Modifier.clip(RoundedCornerShape(5.dp))
                        .background(Color(0xD9000000))
                        .border(1.dp, Color(0xFF34383C), RoundedCornerShape(5.dp))
                        .padding(horizontal = 13.dp, vertical = 5.dp),
                    color = HeroText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
                Text(
                    "MINECRAFT JAVA EDITION",
                    color = HeroText,
                    fontSize = if (compact) 15.sp else 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.1.sp,
                )
                Text(
                    selected?.summary ?: "Select a profile to get started",
                    color = HeroMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LaunchButton(state, actions)
            }
        }
    }
}

@Composable
private fun LaunchMessageCard(message: String, actions: HomeActions) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = HeroShape,
        color = HeroPanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF35302A)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                message,
                modifier = Modifier.weight(1f),
                color = HeroMuted,
                fontSize = 10.sp,
            )
            androidx.compose.material3.TextButton(
                onClick = { actions.onDismissLaunchMessage?.invoke() },
                enabled = actions.onDismissLaunchMessage != null,
            ) { Text("DISMISS", color = HeroOrange, fontSize = 9.sp) }
        }
    }
}
