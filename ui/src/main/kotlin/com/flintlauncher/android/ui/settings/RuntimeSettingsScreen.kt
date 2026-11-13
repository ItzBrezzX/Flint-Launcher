package com.flintlauncher.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flintlauncher.android.ui.state.RuntimeSettingsActions
import com.flintlauncher.android.ui.state.RuntimeSettingsUiState
import com.flintlauncher.android.ui.theme.FlintColors

@Composable
fun RuntimeSettingsScreen(
    state: RuntimeSettingsUiState,
    actions: RuntimeSettingsActions,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 760.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("Device-aware defaults and game compatibility", color = FlintColors.TextMuted)
                    }
                    OutlinedButton(onClick = { actions.onRefresh?.invoke() }, enabled = actions.onRefresh != null) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh runtimes")
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh")
                    }
                }
            }

            if (wide) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DeviceCard(state, Modifier.weight(1f))
                        PerformanceCard(state, Modifier.weight(1f))
                    }
                }
                item { RuntimeCard(state, actions) }
                item { GraphicsCard() }
                item { CompatibilityCard() }
            } else {
                item { DeviceCard(state, Modifier.fillMaxWidth()) }
                item { RuntimeCard(state, actions) }
                item { GraphicsCard() }
                item { PerformanceCard(state, Modifier.fillMaxWidth()) }
                item { CompatibilityCard() }
            }

            state.message?.let { message ->
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = FlintColors.Panel,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(message, modifier = Modifier.padding(12.dp), color = FlintColors.TextPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(state: RuntimeSettingsUiState, modifier: Modifier = Modifier) {
    SettingsCard("DEVICE") {
        SettingLine("Model", state.deviceModel)
        SettingLine("Android", state.androidVersion)
        SettingLine("App process ABI", state.processArch ?: "Unsupported")
        SettingLine("Supported ABIs", state.supportedAbis.joinToString().ifBlank { "Unknown" })
        SettingLine("Physical memory", if (state.totalMemoryMb > 0) state.totalMemoryMb / 1024.toString() + " GB" else "Unknown")
    }
}

@Composable
private fun RuntimeCard(state: RuntimeSettingsUiState, actions: RuntimeSettingsActions) {
    SettingsCard("JAVA RUNTIME") {
        Text(
            "Flint downloads the mobile OpenJDK build that matches this app's process ABI. " +
                "The archive size and SHA-256 are checked before installation.",
            color = FlintColors.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        if (state.loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(color = FlintColors.Ember)
                Text("Checking installed runtimes…")
            }
        } else if (state.installedRuntimes.isEmpty()) {
            Text("No verified Java runtime installed.", color = FlintColors.TextMuted)
        } else {
            state.installedRuntimes.forEach { runtime ->
                SettingLine(
                    "Java " + runtime.major,
                    runtime.arch + " · " + runtime.vendor + " · " + if (runtime.verified) "Verified" else "Not verified",
                )
            }
        }

        if (state.availableJavaMajors.isEmpty()) {
            Text(
                "No downloadable runtime is published for this app ABI. A different APK ABI would be required.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text("Available for this ABI", fontWeight = FontWeight.SemiBold)
            state.availableJavaMajors.forEach { major ->
                val installed = state.installedRuntimes.any { it.major == major && it.verified }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("OpenJDK " + major, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (installed) "Installed and verified" else "Mobile runtime · SHA-256 verified download",
                            color = FlintColors.TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    when {
                        installed -> Text("Installed", color = FlintColors.Ember)
                        state.installingMajor == major -> CircularProgressIndicator(color = FlintColors.Ember)
                        state.installingMajor != null -> Text("Busy", color = FlintColors.TextMuted)
                        else -> Button(
                            onClick = { actions.onInstallJava?.invoke(major) },
                            enabled = actions.onInstallJava != null,
                        ) { Text("Install") }
                    }
                }
            }
        }
    }
}

@Composable
private fun GraphicsCard() {
    SettingsCard("GRAPHICS & RENDERER") {
        SettingLine("Renderer default", "Auto (not available in this build)")
        SettingLine("Compatibility renderer", "GL4ES · not bundled yet")
        SettingLine("VSync", "Off (recommended starting point)")
        SettingLine("FPS limit", "30 (low-power starting point)")
        Text(
            "Flint currently has an EGL/GLES smoke test, not a Minecraft renderer package. " +
                "Renderer choices stay unavailable until their native libraries are included and tested on-device.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PerformanceCard(state: RuntimeSettingsUiState, modifier: Modifier = Modifier) {
    SettingsCard("PERFORMANCE DEFAULTS") {
        SettingLine("Suggested Java heap", state.recommendedMemoryMb.toString() + " MB")
        SettingLine("Render distance", "4–6 chunks (starting profile)")
        SettingLine("Graphics", "Fast")
        SettingLine("Smooth lighting", "Off")
        SettingLine("Particles", "Minimal")
        Text(
            "These are safe starting recommendations only. Flint has not connected these values to the game launch arguments yet.",
            color = FlintColors.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun CompatibilityCard() {
    SettingsCard("AUTOMATIC VERSION COMPATIBILITY") {
        Text(
            "1. Flint reads the Java major version from Mojang's version metadata when it is present.\n" +
                "2. For old versions with no Java metadata, Flint uses Java 8 as its legacy default.\n" +
                "3. It selects the lowest installed Java major that is equal to or newer than the game's requirement.\n" +
                "4. If the required runtime is missing, install the matching mobile runtime above.\n" +
                "5. Unsupported ABI or renderer conditions must be shown as blockers, never ignored.",
            color = FlintColors.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = FlintColors.Panel),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, color = FlintColors.Ember, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            content()
        }
    }
}

@Composable
private fun SettingLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = FlintColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        Text(value, color = FlintColors.TextPrimary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}
