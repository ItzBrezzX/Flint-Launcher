package com.flintlauncher.android

import com.flintlauncher.android.account.AccountType
import com.flintlauncher.android.account.FlintAccount
import com.flintlauncher.android.auth.AuthError
import com.flintlauncher.android.auth.AuthException
import com.flintlauncher.android.auth.MicrosoftAuthClient
import com.flintlauncher.android.auth.MicrosoftTokens
import com.flintlauncher.android.auth.MinecraftSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Identity handed to Minecraft at launch. For Microsoft accounts [accessToken] is a
 * live Minecraft Services token held only in memory; for offline accounts it is the
 * conventional "0" placeholder and [userType] is "legacy".
 */
data class ResolvedSession(
    val username: String,
    val uuidWithHyphens: String,
    val accessToken: String,
    val userType: String,
    val xuid: String = "",
    val clientId: String = "",
) {
    val uuidNoHyphens: String get() = uuidWithHyphens.replace("-", "")
}

/**
 * Resolves the selected account into a launchable session. Offline accounts are
 * deterministic and never touch the network. Microsoft accounts:
 *  1. read the encrypted refresh token from the Keystore vault (never persisted plaintext),
 *  2. exchange it for fresh Microsoft tokens,
 *  3. persist the rotated refresh token back to the vault,
 *  4. run the Xbox → XSTS → Minecraft Services chain,
 *  5. cache the Minecraft access token in memory only, refreshing when it nears expiry.
 *
 * A failed refresh (invalid_grant) surfaces as [SessionError.ReauthRequired] so the UI
 * can offer sign-in again instead of silently launching with a dead token.
 */
interface MinecraftSessionResolver {
    suspend fun resolve(account: FlintAccount): Result<ResolvedSession>
}

sealed class SessionError(message: String) : Exception(message) {
    data object ReauthRequired : SessionError("Microsoft sign-in expired. Please sign in again.")
    data class NotConfigured(override val message: String = "Microsoft sign-in is not configured in this build.") : SessionError(message)
    data class Chain(val cause: AuthException) : SessionError(cause.error.userMessage)
}

class MinecraftSessionManager(
    private val authClient: MicrosoftAuthClient?,
    private val accountStore: MicrosoftAccountStore,
    private val clockNowMillis: () -> Long = { System.currentTimeMillis() },
) : MinecraftSessionResolver {

    private val lock = Mutex()

    /** In-memory only. Access tokens never touch disk. Keyed by account id. */
    @Volatile
    private var cached: Cached? = null

    private data class Cached(
        val accountId: String,
        val session: MinecraftSession,
        val expiresAtMillis: Long,
    )

    override suspend fun resolve(account: FlintAccount): Result<ResolvedSession> = runCatching {
        when (account.type) {
            AccountType.OFFLINE -> ResolvedSession(
                username = account.username,
                uuidWithHyphens = account.uuid,
                accessToken = "0",
                userType = "legacy",
            )
            AccountType.MICROSOFT -> resolveMicrosoft(account)
        }
    }

    private suspend fun resolveMicrosoft(account: FlintAccount): ResolvedSession {
        val client = authClient ?: throw SessionError.NotConfigured()
        cached?.takeIf { it.accountId == account.id && it.expiresAtMillis - clockNowMillis() > REFRESH_HEADROOM_MILLIS }
            ?.let { return toResolved(account, it.session) }
        return lock.withLock {
            // Re-check under lock: a concurrent refresh may have just populated the cache.
            cached?.takeIf { it.accountId == account.id && it.expiresAtMillis - clockNowMillis() > REFRESH_HEADROOM_MILLIS }
                ?.let { return@withLock toResolved(account, it.session) }
            val refreshToken = try {
                accountStore.refreshToken(account.id)
            } catch (e: Exception) {
                throw SessionError.ReauthRequired
            }
            val freshTokens: MicrosoftTokens = try {
                client.refresh(refreshToken)
            } catch (e: AuthException) {
                if (e.error is AuthError.InvalidGrant) throw SessionError.ReauthRequired
                throw SessionError.Chain(e)
            }
            freshTokens.refreshToken?.takeIf { it.isNotBlank() }?.let { rotated ->
                // Rotation: Microsoft may issue a new refresh token. Persist it before
                // proceeding; if persistence fails we still hold it in memory for this launch.
                runCatching { accountStore.updateRefreshToken(account.id, rotated) }
            }
            val session = try {
                client.loginMinecraft(freshTokens.accessToken)
            } catch (e: AuthException) {
                if (e.error is AuthError.InvalidGrant) throw SessionError.ReauthRequired
                throw SessionError.Chain(e)
            }
            cached = Cached(
                accountId = account.id,
                session = session,
                expiresAtMillis = clockNowMillis() + session.expiresInSeconds * 1000L,
            )
            toResolved(account, session)
        }
    }

    private fun toResolved(account: FlintAccount, session: MinecraftSession): ResolvedSession =
        ResolvedSession(
            username = session.profile.name.ifBlank { account.username },
            uuidWithHyphens = account.uuid,
            accessToken = session.accessToken,
            userType = "msa",
        )

    /** Forget cached tokens (e.g. after sign-out or a reauth request). Does not touch the vault. */
    fun clearCache() {
        cached = null
    }

    private companion object {
        /** Refresh a little before actual expiry so a launch never races expiration. */
        const val REFRESH_HEADROOM_MILLIS = 5 * 60 * 1000L
    }
}
