package com.example.gmailish.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "blacklist")
data class BlacklistEntity(
    @PrimaryKey val url: String,
    val createdAt: Date? = null,
    val updatedAt: Date? = null
)