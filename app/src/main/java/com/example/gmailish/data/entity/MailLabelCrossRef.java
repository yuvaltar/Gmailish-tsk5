package com.example.gmailish.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(
        tableName = "mail_label_cross_ref",
        primaryKeys = {"mailId", "labelId"},
        indices = {
                @Index("mailId"),
                @Index("labelId")
        }
)
public class MailLabelCrossRef {
    @NonNull public String mailId;
    @NonNull public String labelId;

    public MailLabelCrossRef(@NonNull String mailId, @NonNull String labelId) {
        this.mailId = mailId;
        this.labelId = labelId;
    }
}
