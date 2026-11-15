package com.flintlauncher.android.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.flintlauncher.android.database.entity.DownloadQueueItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadQueueDao {
    @Query("SELECT * FROM download_queue ORDER BY priority ASC, createdAtMillis ASC")
    fun observeAll(): Flow<List<DownloadQueueItemEntity>>

    @Query("SELECT * FROM download_queue WHERE status IN ('queued','running','paused') ORDER BY priority ASC, createdAtMillis ASC")
    suspend fun getActive(): List<DownloadQueueItemEntity>

    @Query("SELECT * FROM download_queue WHERE groupKey = :groupKey ORDER BY priority ASC, createdAtMillis ASC")
    suspend fun getByGroup(groupKey: String): List<DownloadQueueItemEntity>

    @Query("SELECT * FROM download_queue WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DownloadQueueItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DownloadQueueItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<DownloadQueueItemEntity>)

    @Update
    suspend fun update(item: DownloadQueueItemEntity)

    @Query("UPDATE download_queue SET status = :status, progressBytes = :progressBytes, updatedAtMillis = :now WHERE id = :id")
    suspend fun updateProgress(id: String, status: String, progressBytes: Long, now: Long)

    @Query("UPDATE download_queue SET status = 'paused', updatedAtMillis = :now WHERE groupKey = :groupKey")
    suspend fun pauseGroup(groupKey: String, now: Long)

    @Query("UPDATE download_queue SET status = 'queued', updatedAtMillis = :now WHERE groupKey = :groupKey AND status = 'paused'")
    suspend fun resumeGroup(groupKey: String, now: Long)

    @Query("DELETE FROM download_queue WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM download_queue WHERE groupKey = :groupKey")
    suspend fun deleteGroup(groupKey: String)

    @Query("DELETE FROM download_queue WHERE status IN ('completed','cancelled')")
    suspend fun pruneFinished()
}
