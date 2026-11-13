package com.flintlauncher.android.ui.state

data class RuntimeEntryUi(
    val id: String,
    val major: Int,
    val arch: String,
    val vendor: String,
    val verified: Boolean,
)

enum class RuntimeIntSetting(val preferenceKey: String) {
    JAVA_HEAP_MB("java_heap_mb"),
    RESOLUTION_SCALE("resolution_scale"),
    BUTTON_SCALE("button_scale"),
    BUTTON_OPACITY("button_opacity"),
    LONG_PRESS_DELAY("long_press_delay"),
    MOUSE_SENSITIVITY("mouse_sensitivity"),
    GAMEPAD_DEADZONE("gamepad_deadzone"),
    GYROSCOPE_SENSITIVITY("gyroscope_sensitivity"),
    LOG_FONT_SIZE("log_font_size"),
    LOG_RETENTION_DAYS("log_retention_days"),
}

enum class RuntimeBooleanSetting(val preferenceKey: String) {
    AUTO_SELECT_JAVA("auto_select_java"),
    IGNORE_NOTCH("ignore_notch"),
    SUSTAINED_PERFORMANCE("sustained_performance"),
    MAX_REFRESH_RATE("max_refresh_rate"),
    FULLSCREEN("fullscreen"),
    FORCE_VSYNC("force_vsync"),
    TOUCH_CONTROLS("touch_controls"),
    DISABLE_GESTURES("disable_gestures"),
    DOUBLE_TAP_SWAP_HAND("double_tap_swap_hand"),
    GYROSCOPE_ENABLED("gyroscope_enabled"),
    KEEP_SCREEN_AWAKE("keep_screen_awake"),
    ANIMATIONS_ENABLED("animations_enabled"),
    SHOW_GAME_LOG("show_game_log"),
}

enum class RuntimeStringSetting(val preferenceKey: String) {
    JAVA_ARGUMENTS("java_arguments"),
}

data class RuntimeSettingsUiState(
    val deviceModel: String = "Unknown device",
    val androidVersion: String = "Unknown",
    val processArch: String? = null,
    val supportedAbis: List<String> = emptyList(),
    val totalMemoryMb: Long = 0,
    val installedRuntimes: List<RuntimeEntryUi> = emptyList(),
    val availableJavaMajors: List<Int> = emptyList(),
    val rendererDiagnostics: String = "Not checked",
    val loading: Boolean = false,
    val installingMajor: Int? = null,
    val installStage: String? = null,
    val installPercent: Float? = null,
    val installDownloadedBytes: Long = 0,
    val installTotalBytes: Long? = null,
    val installSpeedBytesPerSecond: Long = 0,
    val message: String? = null,

    // Global launcher defaults. Values are persisted even while the game bridge is being built.
    val javaHeapMb: Int = 768,
    val javaArguments: String = "",
    val autoSelectJava: Boolean = true,
    val resolutionScalePercent: Int = 100,
    val ignoreNotch: Boolean = false,
    val sustainedPerformance: Boolean = false,
    val maxRefreshRate: Boolean = false,
    val fullscreen: Boolean = true,
    val forceVsync: Boolean = false,
    val touchControlsEnabled: Boolean = true,
    val buttonScalePercent: Int = 100,
    val buttonOpacityPercent: Int = 80,
    val disableGestures: Boolean = false,
    val longPressDelayMs: Int = 350,
    val doubleTapSwapHand: Boolean = true,
    val mouseSensitivityPercent: Int = 100,
    val gamepadDeadzonePercent: Int = 15,
    val gyroscopeAvailable: Boolean = false,
    val gyroscopeEnabled: Boolean = false,
    val gyroscopeSensitivityPercent: Int = 100,
    val keepScreenAwake: Boolean = true,
    val animationsEnabled: Boolean = true,
    val showGameLogAutomatically: Boolean = false,
    val logFontSizeSp: Int = 14,
    val logRetentionDays: Int = 7,
) {
    val recommendedMemoryMb: Int
        get() = when {
            totalMemoryMb <= 0 -> 768
            totalMemoryMb < 3_000 -> 512
            totalMemoryMb < 5_000 -> 768
            totalMemoryMb < 7_000 -> 1_024
            else -> (totalMemoryMb / 3).coerceIn(1_024, 4_096).toInt()
        }

    val maxHeapMb: Int
        get() = when {
            processArch == "arm" -> minOf(1_024, (totalMemoryMb - 512).coerceAtLeast(384).toInt())
            totalMemoryMb <= 0 -> 2_048
            else -> (totalMemoryMb - 768).coerceIn(512, 4_096).toInt()
        }
}

data class RuntimeSettingsActions(
    val onRefresh: (() -> Unit)? = null,
    val onInstallJava: ((Int) -> Unit)? = null,
    val onDismissMessage: (() -> Unit)? = null,
    val onSetInt: ((RuntimeIntSetting, Int) -> Unit)? = null,
    val onSetBoolean: ((RuntimeBooleanSetting, Boolean) -> Unit)? = null,
    val onSetString: ((RuntimeStringSetting, String) -> Unit)? = null,
    val onResetSettings: (() -> Unit)? = null,
)
