package com.example.gmailish.data.model;

public final class PendingOperationType {

    // Labels
    public static final String LABEL_CREATE = "LABEL_CREATE";
    public static final String LABEL_RENAME = "LABEL_RENAME";     // optional, for future use
    public static final String LABEL_DELETE = "LABEL_DELETE";     // optional, for future use

    // Mail ↔ Label relations
    public static final String MAIL_ADD_LABEL = "MAIL_ADD_LABEL";
    public static final String MAIL_REMOVE_LABEL = "MAIL_REMOVE_LABEL";

    // Mail state changes
    public static final String MAIL_MARK_READ = "MAIL_MARK_READ";
    public static final String MAIL_TOGGLE_STAR = "MAIL_TOGGLE_STAR";
    public static final String MAIL_TRASH = "MAIL_TRASH";
    public static final String MAIL_SEND = "MAIL_SEND";

    // Draft operations (new!)
    public static final String DRAFT_SAVE = "DRAFT_SAVE";
    public static final String DRAFT_SEND = "DRAFT_SEND";

    private PendingOperationType() {}
}
