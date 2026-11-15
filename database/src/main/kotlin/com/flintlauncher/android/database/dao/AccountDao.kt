package com.flintlauncher.android.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.flintlauncher.android.database.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY lastUsedMillis DESC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY lastUsedMillis DESC")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE selected = 1 LIMIT 1")
    suspend fun getSelected(): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: AccountEntity)

    @Update
    suspend fun update(account: AccountEntity)

    @Query("UPDATE accounts SET selected = (id = :id)")
    suspend fun selectOnly(id: String)

    @Query("UPDATE accounts SET selected = 0")
    suspend fun clearSelection()

    @Query("UPDATE accounts SET lastUsedMillis = :now WHERE id = :id")
    suspend fun touch(id: String, now: Long)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int
}
