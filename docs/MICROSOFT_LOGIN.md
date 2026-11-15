# Microsoft sign-in (device-code flow)

Flint signs in with Microsoft's **device-code flow**: the app shows a short code, you enter it on
Microsoft's page in any browser, and Flint polls until you approve. No redirect URI, no embedded
browser, and Flint never sees your password.

## One-time setup (required, Flint has no built-in client ID)

1. Azure Portal → **App registrations** → **New registration**.
   Supported account types must include **personal Microsoft accounts** (Flint uses the
   `/consumers/` endpoint).
2. **Authentication** → add a **Mobile and desktop applications** platform and set
   **Allow public client flows = Yes**.
3. Copy the **Application (client) ID**.
4. Mojang must approve the app for Minecraft services. Without approval the final
   `login_with_xbox` step is refused. Check Mojang's current process (the XSTS and Minecraft
   Services endpoints and this approval requirement are from community documentation and have
   not been re-verified against a primary source in this repository).
5. Give the ID to the build:
   * local: `./gradlew :app:assembleDebug -PFLINT_MS_CLIENT_ID=<id>` (or the same environment
     variable, or `flint.msClientId` in `~/.gradle/gradle.properties`)
   * GitHub Actions: Settings → Secrets and variables → Actions → **Variables** →
     `FLINT_MS_CLIENT_ID`

A build without the ID still works; the Accounts screen says Microsoft sign-in is not configured.

## What happens on PLAY with a Microsoft account

1. The encrypted refresh token is read from the Keystore-backed vault.
2. Flint refreshes it, then runs Xbox Live → XSTS → Minecraft Services → profile.
3. The fresh Minecraft access token goes to the game (`--userType msa`); a rotated refresh token
   is stored again. Access tokens are never persisted or logged.
4. Errors (expired sign-in, no Xbox profile, child account, region block, no Minecraft) are shown
   as readable messages.

## Status

Implemented and unit-tested against a mock server (`AuthTest`, `MicrosoftAccountStoreTest`).
**Never run against Microsoft's real servers**, and not compiled in the environment that wrote it.
