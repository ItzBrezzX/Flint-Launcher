package com.flintlauncher.android.ui.state

/**
 * Game session lifecycle. Honest phases:
 *  - Preparing: JRE/install checks, argument building (no JVM started yet).
 *  - Loading: JLI_Launch entered; waiting for first frame / title screen.
 *  - Running: JVM thread alive. NOTE: reaching the Minecraft main menu / actual
 *    gameplay is BLOCKED on device verification (LWJGL/GLFW Android hand-off);
 *    Running only means the native JVM thread is alive, not that the game is playable.
 *  - Crashed: JVM exited with a non-zero code or threw; log snippet attached.
 *  - Exited: clean exit (user quit from in-game) — treated same as crash for recovery.
 */
enum class GameSessionPhase {
    Preparing,
    Loading,
    Running,
    Crashed,
    Exited,
}

data class GameSessionUiState(
    val phase: GameSessionPhase = GameSessionPhase.Preparing,
    val versionId: String = "",
    val statusMessage: String = "",
    /** Tail of launcher stdout, for the crash screen. Never contains tokens. */
    val logTail: String = "",
    val rendererStatus: String = "",
    val exitCode: Int? = null,
    val failureReason: String? = null,
) {
    val isTerminal: Boolean get() = phase == GameSessionPhase.Crashed || phase == GameSessionPhase.Exited
}

data class GameSessionActions(
    val onStop: (() -> Unit)? = null,
    val onCopyLog: (() -> Unit)? = null,
    val onReturnToLauncher: (() -> Unit)? = null,
    val onRetry: (() -> Unit)? = null,
    val onToggleFullscreen: (() -> Unit)? = null,
    /** Opens the honest main-menu verification checklist (device-blocked items marked). */
    val onOpenVerification: (() -> Unit)? = null,
)
