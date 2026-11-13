package com.flintlauncher.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintlauncher.android.installer.MinecraftInstaller
import com.flintlauncher.android.minecraft.manifest.VersionManifest
import com.flintlauncher.android.ui.state.InstallPanelState
import com.flintlauncher.android.ui.state.InstallPhase
import com.flintlauncher.android.ui.state.Loadable
import com.flintlauncher.android.ui.state.VersionFilter
import com.flintlauncher.android.ui.state.VersionRow
import com.flintlauncher.android.ui.state.VersionsUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class ListQuery(
    val text: String = "",
    val filter: VersionFilter = VersionFilter.RELEASE,
    val newestFirst: Boolean = true,
)

class VersionsViewModel(
    private val installer: MinecraftInstaller,
    private val coordinator: InstallCoordinator,
    private val startService: () -> Unit,
) : ViewModel() {

    private val catalog = MutableStateFlow<Loadable<VersionManifest>>(Loadable.Loading)
    private val listQuery = MutableStateFlow(ListQuery())

    /** An install cut off by the app being killed; shown once as a "Retry" card. */
    private val interrupted = MutableStateFlow(coordinator.interruptedVersion())

    // Rows are recomputed only when the catalog, the filters or an install's outcome change,
    // never on every progress tick (listing hundreds of versions and checking the disk is not free).
    private val installOutcome = coordinator.state.map { it.versionId to it.phase }.distinctUntilChanged()

    private val rows: StateFlow<Loadable<List<VersionRow>>> =
        combine(catalog, listQuery, installOutcome) { loaded, query, _ ->
            when (loaded) {
                Loadable.Loading -> Loadable.Loading
                is Loadable.Error -> Loadable.Error(loaded.message)
                is Loadable.Content -> Loadable.Content(
                    VersionRows.build(loaded.value, query.filter, query.text, query.newestFirst, installer::isInstalled),
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    val state: StateFlow<VersionsUiState> =
        combine(rows, listQuery, coordinator.state, interrupted) { rows, query, install, interruptedId ->
            VersionsUiState(rows, query.text, query.filter, query.newestFirst, panelFor(install, interruptedId))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VersionsUiState())

    init {
        loadCatalog()
    }

    private fun panelFor(install: InstallState, interruptedId: String?): InstallPanelState? {
        InstallPanelMapper.map(install)?.let { return it }
        if (interruptedId == null) return null
        return InstallPanelState(
            versionId = interruptedId,
            phase = InstallPhase.FAILED,
            stageLabel = "Interrupted",
            message = "This install was interrupted. Retry to continue; finished files are kept.",
        )
    }

    fun loadCatalog() {
        viewModelScope.launch {
            catalog.value = Loadable.Loading
            catalog.value = try {
                Loadable.Content(installer.fetchManifest())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Loadable.Error("Could not load the version list. Check your connection and try again.")
            }
        }
    }

    fun setQuery(value: String) = listQuery.update { it.copy(text = value) }

    fun setFilter(value: VersionFilter) = listQuery.update { it.copy(filter = value) }

    fun toggleSort() = listQuery.update { it.copy(newestFirst = !it.newestFirst) }

    fun install(versionId: String) {
        interrupted.value = null
        if (coordinator.start(versionId)) startService()
    }

    fun pause() = coordinator.pause()

    fun resume() = coordinator.resume()

    fun cancel() = coordinator.cancel()

    fun dismissResult() {
        coordinator.dismiss()
        interrupted.value = null
    }
}
