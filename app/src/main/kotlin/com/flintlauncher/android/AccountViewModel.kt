package com.flintlauncher.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintlauncher.android.account.AccountManager
import com.flintlauncher.android.account.FlintAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class AccountsUiState(
    val accounts: List<FlintAccount> = emptyList(),
    val selectedId: String? = null,
    val username: String = "",
    val busy: Boolean = false,
    val message: String? = null,
) {
    val selected: FlintAccount? get() = accounts.firstOrNull { it.id == selectedId }
}

class AccountViewModel(private val manager: AccountManager) : ViewModel() {
    private val _state = MutableStateFlow(AccountsUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(manager.accounts, manager.selectedId) { accounts, selected ->
                _state.value = _state.value.copy(accounts = accounts, selectedId = selected)
            }.collect {}
        }
    }

    fun setUsername(value: String) { _state.value = _state.value.copy(username = value, message = null) }

    fun addOffline() {
        val name = _state.value.username
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            val result = manager.createOfflineAccount(name)
            _state.value = _state.value.copy(
                busy = false,
                username = if (result.isSuccess) "" else name,
                message = result.exceptionOrNull()?.message ?: "Offline account added and selected.",
            )
        }
    }

    fun select(id: String) {
        viewModelScope.launch { manager.select(id); _state.value = _state.value.copy(message = "Account selected.") }
    }

    fun remove(id: String) {
        viewModelScope.launch { manager.remove(id); _state.value = _state.value.copy(message = "Account removed.") }
    }

    fun dismissMessage() { _state.value = _state.value.copy(message = null) }
}
