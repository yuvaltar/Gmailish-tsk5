package com.example.gmailish.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "mails",
    indices = [
        Index(value = ["ownerId"]),
        Index(value = ["read"]),
        Index(value = ["timestamp"])
    ]
)
data class MailEntity(
    @PrimaryKey val id: String,
    val senderId: String,
    val senderName: String,val recipientId: String,
    val recipientName: String,
    val recipientEmail: String,

    val subject: String,
    val content: String,
    val timestamp: Date,

    val ownerId: String,
    val read: Boolean,
    val starred: Boolean = false
)