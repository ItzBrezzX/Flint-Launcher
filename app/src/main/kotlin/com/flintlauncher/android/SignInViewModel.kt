package com.flintlauncher.android

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintlauncher.android.auth.AuthException
import com.flintlauncher.android.auth.DeviceCodePollStatus
import com.flintlauncher.android.auth.MicrosoftTokens
import com.flintlauncher.android.auth.Pkce
import com.flintlauncher.android.ui.state.SignInPhase
import com.flintlauncher.android.ui.state.SignInUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID

/** One-time side effects the host activity must perform (open a browser, etc.). */
sealed interface SignInEvent {
    data class OpenBrowser(val url: String) : SignInEvent
    data class CopyToClipboard(val label: String, val text: String) : SignInEvent
}

/**
 * Orchestrates both Microsoft sign-in flows against the existing :auth client chain.
 * Tokens are never placed in the UI state; only the resulting username is surfaced on
 * success. The refresh token is persisted encrypted via [MicrosoftAccountStore]; the
 * access token stays in memory inside [MinecraftSessionManager].
 */
class SignInViewModel(
    private val authConfig: AuthConfig,
    private val authClient: com.flintlauncher.android.auth.MicrosoftAuthClient?,
    private val deviceCodeClient: com.flintlauncher.android.auth.DeviceCodeAuthClient?,
    private val accountStore: MicrosoftAccountStore,
    private val sessionManager: MinecraftSessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(SignInUiState(configured = authConfig.isConfigured))
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    private val _events = Channel<SignInEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var flowJob: Job? = null

    fun startPkce() {
        val client = authClient ?: return
        if (!authConfig.isConfigured) return
        flowJob?.cancel()
        val pkce = Pkce.generate()
        val stateParam = UUID.randomUUID().toString()
        RedirectHolder.prepare(pkce, stateParam)
        val url = client.authorizationUrl(pkce.challenge, stateParam)
        _state.update { it.copy(phase = SignInPhase.PkceWaiting, errorMessage = null, successUsername = null) }
        _events.trySend(SignInEvent.OpenBrowser(url))
        flowJob = viewModelScope.launch {
            val redirect = try {
                withTimeout(PKCE_TIMEOUT_MILLIS) {
                    RedirectHolder.redirects.first { it != null }!!
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                RedirectHolder.clear()
                _state.update { it.copy(phase = SignInPhase.Failed, errorMessage = "Sign-in timed out. Please try again.") }
                return@launch
            }
            handlePkceRedirect(client, redirect)
        }
    }

    private suspend fun handlePkceRedirect(
        client: com.flintlauncher.android.auth.MicrosoftAuthClient,
        redirect: String,
    ) {
        val uri = Uri.parse(redirect)
        val pkce = RedirectHolder.pendingPkce
        val expectedState = RedirectHolder.pendingState
        RedirectHolder.clear()
        val error = uri.getQueryParameter("error")
        if (error != null) {
            _state.update { it.copy(phase = SignInPhase.Failed, errorMessage = "Microsoft sign-in was cancelled or refused.") }
            return
        }
        val code = uri.getQueryParameter("code")
        val returnedState = uri.getQueryParameter("state")
        if (code.isNullOrBlank() || pkce == null || returnedState != expectedState) {
            _state.update { it.copy(phase = SignInPhase.Failed, errorMessage = "The sign-in redirect was invalid. Please try again.") }
            return
        }
        completeSignIn {
            val tokens = client.exchangeCode(code, pkce.verifier)
            tokens to client.loginMinecraft(tokens.accessToken)
        }
    }

    fun startDeviceCode() {
        val client = deviceCodeClient ?: return
        val auth = authClient ?: return
        if (!authConfig.isConfigured) return
        flowJob?.cancel()
        _state.update { it.copy(phase = SignInPhase.DeviceCodeWaiting, errorMessage = null, successUsername = null, pollStatus = null, deviceChallenge = null) }
        flowJob = viewModelScope.launch {
            val challenge = try {
                client.requestDeviceCode()
            } catch (e: AuthException) {
                _state.update { it.copy(phase = SignInPhase.Failed, errorMessage = e.error.userMessage) }
                return@launch
            } catch (e: Exception) {
                _state.update { it.copy(phase = SignInPhase.Failed, errorMessage = "Could not start device sign-in. Check your connection.") }
                return@launch
            }
            _state.update { it.copy(deviceChallenge = challenge) }
            client.pollUntilApproved(challenge).collect { status ->
                _state.update { it.copy(pollStatus = status) }
                when (status) {
                    is DeviceCodePollStatus.Approved -> completeSignIn {
                        status.tokens to auth.loginMinecraft(status.tokens.accessToken)
                    }
                    is DeviceCodePollStatus.Denied -> _state.update {
                        it.copy(phase = SignInPhase.Failed, errorMessage = "Device sign-in was denied.")
                    }
                    is DeviceCodePollStatus.Expired -> _state.update {
                        it.copy(phase = SignInPhase.Failed, errorMessage = "The device code expired. Start sign-in again.")
                    }
                    is DeviceCodePollStatus.Error -> _state.update {
                        it.copy(phase = SignInPhase.Failed, errorMessage = status.message)
                    }
                    is DeviceCodePollStatus.Waiting, is DeviceCodePollStatus.SlowedDown -> { /* keep polling */ }
                }
            }
        }
    }

    /** Runs the shared tail: persist refresh token + account, cache the session, surface success. */
    private suspend fun completeSignIn(fetch: suspend () -> Pair<MicrosoftTokens, com.flintlauncher.android.auth.MinecraftSession>) {
        try {
            val (tokens, session) = fetch()
            val account = accountStore.saveLogin(tokens, session)
            sessionManager.clearCache()
            _state.update { it.copy(phase = SignInPhase.Succeeded, successUsername = account.username) }
        } catch (e: AuthException) {
            _state.update { it.copy(phase = SignInPhase.Failed, errorMessage = e.error.userMessage) }
        } catch (e: Exception) {
            _state.update { it.copy(phase = SignInPhase.Failed, errorMessage = e.message ?: "Sign-in failed.") }
        }
    }

    fun copyUserCode(code: String) {
        _events.trySend(SignInEvent.CopyToClipboard("Device code", code))
    }

    fun cancel() {
        flowJob?.cancel()
        RedirectHolder.clear()
        _state.update { SignInUiState(configured = authConfig.isConfigured) }
    }

    fun dismiss() {
        cancel()
    }

    private companion object {
        const val PKCE_TIMEOUT_MILLIS = 5 * 60 * 1000L
    }
}
