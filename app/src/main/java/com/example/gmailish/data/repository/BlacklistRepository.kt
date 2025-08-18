package com.example.gmailish.data.repository

import com.example.gmailish.data.dao.BlacklistDao
import com.example.gmailish.data.entity.BlacklistEntity
import kotlinx.coroutines.flow.Flow

class BlacklistRepository(
    private val blacklistDao: BlacklistDao
) {
    // Observe all blacklist entries
    fun observeAll(): Flow<List<BlacklistEntity>> = blacklistDao.observeAll()

    // Check if a URL is blacklisted (one-shot)
    suspend fun isBlacklisted(url: String): Boolean = blacklistDao.exists(url)

    // Add or update a blacklist entry (after server success or local add)
    suspend fun save(entry: BlacklistEntity) = blacklistDao.upsert(entry)

    // Remove a blacklist entry (after server success or local remove)
    suspend fun remove(url: String) = blacklistDao.delete(url)
}