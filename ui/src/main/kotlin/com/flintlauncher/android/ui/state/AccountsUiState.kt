package com.flintlauncher.android.ui.state

data class AccountRowUi(
    val id: String,
    val username: String,
    val typeLabel: String,
    val selected: Boolean,
)

data class AccountsUiState(
    val accounts: List<AccountRowUi> = emptyList(),
    val usernameInput: String = "",
    val message: String? = null,
)

data class AccountsActions(
    val onUsernameChange: (String) -> Unit = {},
    val onAddOffline: () -> Unit = {},
    val onSelect: (String) -> Unit = {},
    val onRemove: (String) -> Unit = {},
    val onDismissMessage: () -> Unit = {},
    /** Opens the Microsoft sign-in screen. Null means Microsoft auth is not wired. */
    val onStartMicrosoftSignIn: (() -> Unit)? = null,
)
