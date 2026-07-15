package com.przyklad.soundboard;

public final class ChatMessage {
    private final String text;
    private final boolean sentByMe;

    public ChatMessage(String text, boolean sentByMe) {
        this.text = text;
        this.sentByMe = sentByMe;
    }

    public String getText() {
        return text;
    }

    public boolean isSentByMe() {
        return sentByMe;
    }
}
