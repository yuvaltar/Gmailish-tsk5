package com.example.gmailish.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.gmailish.data.entity.MailLabelCrossRef
import com.example.gmailish.data.entity.relations.LabelWithMails
import com.example.gmailish.data.entity.relations.MailWithLabels
import kotlinx.coroutines.flow.Flow

@Dao
interface MailLabelDao {
    // Add a mail-label relation (ignore if already exists)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(ref: MailLabelCrossRef)

    // Remove a specific mail-label relation
    @Query("DELETE FROM mail_label_cross_ref WHERE mailId = :mailId AND labelId = :labelId")
    suspend fun remove(mailId: String, labelId: String)

    // Clear all relations for a mail (used when server returns authoritative labels)
    @Query("DELETE FROM mail_label_cross_ref WHERE mailId = :mailId")
    suspend fun clearForMail(mailId: String)

    // Clear all relations for a label (when deleting a label)
    @Query("DELETE FROM mail_label_cross_ref WHERE labelId = :labelId")
    suspend fun clearForLabel(labelId: String)

    // Observe mails for a specific label (reactive)
    @Transaction
    @Query("""
    SELECT * FROM mails
    WHERE id IN (
        SELECT mailId FROM mail_label_cross_ref WHERE labelId = :labelId
    )
    ORDER BY timestamp DESC
""")
    fun observeMailsForLabel(labelId: String): Flow<List<MailWithLabels>>

    // Observe labels for a specific mail (reactive)
    @Transaction
    @Query("""
    SELECT * FROM labels
    WHERE id IN (
        SELECT labelId FROM mail_label_cross_ref WHERE mailId = :mailId
    )
    ORDER BY name COLLATE NOCASE ASC
""")
    fun observeLabelsForMail(mailId: String): Flow<List<LabelWithMails>>
}