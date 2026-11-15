package com.flintlauncher.android

import com.flintlauncher.android.runtime.LaunchResult
import com.flintlauncher.android.ui.state.GameSessionPhase
import com.flintlauncher.android.ui.state.GameSessionUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives a single game session: calls the real launch coordinator, then monitors the
 * in-process JVM until it exits. All state changes are honest: Running means the JVM
 * thread is alive, NOT that the Minecraft main menu is reachable (that is device-blocked).
 */
class GameSessionController(
    private val coordinator: PlayLaunchCoordinator,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJobSafe() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow(GameSessionUiState())
    val state: StateFlow<GameSessionUiState> = _state.asStateFlow()

    private var launchJob: Job? = null
    private var monitorJob: Job? = null

    fun start(versionId: String) {
        launchJob?.cancel()
        monitorJob?.cancel()
        _state.value = GameSessionUiState(
            versionId = versionId,
            phase = GameSessionPhase.Preparing,
            statusMessage = "Checking Java runtime, installation and building launch arguments…",
        )
        launchJob = scope.launch {
            val result = try {
                coordinator.launch(versionId)
            } catch (error: Exception) {
                LaunchResult.Failed("Launch preparation failed: ${error.message ?: "unknown error"}")
            }
            when (result) {
                is LaunchResult.Started -> {
                    _state.update {
                        it.copy(
                            phase = GameSessionPhase.Running,
                            statusMessage = "JVM running",
                            logTail = coordinator.stdout(),
                        )
                    }
                    monitor(versionId)
                }
                is LaunchResult.Failed -> _state.update {
                    it.copy(
                        phase = GameSessionPhase.Crashed,
                        failureReason = result.reason,
                        logTail = result.logSnippet ?: coordinator.stdout(),
                    )
                }
                LaunchResult.BlockedMissingRuntime -> _state.update {
                    it.copy(phase = GameSessionPhase.Crashed, failureReason = "A compatible, verified Java runtime is required. Install it from Settings first.")
                }
                LaunchResult.BlockedMissingRenderer -> _state.update {
                    it.copy(phase = GameSessionPhase.Crashed, failureReason = "The GL4ES renderer library is missing from this APK.")
                }
                LaunchResult.BlockedNotReady -> _state.update {
                    it.copy(phase = GameSessionPhase.Crashed, failureReason = "Installation or account is not ready. Install the selected version and choose an account.")
                }
            }
        }
    }

    private fun monitor(versionId: String) {
        monitorJob = scope.launch {
            var wasRunning = true
            while (true) {
                delay(MONITOR_INTERVAL_MILLIS)
                val running = try { coordinator.isRunning() } catch (_: Exception) { false }
                val log = withContext(Dispatchers.IO) { coordinator.stdout() }
                _state.update { it.copy(logTail = log.takeLast(LOG_TAIL_CHARS)) }
                if (!running && wasRunning) {
                    // JVM exited. Without a real exit-code hook we treat any unexpected
                    // exit as a crash; clean user-initiated quit is indistinguishable here.
                    _state.update {
                        it.copy(
                            phase = GameSessionPhase.Crashed,
                            failureReason = "Minecraft exited. If you did not quit, this may be a crash — check the log.",
                            logTail = log.takeLast(LOG_TAIL_CHARS),
                        )
                    }
                    return@launch
                }
                wasRunning = running
            }
        }
    }

    fun setRendererStatus(status: String) {
        _state.update { it.copy(rendererStatus = status) }
    }

    fun stop() {
        launchJob?.cancel()
        monitorJob?.cancel()
        scope.launch { runCatching { coordinator.stop() } }
    }

    fun currentLog(): String = _state.value.logTail

    private companion object {
        const val MONITOR_INTERVAL_MILLIS = 1000L
        const val LOG_TAIL_CHARS = 8000
    }
}

private fun SupervisorJobSafe() = kotlinx.coroutines.SupervisorJob()
