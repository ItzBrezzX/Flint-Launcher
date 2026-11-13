package com.flintlauncher.android

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintlauncher.android.runtime.KnownJreArtifacts
import com.flintlauncher.android.runtime.MobileJreManager
import com.flintlauncher.android.runtime.RuntimeSmoke
import com.flintlauncher.android.ui.state.RuntimeEntryUi
import com.flintlauncher.android.ui.state.RuntimeSettingsUiState
import com.flintlauncher.android.ui.state.RuntimeBooleanSetting
import com.flintlauncher.android.ui.state.RuntimeIntSetting
import com.flintlauncher.android.ui.state.RuntimeStringSetting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RuntimeSettingsViewModel(
    private val runtimeManager: MobileJreManager?,
    private val deviceArch: String?,
    deviceModel: String,
    androidVersion: String,
    supportedAbis: List<String>,
    totalMemoryMb: Long,
    private val settingsPreferences: SharedPreferences,
    gyroscopeAvailable: Boolean,
) : ViewModel() {

    private val baseSettings = RuntimeSettingsUiState(
        deviceModel = deviceModel.ifBlank { "Unknown device" },
        androidVersion = androidVersion.ifBlank { "Unknown" },
        processArch = deviceArch,
        supportedAbis = supportedAbis,
        totalMemoryMb = totalMemoryMb,
        availableJavaMajors = deviceArch?.let(KnownJreArtifacts::majorsFor).orEmpty(),
        gyroscopeAvailable = gyroscopeAvailable,
    )

    private val _state = MutableStateFlow(
        baseSettings.copy(
            javaHeapMb = settingsPreferences.getInt("java_heap_mb", baseSettings.recommendedMemoryMb).coerceIn(256, baseSettings.maxHeapMb),
            javaArguments = settingsPreferences.getString("java_arguments", "").orEmpty().take(2048),
            autoSelectJava = settingsPreferences.getBoolean("auto_select_java", true),
            resolutionScalePercent = settingsPreferences.getInt("resolution_scale", 100).coerceIn(25, 100),
            ignoreNotch = settingsPreferences.getBoolean("ignore_notch", false),
            sustainedPerformance = settingsPreferences.getBoolean("sustained_performance", false),
            maxRefreshRate = settingsPreferences.getBoolean("max_refresh_rate", false),
            fullscreen = settingsPreferences.getBoolean("fullscreen", true),
            forceVsync = settingsPreferences.getBoolean("force_vsync", false),
            touchControlsEnabled = settingsPreferences.getBoolean("touch_controls", true),
            buttonScalePercent = settingsPreferences.getInt("button_scale", 100).coerceIn(50, 150),
            buttonOpacityPercent = settingsPreferences.getInt("button_opacity", 80).coerceIn(10, 100),
            disableGestures = settingsPreferences.getBoolean("disable_gestures", false),
            longPressDelayMs = settingsPreferences.getInt("long_press_delay", 350).coerceIn(150, 1_000),
            doubleTapSwapHand = settingsPreferences.getBoolean("double_tap_swap_hand", true),
            mouseSensitivityPercent = settingsPreferences.getInt("mouse_sensitivity", 100).coerceIn(25, 300),
            gamepadDeadzonePercent = settingsPreferences.getInt("gamepad_deadzone", 15).coerceIn(0, 50),
            gyroscopeEnabled = settingsPreferences.getBoolean("gyroscope_enabled", false) && gyroscopeAvailable,
            gyroscopeSensitivityPercent = settingsPreferences.getInt("gyroscope_sensitivity", 100).coerceIn(25, 300),
            keepScreenAwake = settingsPreferences.getBoolean("keep_screen_awake", true),
            animationsEnabled = settingsPreferences.getBoolean("animations_enabled", true),
            showGameLogAutomatically = settingsPreferences.getBoolean("show_game_log", false),
            logFontSizeSp = settingsPreferences.getInt("log_font_size", 14).coerceIn(10, 24),
            logRetentionDays = settingsPreferences.getInt("log_retention_days", 7).coerceIn(1, 30),
        ),
    )
    val state: StateFlow<RuntimeSettingsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            val result = try {
                withContext(Dispatchers.IO) {
                    val runtimes = runtimeManager?.listInstalled().orEmpty().map { runtime ->
                        RuntimeEntryUi(
                            id = runtime.id,
                            major = runtime.majorVersion,
                            arch = runtime.arch,
                            vendor = runtime.vendor,
                            verified = runtime.verified,
                        )
                    }
                    val gpuProbe = RuntimeSmoke.run().eglCapabilities
                    runtimes to gpuProbe
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        message = "Could not refresh runtime diagnostics: " +
                            (error.message ?: error.javaClass.simpleName),
                    )
                }
                return@launch
            }
            _state.update {
                it.copy(
                    installedRuntimes = result.first,
                    rendererDiagnostics = result.second,
                    loading = false,
                )
            }
        }
    }

    fun installJava(major: Int) {
        val manager = runtimeManager
        if (manager == null) {
            _state.update { it.copy(message = "This Android process ABI is not supported by the available runtime catalog.") }
            return
        }
        if (major !in _state.value.availableJavaMajors) {
            _state.update { it.copy(message = "Java $major is not published for this device architecture.") }
            return
        }
        if (_state.value.installingMajor != null) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    installingMajor = major,
                    installStage = "Preparing Java $major download",
                    installPercent = null,
                    installDownloadedBytes = 0,
                    installTotalBytes = null,
                    installSpeedBytesPerSecond = 0,
                    message = "Downloading and verifying Java $major…",
                )
            }
            val result = try {
                manager.install(major) { stage, progress ->
                    _state.update {
                        it.copy(
                            installStage = stage,
                            installPercent = progress?.percent?.toFloat(),
                            installDownloadedBytes = progress?.downloadedBytes ?: it.installDownloadedBytes,
                            installTotalBytes = progress?.totalBytes ?: it.installTotalBytes,
                            installSpeedBytesPerSecond = progress?.speedBytesPerSecond ?: 0,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
            result.fold(
                onSuccess = { runtime ->
                    _state.update {
                        it.copy(
                            installingMajor = null,
                            installStage = null,
                            installPercent = 100f,
                            message = "Java " + runtime.majorVersion + " (" + runtime.arch + ") installed and verified.",
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            installingMajor = null,
                            installStage = null,
                            message = "Java $major installation failed: " +
                                (error.message ?: error.javaClass.simpleName),
                        )
                    }
                },
            )
            refresh()
        }
    }


    fun setIntSetting(setting: RuntimeIntSetting, value: Int) {
        val normalized = when (setting) {
            RuntimeIntSetting.JAVA_HEAP_MB -> value.coerceIn(256, baseSettings.maxHeapMb)
            RuntimeIntSetting.RESOLUTION_SCALE -> value.coerceIn(25, 100)
            RuntimeIntSetting.BUTTON_SCALE -> value.coerceIn(50, 150)
            RuntimeIntSetting.BUTTON_OPACITY -> value.coerceIn(10, 100)
            RuntimeIntSetting.LONG_PRESS_DELAY -> value.coerceIn(150, 1_000)
            RuntimeIntSetting.MOUSE_SENSITIVITY -> value.coerceIn(25, 300)
            RuntimeIntSetting.GAMEPAD_DEADZONE -> value.coerceIn(0, 50)
            RuntimeIntSetting.GYROSCOPE_SENSITIVITY -> value.coerceIn(25, 300)
            RuntimeIntSetting.LOG_FONT_SIZE -> value.coerceIn(10, 24)
            RuntimeIntSetting.LOG_RETENTION_DAYS -> value.coerceIn(1, 30)
        }
        settingsPreferences.edit().putInt(setting.preferenceKey, normalized).apply()
        _state.update { current ->
            when (setting) {
                RuntimeIntSetting.JAVA_HEAP_MB -> current.copy(javaHeapMb = normalized)
                RuntimeIntSetting.RESOLUTION_SCALE -> current.copy(resolutionScalePercent = normalized)
                RuntimeIntSetting.BUTTON_SCALE -> current.copy(buttonScalePercent = normalized)
                RuntimeIntSetting.BUTTON_OPACITY -> current.copy(buttonOpacityPercent = normalized)
                RuntimeIntSetting.LONG_PRESS_DELAY -> current.copy(longPressDelayMs = normalized)
                RuntimeIntSetting.MOUSE_SENSITIVITY -> current.copy(mouseSensitivityPercent = normalized)
                RuntimeIntSetting.GAMEPAD_DEADZONE -> current.copy(gamepadDeadzonePercent = normalized)
                RuntimeIntSetting.GYROSCOPE_SENSITIVITY -> current.copy(gyroscopeSensitivityPercent = normalized)
                RuntimeIntSetting.LOG_FONT_SIZE -> current.copy(logFontSizeSp = normalized)
                RuntimeIntSetting.LOG_RETENTION_DAYS -> current.copy(logRetentionDays = normalized)
            }
        }
    }

    fun setBooleanSetting(setting: RuntimeBooleanSetting, value: Boolean) {
        val normalized = when (setting) {
            RuntimeBooleanSetting.GYROSCOPE_ENABLED -> value && baseSettings.gyroscopeAvailable
            else -> value
        }
        settingsPreferences.edit().putBoolean(setting.preferenceKey, normalized).apply()
        _state.update { current ->
            when (setting) {
                RuntimeBooleanSetting.AUTO_SELECT_JAVA -> current.copy(autoSelectJava = normalized)
                RuntimeBooleanSetting.IGNORE_NOTCH -> current.copy(ignoreNotch = normalized)
                RuntimeBooleanSetting.SUSTAINED_PERFORMANCE -> current.copy(sustainedPerformance = normalized)
                RuntimeBooleanSetting.MAX_REFRESH_RATE -> current.copy(maxRefreshRate = normalized)
                RuntimeBooleanSetting.FULLSCREEN -> current.copy(fullscreen = normalized)
                RuntimeBooleanSetting.FORCE_VSYNC -> current.copy(forceVsync = normalized)
                RuntimeBooleanSetting.TOUCH_CONTROLS -> current.copy(touchControlsEnabled = normalized)
                RuntimeBooleanSetting.DISABLE_GESTURES -> current.copy(disableGestures = normalized)
                RuntimeBooleanSetting.DOUBLE_TAP_SWAP_HAND -> current.copy(doubleTapSwapHand = normalized)
                RuntimeBooleanSetting.GYROSCOPE_ENABLED -> current.copy(gyroscopeEnabled = normalized)
                RuntimeBooleanSetting.KEEP_SCREEN_AWAKE -> current.copy(keepScreenAwake = normalized)
                RuntimeBooleanSetting.ANIMATIONS_ENABLED -> current.copy(animationsEnabled = normalized)
                RuntimeBooleanSetting.SHOW_GAME_LOG -> current.copy(showGameLogAutomatically = normalized)
            }
        }
    }

    fun setStringSetting(setting: RuntimeStringSetting, value: String) {
        val normalized = value.take(2_048)
        settingsPreferences.edit().putString(setting.preferenceKey, normalized).apply()
        _state.update { current ->
            when (setting) {
                RuntimeStringSetting.JAVA_ARGUMENTS -> current.copy(javaArguments = normalized)
            }
        }
    }

    fun resetSettings() {
        settingsPreferences.edit().clear().apply()
        _state.update { current ->
            current.copy(
                javaHeapMb = baseSettings.recommendedMemoryMb.coerceIn(256, baseSettings.maxHeapMb),
                javaArguments = "",
                autoSelectJava = true,
                resolutionScalePercent = 100,
                ignoreNotch = false,
                sustainedPerformance = false,
                maxRefreshRate = false,
                fullscreen = true,
                forceVsync = false,
                touchControlsEnabled = true,
                buttonScalePercent = 100,
                buttonOpacityPercent = 80,
                disableGestures = false,
                longPressDelayMs = 350,
                doubleTapSwapHand = true,
                mouseSensitivityPercent = 100,
                gamepadDeadzonePercent = 15,
                gyroscopeEnabled = false,
                gyroscopeSensitivityPercent = 100,
                keepScreenAwake = true,
                animationsEnabled = true,
                showGameLogAutomatically = false,
                logFontSizeSp = 14,
                logRetentionDays = 7,
                message = "Launcher settings restored to defaults.",
            )
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }
}
