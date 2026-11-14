package com.flintlauncher.android.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flintlauncher.android.ui.state.RuntimeBooleanSetting
import com.flintlauncher.android.ui.state.RuntimeIntSetting
import com.flintlauncher.android.ui.state.RuntimeSettingsActions
import com.flintlauncher.android.ui.state.RuntimeSettingsUiState
import com.flintlauncher.android.ui.state.RuntimeStringSetting
import com.flintlauncher.android.ui.theme.FlintColors

private enum class SettingsSection(val title: String) {
    OVERVIEW("Overview"),
    JAVA("Java"),
    GRAPHICS("Graphics"),
    CONTROLS("Controls"),
    GAME("Game"),
    ADVANCED("Advanced"),
}

@Composable
fun RuntimeSettingsScreen(
    state: RuntimeSettingsUiState,
    actions: RuntimeSettingsActions,
    modifier: Modifier = Modifier,
    onShareDiagnostics: (() -> Unit)? = null,
) {
    var selectedSection by remember { mutableStateOf(SettingsSection.OVERVIEW) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 760.dp
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Runtime, graphics, controls and launcher configuration", color = FlintColors.TextMuted)
                }
                OutlinedButton(onClick = { onShareDiagnostics?.invoke() }, enabled = onShareDiagnostics != null) { Text("Share logs") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { actions.onRefresh?.invoke() }, enabled = actions.onRefresh != null) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh diagnostics")
                    Spacer(Modifier.width(6.dp))
                    Text("Refresh")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsSection.values().forEach { section ->
                    FilterChip(
                        selected = selectedSection == section,
                        onClick = { selectedSection = section },
                        label = { Text(section.title) },
                    )
                }
            }

            Text(
                "Crash and error logs are stored locally. Use Share logs to send the latest diagnostic report for troubleshooting. Logs may include technical device details but do not intentionally include account credentials.",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                color = FlintColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )

            Text(
                "Preferences are stored locally. Options that need the in-game native window/input bridge are saved as defaults but are not applied to Minecraft in this build.",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                color = FlintColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (selectedSection) {
                    SettingsSection.OVERVIEW -> {
                        item { DeviceCard(state, Modifier.fillMaxWidth()) }
                        item { LauncherCard(state, actions) }
                        item { MemorySummaryCard(state) }
                    }
                    SettingsSection.JAVA -> {
                        item { RuntimeCard(state, actions) }
                        item { JavaTuningCard(state, actions) }
                    }
                    SettingsSection.GRAPHICS -> {
                        item { GraphicsCard(state, actions) }
                    }
                    SettingsSection.CONTROLS -> {
                        item { ControlsCard(state, actions) }
                    }
                    SettingsSection.GAME -> {
                        item { GameBehaviorCard(state, actions) }
                    }
                    SettingsSection.ADVANCED -> {
                        item { AdvancedCard(state, actions, wide) }
                    }
                }

                state.message?.let { message ->
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = FlintColors.Panel,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(message, modifier = Modifier.weight(1f), color = FlintColors.TextPrimary)
                                TextButton(onClick = { actions.onDismissMessage?.invoke() }) { Text("Dismiss") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(state: RuntimeSettingsUiState, modifier: Modifier = Modifier) {
    SettingsCard("DEVICE & COMPATIBILITY", modifier) {
        SettingLine("Device", state.deviceModel)
        SettingLine("Android", state.androidVersion)
        SettingLine("App process ABI", state.processArch ?: "Unsupported")
        SettingLine("Supported ABIs", state.supportedAbis.joinToString().ifBlank { "Unknown" })
        SettingLine("Physical memory", if (state.totalMemoryMb > 0) "%.1f GB".format(state.totalMemoryMb / 1024f) else "Unknown")
        SettingLine("System EGL / GLES", state.rendererDiagnostics)
        NoteText("A successful system GPU probe does not mean a Minecraft renderer is installed.")
    }
}

@Composable
private fun MemorySummaryCard(state: RuntimeSettingsUiState) {
    SettingsCard("DEVICE MEMORY PROFILE") {
        SettingLine("Suggested heap", state.recommendedMemoryMb.toString() + " MB")
        SettingLine("Current heap preference", state.javaHeapMb.toString() + " MB")
        SettingLine("Safe UI limit", state.maxHeapMb.toString() + " MB")
        NoteText("The memory value is saved as the global Java heap default. It will be consumed by the game launch builder when that path is connected.")
    }
}

@Composable
private fun LauncherCard(state: RuntimeSettingsUiState, actions: RuntimeSettingsActions) {
    SettingsCard("LAUNCHER EXPERIENCE") {
        SettingSwitch(
            title = "Keep screen awake during play",
            summary = "Saved preference; it will be applied by the active game session.",
            checked = state.keepScreenAwake,
            onCheckedChange = { actions.onSetBoolean?.invoke(RuntimeBooleanSetting.KEEP_SCREEN_AWAKE, it) },
        )
        SettingSwitch(
            title = "Enable interface animations",
            summary = "Global animation preference for launcher navigation.",
            checked = state.animationsEnabled,
            onCheckedChange = { actions.onSetBoolean?.invoke(RuntimeBooleanSetting.ANIMATIONS_ENABLED, it) },
        )
        SettingSwitch(
            title = "Open game log automatically",
            summary = "Show the live log panel when a game session starts.",
            checked = state.showGameLogAutomatically,
            onCheckedChange = { actions.onSetBoolean?.invoke(RuntimeBooleanSetting.SHOW_GAME_LOG, it) },
        )
        SettingSlider(
            title = "Game log font size",
            value = state.logFontSizeSp,
            range = 10..24,
            suffix = " sp",
            onValueChange = { actions.onSetInt?.invoke(RuntimeIntSetting.LOG_FONT_SIZE, it) },
        )
        SettingSlider(
            title = "Log retention",
            value = state.logRetentionDays,
            range = 1..30,
            suffix = " days",
            onValueChange = { actions.onSetInt?.invoke(RuntimeIntSetting.LOG_RETENTION_DAYS, it) },
        )
        NoteText("Log viewer, retention cleanup, and transition controls are not wired to a running game session yet.")
    }
}

@Composable
private fun RuntimeCard(state: RuntimeSettingsUiState, actions: RuntimeSettingsActions) {
    SettingsCard("JAVA RUNTIME MANAGER") {
        Text(
            "Flint selects a mobile OpenJDK build matching the current app process ABI. Downloads are checked against the published SHA-256 before installation.",
            color = FlintColors.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )

        if (state.installingMajor != null) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    state.installStage ?: "Preparing Java " + state.installingMajor,
                    color = FlintColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall,
                )
                val percent = state.installPercent
                if (percent == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = (percent / 100f).coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
                }
                val total = state.installTotalBytes
                val downloaded = state.installDownloadedBytes
                val sizeText = if (total != null && total > 0) {
                    "%.1f / %.1f MB".format(downloaded / 1_048_576f, total / 1_048_576f)
                } else {
                    "%.1f MB".format(downloaded / 1_048_576f)
                }
                val speedText = if (state.installSpeedBytesPerSecond > 0) {
                    " · %.2f MB/s".format(state.installSpeedBytesPerSecond / 1_048_576f)
                } else ""
                Text(
                    (percent?.let { it.toInt().toString() + "% · " } ?: "") + sizeText + speedText,
                    color = FlintColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        if (state.loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(color = FlintColors.Ember)
                Text("Checking installed runtimes…")
            }
        } else if (state.installedRuntimes.isEmpty()) {
            Text("No installed Java runtime detected.", color = FlintColors.TextMuted)
        } else {
            state.installedRuntimes.forEach { runtime ->
                SettingLine(
                    "Java " + runtime.major,
                    runtime.arch + " · " + runtime.vendor + " · " + if (runtime.verified) "Files OK · JVM probe required" else "Incomplete",
                )
            }
        }

        if (state.availableJavaMajors.isEmpty()) {
            Text(
                "No downloadable runtime is published for this app ABI. A different APK ABI may be required.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text("Available for this ABI", fontWeight = FontWeight.SemiBold)
            state.availableJavaMajors.forEach { major ->
                val installed = state.installedRuntimes.any { it.major == major && it.verified }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("OpenJDK " + major, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (installed) "Installed · JVM execution is checked at launch" else "Mobile runtime · SHA-256 checked download",
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
private fun JavaTuningCard(state: RuntimeSettingsUiState, actions: RuntimeSettingsActions) {
    SettingsCard("JAVA & JVM TUNING") {
        SettingSwitch(
            title = "Automatically select Java",
            summary = "Choose a compatible installed runtime from the Minecraft version metadata.",
            checked = state.autoSelectJava,
            onCheckedChange = { actions.onSetBoolean?.invoke(RuntimeBooleanSetting.AUTO_SELECT_JAVA, it) },
        )
        SettingSlider(
            title = "Maximum Java heap",
            value = state.javaHeapMb,
            range = 256..state.maxHeapMb.coerceAtLeast(256),
            suffix = " MB",
            onValueChange = { actions.onSetInt?.invoke(RuntimeIntSetting.JAVA_HEAP_MB, it) },
        )
        OutlinedTextField(
            value = state.javaArguments,
            onValueChange = { actions.onSetString?.invoke(RuntimeStringSetting.JAVA_ARGUMENTS, it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Additional JVM arguments") },
            supportingText = { Text("Advanced: leave empty unless you understand the selected JVM flags. Maximum 2,048 characters.") },
            minLines = 2,
            maxLines = 4,
        )
        NoteText("These are persisted global defaults. Automatic Java selection already uses version metadata; custom heap/arguments still need final integration into the live launch request.")
    }
}

@Composable
private fun GraphicsCard(state: RuntimeSettingsUiState, actions: RuntimeSettingsActions) {
    SettingsCard("RENDERER & GRAPHICS API") {
        SettingLine("System GPU probe", state.rendererDiagnostics)
        SettingLine("Selected renderer", "Auto (safe default)")
        listOf(
            "GL4ES" to "Legacy OpenGL compatibility · not bundled",
            "Zink / Vulkan" to "OpenGL over Vulkan · not bundled",
            "MobileGlues" to "Modern OpenGL ES path · not bundled",
            "LTW" to "Partial desktop OpenGL path · not bundled",
            "System GLES" to "Device driver only; not a guaranteed game renderer",
        ).forEach { (name, summary) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.SemiBold)
                    Text(summary, color = FlintColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                }
                Text("Unavailable", color = FlintColors.TextMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
        SettingSlider(
            title = "Resolution scale",
            value = state.resolutionScalePercent,
            range = 25..100,
            suffix = "%",
            onValueChange = { actions.onSetInt?.invoke(RuntimeIntSetting.RESOLUTION_SCALE, it) },
        )
        SettingSwitch(
            title = "Fullscreen game surface",
            summary = "Use a full display surface when the Android game window backend is available.",
            checked = state.fullscreen,
            onCheckedChange = { actions.onSetBoolean?.invoke(RuntimeBooleanSetting.FULLSCREEN, it) },
        )
        SettingSwitch(
            title = "Ignore display cutout / notch",
            summary = "Allow the game surface to use the full display area.",
            checked = state.ignoreNotch,
            onCheckedChange = { actions.onSetBoolean?.invoke(RuntimeBooleanSetting.IGNORE_NOTCH, it) },
        )
        SettingSwitch(
            title = "Sustained performance mode",
            summary = "Request a stable long-session performance mode on supported devices.",
            checked = state.sustainedPerformance,
            onCheckedChange = { actions.onSetBoolean?.invoke(RuntimeBooleanSetting.SUSTAINED_PERFORMANCE, it) },
        )
        SettingSwitch(
            title = "Request maximum refresh rate",
            summary = "Request the highest supported display refresh rate.",
            checked = state.maxRefreshRate,
            onCheckedChange = { actions.onSetBoolean?.invoke(RuntimeBooleanSetting.MAX_REFRESH_RATE, it) },
        )
        SettingSwitch(
            title = "Force VSync",
            summary = "Only meaningful after a renderer and swapchain are active.",
            checked = state.forceVsync,
            onCheckedChange = { actions.onSetBoolean?.invoke(RuntimeBooleanSetting.FORCE_VSYNC, it) },
        )
        NoteText("Flint does not yet ship a GL4ES, Zink, MobileGlues or LWJGL window backend. Renderer selection and native driver management remain intentionally unavailable.")
    }
}

@Composable
private fun ControlsCard(state: RuntimeSettingsUiState, actions: RuntimeSettingsActions) {
    SettingsCard("TOUCH CONTROLS & OVERLAY") {
        SettingSwitch(
            title = "Enable touchscreen controls",
            summary = "Use the saved touch overlay layout when the game input bridge is connected.",
            checked = state.touchControlsEnabled,
            onCheckedChange = { actions.onSetBoolean?.invoke(RuntimeBooleanSetting.TOUCH_CONTROLS, it) },
        )
        SettingSlider(
            title = "Button size",
            value = state.buttonScalePercent,
            range = 50..150,
            suffix = "%",
            onValueChange = { actions.onSetInt?.invoke(RuntimeIntSetting.BUTTON_SCALE, it) },
        )
        SettingSlider(
            title = "Button opacity",
            value = state.buttonOpacityPercent,
            range = 10..100,
            suffix = "%",
            onValueChange = { actions.onSetInt?.invoke(RuntimeIntSetting.BUTTON_OPACITY, it) },
        )
        SettingSwitch(
            title = "Disable touch gestures",
            summary = "Disable gesture shortcuts such as double-tap actions.",
            checked = state.disableGestures,
            onCheckedChange = { actions.onSetBoolean?.invoke(RuntimeBooleanSetting.DISABLE_GESTURES, it) },
        )
        SettingSwitch(
            title = "Double tap to swap hand",
            summary = "Saved gesture preference.",
            checked = state.doubleTapSwapHand,
            enabled = !state.disableGestures,
            onCheckedChange = { actions.onSetBoolean?.invoke(RuntimeBooleanSetting.DOUBLE_TAP_SWAP_HAND, it) },
        )
        SettingSlider(
            title = "Long-press delay",
            value = state.longPressDelayMs,
            range = 150..1_000,
            suffix = " ms",
            onValueChange = { actions.onSetInt?.invoke(RuntimeIntSetting.LONG_PRESS_DELAY, it) },
            enabled = !state.disableGestures,
        )
        NoteText("Custom button placement, drawers, joysticks, import/export and per-version layouts will use Flint's control editor. The overlay-to-Minecraft input bridge is not complete yet.")
    }

    SettingsCard("MOUSE, GAMEPAD & MOTION INPUT") {
        SettingSlider(
            title = "Mouse sensitivity",
            value = state.mouseSensitivityPercent,
            range = 25..300,
            suffix = "%",
            onValueChange = { actions.onSetInt?.invoke(RuntimeIntSetting.MOUSE_SENSITIVITY, it) },
        )
        SettingSlider(
            title = "Gamepad joystick deadzone",
            value = state.gamepadDeadzonePercent,
            range = 0..50,
            suffix = "%",
            onValueChange = { actions.onSetInt?.invoke(RuntimeIntSetting.GAMEPAD_DEADZONE, it) },
        )
        SettingSwitch(
            title = "Gyroscope aiming",
            summary = if (state.gyroscopeAvailable) "Use device motion for camera control." else "This device does not report a gyroscope sensor.",
            checked = state.gyroscopeEnabled,
            enabled = state.gyroscopeAvailable,
            onCheckedChange = { actions.onSetBoolean?.invoke(RuntimeBooleanSetting.GYROSCOPE_ENABLED, it) },
        )
        SettingSlider(
            title = "Gyroscope sensitivity",
            value = state.gyroscopeSensitivityPercent,
            range = 25..300,
            suffix = "%",
            onValueChange = { actions.onSetInt?.invoke(RuntimeIntSetting.GYROSCOPE_SENSITIVITY, it) },
            enabled = state.gyroscopeAvailable && state.gyroscopeEnabled,
        )
        NoteText("Physical mouse capture, keyboard mapping, gamepad remapping and gyroscope events are not connected to the game session yet.")
    }
}

@Composable
private fun GameBehaviorCard(state: RuntimeSettingsUiState, actions: RuntimeSettingsActions) {
    SettingsCard("GAME SESSION") {
        SettingLine("Default Java heap", state.javaHeapMb.toString() + " MB")
        SettingLine("Default renderer", "Auto · blocked until backend is installed")
        SettingSwitch(
            title = "Keep display awake",
            summary = "Prevent the display from sleeping during Minecraft sessions.",
            checked = state.keepScreenAwake,
            onCheckedChange = { actions.onSetBoolean?.invoke(RuntimeBooleanSetting.KEEP_SCREEN_AWAKE, it) },
        )
        SettingSwitch(
            title = "Show game log on launch",
            summary = "Automatically open the live console when the game starts.",
            checked = state.showGameLogAutomatically,
            onCheckedChange = { actions.onSetBoolean?.invoke(RuntimeBooleanSetting.SHOW_GAME_LOG, it) },
        )
        NoteText("Version-specific settings, game options.txt management, live FPS controls, quick settings and session lifecycle will be connected as the launch/window engine is completed.")
    }
}

@Composable
private fun AdvancedCard(state: RuntimeSettingsUiState, actions: RuntimeSettingsActions, wide: Boolean) {
    SettingsCard("ADVANCED & EXPERIMENTAL") {
        SettingLine("Current process ABI", state.processArch ?: "Unsupported")
        SettingLine("Runtime availability", if (state.availableJavaMajors.isEmpty()) "No compatible download" else state.availableJavaMajors.joinToString())
        SettingLine("Renderer status", "No game renderer bundled")
        SettingLine("GPU / Vulkan driver plugins", "Not implemented")
        SettingLine("Native library compatibility layer", "Not implemented")
        SettingLine("Shader cache / dump tools", "Not implemented")
        SettingLine("CPU big-core affinity", "Not implemented")
        SettingLine("OpenAL backend selection", "Not implemented")
        NoteText("Experimental switches are intentionally not exposed as active toggles until Flint has a matching native implementation and device-level tests. This prevents settings that appear to work but do nothing or crash the JVM.")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            OutlinedButton(
                onClick = { actions.onResetSettings?.invoke() },
                enabled = actions.onResetSettings != null,
            ) { Text("Restore defaults") }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = FlintColors.Panel),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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

@Composable
private fun SettingSwitch(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = if (enabled) FlintColors.TextPrimary else FlintColors.TextMuted, fontWeight = FontWeight.SemiBold)
            Text(summary, color = FlintColors.TextMuted, style = MaterialTheme.typography.labelSmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun SettingSlider(
    title: String,
    value: Int,
    range: IntRange,
    suffix: String,
    enabled: Boolean = true,
    onValueChange: (Int) -> Unit,
) {
    var previewValue by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, modifier = Modifier.weight(1f), color = if (enabled) FlintColors.TextPrimary else FlintColors.TextMuted)
            Text(
                previewValue.toInt().toString() + suffix,
                color = if (enabled) FlintColors.Ember else FlintColors.TextMuted,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Slider(
            value = previewValue.coerceIn(range.first.toFloat(), range.last.toFloat()),
            onValueChange = { previewValue = it },
            onValueChangeFinished = { onValueChange(previewValue.toInt().coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled,
        )
    }
}

@Composable
private fun NoteText(text: String) {
    Text(text, color = FlintColors.TextMuted, style = MaterialTheme.typography.bodySmall)
}
