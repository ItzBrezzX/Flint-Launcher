package com.flintlauncher.android

/**
 * Microsoft identity configuration. Flint is a public client: PKCE and device-code
 * flows require NO client secret, so nothing sensitive belongs here.
 *
 * [clientId] must be an Azure AD app registration approved for Minecraft services;
 * [redirectUri] must match a redirect registered for that app AND the custom-scheme
 * intent-filter in AndroidManifest (SignInRedirectActivity). When [clientId] is
 * blank, Microsoft sign-in is disabled and the UI honestly reports "not configured"
 * instead of showing a button that cannot succeed.
 */
data class AuthConfig(
    val clientId: String,
    val redirectUri: String,
) {
    val isConfigured: Boolean
        get() = clientId.isNotBlank() && redirectUri.isNotBlank()

    companion object {
        /** Custom scheme used for the OAuth redirect. Keep in sync with AndroidManifest. */
        const val REDIRECT_SCHEME = "flintlauncher"
        const val REDIRECT_HOST = "oauth"
        const val REDIRECT_URI = "$REDIRECT_SCHEME://$REDIRECT_HOST"

        /**
         * Build-time default. Set FLINT_AZURE_CLIENT_ID in CI/local.properties to bake a
         * real id; the empty default keeps the repo free of identifiers and disables
         * Microsoft sign-in honestly until one is provided.
         */
        val DEFAULT = AuthConfig(
            clientId = BuildConfigFields.azureClientId,
            redirectUri = REDIRECT_URI,
        )
    }
}

/**
 * Fields that would normally come from BuildConfig. Kept explicit so the app does not
 * depend on build config generation being wired; replace values at build time.
 */
private object BuildConfigFields {
    // Intentionally blank: a real Azure client id is deployment-specific and must not
    // be committed. Set via your build pipeline and read here, or pass through AppContainer.
    const val azureClientId: String = ""
}
