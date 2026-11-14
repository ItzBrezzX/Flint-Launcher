package com.flintlauncher.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintlauncher.android.partners.PartnersRepository
import com.flintlauncher.android.installer.InstallResult
import com.flintlauncher.android.installer.InstallStage
import com.flintlauncher.android.installer.MinecraftInstaller
import com.flintlauncher.android.runtime.KnownJreArtifacts
import com.flintlauncher.android.runtime.MobileJreManager
import com.flintlauncher.android.ui.state.HomeUiState
import com.flintlauncher.android.ui.state.Loadable
import com.flintlauncher.android.ui.state.PartnerServerUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val partners: PartnersRepository,
    private val installer: MinecraftInstaller,
    private val runtimeManager: MobileJreManager?,
    private val deviceArch: String?,
    launcherVersion: String,
) : ViewModel() {

    private val _state = MutableStateFlow(
        HomeUiState(launcherVersion = launcherVersion, partners = Loadable.Loading),
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var installationJob: Job? = null
    private var launchJob: Job? = null

    init {
        refreshInstallations()
        refreshPartners(force = false)
    }

    fun refreshInstallations() {
        installationJob?.cancel()
        installationJob = viewModelScope.launch {
            val installed = withContext(Dispatchers.IO) {
                installer.installedVersions().map { version ->
                    com.flintlauncher.android.ui.state.InstallationUi(
                        id = version.versionId,
                        name = "Minecraft ${version.versionId}",
                        minecraftVersion = version.versionId,
                        loader = "vanilla",
                        javaMajor = version.requiredJavaMajor,
                    )
                }
            }
            _state.update { current ->
                val selectedId = current.selectedInstallationId
                    ?.takeIf { id -> installed.any { it.id == id } }
                    ?: installed.firstOrNull()?.id
                current.copy(installations = installed, selectedInstallationId = selectedId)
            }
        }
    }

    fun selectInstallation(id: String) {
        if (_state.value.installations.none { it.id == id }) return
        launchJob?.cancel()
        _state.update { it.copy(selectedInstallationId = id, launchMessage = null, launchBusy = false, launchStage = null) }
    }

    /**
     * Prepare the closest compatible mobile JRE automatically. This build still
     * refuses to launch the JVM until a real Minecraft renderer is bundled.
     */
    fun launchSelected() {
        val selected = _state.value.selected
        if (selected == null) {
            _state.update { it.copy(launchMessage = "Install a Minecraft version first.") }
            return
        }
        launchJob?.cancel()
        launchJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    launchBusy = true,
                    launchStage = InstallStage.RESOLVING_METADATA.label,
                    launchProgressPercent = null,
                    launchDownloadedBytes = 0,
                    launchTotalBytes = null,
                    launchSpeedBytesPerSecond = 0,
                    launchMessage = "Preparing Minecraft ${selected.minecraftVersion} environment…",
                )
            }
            val prepared = installer.install(
                selected.minecraftVersion,
                onStage = { stage ->
                    _state.update {
                        it.copy(
                            launchStage = stage.label,
                            launchProgressPercent = null,
                            launchDownloadedBytes = 0,
                            launchTotalBytes = null,
                            launchSpeedBytesPerSecond = 0,
                        )
                    }
                },
                onProgress = { progress ->
                    _state.update {
                        it.copy(
                            launchProgressPercent = progress.percent?.toFloat(),
                            launchDownloadedBytes = progress.downloadedBytes,
                            launchTotalBytes = progress.totalBytes,
                            launchSpeedBytesPerSecond = progress.speedBytesPerSecond,
                        )
                    }
                },
            )
            when (prepared) {
                is InstallResult.Failed -> {
                    refreshInstallations()
                    _state.update {
                        it.copy(
                            launchBusy = false,
                            launchStage = null,
                            launchMessage = "Minecraft environment preparation failed: ${prepared.message}",
                        )
                    }
                    return@launch
                }
                InstallResult.Cancelled -> {
                    _state.update { it.copy(launchBusy = false, launchStage = null, launchMessage = "Minecraft preparation was cancelled.") }
                    return@launch
                }
                is InstallResult.Success -> Unit
            }

            val manager = runtimeManager
            val arch = deviceArch
            if (manager == null || arch == null) {
                _state.update {
                    it.copy(launchBusy = false, launchStage = null, launchMessage = "This APK process ABI is not supported by Flint's mobile Java runtime catalog.")
                }
                return@launch
            }

            val compatible = try {
                manager.findCompatible(selected.javaMajor, arch)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(launchBusy = false, launchStage = null, launchMessage = "Could not check Java runtimes: ${error.message ?: "unknown error"}") }
                return@launch
            }

            val runtime = if (compatible != null) {
                compatible
            } else {
                val targetMajor = KnownJreArtifacts.compatibleMajor(selected.javaMajor, arch)
                if (targetMajor == null) {
                    _state.update {
                        it.copy(launchBusy = false, launchStage = null, launchMessage = "No published mobile Java runtime for ABI $arch satisfies Java ${selected.javaMajor}.")
                    }
                    return@launch
                }
                _state.update {
                    it.copy(launchMessage = "Downloading Java $targetMajor for ${selected.minecraftVersion}…")
                }
                val result = try {
                    manager.install(targetMajor) { stage, progress ->
                        _state.update {
                            it.copy(
                                launchStage = stage,
                                launchProgressPercent = progress?.percent?.toFloat(),
                                launchDownloadedBytes = progress?.downloadedBytes ?: it.launchDownloadedBytes,
                                launchTotalBytes = progress?.totalBytes ?: it.launchTotalBytes,
                                launchSpeedBytesPerSecond = progress?.speedBytesPerSecond ?: 0,
                            )
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    _state.update { it.copy(launchBusy = false, launchStage = null, launchMessage = "Java download failed: ${error.message ?: "unknown error"}") }
                    return@launch
                }
                result.getOrElse { error ->
                    _state.update {
                        it.copy(launchBusy = false, launchStage = null, launchMessage = "Java $targetMajor could not be installed: ${error.message ?: "unknown error"}")
                    }
                    return@launch
                }
            }

            /*
             * Do not execute JLI_Launch from the launcher process here.
             * The current native probe starts a process-global HotSpot VM
             * synchronously in this same Android process. On unsupported
             * Android/JRE combinations that can terminate the whole app
             * with a native signal, which Kotlin try/catch cannot intercept.
             *
             * Game launch is intentionally gated until the renderer + game
             * window backend exists and JVM probing can run in an isolated
             * process. Installing Java is not equivalent to launching MC.
             */
            _state.update {
                it.copy(
                    launchBusy = false,
                    launchStage = null,
                    launchMessage = "Java ${runtime.majorVersion} (${runtime.arch}) is installed. Minecraft launch is not available yet: Flint's GL4ES/OpenGL renderer and game-window backend are not integrated. JVM execution is paused here to prevent a native JVM crash in the launcher process.",
                )
            }
        }
    }

    fun dismissLaunchMessage() {
        _state.update { it.copy(launchMessage = null) }
    }

    fun refreshPartners(force: Boolean = true) {
        viewModelScope.launch {
            // Keep showing the current list while refreshing; only show the spinner when there is none.
            _state.update { if (it.partners is Loadable.Content) it else it.copy(partners = Loadable.Loading) }
            val loaded: Loadable<List<PartnerServerUi>> = try {
                Loadable.Content(partners.load(forceRefresh = force).map { it.toUi() })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Loadable.Error("Could not load the server list.")
            }
            _state.update { it.copy(partners = loaded) }
        }
    }
}
