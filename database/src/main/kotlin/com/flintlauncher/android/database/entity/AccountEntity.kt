package com.flintlauncher.android.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room mirror of the account metadata stored by :account's FileAccountRepository.
 * Tokens are NEVER stored here: a Microsoft account row carries only an opaque
 * [tokenRef] into the Keystore-backed encrypted vault. Offline rows have null tokenRef.
 *
 * This is a Spike-0 scaffold: the app currently reads accounts from the JSON file.
 * Room is wired so a later migration can replace it behind the same repository interface.
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val type: String, // "OFFLINE" or "MICROSOFT" — stored as text for forward-compatible enum values
    val username: String,
    /** Canonical UUID with hyphens. */
    val uuid: String,
    val tokenRef: String? = null,
    val skinUrl: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val lastUsedMillis: Long = System.currentTimeMillis(),
    /** Exactly one row may be selected; enforced by the repository, not by a constraint here. */
    val selected: Boolean = false,
)
