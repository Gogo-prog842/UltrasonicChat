package com.przyklad.soundboard;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.ChatViewHolder> {
    public interface Listener {
        void onChatClicked(@NonNull ChatSummary summary);
    }

    private final Listener listener;
    private final List<ChatSummary> allItems = new ArrayList<>();
    private final List<ChatSummary> visibleItems = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public ChatListAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void submitList(@NonNull List<ChatSummary> items) {
        allItems.clear();
        allItems.addAll(items);
        visibleItems.clear();
        visibleItems.addAll(items);
        notifyDataSetChanged();
    }

    public void filter(@NonNull String query) {
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        visibleItems.clear();
        if (normalized.isEmpty()) {
            visibleItems.addAll(allItems);
        } else {
            for (ChatSummary item : allItems) {
                if (item.getNickname().toLowerCase(Locale.ROOT).contains(normalized)) {
                    visibleItems.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return visibleItems.isEmpty();
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_list, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatSummary item = visibleItems.get(position);
        holder.nicknameText.setText(item.getNickname());
        holder.lastMessageText.setText(item.getLastMessage());
        holder.channelText.setText(holder.itemView.getContext().getString(
                R.string.channel_badge,
                item.getChannelIndex() + 1
        ));
        holder.avatarInitial.setText(initialFor(item.getNickname()));
        holder.timeText.setText(item.getLastTimestamp() == 0L
                ? ""
                : timeFormat.format(new Date(item.getLastTimestamp())));
        holder.itemView.setOnClickListener(view -> listener.onChatClicked(item));
    }

    @Override
    public int getItemCount() {
        return visibleItems.size();
    }

    private static String initialFor(String nickname) {
        String trimmed = nickname.trim();
        return trimmed.isEmpty() ? "?" : trimmed.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    static final class ChatViewHolder extends RecyclerView.ViewHolder {
        final TextView avatarInitial;
        final TextView nicknameText;
        final TextView lastMessageText;
        final TextView timeText;
        final TextView channelText;

        ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarInitial = itemView.findViewById(R.id.avatarInitial);
            nicknameText = itemView.findViewById(R.id.nicknameText);
            lastMessageText = itemView.findViewById(R.id.lastMessageText);
            timeText = itemView.findViewById(R.id.timeText);
            channelText = itemView.findViewById(R.id.channelText);
        }
    }
}
