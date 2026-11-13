package com.flintlauncher.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintlauncher.android.partners.PartnersRepository
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
        _state.update { it.copy(selectedInstallationId = id, launchMessage = null) }
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
            _state.update { it.copy(launchMessage = "Checking Minecraft files and Java runtime…") }
            val complete = withContext(Dispatchers.IO) {
                installer.isInstalled(selected.minecraftVersion)
            }
            if (!complete) {
                refreshInstallations()
                _state.update {
                    it.copy(launchMessage = "Minecraft ${selected.minecraftVersion} is incomplete. Repair this instance before playing.")
                }
                return@launch
            }

            val manager = runtimeManager
            val arch = deviceArch
            if (manager == null || arch == null) {
                _state.update {
                    it.copy(launchMessage = "This APK process ABI is not supported by Flint's mobile Java runtime catalog.")
                }
                return@launch
            }

            val compatible = try {
                manager.findCompatible(selected.javaMajor, arch)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(launchMessage = "Could not check Java runtimes: ${error.message ?: "unknown error"}") }
                return@launch
            }

            val runtime = if (compatible != null) {
                compatible
            } else {
                val targetMajor = KnownJreArtifacts.compatibleMajor(selected.javaMajor, arch)
                if (targetMajor == null) {
                    _state.update {
                        it.copy(launchMessage = "No published mobile Java runtime for ABI $arch satisfies Java ${selected.javaMajor}.")
                    }
                    return@launch
                }
                _state.update {
                    it.copy(launchMessage = "Downloading Java $targetMajor for ${selected.minecraftVersion}…")
                }
                val result = try {
                    manager.install(targetMajor)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    _state.update { it.copy(launchMessage = "Java download failed: ${error.message ?: "unknown error"}") }
                    return@launch
                }
                result.getOrElse { error ->
                    _state.update {
                        it.copy(launchMessage = "Java $targetMajor could not be installed: ${error.message ?: "unknown error"}")
                    }
                    return@launch
                }
            }

            val verified = try {
                manager.verify(runtime)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            if (!verified) {
                _state.update { it.copy(launchMessage = "Java ${runtime.majorVersion} was installed but failed runtime verification.") }
                return@launch
            }

            _state.update {
                it.copy(
                    launchMessage = "Java ${runtime.majorVersion} (${runtime.arch}) is verified for Minecraft ${selected.minecraftVersion}. Flint still cannot start the game: the GL4ES/OpenGL compatibility renderer and full game window bridge are not bundled yet.",
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
