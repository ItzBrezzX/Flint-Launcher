package com.flintlauncher.android.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.flintlauncher.android.ui.theme.FlintColors

/** Top-level destinations. There is intentionally no STORE: MODS replaces it. */
enum class Destination(val label: String, val icon: ImageVector) {
    PLAY("PLAY", Icons.Filled.PlayArrow),
    PROFILES("PROFILES", Icons.Filled.AccountCircle),
    MODS("MODS", Icons.Filled.Build),
    PARTNERS("PARTNERS", Icons.Filled.Star),
    SETTINGS("SETTINGS", Icons.Filled.Settings),
}

/**
 * Phones (narrow): bottom navigation bar, thumb reachable.
 * Tablets and landscape (>= 600dp): navigation rail on the left.
 */
@Composable
fun FlintScaffold(
    selected: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (maxWidth >= 600.dp) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(containerColor = FlintColors.Panel) {
                    Destination.entries.forEach { destination ->
                        NavigationRailItem(
                            selected = destination == selected,
                            onClick = { onSelect(destination) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = FlintColors.OnEmber,
                                selectedTextColor = FlintColors.Ember,
                                indicatorColor = FlintColors.Ember,
                                unselectedIconColor = FlintColors.TextMuted,
                                unselectedTextColor = FlintColors.TextMuted,
                            ),
                        )
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .navigationBarsPadding(),
                ) { content() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) { content() }
                NavigationBar(containerColor = FlintColors.Panel) {
                    Destination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = destination == selected,
                            onClick = { onSelect(destination) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = FlintColors.OnEmber,
                                selectedTextColor = FlintColors.Ember,
                                indicatorColor = FlintColors.Ember,
                                unselectedIconColor = FlintColors.TextMuted,
                                unselectedTextColor = FlintColors.TextMuted,
                            ),
                        )
                    }
                }
            }
        }
    }
}
