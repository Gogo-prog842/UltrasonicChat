package com.przyklad.soundboard;

public final class ChatMessage {
    private final String text;
    private final boolean sentByMe;
    private final long timestamp;

    public ChatMessage(String text, boolean sentByMe, long timestamp) {
        this.text = text;
        this.sentByMe = sentByMe;
        this.timestamp = timestamp;
    }

    public String getText() {
        return text;
    }

    public boolean isSentByMe() {
        return sentByMe;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
