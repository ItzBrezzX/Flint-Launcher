package com.flintlauncher.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintlauncher.android.partners.PartnersRepository
import com.flintlauncher.android.ui.state.HomeUiState
import com.flintlauncher.android.ui.state.Loadable
import com.flintlauncher.android.ui.state.PartnerServerUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val partners: PartnersRepository,
    launcherVersion: String,
) : ViewModel() {

    private val _state = MutableStateFlow(
        HomeUiState(launcherVersion = launcherVersion, partners = Loadable.Loading),
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        refreshPartners(force = false)
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
