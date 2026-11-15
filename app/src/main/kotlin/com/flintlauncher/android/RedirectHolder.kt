package com.flintlauncher.android

import com.flintlauncher.android.auth.PkcePair
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide hand-off between the custom-scheme redirect activity and the sign-in
 * ViewModel. The browser redirect lands in [SignInRedirectActivity], which writes the
 * full redirect URI here; the ViewModel collects [redirects] and exchanges the code.
 *
 * Only one sign-in may be in flight at a time; starting a new flow clears any pending
 * PKCE pair. Values are transient and never persisted.
 */
object RedirectHolder {
    private val _redirects = MutableStateFlow<String?>(null)
    val redirects: StateFlow<String?> = _redirects.asStateFlow()

    @Volatile
    var pendingPkce: PkcePair? = null
        private set

    @Volatile
    var pendingState: String? = null
        private set

    fun prepare(pkce: PkcePair, state: String) {
        pendingPkce = pkce
        pendingState = state
        _redirects.value = null
    }

    fun deliver(redirectUri: String) {
        _redirects.value = redirectUri
    }

    fun clear() {
        pendingPkce = null
        pendingState = null
        _redirects.value = null
    }
}
