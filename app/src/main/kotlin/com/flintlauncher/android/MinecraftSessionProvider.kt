package com.flintlauncher.android

import com.flintlauncher.android.account.AccountType
import com.flintlauncher.android.account.FlintAccount
import com.flintlauncher.android.auth.AuthError
import com.flintlauncher.android.auth.AuthException
import com.flintlauncher.android.auth.MicrosoftAuthClient
import com.flintlauncher.android.auth.MinecraftSession

/** Produces the live credentials Minecraft needs for an account. Offline accounts never reach this. */
fun interface MinecraftSessionProvider {
    /** @throws AuthException with a message that is safe to show. */
    suspend fun sessionFor(account: FlintAccount): MinecraftSession
}

/**
 * Exchanges the stored Microsoft refresh token for a fresh Minecraft access token on every
 * launch (Minecraft tokens live about a day, so nothing but the refresh token is persisted),
 * and stores the rotated refresh token Microsoft hands back.
 */
class MicrosoftSessionProvider(
    private val client: MicrosoftAuthClient,
    private val store: MicrosoftAccountStore,
) : MinecraftSessionProvider {
    override suspend fun sessionFor(account: FlintAccount): MinecraftSession {
        require(account.type == AccountType.MICROSOFT) { "Offline accounts do not use Microsoft sessions." }
        val refreshToken = try {
            store.refreshToken(account.id)
        } catch (error: IllegalArgumentException) {
            // Missing credentials mean the person has to sign in again, not that the app is broken.
            throw AuthException(AuthError.InvalidGrant)
        }
        val (tokens, session) = client.signInWithRefreshToken(refreshToken)
        tokens.refreshToken?.takeIf { it.isNotBlank() && it != refreshToken }?.let {
            store.updateRefreshToken(account.id, it)
        }
        return session
    }
}
