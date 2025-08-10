package com.example.gmailish.data.repository

import com.example.gmailish.data.dao.LabelDao
import com.example.gmailish.data.dao.MailLabelDao
import com.example.gmailish.data.entity.LabelEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking

class LabelRepository(
    private val labelDao: LabelDao,
    private val mailLabelDao: MailLabelDao
) {
    // Observe all labels for an owner
    fun observeLabels(ownerId: String): Flow<List<LabelEntity>> = labelDao.observeLabels(ownerId)

    // One-shot get label by name (avoid duplicates when creating)
    suspend fun getLabelByName(ownerId: String, name: String): LabelEntity? =
        labelDao.getByName(ownerId, name)

    // Save/replace a single label (call after server success)
    suspend fun saveLabel(label: LabelEntity) = labelDao.upsert(label)

    // Save/replace a list of labels (after fetch)
    suspend fun saveLabels(labels: List<LabelEntity>) = labelDao.upsertAll(labels)

    // Delete a label (and clear relations) after server success
    suspend fun deleteLabel(labelId: String) {
        // First clear cross-refs so no orphan links remain
        mailLabelDao.clearForLabel(labelId)
        // Then delete the label row
        labelDao.deleteById(labelId)
    }

    // Java-friendly blocking wrapper for saving a single label.
    // Call this ONLY from a background thread.
    fun saveLabelBlocking(label: LabelEntity) = runBlocking {
        labelDao.upsert(label)
    }
}
