package com.example.gmailish.model;

public class Email {
    public String senderName;
    public String subject;
    public String content;
    public String timestamp;
    public boolean read;
    public boolean starred;
    public String id;

    public Email(String senderName, String subject, String content, String timestamp, boolean read, boolean starred, String id) {
        this.senderName = senderName;
        this.subject = subject;
        this.content = content;
        this.timestamp = timestamp;
        this.read = read;
        this.starred = starred;
        this.id = id;
    }
}
