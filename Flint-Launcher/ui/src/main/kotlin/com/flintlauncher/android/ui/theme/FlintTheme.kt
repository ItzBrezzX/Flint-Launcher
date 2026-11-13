package com.flintlauncher.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Flint palette: charcoal surfaces with a single ember-orange accent. */
object FlintColors {
    val Charcoal = Color(0xFF141416)
    val Panel = Color(0xFF1E1E21)
    val PanelRaised = Color(0xFF28282C)
    val Ember = Color(0xFFFF7A1A)
    val EmberDark = Color(0xFFD9600C)
    val OnEmber = Color(0xFF1A0E02)
    val TextPrimary = Color(0xFFECECEE)
    val TextMuted = Color(0xFF9A9AA2)
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
