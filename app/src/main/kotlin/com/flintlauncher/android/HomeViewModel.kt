package com.flintlauncher.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintlauncher.android.account.AccountManager
import com.flintlauncher.android.account.AccountType
import com.flintlauncher.android.partners.PartnersRepository
import com.flintlauncher.android.installer.MinecraftInstaller
import com.flintlauncher.android.runtime.LaunchResult
import com.flintlauncher.android.ui.state.AccountKind
import com.flintlauncher.android.ui.state.AccountUi
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
    private val accounts: AccountManager,
    private val playLaunchCoordinator: PlayLaunchCoordinator,
    launcherVersion: String,
    /**
     * Opens the game screen for a version. The game runs in a separate process (see FlintGameActivity),
     * so PLAY must hand off to it instead of launching the JVM inside the launcher's own process.
     */
    private val startGame: ((String) -> Unit)? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(
        HomeUiState(launcherVersion = launcherVersion, partners = Loadable.Loading),
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var installationJob: Job? = null
    private var launchJob: Job? = null

    init {
        refreshInstallations()
        refreshAccount()
        refreshPartners(force = false)
    }

    fun refreshInstallations() {
        installationJob?.cancel()
        installationJob = viewModelScope.launch {
            val installed = withContext(Dispatchers.IO) {
                installer.installedVersions().map { version ->
                    com.flintlauncher.android.ui.state.InstallationUi(
                        id = version.versionId,
                        name = "Minecraft " + version.versionId,
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

    fun refreshAccount() {
        viewModelScope.launch {
            val selected = try {
                accounts.selected()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            val accountUi = selected?.let {
                AccountUi(
                    username = it.username,
                    kind = if (it.type == AccountType.OFFLINE) AccountKind.OFFLINE else AccountKind.MICROSOFT,
                )
            }
            _state.update { it.copy(account = accountUi) }
        }
    }

    fun selectInstallation(id: String) {
        if (_state.value.installations.none { it.id == id }) return
        launchJob?.cancel()
        _state.update { it.copy(selectedInstallationId = id, launchMessage = null) }
    }

    /**
     * PLAY now builds and submits a concrete LaunchRequest through PlayLaunchCoordinator.
     * A Started result only confirms that the native JLI worker was entered; it does not
     * claim that the Minecraft title screen or in-game renderer has been verified.
     */
    fun launchSelected() {
        if (launchJob?.isActive == true) return
        val selected = _state.value.selected
        if (selected == null) {
            _state.update { it.copy(launchMessage = "Install a Minecraft version first.") }
            return
        }

        startGame?.let { open ->
            _state.update { it.copy(launchMessage = null) }
            open(selected.minecraftVersion)
            return
        }

        launchJob = viewModelScope.launch {
            _state.update { it.copy(launchMessage = "Preparing Minecraft " + selected.minecraftVersion + " for launch…") }
            val result = try {
                playLaunchCoordinator.launch(selected.minecraftVersion)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LaunchResult.Failed("Minecraft launch preparation failed: " + (error.message ?: "unknown error"))
            }

            val message = when (result) {
                is LaunchResult.Started ->
                    "Minecraft " + selected.minecraftVersion +
                        " has entered the Pojav-style JVM launch path. This confirms JLI started, not that the title screen is playable; Android GLFW/LWJGL hand-off still needs device verification."
                is LaunchResult.Failed -> result.reason
                LaunchResult.BlockedMissingRuntime -> "A compatible, verified Java runtime is required before Minecraft can launch."
                LaunchResult.BlockedMissingRenderer -> "The GL4ES renderer library is missing from this APK. Rebuild the :native module with GL4ES enabled."
                LaunchResult.BlockedNotReady -> "Minecraft installation or account is not ready. Install the selected version and choose an account first."
            }
            _state.update { it.copy(launchMessage = message) }
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
