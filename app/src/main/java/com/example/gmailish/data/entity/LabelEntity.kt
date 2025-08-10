package com.example.gmailish.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "labels",
    indices = [
        Index(value = ["ownerId"]),
        Index(value = ["ownerId", "name"], unique = true)
    ]
)
data class LabelEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val name: String
)