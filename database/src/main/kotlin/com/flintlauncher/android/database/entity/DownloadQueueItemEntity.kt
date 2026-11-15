package com.flintlauncher.android.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent download queue entry. Survives app restart so a killed Minecraft install
 * can resume instead of restarting from zero. Progress is tracked in bytes; totals
 * come from the HTTP response and are never guessed.
 *
 * Status values are plain strings ("queued"|"running"|"paused"|"completed"|"failed"|"cancelled")
 * so new states can be added without a schema migration.
 */
@Entity(tableName = "download_queue")
data class DownloadQueueItemEntity(
    @PrimaryKey val id: String,
    val url: String,
    val targetPath: String,
    val expectedSizeBytes: Long? = null,
    val expectedSha1: String? = null,
    val status: String = "queued",
    val progressBytes: Long = 0L,
    val attempts: Int = 0,
    /** Lower number = higher priority; installs use 100, runtime/JRE uses 50. */
    val priority: Int = 100,
    /** Groups files belonging to one install so the queue can be paused/cancelled as a unit. */
    val groupKey: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val lastError: String? = null,
)
