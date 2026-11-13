package com.flintlauncher.android.auth

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

data class PkcePair(val verifier: String, val challenge: String)

/** RFC 7636, S256 method. */
object Pkce {
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    fun generate(random: SecureRandom = SecureRandom()): PkcePair {
        val bytes = ByteArray(48).also(random::nextBytes) // 48 bytes -> 64 URL-safe characters
        val verifier = encoder.encodeToString(bytes)
        return PkcePair(verifier, challengeFor(verifier))
    }

    fun challengeFor(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        return encoder.encodeToString(digest)
    }
}

/** Offline profiles use the standard name-based UUID. They are never treated as authenticated. */
object OfflineAccount {
    fun uuid(username: String): UUID =
        UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray(StandardCharsets.UTF_8))
}
