package com.flintlauncher.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Flint palette: charcoal surfaces with a single ember-orange accent. */
object FlintColors {
    val Charcoal = Color(0xFF07110B)
    val Panel = Color(0xFF101B14)
    val PanelRaised = Color(0xFF19271D)
    val Ember = Color(0xFF35D12C)
    val EmberDark = Color(0xFF208D26)
    val OnEmber = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFFEAF2EA)
    val TextMuted = Color(0xFFA6B4A8)
    val StatusOnline = Color(0xFF4CAF50)
    val StatusOffline = Color(0xFFE5484D)
    val StatusUnknown = Color(0xFF6E6E76)
    val Surface = Panel
    val SurfaceVariant = PanelRaised
}

private val FlintDarkScheme = darkColorScheme(
    primary = FlintColors.Ember,
    onPrimary = FlintColors.OnEmber,
    background = FlintColors.Charcoal,
    onBackground = FlintColors.TextPrimary,
    surface = FlintColors.Panel,
    onSurface = FlintColors.TextPrimary,
    surfaceVariant = FlintColors.PanelRaised,
    onSurfaceVariant = FlintColors.TextMuted,
)

@Composable
fun FlintTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FlintDarkScheme, content = content)
}
