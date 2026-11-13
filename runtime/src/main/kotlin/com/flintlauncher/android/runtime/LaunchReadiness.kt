package com.flintlauncher.android.runtime

import com.flintlauncher.android.account.AccountType
import com.flintlauncher.android.account.FlintAccount
import java.io.File

/**
 * Explicit installation / launch readiness states.
 * READY_TO_PLAY is only set when every mandatory check passes for the chosen mode.
 */
enum class LaunchState {
    NOT_INSTALLED,
    DOWNLOADING,
    INSTALLING,
    VERIFYING,
    MISSING_RUNTIME,
    RUNTIME_INCOMPATIBLE,
    REPAIR_REQUIRED,
    AUTHENTICATION_REQUIRED,
    READY_TO_PLAY,
    LAUNCHING,
    RUNNING,
    CRASHED,
}

data class ReadinessIssue(
    val code: String,
    val message: String,
    val actionable: String? = null,
)

data class LaunchReadiness(
    val state: LaunchState,
    val issues: List<ReadinessIssue> = emptyList(),
    val requiredJavaMajor: Int? = null,
    val selectedRuntimeId: String? = null,
    val selectedRendererId: String? = null,
) {
    val isPlayable: Boolean get() = state == LaunchState.READY_TO_PLAY
}

/**
 * Pure-logic readiness resolver.
 * Does not start processes; it only decides whether a launch is allowed.
 */
object LaunchReadinessResolver {

    data class Inputs(
        val versionId: String,
        val installMarkerExists: Boolean,
        val clientJar: File?,
        val missingLibraries: List<String> = emptyList(),
        val missingNatives: List<String> = emptyList(),
        val assetIndexPresent: Boolean = false,
        val requiredJavaMajor: Int = 8,
        val availableRuntimeMajor: Int? = null,
        val runtimeArchSupported: Boolean = false,
        val rendererAvailable: Boolean = false,
        val account: FlintAccount? = null,
        /** When true, Microsoft session is present and not expired. */
        val microsoftSessionValid: Boolean = false,
        val gameDirWritable: Boolean = true,
        val enoughStorage: Boolean = true,
    )

    fun resolve(input: Inputs): LaunchReadiness {
        val issues = mutableListOf<ReadinessIssue>()

        if (!input.installMarkerExists || input.clientJar == null || !input.clientJar.isFile) {
            issues += ReadinessIssue("NOT_INSTALLED", "Minecraft ${input.versionId} is not installed.", "Install")
            return LaunchReadiness(LaunchState.NOT_INSTALLED, issues)
        }

        if (input.missingLibraries.isNotEmpty() || input.missingNatives.isNotEmpty() || !input.assetIndexPresent) {
            val detail = buildList {
                if (input.missingLibraries.isNotEmpty()) add("${input.missingLibraries.size} libraries")
                if (input.missingNatives.isNotEmpty()) add("${input.missingNatives.size} natives")
                if (!input.assetIndexPresent) add("asset index")
            }.joinToString(", ")
            issues += ReadinessIssue("REPAIR_REQUIRED", "Installation incomplete: missing $detail.", "Repair")
            return LaunchReadiness(LaunchState.REPAIR_REQUIRED, issues, input.requiredJavaMajor)
        }

        if (!input.enoughStorage) {
            issues += ReadinessIssue("STORAGE", "Not enough free storage to launch safely.")
        }
        if (!input.gameDirWritable) {
            issues += ReadinessIssue("GAME_DIR", "Game directory is not writable.")
        }

        if (input.availableRuntimeMajor == null) {
            issues += ReadinessIssue(
                "MISSING_RUNTIME",
                "No Java runtime is installed. Minecraft ${input.versionId} needs Java ${input.requiredJavaMajor}+.",
                "Install Java runtime",
            )
            return LaunchReadiness(LaunchState.MISSING_RUNTIME, issues, input.requiredJavaMajor)
        }

        if (input.availableRuntimeMajor < input.requiredJavaMajor) {
            issues += ReadinessIssue(
                "RUNTIME_INCOMPATIBLE",
                "Installed Java ${input.availableRuntimeMajor} is too old; need Java ${input.requiredJavaMajor}+.",
                "Install newer runtime",
            )
            return LaunchReadiness(
                LaunchState.RUNTIME_INCOMPATIBLE,
                issues,
                input.requiredJavaMajor,
            )
        }

        if (!input.runtimeArchSupported) {
            issues += ReadinessIssue(
                "RUNTIME_ARCH",
                "Installed runtime is not ARM64-compatible with this device.",
                "Install ARM64 runtime",
            )
            return LaunchReadiness(LaunchState.RUNTIME_INCOMPATIBLE, issues, input.requiredJavaMajor)
        }

        if (!input.rendererAvailable) {
            issues += ReadinessIssue(
                "RENDERER",
                "No compatible OpenGL ES / translation renderer is available on this GPU.",
                "Check renderer settings",
            )
            // Renderer missing is blocking for play
            return LaunchReadiness(LaunchState.RUNTIME_INCOMPATIBLE, issues, input.requiredJavaMajor)
        }

        when {
            input.account == null -> {
                issues += ReadinessIssue(
                    "NO_ACCOUNT",
                    "Select or create an account before playing.",
                    "Add account",
                )
                return LaunchReadiness(LaunchState.AUTHENTICATION_REQUIRED, issues, input.requiredJavaMajor)
            }
            input.account.type == AccountType.MICROSOFT && !input.microsoftSessionValid -> {
                issues += ReadinessIssue(
                    "MS_AUTH",
                    "Microsoft session expired or missing. Sign in again.",
                    "Reauthenticate",
                )
                return LaunchReadiness(LaunchState.AUTHENTICATION_REQUIRED, issues, input.requiredJavaMajor)
            }
            // OFFLINE is always valid for offline launch mode
        }

        if (issues.isNotEmpty()) {
            // Non-blocking warnings already collected; still not fully ready
            return LaunchReadiness(LaunchState.REPAIR_REQUIRED, issues, input.requiredJavaMajor)
        }

        return LaunchReadiness(
            state = LaunchState.READY_TO_PLAY,
            issues = emptyList(),
            requiredJavaMajor = input.requiredJavaMajor,
            selectedRuntimeId = "java-${input.availableRuntimeMajor}",
            selectedRendererId = "auto",
        )
    }
}
