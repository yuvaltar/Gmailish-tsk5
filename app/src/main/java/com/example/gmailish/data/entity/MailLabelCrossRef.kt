package com.example.gmailish.data.entity

import androidx.room.Entity

@Entity(
    tableName = "mail_label_cross_ref",
    primaryKeys = ["mailId", "labelId"]
)
data class MailLabelCrossRef(
    val mailId: String,
    val labelId: String
)