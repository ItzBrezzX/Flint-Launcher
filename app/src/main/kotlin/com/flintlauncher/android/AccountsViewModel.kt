package com.flintlauncher.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintlauncher.android.account.AccountManager
import com.flintlauncher.android.account.AccountType
import com.flintlauncher.android.ui.state.AccountRowUi
import com.flintlauncher.android.ui.state.AccountsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AccountsViewModel(
    private val manager: AccountManager,
    private val microsoftAccountStore: MicrosoftAccountStore,
) : ViewModel() {
    private val _state = MutableStateFlow(AccountsUiState())
    val state: StateFlow<AccountsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(manager.accounts, manager.selectedId) { accounts, selectedId ->
                accounts.map { account ->
                    AccountRowUi(
                        id = account.id,
                        username = account.username,
                        typeLabel = if (account.type == AccountType.OFFLINE) "Offline account" else "Microsoft account",
                        selected = account.id == selectedId,
                    )
                }
            }.collect { rows -> _state.value = _state.value.copy(accounts = rows) }
        }
    }

    fun setUsername(value: String) {
        _state.value = _state.value.copy(usernameInput = value.take(16), message = null)
    }

    fun addOffline() {
        val username = _state.value.usernameInput
        viewModelScope.launch {
            manager.createOfflineAccount(username).fold(
                onSuccess = { account ->
                    _state.value = _state.value.copy(usernameInput = "", message = "${account.username} added and selected.")
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(message = error.message ?: "Could not add account.")
                },
            )
        }
    }

    fun select(id: String) {
        viewModelScope.launch {
            runCatching { manager.select(id) }
                .onSuccess { _state.value = _state.value.copy(message = "Account selected.") }
                .onFailure { error ->
                    _state.value = _state.value.copy(message = error.message ?: "Could not select account.")
                }
        }
    }

    fun remove(id: String) {
        viewModelScope.launch {
            runCatching { microsoftAccountStore.removeAccount(id) }
                .onSuccess {
                    _state.value = _state.value.copy(message = "Account and any stored credentials removed.")
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(message = error.message ?: "Could not remove account.")
                }
        }
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
