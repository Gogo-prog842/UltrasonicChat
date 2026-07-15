package com.przyklad.soundboard;

public final class ChatSummary {
    private final String id;
    private final String nickname;
    private final int channelIndex;
    private final String lastMessage;
    private final long lastTimestamp;

    public ChatSummary(String id, String nickname, int channelIndex, String lastMessage, long lastTimestamp) {
        this.id = id;
        this.nickname = nickname;
        this.channelIndex = channelIndex;
        this.lastMessage = lastMessage;
        this.lastTimestamp = lastTimestamp;
    }

    public String getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    public int getChannelIndex() {
        return channelIndex;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public long getLastTimestamp() {
        return lastTimestamp;
    }
}
