package com.flintlauncher.android.ui.state

data class RuntimeEntryUi(
    val id: String,
    val major: Int,
    val arch: String,
    val vendor: String,
    val verified: Boolean,
)

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
) {
    val recommendedMemoryMb: Int
        get() = when {
            totalMemoryMb <= 0 -> 768
            totalMemoryMb < 3_000 -> 512
            totalMemoryMb < 5_000 -> 768
            totalMemoryMb < 7_000 -> 1_024
            else -> (totalMemoryMb / 3).coerceIn(1_024, 4_096).toInt()
        }
}

data class RuntimeSettingsActions(
    val onRefresh: (() -> Unit)? = null,
    val onInstallJava: ((Int) -> Unit)? = null,
    val onDismissMessage: (() -> Unit)? = null,
)
