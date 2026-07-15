package com.przyklad.soundboard;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

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

import com.google.android.material.snackbar.Snackbar;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends AppCompatActivity {

    private static final String STATE_TEXTS = "chat_texts";
    private static final String STATE_SENT_FLAGS = "chat_sent_flags";

    private RecyclerView messagesList;
    private EditText messageInput;
    private ImageButton sendButton;
    private TextView statusText;
    private View statusDot;
    private TextView emptyText;

    private MessageAdapter adapter;
    private UltrasonicModem modem;
    private boolean receiverActive = false;

    private final ActivityResultLauncher<String> microphonePermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) {
                            startReceiverIfAllowed();
                        } else {
                            setStatus(
                                    "Brak mikrofonu • odbiór wyłączony",
                                    R.color.status_red
                            );
                            Snackbar.make(
                                    messageInput,
                                    "Możesz nadawać, ale odbiór wymaga mikrofonu.",
                                    Snackbar.LENGTH_LONG
                            ).show();
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        bindViews();
        configureInsets();
        configureMessages(savedInstanceState);
        configureComposer();
        createModem();
        requestMicrophonePermissionIfNeeded();
    }

    private void bindViews() {
        messagesList = findViewById(R.id.messagesList);
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);
        statusText = findViewById(R.id.statusText);
        statusDot = findViewById(R.id.statusDot);
        emptyText = findViewById(R.id.emptyText);
    }

    private void configureInsets() {
        View root = findViewById(R.id.rootContainer);
        int initialLeft = root.getPaddingLeft();
        int initialTop = root.getPaddingTop();
        int initialRight = root.getPaddingRight();
        int initialBottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottom = Math.max(systemBars.bottom, ime.bottom);

            view.setPadding(
                    initialLeft + systemBars.left,
                    initialTop + systemBars.top,
                    initialRight + systemBars.right,
                    initialBottom + bottom
            );
            return insets;
        });
    }

    private void configureMessages(Bundle savedInstanceState) {
        adapter = new MessageAdapter();

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        messagesList.setLayoutManager(layoutManager);
        messagesList.setAdapter(adapter);

        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                updateEmptyState();
            }

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                updateEmptyState();
                scrollToLastMessage();
            }
        });

        restoreMessages(savedInstanceState);
        updateEmptyState();
    }

    private void restoreMessages(Bundle state) {
        if (state == null) {
            return;
        }

        ArrayList<String> texts = state.getStringArrayList(STATE_TEXTS);
        boolean[] sentFlags = state.getBooleanArray(STATE_SENT_FLAGS);
        if (texts == null || sentFlags == null || texts.size() != sentFlags.length) {
            return;
        }

        List<ChatMessage> restored = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            restored.add(new ChatMessage(texts.get(i), sentFlags[i]));
        }
        adapter.replaceMessages(restored);
    }

    private void configureComposer() {
        sendButton.setOnClickListener(view -> sendCurrentMessage());

        messageInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentMessage();
                return true;
            }
            return false;
        });
    }

    private void createModem() {
        modem = new UltrasonicModem(new UltrasonicModem.Callback() {
            @Override
            public void onReceiverStateChanged(boolean active) {
                runOnUiThread(() -> {
                    receiverActive = active;
                    if (modem == null || !modem.isTransmitting()) {
                        if (active) {
                            setStatus("Nasłuchiwanie • 18–20 kHz", R.color.status_green);
                        } else if (hasMicrophonePermission()) {
                            setStatus("Odbiornik zatrzymany", R.color.status_yellow);
                        }
                    }
                });
            }

            @Override
            public void onTransmissionStateChanged(boolean active) {
                runOnUiThread(() -> {
                    sendButton.setEnabled(!active);
                    sendButton.setAlpha(active ? 0.55f : 1.0f);

                    if (active) {
                        setStatus("Nadawanie wiadomości…", R.color.primary);
                    } else if (receiverActive) {
                        setStatus("Nasłuchiwanie • 18–20 kHz", R.color.status_green);
                    } else if (!hasMicrophonePermission()) {
                        setStatus("Brak mikrofonu • odbiór wyłączony", R.color.status_red);
                    }
                });
            }

            @Override
            public void onMessageReceived(@NonNull String message) {
                runOnUiThread(() -> addMessage(message, false));
            }

            @Override
            public void onError(@NonNull String message) {
                runOnUiThread(() -> Snackbar.make(
                        messageInput,
                        message,
                        Snackbar.LENGTH_LONG
                ).show());
            }
        });
    }

    private void sendCurrentMessage() {
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }

        int byteLength = text.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength > UltrasonicModem.MAX_PAYLOAD_BYTES) {
            Snackbar.make(
                    messageInput,
                    "Wiadomość ma " + byteLength + " bajtów. Limit to "
                            + UltrasonicModem.MAX_PAYLOAD_BYTES + ".",
                    Snackbar.LENGTH_LONG
            ).show();
            return;
        }

        if (modem == null || !modem.send(text)) {
            Snackbar.make(
                    messageInput,
                    "Nadajnik jest zajęty. Poczekaj na zakończenie transmisji.",
                    Snackbar.LENGTH_SHORT
            ).show();
            return;
        }

        addMessage(text, true);
        messageInput.setText("");
    }

    private void addMessage(String text, boolean sentByMe) {
        adapter.addMessage(new ChatMessage(text, sentByMe));
    }

    private void scrollToLastMessage() {
        if (adapter.getItemCount() > 0) {
            messagesList.post(() ->
                    messagesList.smoothScrollToPosition(adapter.getItemCount() - 1));
        }
    }

    private void updateEmptyState() {
        emptyText.setVisibility(adapter.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void requestMicrophonePermissionIfNeeded() {
        if (hasMicrophonePermission()) {
            startReceiverIfAllowed();
            return;
        }

        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.microphone_permission_title)
                    .setMessage(R.string.microphone_permission_message)
                    .setNegativeButton("Nie teraz", (dialog, which) -> setStatus(
                            "Brak mikrofonu • odbiór wyłączony",
                            R.color.status_red
                    ))
                    .setPositiveButton("Zezwól", (dialog, which) ->
                            microphonePermissionLauncher.launch(
                                    Manifest.permission.RECORD_AUDIO))
                    .show();
        } else {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private boolean hasMicrophonePermission() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void startReceiverIfAllowed() {
        if (modem != null && hasMicrophonePermission()) {
            modem.startReceiver();
        }
    }

    private void setStatus(String text, int colorRes) {
        statusText.setText(text);
        int color = ContextCompat.getColor(this, colorRes);
        ViewCompat.setBackgroundTintList(statusDot, ColorStateList.valueOf(color));
    }

    @Override
    protected void onStart() {
        super.onStart();
        startReceiverIfAllowed();
    }

    @Override
    protected void onStop() {
        if (modem != null) {
            modem.stopReceiver();
        }
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        List<ChatMessage> snapshot = adapter.snapshot();
        ArrayList<String> texts = new ArrayList<>(snapshot.size());
        boolean[] sentFlags = new boolean[snapshot.size()];

        for (int i = 0; i < snapshot.size(); i++) {
            ChatMessage message = snapshot.get(i);
            texts.add(message.getText());
            sentFlags[i] = message.isSentByMe();
        }

        outState.putStringArrayList(STATE_TEXTS, texts);
        outState.putBooleanArray(STATE_SENT_FLAGS, sentFlags);
    }

    @Override
    protected void onDestroy() {
        if (modem != null) {
            modem.close();
            modem = null;
        }
        super.onDestroy();
    }
}
