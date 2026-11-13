package com.flintlauncher.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintlauncher.android.partners.PartnersRepository
import com.flintlauncher.android.installer.MinecraftInstaller
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
    launcherVersion: String,
) : ViewModel() {

    private val _state = MutableStateFlow(
        HomeUiState(launcherVersion = launcherVersion, partners = Loadable.Loading),
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var installationJob: Job? = null

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
        _state.update { it.copy(selectedInstallationId = id, launchMessage = null) }
    }

    /**
     * The Play action reports the real current blocker. This build still needs a verified
     * mobile JRE and an integrated Android renderer before it can start a game process.
     */
    fun launchSelected() {
        val selected = _state.value.selected
        if (selected == null) {
            _state.update { it.copy(launchMessage = "Install a Minecraft version first.") }
            return
        }
        if (!installer.isInstalled(selected.minecraftVersion)) {
            refreshInstallations()
            _state.update { it.copy(launchMessage = "This installation is incomplete. Repair it from Instances before playing.") }
            return
        }
        _state.update {
            it.copy(
                launchMessage = "Minecraft ${selected.minecraftVersion} is installed. Game launch is not ready yet: this build still needs a verified ARM64 mobile Java runtime and an integrated graphics renderer.",
            )
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
