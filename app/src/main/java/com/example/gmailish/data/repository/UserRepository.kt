package com.example.gmailish.data.repository

import com.example.gmailish.data.dao.UserDao
import com.example.gmailish.data.entity.UserEntity
import kotlinx.coroutines.flow.Flow

class UserRepository(
    private val userDao: UserDao
) {
    // Observe a user by id (reactive)
    fun observeUser(id: String): Flow<UserEntity?> = userDao.observeById(id)

    // One-shot fetch by username (useful for lookups)
    suspend fun getUserByUsername(username: String): UserEntity? = userDao.getByUsername(username)

    // Save/replace a user (call this after successful register/login/me)
    suspend fun saveUser(user: UserEntity) = userDao.upsert(user)

    // Clear local users on logout
    suspend fun clearUsers() = userDao.clear()
}