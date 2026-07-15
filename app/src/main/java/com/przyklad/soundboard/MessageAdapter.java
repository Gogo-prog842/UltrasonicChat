package com.przyklad.soundboard;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private final List<ChatMessage> messages = new ArrayList<>();

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void replaceMessages(List<ChatMessage> restoredMessages) {
        messages.clear();
        messages.addAll(restoredMessages);
        notifyDataSetChanged();
    }

    public List<ChatMessage> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(messages));
    }

    public boolean isEmpty() {
        return messages.isEmpty();
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
        holder.bind(messages.get(position));
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static final class MessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView messageText;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
        }

        void bind(ChatMessage message) {
            Context context = itemView.getContext();
            messageText.setText(message.getText());

            FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams) messageText.getLayoutParams();

            int sideMargin = dp(context, 64);
            if (message.isSentByMe()) {
                params.gravity = Gravity.END;
                params.setMargins(sideMargin, 0, 0, 0);
                messageText.setBackground(
                        ContextCompat.getDrawable(context, R.drawable.send_bubble));
                messageText.setTextColor(Color.WHITE);
            } else {
                params.gravity = Gravity.START;
                params.setMargins(0, 0, sideMargin, 0);
                messageText.setBackground(
                        ContextCompat.getDrawable(context, R.drawable.receive_bubble));
                messageText.setTextColor(
                        ContextCompat.getColor(context, R.color.received_text));
            }

            messageText.setLayoutParams(params);
        }

        private static int dp(Context context, int value) {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        }
    }
}
