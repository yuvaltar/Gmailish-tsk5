package com.example.gmailish.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.gmailish.data.entity.LabelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LabelDao {
    // Insert or replace one label
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(label: LabelEntity)

    // Insert or replace many labels
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(labels: List<LabelEntity>)

    // Observe all labels for an owner (reactive)
    @Query("SELECT * FROM labels WHERE ownerId = :ownerId ORDER BY name COLLATE NOCASE ASC")
    fun observeLabels(ownerId: String): Flow<List<LabelEntity>>

    // Get a label by name for an owner (one-shot)
    @Query("SELECT * FROM labels WHERE ownerId = :ownerId AND name = :name LIMIT 1")
    suspend fun getByName(ownerId: String, name: String): LabelEntity?

    // Delete a label by id
    @Query("DELETE FROM labels WHERE id = :labelId")
    suspend fun deleteById(labelId: String)
}