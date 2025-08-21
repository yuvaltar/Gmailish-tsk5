package com.example.gmailish.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Date;

@Entity(
        tableName = "mails",
        indices = {
                @Index(value = {"ownerId"}),
                @Index(value = {"read"}),
                @Index(value = {"timestamp"})
        }
)
public class MailEntity {

    @PrimaryKey
    @NonNull
    private String id;

    private String senderId;
    private String senderName;
    private String recipientId;
    private String recipientName;
    private String recipientEmail;
    private String subject;
    private String content;
    private Date timestamp;
    private String ownerId;
    private boolean read;
    private boolean starred;

    // Optional: a local flag for drafts (Room will create the column with default 0)
    @ColumnInfo(name = "isDraft", defaultValue = "0")
    private boolean isDraft;

    /**
     * The ONLY constructor Room should use.
     * If you add/remove fields, update this signature so it includes them all in the same order.
     */
    public MailEntity(@NonNull String id,
                      String senderId,
                      String senderName,
                      String recipientId,
                      String recipientName,
                      String recipientEmail,
                      String subject,
                      String content,
                      Date timestamp,
                      String ownerId,
                      boolean read,
                      boolean starred,
                      boolean isDraft) {
        this.id = id;
        this.senderId = senderId;
        this.senderName = senderName;
        this.recipientId = recipientId;
        this.recipientName = recipientName;
        this.recipientEmail = recipientEmail;
        this.subject = subject;
        this.content = content;
        this.timestamp = timestamp;
        this.ownerId = ownerId;
        this.read = read;
        this.starred = starred;
        this.isDraft = isDraft;
    }

    /**
     * Convenience constructor used by app code (NOT by Room).
     * Default isDraft=false. Marked with @Ignore so Room doesn’t consider it.
     */
    @Ignore
    public MailEntity(@NonNull String id,
                      String senderId,
                      String senderName,
                      String recipientId,
                      String recipientName,
                      String recipientEmail,
                      String subject,
                      String content,
                      Date timestamp,
                      String ownerId,
                      boolean read,
                      boolean starred) {
        this(id, senderId, senderName, recipientId, recipientName, recipientEmail,
                subject, content, timestamp, ownerId, read, starred, false);
    }

    // ---- Getters / Setters ----

    @NonNull public String getId() { return id; }
    public String getSenderId() { return senderId; }
    public String getSenderName() { return senderName; }
    public String getRecipientId() { return recipientId; }
    public String getRecipientName() { return recipientName; }
    public String getRecipientEmail() { return recipientEmail; }
    public String getSubject() { return subject; }
    public String getContent() { return content; }
    public Date getTimestamp() { return timestamp; }
    public String getOwnerId() { return ownerId; }
    public boolean getRead() { return read; }
    public boolean getStarred() { return starred; }
    public boolean isDraft() { return isDraft; }

    public void setId(@NonNull String id) { this.id = id; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public void setSenderName(String senderName) { this.senderName = senderName; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }
    public void setSubject(String subject) { this.subject = subject; }
    public void setContent(String content) { this.content = content; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public void setRead(boolean read) { this.read = read; }
    public void setStarred(boolean starred) { this.starred = starred; }
    public void setDraft(boolean draft) { isDraft = draft; }
}
