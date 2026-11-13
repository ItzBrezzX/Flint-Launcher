package com.flintlauncher.android.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flintlauncher.android.ui.state.HomeActions
import com.flintlauncher.android.ui.state.HomeUiState
import com.flintlauncher.android.ui.theme.FlintColors

private val HomeCardShape = RoundedCornerShape(8.dp)

/**
 * Landscape-first launcher dashboard. All displayed instance and server data
 * comes from HomeUiState; navigation and launch controls use real callbacks.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 560.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = if (wide) 26.dp else 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("HOME", color = FlintColors.Ember, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                    Text(
                        text = if (state.account == null) "Welcome to Flint" else "Welcome back, ${state.account.username}",
                        color = FlintColors.TextPrimary, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text("MINECRAFT JAVA EDITION", color = FlintColors.TextMuted,
                    fontSize = 9.sp, letterSpacing = 1.sp)
            }

            if (wide) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column(Modifier.weight(1.55f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        HeroBanner()
                        FeaturedSection(actions)
                        NewsSection(state.news, actions)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        InstancePanel(state, actions)
                        PartnersSection(state.partners, actions)
                    }
                }
            } else {
                HeroBanner()
                InstancePanel(state, actions)
                FeaturedSection(actions)
                NewsSection(state.news, actions)
                PartnersSection(state.partners, actions)
            }
            Text(
                "Flint Launcher · v${state.launcherVersion.ifBlank { "development" }}",
                color = FlintColors.TextMuted, fontSize = 10.sp,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
private fun HeroBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth().height(154.dp),
        color = FlintColors.Panel,
        shape = HomeCardShape,
        border = BorderStroke(1.dp, FlintColors.PanelRaised),
    ) {
        // Intentionally empty: this is the reserved hero-art area from the reference layout.
        Box(Modifier.fillMaxSize())
    }
}

@Composable
private fun FeaturedSection(actions: HomeActions) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("FEATURED")
            Spacer(Modifier.weight(1f))
            Text("QUICK ACCESS", color = FlintColors.TextMuted, fontSize = 9.sp, letterSpacing = 1.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FeaturedTile(
                title = "INSTANCES",
                description = "Installations and game versions",
                enabled = actions.onOpenInstances != null,
                onClick = { actions.onOpenInstances?.invoke() },
                modifier = Modifier.weight(1f),
            )
            FeaturedTile(
                title = "MOD LIBRARY",
                description = "Mods, shaders and packs",
                enabled = actions.onOpenLibrary != null,
                onClick = { actions.onOpenLibrary?.invoke() },
                modifier = Modifier.weight(1f),
            )
            FeaturedTile(
                title = "SETTINGS",
                description = "Java and graphics options",
                enabled = actions.onOpenSettings != null,
                onClick = { actions.onOpenSettings?.invoke() },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FeaturedTile(
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(94.dp).clickable(enabled = enabled, onClick = onClick),
        color = FlintColors.Panel,
        shape = HomeCardShape,
        border = BorderStroke(1.dp, FlintColors.PanelRaised),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, color = FlintColors.Ember, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(description, color = FlintColors.TextMuted, fontSize = 9.sp,
                lineHeight = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun InstancePanel(state: HomeUiState, actions: HomeActions) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = FlintColors.Panel,
        shape = HomeCardShape,
        border = BorderStroke(1.dp, FlintColors.PanelRaised),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("YOUR INSTANCE", color = FlintColors.TextPrimary, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    Text("Select a game installation", color = FlintColors.TextMuted, fontSize = 10.sp)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (state.selected == null) "NOT READY" else "DETECTED",
                    color = if (state.selected == null) FlintColors.TextMuted else FlintColors.Ember,
                    fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
                )
            }
            Surface(color = FlintColors.Charcoal, shape = RoundedCornerShape(6.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(state.selected?.name ?: "No instance selected",
                        color = FlintColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        state.selected?.let { "Minecraft ${it.minecraftVersion}  ·  ${it.loader}  ·  Java ${it.javaMajor}" }
                            ?: "Install a Minecraft version to get started.",
                        color = FlintColors.TextMuted, fontSize = 10.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    if (state.installations.size > 1) {
                        Text("${state.installations.size} installations available · use the selector below",
                            color = FlintColors.Ember, fontSize = 9.sp)
                    }
                }
            }
            LaunchButton(state, actions)
            TextButton(
                onClick = { actions.onOpenInstances?.invoke() },
                enabled = actions.onOpenInstances != null,
                modifier = Modifier.align(Alignment.End),
            ) { Text("MANAGE INSTANCES", fontSize = 10.sp, color = FlintColors.Ember) }
        }
    }
}
