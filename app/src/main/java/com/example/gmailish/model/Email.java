package com.example.gmailish.model;

public class Email {
    public String senderName;
    public String subject;
    public String content;
    public String timestamp;
    public boolean read;
    public boolean starred;
    public String id;

    // NEW: who it’s addressed to (for drafts/opening editor)
    public String to;

    // NEW: is this mail a draft? (derived from labels array)
    public boolean isDraft;

    public Email(String senderName,
                 String subject,
                 String content,
                 String timestamp,
                 boolean read,
                 boolean starred,
                 String id) {
        this(senderName, subject, content, timestamp, read, starred, id, null, false);
    }

    public Email(String senderName,
                 String subject,
                 String content,
                 String timestamp,
                 boolean read,
                 boolean starred,
                 String id,
                 String to,
                 boolean isDraft) {
        this.senderName = senderName;
        this.subject = subject;
        this.content = content;
        this.timestamp = timestamp;
        this.read = read;
        this.starred = starred;
        this.id = id;
        this.to = to;
        this.isDraft = isDraft;
    }
}
