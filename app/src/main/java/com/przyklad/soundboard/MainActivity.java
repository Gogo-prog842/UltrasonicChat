package com.przyklad.soundboard;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public final class MainActivity extends AppCompatActivity {
    public static final String EXTRA_NICKNAME = "nickname";
    public static final String EXTRA_CHANNEL = "channel";

    private ChatRepository repository;
    private ChatListAdapter adapter;
    private TextView emptyText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);
        applySystemBarInsets(findViewById(R.id.mainRoot));

        repository = new ChatRepository(this);
        emptyText = findViewById(R.id.emptyText);

        RecyclerView recyclerView = findViewById(R.id.chatListRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatListAdapter(this::openChat);
        recyclerView.setAdapter(adapter);

        findViewById(R.id.searchButton).setOnClickListener(view -> showSearchDialog());
        FloatingActionButton fab = findViewById(R.id.newChatFab);
        fab.setOnClickListener(view -> showNewChatDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadConversations();
    }

    private void reloadConversations() {
        adapter.submitList(repository.getConversations());
        updateEmptyState();
    }

    private void updateEmptyState() {
        emptyText.setVisibility(adapter.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showSearchDialog() {
        EditText input = new EditText(this);
        input.setHint(R.string.search_hint);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        int padding = dp(20);
        input.setPadding(padding, dp(8), padding, dp(8));

        new AlertDialog.Builder(this)
                .setTitle(R.string.search)
                .setView(input)
                .setPositiveButton(R.string.search, (dialog, which) -> {
                    adapter.filter(input.getText().toString());
                    updateEmptyState();
                })
                .setNeutralButton("Wyczyść", (dialog, which) -> {
                    adapter.filter("");
                    updateEmptyState();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showNewChatDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int horizontal = dp(22);
        container.setPadding(horizontal, dp(6), horizontal, 0);

        EditText nicknameInput = new EditText(this);
        nicknameInput.setHint(R.string.nickname);
        nicknameInput.setSingleLine(true);
        nicknameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        container.addView(nicknameInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        Spinner channelSpinner = new Spinner(this);
        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.channel_labels,
                android.R.layout.simple_spinner_item
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        channelSpinner.setAdapter(spinnerAdapter);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)
        );
        spinnerParams.topMargin = dp(10);
        container.addView(channelSpinner, spinnerParams);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.new_chat)
                .setView(container)
                .setPositiveButton(R.string.create, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String nickname = nicknameInput.getText().toString().trim();
                    if (nickname.isEmpty()) {
                        nicknameInput.setError(getString(R.string.invalid_nickname));
                        return;
                    }
                    int channel = channelSpinner.getSelectedItemPosition();
                    repository.ensureConversation(nickname, channel);
                    dialog.dismiss();
                    openChat(new ChatSummary(
                            ChatRepository.conversationId(nickname, channel),
                            nickname,
                            channel,
                            "Brak wiadomości",
                            0L
                    ));
                }));
        dialog.show();
    }

    private void openChat(@NonNull ChatSummary summary) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(EXTRA_NICKNAME, summary.getNickname());
        intent.putExtra(EXTRA_CHANNEL, summary.getChannelIndex());
        startActivity(intent);
    }

    private void applySystemBarInsets(@NonNull View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
