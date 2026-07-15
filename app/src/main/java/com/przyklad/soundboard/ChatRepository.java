package com.przyklad.soundboard;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class ChatRepository {
    private static final String PREFS_NAME = "ultrasonic_messenger_data";
    private static final String KEY_CONVERSATIONS = "conversations";
    private static final String MESSAGE_PREFIX = "messages_";

    private final SharedPreferences preferences;

    public ChatRepository(@NonNull Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static String conversationId(@NonNull String nickname, int channelIndex) {
        String normalized = nickname.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9ąćęłńóśźż_-]+", "_");
        if (normalized.isBlank()) {
            normalized = Integer.toHexString(Arrays.hashCode(nickname.getBytes(StandardCharsets.UTF_8)));
        }
        return normalized + "_channel_" + channelIndex;
    }

    public synchronized void ensureConversation(@NonNull String nickname, int channelIndex) {
        String id = conversationId(nickname, channelIndex);
        List<ChatSummary> conversations = getConversations();
        for (ChatSummary summary : conversations) {
            if (summary.getId().equals(id)) {
                return;
            }
        }
        conversations.add(new ChatSummary(id, nickname.trim(), channelIndex, "Brak wiadomości", 0L));
        saveConversations(conversations);
    }

    public synchronized List<ChatSummary> getConversations() {
        List<ChatSummary> result = new ArrayList<>();
        String raw = preferences.getString(KEY_CONVERSATIONS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                result.add(new ChatSummary(
                        object.getString("id"),
                        object.getString("nickname"),
                        object.getInt("channel"),
                        object.optString("lastMessage", "Brak wiadomości"),
                        object.optLong("lastTimestamp", 0L)
                ));
            }
        } catch (JSONException ignored) {
            preferences.edit().remove(KEY_CONVERSATIONS).apply();
        }
        result.sort(Comparator.comparingLong(ChatSummary::getLastTimestamp).reversed());
        return result;
    }

    public synchronized List<ChatMessage> getMessages(@NonNull String conversationId) {
        List<ChatMessage> result = new ArrayList<>();
        String raw = preferences.getString(MESSAGE_PREFIX + conversationId, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                result.add(new ChatMessage(
                        object.getString("text"),
                        object.getBoolean("sentByMe"),
                        object.getLong("timestamp")
                ));
            }
        } catch (JSONException ignored) {
            preferences.edit().remove(MESSAGE_PREFIX + conversationId).apply();
        }
        return result;
    }

    public synchronized void appendMessage(
            @NonNull String nickname,
            int channelIndex,
            @NonNull ChatMessage message
    ) {
        String id = conversationId(nickname, channelIndex);
        List<ChatMessage> messages = getMessages(id);
        messages.add(message);

        JSONArray messageArray = new JSONArray();
        try {
            for (ChatMessage item : messages) {
                JSONObject object = new JSONObject();
                object.put("text", item.getText());
                object.put("sentByMe", item.isSentByMe());
                object.put("timestamp", item.getTimestamp());
                messageArray.put(object);
            }
        } catch (JSONException exception) {
            throw new IllegalStateException("Nie udało się zapisać wiadomości", exception);
        }

        preferences.edit()
                .putString(MESSAGE_PREFIX + id, messageArray.toString())
                .apply();

        List<ChatSummary> conversations = getConversations();
        boolean updated = false;
        for (int i = 0; i < conversations.size(); i++) {
            ChatSummary current = conversations.get(i);
            if (current.getId().equals(id)) {
                conversations.set(i, new ChatSummary(
                        id,
                        nickname.trim(),
                        channelIndex,
                        message.getText(),
                        message.getTimestamp()
                ));
                updated = true;
                break;
            }
        }
        if (!updated) {
            conversations.add(new ChatSummary(
                    id,
                    nickname.trim(),
                    channelIndex,
                    message.getText(),
                    message.getTimestamp()
            ));
        }
        saveConversations(conversations);
    }

    private void saveConversations(@NonNull List<ChatSummary> conversations) {
        JSONArray array = new JSONArray();
        try {
            for (ChatSummary summary : conversations) {
                JSONObject object = new JSONObject();
                object.put("id", summary.getId());
                object.put("nickname", summary.getNickname());
                object.put("channel", summary.getChannelIndex());
                object.put("lastMessage", summary.getLastMessage());
                object.put("lastTimestamp", summary.getLastTimestamp());
                array.put(object);
            }
        } catch (JSONException exception) {
            throw new IllegalStateException("Nie udało się zapisać listy rozmów", exception);
        }
        preferences.edit().putString(KEY_CONVERSATIONS, array.toString()).apply();
    }
}
