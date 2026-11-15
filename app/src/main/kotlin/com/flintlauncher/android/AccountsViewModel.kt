package com.flintlauncher.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintlauncher.android.account.AccountManager
import com.flintlauncher.android.account.AccountType
import com.flintlauncher.android.auth.AuthError
import com.flintlauncher.android.auth.AuthException
import com.flintlauncher.android.auth.MicrosoftAuthClient
import com.flintlauncher.android.ui.state.AccountRowUi
import com.flintlauncher.android.ui.state.AccountsUiState
import com.flintlauncher.android.ui.state.MicrosoftSignInUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AccountsViewModel(
    private val manager: AccountManager,
    private val microsoftAccountStore: MicrosoftAccountStore,
    /** Null when the build has no Azure client ID; the Microsoft card then explains how to set one. */
    private val authClient: MicrosoftAuthClient? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(
        AccountsUiState(microsoft = MicrosoftSignInUi(configured = authClient != null)),
    )
    val state: StateFlow<AccountsUiState> = _state.asStateFlow()

    private var signInJob: Job? = null

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

    /**
     * Device-code sign-in: request a code, show it, poll until the person approves on Microsoft's
     * page, then run the Xbox/XSTS/Minecraft chain and store only the refresh token (encrypted).
     */
    fun startMicrosoftSignIn() {
        val client = authClient
        if (client == null) {
            _state.update { it.copy(message = AuthError.ClientIdMissing.userMessage) }
            return
        }
        if (signInJob?.isActive == true) return

        signInJob = viewModelScope.launch {
            _state.update {
                it.copy(microsoft = it.microsoft.copy(busy = true, userCode = null, verificationUri = null), message = null)
            }
            try {
                val info = client.requestDeviceCode()
                _state.update {
                    it.copy(microsoft = it.microsoft.copy(userCode = info.userCode, verificationUri = info.verificationUri))
                }
                val tokens = client.awaitDeviceCode(info)
                val session = client.loginMinecraft(tokens.accessToken)
                val account = microsoftAccountStore.saveLogin(tokens, session)
                _state.update { it.copy(message = "${account.username} signed in and selected.") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: AuthException) {
                _state.update { it.copy(message = error.error.userMessage) }
            } catch (error: Exception) {
                _state.update { it.copy(message = error.message ?: "Microsoft sign-in failed.") }
            } finally {
                _state.update {
                    it.copy(microsoft = it.microsoft.copy(busy = false, userCode = null, verificationUri = null))
                }
            }
        }
    }

    fun cancelMicrosoftSignIn() {
        if (signInJob?.isActive != true) return
        signInJob?.cancel()
        _state.update { it.copy(message = "Microsoft sign-in cancelled.") }
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
