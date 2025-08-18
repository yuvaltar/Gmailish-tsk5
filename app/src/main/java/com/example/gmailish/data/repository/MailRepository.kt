package com.example.gmailish.data.repository

import android.util.Log
import com.example.gmailish.data.dao.LabelDao
import com.example.gmailish.data.dao.MailDao
import com.example.gmailish.data.dao.MailLabelDao
import com.example.gmailish.data.entity.LabelEntity
import com.example.gmailish.data.entity.MailEntity
import com.example.gmailish.data.entity.MailLabelCrossRef
import com.example.gmailish.data.entity.relations.MailWithLabels
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking

class MailRepository(
    private val mailDao: MailDao,
    private val labelDao: LabelDao,
    private val mailLabelDao: MailLabelDao
) {
    private val TAG = "MailRepo"


    // Observe a single mail with its labels (nullable because the mail may not exist yet)
    fun observeMail(mailId: String): Flow<MailWithLabels?> {
        Log.d(TAG, "observeMail: mailId=$mailId")
        return mailDao.observeMailWithLabels(mailId)
    }

    // Observe all mails for a user (owner), with labels
    fun observeMails(ownerId: String): Flow<List<MailWithLabels>> {
        Log.d(TAG, "observeMails: ownerId=$ownerId")
        return mailDao.observeMailsByOwner(ownerId)
    }

    // Local search
    fun search(ownerId: String, likeQuery: String): Flow<List<MailWithLabels>> {
        Log.d(TAG, "search: ownerId=$ownerId query=$likeQuery")
        return mailDao.search(ownerId, likeQuery)
    }

    // Save/replace a single mail
    suspend fun saveMail(mail: MailEntity) {
        Log.d(TAG, "saveMail: id=${mail.id}")
        mailDao.upsert(mail)
        Log.d(TAG, "saveMail: done id=${mail.id}")
    }

    // Save/replace multiple mails
    suspend fun saveMails(mails: List<MailEntity>) {
        Log.d(TAG, "saveMails: count=${mails.size}")
        mailDao.upsertAll(mails)
        Log.d(TAG, "saveMails: done count=${mails.size}")
    }

    // Java-friendly blocking wrapper for saving many mails
    fun saveMailsBlocking(mails: List<MailEntity>) = runBlocking {
        val t0 = System.currentTimeMillis()
        Log.d(TAG, "saveMailsBlocking: count=${mails.size}")
        mailDao.upsertAll(mails)
        Log.d(TAG, "saveMailsBlocking: done in ${System.currentTimeMillis() - t0}ms")
    }

    // Mark read/unread locally
    suspend fun setRead(mailId: String, read: Boolean) {
        Log.d(TAG, "setRead: id=$mailId read=$read")
        val rows = mailDao.setRead(mailId, read)
        Log.d(TAG, "setRead: rowsAffected=$rows id=$mailId")
    }

    // Set starred locally
    suspend fun setStarred(mailId: String, starred: Boolean) {
        Log.d(TAG, "setStarred: id=$mailId starred=$starred")
        val rows = mailDao.setStarred(mailId, starred)
        Log.d(TAG, "setStarred: rowsAffected=$rows id=$mailId")
    }

    // Delete a mail locally
    suspend fun deleteMail(mailId: String) {
        Log.d(TAG, "deleteMail (suspend): id=$mailId")
        val rows = mailDao.deleteById(mailId)
        Log.d(TAG, "deleteMail (suspend): rowsDeleted=$rows id=$mailId")
    }

    // Replace labels for a mail with an authoritative list from server
    suspend fun replaceMailLabels(mailId: String, labelIds: List<String>, ownerIdForLabels: String) {
        Log.d(TAG, "replaceMailLabels: mail=$mailId labels=${labelIds.size} owner=$ownerIdForLabels")
        val labelEntities = labelIds.map { id -> LabelEntity(id = id, ownerId = ownerIdForLabels, name = id) }
        labelDao.upsertAll(labelEntities)
        val cleared = mailLabelDao.clearForMail(mailId)
        Log.d(TAG, "replaceMailLabels: clearedCrossRefs=$cleared mail=$mailId")
        labelIds.forEach { lid ->
            mailLabelDao.add(MailLabelCrossRef(mailId = mailId, labelId = lid))
            Log.d(TAG, "replaceMailLabels: addedCrossRef mail=$mailId label=$lid")
        }
        Log.d(TAG, "replaceMailLabels: done mail=$mailId")
    }

    // Add a single label to a mail (suspend)
    suspend fun addLabelToMail(
        mailId: String,
        labelId: String,
        ownerIdForLabel: String,
        labelNameFallback: String = labelId
    ) {
        Log.d(TAG, "addLabelToMail: mail=$mailId label=$labelId owner=$ownerIdForLabel name=$labelNameFallback")
        labelDao.upsert(LabelEntity(id = labelId, ownerId = ownerIdForLabel, name = labelNameFallback))
        mailLabelDao.add(MailLabelCrossRef(mailId = mailId, labelId = labelId))
        Log.d(TAG, "addLabelToMail: done mail=$mailId label=$labelId")
    }

    // Remove a single label from a mail (suspend)
    suspend fun removeLabelFromMail(mailId: String, labelId: String) {
        Log.d(TAG, "removeLabelFromMail: mail=$mailId label=$labelId")
        mailLabelDao.remove(mailId, labelId)
        Log.d(TAG, "removeLabelFromMail: done mail=$mailId label=$labelId")
    }

// ========== Java-friendly blocking wrappers (exact Java signatures) ==========

    // public final void deleteMailBlocking(String mailId)
    fun deleteMailBlocking(mailId: String) = runBlocking {
        val t0 = System.currentTimeMillis()
        try {
            Log.d(TAG, "deleteMailBlocking: clearForMail mail=$mailId")
            val cleared = mailLabelDao.clearForMail(mailId)
            Log.d(TAG, "deleteMailBlocking: cleared=$cleared mail=$mailId")

            Log.d(TAG, "deleteMailBlocking: deleteById mail=$mailId")
            val deleted = mailDao.deleteById(mailId)
            Log.d(TAG, "deleteMailBlocking: rowsDeleted=$deleted mail=$mailId")
        } catch (e: Exception) {
            Log.e(TAG, "deleteMailBlocking: ERROR mail=$mailId ${e.message}", e)
            throw e
        } finally {
            Log.d(TAG, "deleteMailBlocking: finished in ${System.currentTimeMillis() - t0}ms mail=$mailId")
        }
    }

    // public final void addLabelToMailBlocking(String mailId, String labelId, String ownerIdForLabel, String labelNameFallback)
    fun addLabelToMailBlocking(
        mailId: String,
        labelId: String,
        ownerIdForLabel: String,
        labelNameFallback: String
    ) = runBlocking {
        val t0 = System.currentTimeMillis()
        try {
            Log.d(TAG, "addLabelToMailBlocking: mail=$mailId label=$labelId owner=$ownerIdForLabel name=$labelNameFallback")
            labelDao.upsert(LabelEntity(id = labelId, ownerId = ownerIdForLabel, name = labelNameFallback))
            mailLabelDao.add(MailLabelCrossRef(mailId = mailId, labelId = labelId))
            Log.d(TAG, "addLabelToMailBlocking: done mail=$mailId label=$labelId")
        } catch (e: Exception) {
            Log.e(TAG, "addLabelToMailBlocking: ERROR mail=$mailId label=$labelId ${e.message}", e)
            throw e
        } finally {
            Log.d(TAG, "addLabelToMailBlocking: finished in ${System.currentTimeMillis() - t0}ms mail=$mailId")
        }
    }

    // public final void removeLabelFromMailBlocking(String mailId, String labelId)
    fun removeLabelFromMailBlocking(mailId: String, labelId: String) = runBlocking {
        val t0 = System.currentTimeMillis()
        try {
            Log.d(TAG, "removeLabelFromMailBlocking: mail=$mailId label=$labelId")
            mailLabelDao.remove(mailId, labelId)
            Log.d(TAG, "removeLabelFromMailBlocking: done mail=$mailId label=$labelId")
        } catch (e: Exception) {
            Log.e(TAG, "removeLabelFromMailBlocking: ERROR mail=$mailId label=$labelId ${e.message}", e)
            throw e
        } finally {
            Log.d(TAG, "removeLabelFromMailBlocking: finished in ${System.currentTimeMillis() - t0}ms mail=$mailId")
        }
    }
}