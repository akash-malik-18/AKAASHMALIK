package com.parentalcontrol.childapp.models;

/**
 * ChatMessage
 * ───────────
 * POJO model for an in-app chat message shown in ChatActivity.
 */
public class ChatMessage {

    private String text;       // Message content
    private String sender;     // Display name of the sender
    private long   timestamp;  // Epoch millis
    private boolean isMine;    // true = sent by this child, false = received

    public ChatMessage() {}

    public ChatMessage(String text, String sender, long timestamp, boolean isMine) {
        this.text      = text;
        this.sender    = sender;
        this.timestamp = timestamp;
        this.isMine    = isMine;
    }

    // Getters
    public String  getText()      { return text; }
    public String  getSender()    { return sender; }
    public long    getTimestamp() { return timestamp; }
    public boolean isMine()       { return isMine; }

    // Setters
    public void setText(String text)           { this.text      = text; }
    public void setSender(String sender)       { this.sender    = sender; }
    public void setTimestamp(long timestamp)   { this.timestamp = timestamp; }
    public void setMine(boolean mine)          { this.isMine    = mine; }
}
