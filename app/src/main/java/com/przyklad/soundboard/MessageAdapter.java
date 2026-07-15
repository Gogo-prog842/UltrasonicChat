package com.przyklad.soundboard;

import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {
    private final List<ChatMessage> messages = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public void submitList(@NonNull List<ChatMessage> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    public void addMessage(@NonNull ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public int getLastPosition() {
        return Math.max(0, messages.size() - 1);
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        holder.messageText.setText(message.getText());
        holder.messageTime.setText(timeFormat.format(new Date(message.getTimestamp())));

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.bubbleContainer.getLayoutParams();
        if (message.isSentByMe()) {
            params.gravity = Gravity.END;
            holder.bubbleContainer.setBackgroundResource(R.drawable.send_bubble);
            holder.messageText.setTextColor(Color.WHITE);
            holder.messageTime.setTextColor(0xCCFFFFFF);
        } else {
            params.gravity = Gravity.START;
            holder.bubbleContainer.setBackgroundResource(R.drawable.receive_bubble);
            holder.messageText.setTextColor(holder.itemView.getContext().getColor(R.color.text_primary));
            holder.messageTime.setTextColor(holder.itemView.getContext().getColor(R.color.text_secondary));
        }
        holder.bubbleContainer.setLayoutParams(params);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static final class MessageViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout bubbleContainer;
        final TextView messageText;
        final TextView messageTime;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            bubbleContainer = itemView.findViewById(R.id.bubbleContainer);
            messageText = itemView.findViewById(R.id.messageText);
            messageTime = itemView.findViewById(R.id.messageTime);
        }
    }
}
