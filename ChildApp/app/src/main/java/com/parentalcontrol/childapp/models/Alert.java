package com.parentalcontrol.childapp.models;

/**
 * Alert
 * ─────
 * POJO model that represents one flagged-message alert pushed to Firebase.
 *
 * Firebase serializes this automatically when you call .setValue(alert).
 * All fields must have a no-arg constructor + public getters for Firebase.
 */
public class Alert {

    private String message;    // Full text of the flagged message
    private String sender;     // Child's name / phone number who sent/received it
    private String keyword;    // The restricted keyword that was matched
    private String source;     // "SMS" | "Notification" | "InApp"
    private String appName;    // e.g. "WhatsApp", "Instagram", "ChildApp"
    private long   timestamp;  // Epoch milliseconds

    /** Required no-arg constructor for Firebase deserialization */
    public Alert() {}

    public Alert(String message, String sender, String keyword,
                 String source, String appName, long timestamp) {
        this.message   = message;
        this.sender    = sender;
        this.keyword   = keyword;
        this.source    = source;
        this.appName   = appName;
        this.timestamp = timestamp;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getMessage()   { return message; }
    public String getSender()    { return sender; }
    public String getKeyword()   { return keyword; }
    public String getSource()    { return source; }
    public String getAppName()   { return appName; }
    public long   getTimestamp() { return timestamp; }

    // ── Setters (needed by Firebase) ─────────────────────────────────────────

    public void setMessage(String message)     { this.message   = message; }
    public void setSender(String sender)       { this.sender    = sender; }
    public void setKeyword(String keyword)     { this.keyword   = keyword; }
    public void setSource(String source)       { this.source    = source; }
    public void setAppName(String appName)     { this.appName   = appName; }
    public void setTimestamp(long timestamp)   { this.timestamp = timestamp; }
}
