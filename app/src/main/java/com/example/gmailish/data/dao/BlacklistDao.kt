package com.example.gmailish.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.gmailish.data.entity.BlacklistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlacklistDao {
    // Insert or replace a blacklist entry
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: BlacklistEntity)

    // Remove by URL
    @Query("DELETE FROM blacklist WHERE url = :url")
    suspend fun delete(url: String)

    // Observe all blacklist entries
    @Query("SELECT * FROM blacklist ORDER BY url ASC")
    fun observeAll(): Flow<List<BlacklistEntity>>

    // Check if a URL is blacklisted (one-shot)
    @Query("SELECT EXISTS(SELECT 1 FROM blacklist WHERE url = :url LIMIT 1)")
    suspend fun exists(url: String): Boolean
}