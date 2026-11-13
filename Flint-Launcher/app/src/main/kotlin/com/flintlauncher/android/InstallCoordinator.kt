package com.flintlauncher.android

import com.flintlauncher.android.download.DownloadManager
import com.flintlauncher.android.download.DownloadProgress
import com.flintlauncher.android.installer.InstallResult
import com.flintlauncher.android.installer.InstallStage
import com.flintlauncher.android.installer.MinecraftInstaller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

interface InstallRunner {
    suspend fun install(versionId: String, onStage: (InstallStage) -> Unit): InstallResult
}

interface DownloadControls {
    val progress: StateFlow<DownloadProgress>
    fun pause()
    fun resume()
    fun cancel()
}

class MinecraftInstallRunner(private val installer: MinecraftInstaller) : InstallRunner {
    override suspend fun install(versionId: String, onStage: (InstallStage) -> Unit): InstallResult =
        installer.install(versionId, onStage)
}

class ManagerControls(private val manager: DownloadManager) : DownloadControls {
    override val progress: StateFlow<DownloadProgress> get() = manager.progress
    override fun pause() = manager.pause()
    override fun resume() = manager.resume()
    override fun cancel() = manager.cancel()
}

/** Remembers an install that was running, so the app can offer to continue it after a restart. */
interface PendingInstallStore {
    fun save(versionId: String)
    fun load(): String?
    fun clear()
}

class FilePendingInstallStore(private val file: File) : PendingInstallStore {
    override fun save(versionId: String) {
        file.absoluteFile.parentFile?.mkdirs()
        file.writeText(versionId)
    }

    override fun load(): String? =
        if (file.isFile) file.readText().trim().takeIf { it.isNotEmpty() } else null

    override fun clear() {
        file.delete()
    }
}

data class InstallState(
    val versionId: String? = null,
    val phase: Phase = Phase.IDLE,
    val stage: InstallStage? = null,
    val progress: DownloadProgress = DownloadProgress(),
    val message: String? = null,
) {
    enum class Phase { IDLE, RUNNING, SUCCEEDED, FAILED, CANCELLED }
}

/**
 * Owns the one running install, independent of any screen, so rotating the phone or leaving the
 * screen never interrupts it. A foreground service keeps the process alive while it runs.
 * Install is idempotent, so "resume after restart" is simply starting it again.
 */
class InstallCoordinator(
    private val runner: InstallRunner,
    private val controls: DownloadControls,
    private val pending: PendingInstallStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(InstallState())
    val state: StateFlow<InstallState> = _state.asStateFlow()
    private var job: Job? = null

    val isRunning: Boolean get() = job?.isActive == true

    /** An install that was interrupted by the app being killed, if any. */
    fun interruptedVersion(): String? = if (isRunning) null else pending.load()

    /** Returns false (and does nothing) when an install is already running. */
    fun start(versionId: String): Boolean {
        if (isRunning) return false
        pending.save(versionId)
        _state.value = InstallState(versionId, InstallState.Phase.RUNNING, InstallStage.RESOLVING_METADATA)
        job = scope.launch {
            val progressJob = launch {
                controls.progress.collect { progress ->
                    _state.update { if (it.phase == InstallState.Phase.RUNNING) it.copy(progress = progress) else it }
                }
            }
            try {
                when (val result = runner.install(versionId) { stage -> _state.update { it.copy(stage = stage) } }) {
                    is InstallResult.Success -> {
                        pending.clear()
                        finish(InstallState.Phase.SUCCEEDED, "Installed")
                    }
                    is InstallResult.Failed -> finish(InstallState.Phase.FAILED, describe(result))
                    InstallResult.Cancelled -> {
                        pending.clear()
                        finish(InstallState.Phase.CANCELLED, "Cancelled")
                    }
                }
            } catch (e: CancellationException) {
                pending.clear()
                finish(InstallState.Phase.CANCELLED, "Cancelled")
                throw e
            } catch (e: Exception) {
                finish(InstallState.Phase.FAILED, "Unexpected error: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                progressJob.cancel()
            }
        }
        return true
    }

    fun pause() = controls.pause()

    fun resume() = controls.resume()

    fun cancel() {
        controls.cancel()
        job?.cancel()
    }

    /** Clears a finished result from the screen. Does nothing while an install is running. */
    fun dismiss() {
        if (isRunning) return
        pending.clear() // the user has seen the outcome; do not offer it again after a restart
        _state.value = InstallState()
    }

    private fun finish(phase: InstallState.Phase, message: String) {
        _state.update { it.copy(phase = phase, message = message, progress = controls.progress.value) }
    }

    private fun describe(result: InstallResult.Failed): String {
        val failures = result.failures
        val reasons = failures.map { it.reason }.distinct()
        return when {
            failures.size == 1 -> "${failures[0].request.destination.name}: ${failures[0].reason}"
            reasons.size == 1 -> "${reasons[0]} (${failures.size} files)"
            reasons.isNotEmpty() -> "${result.message} (first problem: ${reasons.first()})"
            else -> result.message
        }
    }
}
