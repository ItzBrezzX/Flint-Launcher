package com.flintlauncher.android

import com.flintlauncher.android.account.AccountManager
import com.flintlauncher.android.account.AccountType
import com.flintlauncher.android.account.FlintAccount
import com.flintlauncher.android.account.TokenVault
import com.flintlauncher.android.auth.MinecraftSession
import com.flintlauncher.android.auth.MicrosoftTokens
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Owns the boundary between non-secret account metadata and encrypted Microsoft credentials.
 * Only the long-lived Microsoft refresh token is persisted; access tokens remain in memory.
 */
class MicrosoftAccountStore(
    private val accounts: AccountManager,
    private val vault: TokenVault,
) {
    suspend fun saveLogin(tokens: MicrosoftTokens, session: MinecraftSession): FlintAccount =
        withContext(Dispatchers.IO) {
            val refreshToken = requireNotNull(tokens.refreshToken) {
                "Microsoft did not return a refresh token. Sign-in cannot be persisted safely."
            }
            require(refreshToken.isNotBlank()) { "Microsoft returned an empty refresh token." }

            val reference = "microsoft:${UUID.randomUUID()}"
            val account = FlintAccount(
                id = UUID.randomUUID().toString(),
                type = AccountType.MICROSOFT,
                username = session.profile.name,
                uuid = canonicalUuid(session.profile.id),
                tokenRef = reference,
                skinUrl = session.profile.activeSkin?.url,
            )

            // Write the secret first. If metadata persistence fails, roll back the secret.
            vault.put(reference, refreshToken)
            try {
                accounts.addMicrosoftAccount(account).getOrThrow()
            } catch (failure: Throwable) {
                runCatching { vault.remove(reference) }
                throw failure
            }
            account
        }

    suspend fun refreshToken(accountId: String): String = withContext(Dispatchers.IO) {
        val account = requireNotNull(accounts.list().firstOrNull { it.id == accountId }) {
            "Account no longer exists."
        }
        require(account.type == AccountType.MICROSOFT) { "Offline accounts do not have Microsoft tokens." }
        val reference = requireNotNull(account.tokenRef) { "Microsoft account needs to sign in again." }
        requireNotNull(vault.get(reference)) { "Microsoft credentials are missing. Sign in again." }
    }

    suspend fun updateRefreshToken(accountId: String, refreshToken: String) = withContext(Dispatchers.IO) {
        require(refreshToken.isNotBlank()) { "Refresh token must not be empty." }
        val account = requireNotNull(accounts.list().firstOrNull { it.id == accountId }) {
            "Account no longer exists."
        }
        require(account.type == AccountType.MICROSOFT) { "Cannot attach Microsoft credentials to an offline account." }
        val reference = requireNotNull(account.tokenRef) { "Microsoft account needs to sign in again." }
        vault.put(reference, refreshToken)
    }

    suspend fun removeAccount(accountId: String) = withContext(Dispatchers.IO) {
        val account = accounts.list().firstOrNull { it.id == accountId } ?: return@withContext
        // Remove the credential before metadata so a storage failure never leaves an
        // apparently usable account whose token has already been discarded.
        account.tokenRef?.let(vault::remove)
        accounts.remove(accountId)
    }

    private fun canonicalUuid(value: String): String {
        val hex = value.replace("-", "")
        require(hex.matches(Regex("^[0-9a-fA-F]{32}$"))) { "Minecraft profile returned an invalid UUID." }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
    }
}
