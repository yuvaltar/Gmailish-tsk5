package com.example.gmailish.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.gmailish.data.entity.MailEntity
import com.example.gmailish.data.entity.relations.MailWithLabels
import kotlinx.coroutines.flow.Flow

@Dao
interface MailDao {


    // Insert or replace one mail
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mail: MailEntity)

    // Insert or replace many mails
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(mails: List<MailEntity>)

    // Get a single mail with its labels (reactive)
    @Transaction
    @Query("SELECT * FROM mails WHERE id = :mailId LIMIT 1")
    fun observeMailWithLabels(mailId: String): Flow<MailWithLabels?>

    // Observe all mails for an owner (reactive), sorted by time desc
    @Transaction
    @Query("SELECT * FROM mails WHERE ownerId = :ownerId ORDER BY timestamp DESC")
    fun observeMailsByOwner(ownerId: String): Flow<List<MailWithLabels>>

    // Local text search across subject/content/senderName/recipientEmail for an owner
    @Transaction
    @Query(
        """
    SELECT * FROM mails
    WHERE ownerId = :ownerId
      AND (
        subject LIKE :query OR
        content LIKE :query OR
        senderName LIKE :query OR
        recipientEmail LIKE :query
      )
    ORDER BY timestamp DESC
    """
    )
    fun search(ownerId: String, query: String): Flow<List<MailWithLabels>>

    // Mark a mail read/unread
    @Query("UPDATE mails SET read = :read WHERE id = :mailId")
    suspend fun setRead(mailId: String, read: Boolean)

    // Toggle/set star locally
    @Query("UPDATE mails SET starred = :starred WHERE id = :mailId")
    suspend fun setStarred(mailId: String, starred: Boolean)

    // Remove a mail. Return rows affected so you can log in repository.
    @Query("DELETE FROM mails WHERE id = :mailId")
    suspend fun deleteById(mailId: String): Int
}