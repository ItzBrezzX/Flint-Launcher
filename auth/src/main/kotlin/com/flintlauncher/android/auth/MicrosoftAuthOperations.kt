package com.flintlauncher.android.auth

/**
 * Minimal auth operations needed by [com.flintlauncher.android.MinecraftSessionManager].
 * Extracted as an interface so the session manager can be unit-tested with fakes.
 * [MicrosoftAuthClient] implements this.
 */
interface MicrosoftAuthOperations {
    suspend fun refresh(refreshToken: String): MicrosoftTokens
    suspend fun loginMinecraft(microsoftAccessToken: String): MinecraftSession
}
