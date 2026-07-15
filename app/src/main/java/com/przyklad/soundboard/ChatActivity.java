package com.przyklad.soundboard;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

public final class ChatActivity extends AppCompatActivity implements UltrasonicModem.Callback {
    private ChatRepository repository;
    private MessageAdapter messageAdapter;
    private RecyclerView messagesRecyclerView;
    private EditText messageInput;
    private FloatingActionButton sendButton;
    private Spinner channelSpinner;
    private TextView statusText;

    private String nickname;
    private int channelIndex;
    private boolean spinnerInitialized;
    private boolean activityVisible;
    private UltrasonicModem modem;

    private final ActivityResultLauncher<String> microphonePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startListeningIfPossible();
                } else {
                    statusText.setText(R.string.microphone_off);
                    Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_chat);
        applySystemBarInsets(findViewById(R.id.chatRoot));

        nickname = getIntent().getStringExtra(MainActivity.EXTRA_NICKNAME);
        channelIndex = getIntent().getIntExtra(MainActivity.EXTRA_CHANNEL, 0);
        if (nickname == null || nickname.isBlank() || channelIndex < 0 || channelIndex > 2) {
            finish();
            return;
        }

        repository = new ChatRepository(this);
        repository.ensureConversation(nickname, channelIndex);

        TextView nicknameText = findViewById(R.id.nicknameText);
        TextView avatarInitial = findViewById(R.id.avatarInitial);
        statusText = findViewById(R.id.statusText);
        nicknameText.setText(nickname);
        avatarInitial.setText(nickname.substring(0, 1).toUpperCase(Locale.ROOT));
        findViewById(R.id.backButton).setOnClickListener(view -> finish());

        messagesRecyclerView = findViewById(R.id.messagesRecyclerView);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        messagesRecyclerView.setLayoutManager(layoutManager);
        messageAdapter = new MessageAdapter();
        messagesRecyclerView.setAdapter(messageAdapter);

        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);
        sendButton.setOnClickListener(view -> sendCurrentMessage());
        messageInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentMessage();
                return true;
            }
            return false;
        });

        channelSpinner = findViewById(R.id.channelSpinner);
        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.channel_labels,
                android.R.layout.simple_spinner_item
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        channelSpinner.setAdapter(spinnerAdapter);
        channelSpinner.setSelection(channelIndex, false);
        channelSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                if (!spinnerInitialized) {
                    spinnerInitialized = true;
                    return;
                }
                switchChannel(position);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                // Kanał zawsze pozostaje wybrany.
            }
        });

        modem = new UltrasonicModem(channelIndex, this);
        loadCurrentConversation();
        ensureMicrophonePermission();
    }

    @Override
    protected void onStart() {
        super.onStart();
        activityVisible = true;
        startListeningIfPossible();
    }

    @Override
    protected void onStop() {
        activityVisible = false;
        if (modem != null) {
            modem.stopReceiver();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (modem != null) {
            modem.close();
        }
        super.onDestroy();
    }

    private void sendCurrentMessage() {
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }

        if (text.getBytes(StandardCharsets.UTF_8).length > UltrasonicModem.MAX_PAYLOAD_BYTES) {
            Toast.makeText(this, R.string.message_too_long, Toast.LENGTH_LONG).show();
            return;
        }

        if (!modem.send(text)) {
            Toast.makeText(this, R.string.busy, Toast.LENGTH_SHORT).show();
            return;
        }

        ChatMessage message = new ChatMessage(text, true, System.currentTimeMillis());
        repository.appendMessage(nickname, channelIndex, message);
        messageAdapter.addMessage(message);
        scrollToBottom();
        messageInput.setText("");
    }

    private void switchChannel(int newChannel) {
        if (newChannel == channelIndex) {
            return;
        }
        if (modem.isTransmitting()) {
            Toast.makeText(this, R.string.busy, Toast.LENGTH_SHORT).show();
            channelSpinner.setSelection(channelIndex, false);
            return;
        }

        channelIndex = newChannel;
        repository.ensureConversation(nickname, channelIndex);
        modem.setChannel(channelIndex);
        loadCurrentConversation();
        startListeningIfPossible();
    }

    private void loadCurrentConversation() {
        String id = ChatRepository.conversationId(nickname, channelIndex);
        List<ChatMessage> messages = repository.getMessages(id);
        messageAdapter.submitList(messages);
        scrollToBottom();
    }

    private void scrollToBottom() {
        messagesRecyclerView.post(() -> messagesRecyclerView.scrollToPosition(messageAdapter.getLastPosition()));
    }

    private void ensureMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startListeningIfPossible();
            return;
        }

        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.permission_title)
                    .setMessage(R.string.permission_message)
                    .setPositiveButton("Zezwól", (dialog, which) ->
                            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void startListeningIfPossible() {
        if (!activityVisible || modem == null) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            modem.startReceiver();
        }
    }

    @Override
    public void onReceiverStateChanged(boolean active) {
        runOnUiThread(() -> statusText.setText(active
                ? getString(R.string.channel_status, channelIndex + 1, getString(R.string.listening))
                : getString(R.string.microphone_off)));
    }

    @Override
    public void onTransmissionStateChanged(boolean active) {
        runOnUiThread(() -> {
            sendButton.setEnabled(!active);
            channelSpinner.setEnabled(!active);
            statusText.setText(active
                    ? getString(R.string.transmitting)
                    : getString(R.string.channel_status, channelIndex + 1, getString(R.string.listening)));
        });
    }

    @Override
    public void onMessageReceived(@NonNull String messageText) {
        runOnUiThread(() -> {
            ChatMessage message = new ChatMessage(messageText, false, System.currentTimeMillis());
            repository.appendMessage(nickname, channelIndex, message);
            messageAdapter.addMessage(message);
            scrollToBottom();
        });
    }

    @Override
    public void onError(@NonNull String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    private void applySystemBarInsets(@NonNull android.view.View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottom = Math.max(systemBars.bottom, ime.bottom);
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, bottom);
            return insets;
        });
    }
}
