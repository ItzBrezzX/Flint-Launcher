package com.flintlauncher.android.account

import java.util.UUID

/**
 * Account identity used for launch.
 *
 * OFFLINE accounts never carry Microsoft tokens and never trigger network auth.
 * MICROSOFT accounts hold encrypted token references only (never plaintext in models that may be logged).
 */
enum class AccountType {
    OFFLINE,
    MICROSOFT,
}

data class FlintAccount(
    val id: String,
    val type: AccountType,
    val username: String,
    /** Canonical UUID string (with hyphens). */
    val uuid: String,
    /** For MICROSOFT only: opaque key into encrypted token store. Null for offline. */
    val tokenRef: String? = null,
    val skinUrl: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val lastUsedMillis: Long = System.currentTimeMillis(),
) {
    init {
        require(username.isNotBlank()) { "username must not be blank" }
        require(uuid.isNotBlank()) { "uuid must not be blank" }
        when (type) {
            AccountType.OFFLINE -> require(tokenRef == null) { "offline account must not carry tokenRef" }
            AccountType.MICROSOFT -> { /* tokenRef may be null until first successful login */ }
        }
    }

    override fun toString(): String =
        "FlintAccount(id=$id, type=$type, username=$username, uuid=$uuid)"
}

/**
 * Standard Java offline UUID:
 * UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).toByteArray(UTF_8))
 */
object OfflineUuid {
    private val PREFIX = "OfflinePlayer:"

    fun fromUsername(username: String): UUID {
        val bytes = (PREFIX + username).toByteArray(Charsets.UTF_8)
        return UUID.nameUUIDFromBytes(bytes)
    }

    fun fromUsernameString(username: String): String = fromUsername(username).toString()
}

/** Minecraft username rules (Java Edition offline / profile name). */
object UsernameRules {
    private val VALID = Regex("^[A-Za-z0-9_]{3,16}$")

    fun isValid(username: String): Boolean = VALID.matches(username)

    fun validateOrNull(username: String): String? {
        val trimmed = username.trim()
        return if (isValid(trimmed)) trimmed else null
    }

    const val HINT = "3–16 characters: letters, numbers, underscore"
}
