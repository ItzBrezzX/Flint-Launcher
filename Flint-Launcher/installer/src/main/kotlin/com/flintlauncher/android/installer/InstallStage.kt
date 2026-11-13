package com.flintlauncher.android.installer

/**
 * Fine-grained installation stages shown in the real-time download UI.
 * Each stage is only advanced when the corresponding work has actually completed.
 */
enum class InstallStage(val label: String) {
    RESOLVING_METADATA("Resolving version metadata"),
    CHECKING_INSTALLED("Checking installed files"),
    CALCULATING_STORAGE("Calculating required storage"),
    PREPARING_QUEUE("Preparing download queue"),
    DOWNLOADING_CLIENT("Downloading Minecraft client"),
    DOWNLOADING_LIBRARIES("Downloading libraries"),
    DOWNLOADING_NATIVES("Downloading native libraries"),
    DOWNLOADING_ASSETS("Downloading assets"),
    EXTRACTING("Extracting / preparing files"),
    VALIDATING_INTEGRITY("Validating file integrity"),
    GENERATING_METADATA("Generating installation metadata"),
    REGISTERING_PROFILE("Registering Minecraft profile"),
    FINAL_VERIFICATION("Final installation verification"),
    READY("Ready to play"),
    ;

    companion object {
        /** Backward-compatible mapping from the previous coarse stages. */
        fun fromLegacy(name: String): InstallStage = when (name) {
            "FETCHING_METADATA" -> RESOLVING_METADATA
            "DOWNLOADING_INDEX" -> PREPARING_QUEUE
            "DOWNLOADING_FILES" -> DOWNLOADING_ASSETS
            "VERIFYING" -> VALIDATING_INTEGRITY
            "DONE" -> READY
            else -> entries.find { it.name == name } ?: RESOLVING_METADATA
        }
    }
}
