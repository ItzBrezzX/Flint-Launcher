package com.flintlauncher.android.ui.state

import com.flintlauncher.android.account.FlintAccount

data class AccountsUiState(
    val accounts: List<FlintAccount> = emptyList(),
    val selectedId: String? = null,
    val username: String = "",
    val busy: Boolean = false,
    val message: String? = null,
) {
    val selected: FlintAccount? get() = accounts.firstOrNull { it.id == selectedId }
}
