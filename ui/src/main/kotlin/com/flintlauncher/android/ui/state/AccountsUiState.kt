package com.flintlauncher.android.ui.state

data class AccountRowUi(
    val id: String,
    val username: String,
    val typeLabel: String,
    val selected: Boolean,
)

/** State of the Microsoft device-code sign-in card. */
data class MicrosoftSignInUi(
    /** False when this build has no Azure client ID: the card then explains how to configure it. */
    val configured: Boolean = false,
    /** True while the code is being requested or while waiting for the person to finish on Microsoft's page. */
    val busy: Boolean = false,
    val userCode: String? = null,
    val verificationUri: String? = null,
)

data class AccountsUiState(
    val accounts: List<AccountRowUi> = emptyList(),
    val usernameInput: String = "",
    val message: String? = null,
    val microsoft: MicrosoftSignInUi = MicrosoftSignInUi(),
)

data class AccountsActions(
    val onUsernameChange: (String) -> Unit = {},
    val onAddOffline: () -> Unit = {},
    val onSelect: (String) -> Unit = {},
    val onRemove: (String) -> Unit = {},
    val onDismissMessage: () -> Unit = {},
    val onMicrosoftSignIn: () -> Unit = {},
    val onMicrosoftCancel: () -> Unit = {},
    val onOpenVerificationPage: (String) -> Unit = {},
    val onCopyCode: (String) -> Unit = {},
)
