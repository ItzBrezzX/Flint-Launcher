package com.flintlauncher.android.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.flintlauncher.android.database.dao.AccountDao
import com.flintlauncher.android.database.dao.DownloadQueueDao
import com.flintlauncher.android.database.dao.ProfileDao
import com.flintlauncher.android.database.entity.AccountEntity
import com.flintlauncher.android.database.entity.DownloadQueueItemEntity
import com.flintlauncher.android.database.entity.ProfileEntity

/**
 * Minimal Room scaffold for Spike 0. Three tables: accounts (metadata only, never tokens),
 * profiles, and a resumable download queue. The JSON-file repositories currently in use
 * (:account FileAccountRepository, :profiles ProfileRepository) remain the source of truth
 * until a migration is written; Room exists here so DAOs/queries compile and can be adopted
 * incrementally. Schema is exported to database/schemas/ via the ksp room.schemaLocation arg.
 */
@Database(
    entities = [
        AccountEntity::class,
        ProfileEntity::class,
        DownloadQueueItemEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class FlintDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun profileDao(): ProfileDao
    abstract fun downloadQueueDao(): DownloadQueueDao

    companion object {
        const val DATABASE_NAME = "flint-launcher.db"
    }
}
