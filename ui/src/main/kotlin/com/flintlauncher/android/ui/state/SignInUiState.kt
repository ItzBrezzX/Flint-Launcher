package com.flintlauncher.android.ui.state

import com.flintlauncher.android.auth.DeviceCodeChallenge
import com.flintlauncher.android.auth.DeviceCodePollStatus

/**
 * Sign-in screen state. The two flows are mutually exclusive. When [configured] is
 * false (no Azure client id in this build), the screen shows an honest "not configured"
 * notice and no flow can start.
 */
data class SignInUiState(
    val configured: Boolean = false,
    val phase: SignInPhase = SignInPhase.Choose,
    val deviceChallenge: DeviceCodeChallenge? = null,
    val pollStatus: DeviceCodePollStatus? = null,
    val errorMessage: String? = null,
    val successUsername: String? = null,
) {
    val isBusy: Boolean
        get() = phase == SignInPhase.PkceWaiting || phase == SignInPhase.DeviceCodeWaiting
}

enum class SignInPhase {
    /** User picks between browser PKCE and device code. */
    Choose,
    /** Browser opened; waiting for the custom-scheme redirect to return an authorization code. */
    PkceWaiting,
    /** Device code shown; polling token endpoint until the user approves on another device. */
    DeviceCodeWaiting,
    /** Exchange + Xbox/XSTS/MC Services chain succeeded; account saved. */
    Succeeded,
    Failed,
}

data class SignInActions(
    val onStartPkce: (() -> Unit)? = null,
    val onStartDeviceCode: (() -> Unit)? = null,
    val onCancel: (() -> Unit)? = null,
    /** Opens the device-code verification URL (https only) in the browser. */
    val onOpenVerificationUrl: ((String) -> Unit)? = null,
    val onCopyUserCode: ((String) -> Unit)? = null,
    val onDismiss: (() -> Unit)? = null,
)
