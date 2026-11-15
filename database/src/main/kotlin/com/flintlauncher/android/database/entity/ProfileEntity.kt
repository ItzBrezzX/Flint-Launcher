package com.flintlauncher.android.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room mirror of :profiles Profile. JVM/game arguments are stored as a single
 * newline-joined string (Room has no built-in List<String> column in the scaffold);
 * the repository splits/joins them. Risky JVM arguments are filtered at import time
 * by ProfileSecurity before anything reaches this table.
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String = "grass",
    val minecraftVersion: String,
    val loader: String = "vanilla",
    val loaderVersion: String? = null,
    val javaMajor: Int? = null,
    val memoryMb: Int = 2048,
    val jvmArguments: String = "",
    val gameArguments: String = "",
    val enabled: Boolean = true,
    val isDefault: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
)
