package com.flintlauncher.android.auth

data class MicrosoftTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSeconds: Long,
) {
    // Tokens must never reach logs or crash reports.
    override fun toString() = "MicrosoftTokens(expiresInSeconds=$expiresInSeconds)"
}

/** Result of the device-code request: show [userCode] and [verificationUri] to the person. */
data class DeviceCodeInfo(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInSeconds: Long,
    val intervalSeconds: Long,
) {
    // The device code is a secret until the sign-in completes.
    override fun toString() = "DeviceCodeInfo(userCode=$userCode, verificationUri=$verificationUri)"
}

data class SkinInfo(val url: String, val variant: String?, val active: Boolean)

data class MinecraftProfile(val id: String, val name: String, val skins: List<SkinInfo>) {
    val activeSkin: SkinInfo? get() = skins.firstOrNull { it.active } ?: skins.firstOrNull()
}

data class MinecraftSession(
    val accessToken: String,
    val expiresInSeconds: Long,
    val profile: MinecraftProfile,
    /** True when the entitlement list itself confirmed the game; otherwise ownership rests on the profile lookup. */
    val entitlementConfirmed: Boolean,
) {
    override fun toString() = "MinecraftSession(profile=${profile.name}, expiresInSeconds=$expiresInSeconds)"
}

/** Every failure the sign-in chain can report, each with text that is safe to show. */
sealed class AuthError(val userMessage: String) {
    data object InvalidGrant : AuthError("Your Microsoft sign-in expired. Please sign in again.")
    data object ClientIdMissing : AuthError("Microsoft sign-in is not configured in this build. Set FLINT_MS_CLIENT_ID (an Azure app ID with public client flows enabled) and rebuild.")
    data object DeviceCodeExpired : AuthError("The sign-in code expired before it was entered. Start again.")
    data object AuthorizationDeclined : AuthError("The sign-in request was declined on the Microsoft page.")
    data object NoXboxAccount : AuthError("This Microsoft account has no Xbox profile. Create one at xbox.com, then try again.")
    data object XboxUnavailableInCountry : AuthError("Xbox Live is not available in this account's country or region.")
    data object AdultVerificationRequired : AuthError("This account needs adult verification on Xbox before it can sign in.")
    data object ChildAccount : AuthError("This is a child account. An adult must add it to a Microsoft family group first.")
    data class XstsFailed(val code: Long?) : AuthError("Xbox sign-in was refused (code ${code ?: "unknown"}).")
    data object NoMinecraftProfile : AuthError("This account does not own Minecraft: Java Edition, or has no Minecraft profile yet.")
    data class Http(val step: String, val status: Int) : AuthError("Sign-in failed at the $step step (HTTP $status).")
    data class Network(val step: String) : AuthError("Could not reach the $step service. Check your connection.")
    data class Malformed(val step: String) : AuthError("Unexpected response from the $step service.")
}

class AuthException(val error: AuthError) : Exception(error.userMessage)
