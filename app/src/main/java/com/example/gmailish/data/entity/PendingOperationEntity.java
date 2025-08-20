package com.example.gmailish.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Date;

@Entity(tableName = "pending_operations")
public class PendingOperationEntity {

    @PrimaryKey @NonNull
    public String id;

    // Example types we’ll use:
    // "LABEL_ADD", "LABEL_REMOVE", "LABEL_MOVE"
    public String type;

    // JSON payload, varies per type. For LABEL_MOVE:
    // { "mailId": "...", "targetLabel": "...", "removedLabels": ["primary","promotions", ...] }
    public String payloadJson;

    public Date createdAt;
    public int retryCount;
    public String status; // "PENDING","DONE","FAILED"
    public String relatedLocalId; // optional local linkage if needed

    public PendingOperationEntity(@NonNull String id, String type, String payloadJson,
                                  Date createdAt, int retryCount, String status, String relatedLocalId) {
        this.id = id;
        this.type = type;
        this.payloadJson = payloadJson;
        this.createdAt = createdAt;
        this.retryCount = retryCount;
        this.status = status;
        this.relatedLocalId = relatedLocalId;
    }
}
