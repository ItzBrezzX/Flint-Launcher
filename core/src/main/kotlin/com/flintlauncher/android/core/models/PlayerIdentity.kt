package com.flintlauncher.android.core.models

/**
 * The identity passed to Minecraft at launch. Offline accounts use a deterministic UUID
 * derived from the name and a zero access token; Microsoft accounts carry a real token.
 * Kept in core so the launch pipeline, mod installers and profile export all share one
 * shape instead of redefining (username, uuid, accessToken, userType) in each module.
 */
data class PlayerIdentity(
    val username: String,
    /** Without dashes is what modern Minecraft expects; callers may pass either and we normalize. */
    val uuid: String,
    val accessToken: String,
    /** "msa" for Microsoft, "legacy" for offline / Mojang-era accounts. */
    val userType: String,
    val xuid: String = "",
    val clientId: String = "",
) {
    val uuidNoDashes: String get() = uuid.replace("-", "")

    companion object {
        fun offline(username: String): PlayerIdentity = PlayerIdentity(
            username = username,
            // Offline UUID: Java's name-based UUID (variant 3, "OfflinePlayer:<name>").
            // We do not compute it here; the auth shard owns account creation. This is a
            // convenience constant only; callers should pass the real stored uuid.
            uuid = "00000000-0000-0000-0000-000000000000",
            accessToken = "0",
            userType = "legacy",
        )
    }
}
