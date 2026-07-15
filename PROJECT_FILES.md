# Ultrasonic Messenger — kompletna zawartość plików

## `.gitignore`

```text
.gradle/
.idea/
local.properties
*.iml
/build/
/app/build/
/captures/
.externalNativeBuild/
.cxx/

```

## `README.md`

```text
# Ultrasonic Messenger

Kompletny projekt demonstracyjnej aplikacji Android napisanej w Javie. Aplikacja tworzy osobne rozmowy dla nicku i wybranego kanału częstotliwości, zapisuje historię lokalnie i przesyła tekst przez wysokie tony generowane przez `AudioTrack`.

## Uruchomienie

1. Rozpakuj projekt i otwórz folder `UltrasonicMessenger` w Android Studio.
2. Ustaw Gradle JDK na JDK 17–22. Gradle 8.13 działa na JDK 22, natomiast kod aplikacji generuje bytecode Java 17 zgodny z Androidem.
3. Zainstaluj Android SDK 36.
4. Uruchom aplikację na dwóch fizycznych telefonach.
5. Na obu urządzeniach wybierz ten sam kanał i zezwól na mikrofon.

## Kanały

- Kanał 1: centrum około 17 kHz, używane tony 16,7–17,3 kHz.
- Kanał 2: centrum około 18 kHz, używane tony 17,7–18,3 kHz.
- Kanał 3: centrum około 19 kHz, używane tony 18,7–19,3 kHz.

## Protokół

Bezpośredni wzór `17000 + char * 20` przekraczałby pasmo wybranego kanału i dla wielu znaków wychodził ponad 20 kHz. Projekt używa stabilniejszej modulacji 4-FSK:

- 4 częstotliwości danych kodują wartości 2-bitowe,
- każdy bajt UTF-8 jest przesyłany jako 4 tony,
- każdy ton trwa 400 ms, po nim jest 100 ms ciszy,
- ramka zawiera trzy symbole START, 2-bajtową długość, tekst UTF-8, CRC-8 i dwa symbole END,
- odbiornik analizuje próbki PCM przez FFT i dekoduje dominujące częstotliwości.

## Ważne ograniczenia sprzętowe

Część telefonów filtruje częstotliwości powyżej 18 kHz albo ma słaby głośnik/mikrofon w tym paśmie. Najlepiej testować w cichym pomieszczeniu, z głośnością około 70–90%, w odległości 10–40 cm. Kanał 17 kHz zwykle ma największą kompatybilność, ale może być słyszalny dla młodszych osób.

Odbiornik działa w osobnym wątku podczas otwartego ekranu czatu. Stały odbiór po wygaszeniu ekranu wymagałby osobnego foreground service z trwałym powiadomieniem.

```

## `app/build.gradle`

```groovy
plugins {
    id 'com.android.application'
}

android {
    namespace 'com.przyklad.soundboard'
    compileSdk 36

    defaultConfig {
        applicationId 'com.przyklad.soundboard'
        minSdk 26
        targetSdk 35
        versionCode 1
        versionName '1.0'
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }

    compileOptions {
        // Gradle może działać na JDK 22. Kod Androida kompilujemy do wspieranego bytecode Java 17.
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}

dependencies {
    implementation 'androidx.activity:activity:1.10.1'
    implementation 'androidx.appcompat:appcompat:1.7.1'
    implementation 'androidx.constraintlayout:constraintlayout:2.2.1'
    implementation 'androidx.recyclerview:recyclerview:1.4.0'
    implementation 'com.google.android.material:material:1.12.0'
}

```

## `app/proguard-rules.pro`

```text
# Projekt demonstracyjny nie wymaga dodatkowych reguł ProGuard/R8.

```

## `app/src/main/AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-feature
        android:name="android.hardware.microphone"
        android:required="false" />

    <application
        android:allowBackup="true"
        android:icon="@drawable/ic_app"
        android:label="@string/app_name"
        android:roundIcon="@drawable/ic_app"
        android:supportsRtl="true"
        android:theme="@style/Theme.UltrasonicMessenger">

        <activity
            android:name=".ChatActivity"
            android:exported="false"
            android:screenOrientation="portrait"
            android:windowSoftInputMode="adjustResize" />

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="portrait">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>

</manifest>

```

## `app/src/main/java/com/przyklad/soundboard/ChatActivity.java`

```java
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

```

## `app/src/main/java/com/przyklad/soundboard/ChatListAdapter.java`

```java
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

```

## `app/src/main/java/com/przyklad/soundboard/ChatMessage.java`

```java
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

```

## `app/src/main/java/com/przyklad/soundboard/ChatRepository.java`

```java
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

```

## `app/src/main/java/com/przyklad/soundboard/ChatSummary.java`

```java
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

```

## `app/src/main/java/com/przyklad/soundboard/MainActivity.java`

```java
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

```

## `app/src/main/java/com/przyklad/soundboard/MessageAdapter.java`

```java
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

```

## `app/src/main/java/com/przyklad/soundboard/UltrasonicModem.java`

```java
package com.przyklad.soundboard;

import android.annotation.SuppressLint;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Process;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Modem akustyczny 4-FSK dla trzech kanałów około 17, 18 i 19 kHz.
 *
 * Każdy ton danych przenosi 2 bity (wartość 0..3). Pozwala to utrzymać
 * wszystkie częstotliwości w realnym paśmie głośników/mikrofonów telefonu.
 * Odbiornik wykonuje FFT na oknach PCM i wybiera dominującą częstotliwość.
 *
 * Ramka:
 * START x3, długość UTF-8 (2 bajty), payload, CRC-8, END x2.
 */
public final class UltrasonicModem implements AutoCloseable {
    public interface Callback {
        void onReceiverStateChanged(boolean active);
        void onTransmissionStateChanged(boolean active);
        void onMessageReceived(@NonNull String message);
        void onError(@NonNull String message);
    }

    public static final int MAX_PAYLOAD_BYTES = 240;

    private static final int SAMPLE_RATE = 48_000;
    private static final int TONE_DURATION_MS = 400;
    private static final int GAP_DURATION_MS = 100;
    private static final int FFT_SIZE = 2_048;
    private static final int TONE_SAMPLES = SAMPLE_RATE * TONE_DURATION_MS / 1000;
    private static final int GAP_SAMPLES = SAMPLE_RATE * GAP_DURATION_MS / 1000;

    private static final int SYMBOL_DATA_0 = 0;
    private static final int SYMBOL_DATA_1 = 1;
    private static final int SYMBOL_DATA_2 = 2;
    private static final int SYMBOL_DATA_3 = 3;
    private static final int SYMBOL_START = 4;
    private static final int SYMBOL_END = 5;
    private static final int SYMBOL_NONE = -1;
    private static final int SYMBOL_COUNT = 6;

    private static final double[] CHANNEL_CENTERS = {17_000.0, 18_000.0, 19_000.0};
    private static final double[] SYMBOL_OFFSETS = {-180.0, -60.0, 60.0, 180.0, -300.0, 300.0};

    private static final double MIN_FFT_MAGNITUDE = 180.0;
    private static final double MIN_DOMINANCE_RATIO = 1.45;
    private static final int REQUIRED_STABLE_WINDOWS = 5;

    private final Callback callback;
    private final ExecutorService transmitterExecutor =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "ultrasonic-tx"));
    private final ExecutorService receiverExecutor =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "ultrasonic-rx"));

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean transmitting = new AtomicBoolean(false);
    private final AtomicBoolean receiverRequested = new AtomicBoolean(false);
    private final AtomicInteger receiverGeneration = new AtomicInteger(0);
    private final AtomicInteger channelIndex = new AtomicInteger(0);

    private final FrameDecoder decoder = new FrameDecoder();
    private final FftFrequencyDetector detector = new FftFrequencyDetector();
    private final short[] silence = new short[GAP_SAMPLES];

    private volatile AudioRecord audioRecord;
    private volatile short[][] cachedTones;
    private volatile String lastReceivedMessage = "";
    private volatile long lastReceivedAtMs;

    public UltrasonicModem(int initialChannelIndex, @NonNull Callback callback) {
        validateChannel(initialChannelIndex);
        this.callback = callback;
        channelIndex.set(initialChannelIndex);
        rebuildToneCache(initialChannelIndex);
    }

    public void setChannel(int newChannelIndex) {
        validateChannel(newChannelIndex);
        if (channelIndex.get() == newChannelIndex) {
            return;
        }
        boolean restart = receiverRequested.get();
        stopReceiver();
        channelIndex.set(newChannelIndex);
        decoder.reset();
        rebuildToneCache(newChannelIndex);
        if (restart && !closed.get()) {
            startReceiver();
        }
    }

    public boolean isTransmitting() {
        return transmitting.get();
    }

    public boolean send(@NonNull String text) {
        if (closed.get()) {
            return false;
        }
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        if (payload.length == 0 || payload.length > MAX_PAYLOAD_BYTES) {
            return false;
        }
        if (!transmitting.compareAndSet(false, true)) {
            return false;
        }

        int channelSnapshot = channelIndex.get();
        short[][] tonesSnapshot = cachedTones;
        transmitterExecutor.execute(() -> {
            callback.onTransmissionStateChanged(true);
            decoder.reset();
            try {
                transmitFrame(payload, tonesSnapshot);
            } catch (Exception exception) {
                callback.onError("Błąd nadawania: " + safeMessage(exception));
            } finally {
                sleepQuietly(350);
                if (channelIndex.get() == channelSnapshot) {
                    decoder.reset();
                }
                transmitting.set(false);
                callback.onTransmissionStateChanged(false);
            }
        });
        return true;
    }

    public void startReceiver() {
        if (closed.get() || !receiverRequested.compareAndSet(false, true)) {
            return;
        }
        int generation = receiverGeneration.incrementAndGet();
        receiverExecutor.execute(() -> receiverLoop(generation));
    }

    public void stopReceiver() {
        receiverRequested.set(false);
        receiverGeneration.incrementAndGet();
        AudioRecord record = audioRecord;
        if (record != null) {
            try {
                record.stop();
            } catch (IllegalStateException ignored) {
                // Pętla odbiornika mogła zakończyć nagrywanie wcześniej.
            }
        }
    }

    private void transmitFrame(byte[] payload, short[][] tones) {
        int minBuffer = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        if (minBuffer <= 0) {
            throw new IllegalStateException("AudioTrack nie zwrócił poprawnego bufora");
        }

        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(Math.max(minBuffer, TONE_SAMPLES * 2))
                .build();

        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            track.release();
            throw new IllegalStateException("Nie udało się uruchomić AudioTrack");
        }

        try {
            track.setVolume(0.82f);
            track.play();
            writeSymbol(track, tones, SYMBOL_START);
            writeSymbol(track, tones, SYMBOL_START);
            writeSymbol(track, tones, SYMBOL_START);

            writeByte(track, tones, (payload.length >>> 8) & 0xFF);
            writeByte(track, tones, payload.length & 0xFF);
            for (byte value : payload) {
                writeByte(track, tones, value & 0xFF);
            }
            writeByte(track, tones, crc8(payload));

            writeSymbol(track, tones, SYMBOL_END);
            writeSymbol(track, tones, SYMBOL_END);
        } finally {
            try {
                track.stop();
            } catch (IllegalStateException ignored) {
                // Zasób i tak zostanie zwolniony.
            }
            track.flush();
            track.release();
        }
    }

    private void writeByte(AudioTrack track, short[][] tones, int unsignedByte) {
        writeSymbol(track, tones, (unsignedByte >>> 6) & 0x03);
        writeSymbol(track, tones, (unsignedByte >>> 4) & 0x03);
        writeSymbol(track, tones, (unsignedByte >>> 2) & 0x03);
        writeSymbol(track, tones, unsignedByte & 0x03);
    }

    private void writeSymbol(AudioTrack track, short[][] tones, int symbol) {
        writeFully(track, tones[symbol]);
        writeFully(track, silence);
    }

    private static void writeFully(AudioTrack track, short[] samples) {
        int offset = 0;
        while (offset < samples.length) {
            int written = track.write(samples, offset, samples.length - offset, AudioTrack.WRITE_BLOCKING);
            if (written < 0) {
                throw new IllegalStateException("AudioTrack.write(): " + written);
            }
            offset += written;
        }
    }

    @SuppressLint("MissingPermission")
    private void receiverLoop(int generation) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        AudioRecord record = null;
        int localChannel = channelIndex.get();

        try {
            if (!receiverRequested.get() || generation != receiverGeneration.get() || closed.get()) {
                return;
            }
            record = createAudioRecord();
            audioRecord = record;
            record.startRecording();
            callback.onReceiverStateChanged(true);

            short[] window = new short[FFT_SIZE];
            SymbolDebouncer debouncer = new SymbolDebouncer(decoder);

            while (receiverRequested.get()
                    && generation == receiverGeneration.get()
                    && channelIndex.get() == localChannel
                    && !closed.get()) {
                int read;
                try {
                    read = record.read(window, 0, window.length, AudioRecord.READ_BLOCKING);
                } catch (IllegalStateException exception) {
                    if (receiverRequested.get() && generation == receiverGeneration.get()) {
                        throw exception;
                    }
                    break;
                }

                if (read <= 0) {
                    continue;
                }
                if (transmitting.get()) {
                    debouncer.reset();
                    decoder.reset();
                    continue;
                }

                int symbol = detector.detect(window, read, localChannel);
                debouncer.accept(symbol);
            }
        } catch (SecurityException exception) {
            if (!closed.get()) {
                callback.onError("Brak uprawnienia do mikrofonu");
            }
        } catch (Exception exception) {
            if (receiverRequested.get() && generation == receiverGeneration.get() && !closed.get()) {
                callback.onError("Błąd odbiornika: " + safeMessage(exception));
            }
        } finally {
            if (audioRecord == record) {
                audioRecord = null;
            }
            if (record != null) {
                try {
                    if (record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        record.stop();
                    }
                } catch (IllegalStateException ignored) {
                    // Zasób i tak zostanie zwolniony.
                }
                record.release();
            }
            if (generation == receiverGeneration.get()) {
                receiverRequested.set(false);
                callback.onReceiverStateChanged(false);
            }
        }
    }

    @SuppressLint("MissingPermission")
    private static AudioRecord createAudioRecord() {
        int minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        if (minBuffer <= 0) {
            throw new IllegalStateException("AudioRecord nie zwrócił poprawnego bufora");
        }
        int bufferBytes = Math.max(minBuffer, FFT_SIZE * 2 * 6);

        try {
            AudioRecord unprocessed = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.UNPROCESSED)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build())
                    .setBufferSizeInBytes(bufferBytes)
                    .build();
            if (unprocessed.getState() == AudioRecord.STATE_INITIALIZED) {
                return unprocessed;
            }
            unprocessed.release();
        } catch (IllegalArgumentException | UnsupportedOperationException ignored) {
            // Nie każdy telefon udostępnia źródło UNPROCESSED.
        }

        AudioRecord fallback = new AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build())
                .setBufferSizeInBytes(bufferBytes)
                .build();
        if (fallback.getState() != AudioRecord.STATE_INITIALIZED) {
            fallback.release();
            throw new IllegalStateException("Nie udało się uruchomić AudioRecord");
        }
        return fallback;
    }

    private synchronized void rebuildToneCache(int channel) {
        short[][] tones = new short[SYMBOL_COUNT][];
        for (int symbol = 0; symbol < SYMBOL_COUNT; symbol++) {
            tones[symbol] = generateTone(frequencyForSymbol(channel, symbol));
        }
        cachedTones = tones;
    }

    private static short[] generateTone(double frequency) {
        short[] samples = new short[TONE_SAMPLES];
        int fadeSamples = SAMPLE_RATE / 100;
        double amplitude = Short.MAX_VALUE * 0.68;
        for (int i = 0; i < samples.length; i++) {
            double envelope = 1.0;
            if (i < fadeSamples) {
                envelope = i / (double) fadeSamples;
            } else if (i >= samples.length - fadeSamples) {
                envelope = (samples.length - 1 - i) / (double) fadeSamples;
            }
            double phase = 2.0 * Math.PI * frequency * i / SAMPLE_RATE;
            samples[i] = (short) Math.round(Math.sin(phase) * amplitude * envelope);
        }
        return samples;
    }

    private static double frequencyForSymbol(int channel, int symbol) {
        validateChannel(channel);
        if (symbol < 0 || symbol >= SYMBOL_COUNT) {
            throw new IllegalArgumentException("Nieznany symbol: " + symbol);
        }
        return CHANNEL_CENTERS[channel] + SYMBOL_OFFSETS[symbol];
    }

    private static void validateChannel(int channel) {
        if (channel < 0 || channel >= CHANNEL_CENTERS.length) {
            throw new IllegalArgumentException("Nieznany kanał: " + channel);
        }
    }

    private static int crc8(byte[] data) {
        int crc = 0;
        for (byte value : data) {
            crc ^= value & 0xFF;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x80) != 0
                        ? ((crc << 1) ^ 0x07) & 0xFF
                        : (crc << 1) & 0xFF;
            }
        }
        return crc;
    }

    private void deliverDecodedMessage(String message) {
        long now = System.currentTimeMillis();
        if (message.equals(lastReceivedMessage) && now - lastReceivedAtMs < 2_500L) {
            return;
        }
        lastReceivedMessage = message;
        lastReceivedAtMs = now;
        callback.onMessageReceived(message);
    }

    private final class FrameDecoder {
        private static final int WAIT_START = 0;
        private static final int READ_FRAME = 1;
        private static final int WAIT_END = 2;

        private int state = WAIT_START;
        private int startCount;
        private int endCount;
        private int assembledByte;
        private int symbolsInByte;
        private int lengthBytesRead;
        private int expectedPayloadLength = -1;
        private final ByteArrayOutputStream frameBytes = new ByteArrayOutputStream();

        synchronized void acceptSymbol(int symbol) {
            switch (state) {
                case WAIT_START -> acceptStart(symbol);
                case READ_FRAME -> acceptFrameSymbol(symbol);
                case WAIT_END -> acceptEnd(symbol);
                default -> reset();
            }
        }

        private void acceptStart(int symbol) {
            if (symbol == SYMBOL_START) {
                startCount++;
                if (startCount >= 3) {
                    beginFrame();
                }
            } else {
                startCount = 0;
            }
        }

        private void beginFrame() {
            state = READ_FRAME;
            startCount = 0;
            endCount = 0;
            assembledByte = 0;
            symbolsInByte = 0;
            lengthBytesRead = 0;
            expectedPayloadLength = -1;
            frameBytes.reset();
        }

        private void acceptFrameSymbol(int symbol) {
            if (symbol < SYMBOL_DATA_0 || symbol > SYMBOL_DATA_3) {
                reset();
                if (symbol == SYMBOL_START) {
                    startCount = 1;
                }
                return;
            }

            assembledByte = (assembledByte << 2) | symbol;
            symbolsInByte++;
            if (symbolsInByte < 4) {
                return;
            }

            int value = assembledByte & 0xFF;
            assembledByte = 0;
            symbolsInByte = 0;

            if (lengthBytesRead < 2) {
                if (lengthBytesRead == 0) {
                    expectedPayloadLength = value << 8;
                } else {
                    expectedPayloadLength |= value;
                    if (expectedPayloadLength <= 0 || expectedPayloadLength > MAX_PAYLOAD_BYTES) {
                        reset();
                        return;
                    }
                }
                lengthBytesRead++;
                return;
            }

            frameBytes.write(value);
            if (frameBytes.size() == expectedPayloadLength + 1) {
                state = WAIT_END;
                endCount = 0;
            } else if (frameBytes.size() > expectedPayloadLength + 1) {
                reset();
            }
        }

        private void acceptEnd(int symbol) {
            if (symbol != SYMBOL_END) {
                reset();
                return;
            }
            endCount++;
            if (endCount < 2) {
                return;
            }

            byte[] frame = frameBytes.toByteArray();
            if (frame.length == expectedPayloadLength + 1) {
                byte[] payload = Arrays.copyOf(frame, expectedPayloadLength);
                int receivedCrc = frame[frame.length - 1] & 0xFF;
                if (receivedCrc == crc8(payload)) {
                    try {
                        String decoded = StandardCharsets.UTF_8.newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                                .decode(ByteBuffer.wrap(payload))
                                .toString();
                        deliverDecodedMessage(decoded);
                    } catch (CharacterCodingException exception) {
                        callback.onError("Odebrano niepoprawny tekst UTF-8");
                    }
                }
            }
            reset();
        }

        synchronized void reset() {
            state = WAIT_START;
            startCount = 0;
            endCount = 0;
            assembledByte = 0;
            symbolsInByte = 0;
            lengthBytesRead = 0;
            expectedPayloadLength = -1;
            frameBytes.reset();
        }
    }

    private static final class SymbolDebouncer {
        private final FrameDecoder decoder;
        private int candidate = SYMBOL_NONE;
        private int stableWindows;
        private boolean emitted;

        SymbolDebouncer(FrameDecoder decoder) {
            this.decoder = decoder;
        }

        void accept(int symbol) {
            if (symbol == SYMBOL_NONE) {
                reset();
                return;
            }
            if (candidate != symbol) {
                candidate = symbol;
                stableWindows = 1;
                emitted = false;
                return;
            }
            stableWindows++;
            if (!emitted && stableWindows >= REQUIRED_STABLE_WINDOWS) {
                emitted = true;
                decoder.acceptSymbol(candidate);
            }
        }

        void reset() {
            candidate = SYMBOL_NONE;
            stableWindows = 0;
            emitted = false;
        }
    }

    private static final class FftFrequencyDetector {
        private final double[] real = new double[FFT_SIZE];
        private final double[] imaginary = new double[FFT_SIZE];
        private final double[] hann = new double[FFT_SIZE];

        FftFrequencyDetector() {
            for (int i = 0; i < FFT_SIZE; i++) {
                hann[i] = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / (FFT_SIZE - 1));
            }
        }

        synchronized int detect(short[] samples, int length, int channel) {
            Arrays.fill(real, 0.0);
            Arrays.fill(imaginary, 0.0);
            int usable = Math.min(length, FFT_SIZE);
            for (int i = 0; i < usable; i++) {
                real[i] = samples[i] * hann[i];
            }
            fft(real, imaginary);

            double best = 0.0;
            double second = 0.0;
            int bestSymbol = SYMBOL_NONE;
            for (int symbol = 0; symbol < SYMBOL_COUNT; symbol++) {
                double magnitude = magnitudeNear(frequencyForSymbol(channel, symbol));
                if (magnitude > best) {
                    second = best;
                    best = magnitude;
                    bestSymbol = symbol;
                } else if (magnitude > second) {
                    second = magnitude;
                }
            }

            double normalized = best / FFT_SIZE;
            double dominance = second <= 0.001 ? Double.POSITIVE_INFINITY : best / second;
            if (normalized < MIN_FFT_MAGNITUDE || dominance < MIN_DOMINANCE_RATIO) {
                return SYMBOL_NONE;
            }
            return bestSymbol;
        }

        private double magnitudeNear(double frequency) {
            int centerBin = (int) Math.round(frequency * FFT_SIZE / SAMPLE_RATE);
            double best = 0.0;
            for (int bin = Math.max(1, centerBin - 2); bin <= Math.min(FFT_SIZE / 2 - 1, centerBin + 2); bin++) {
                double magnitude = Math.hypot(real[bin], imaginary[bin]);
                if (magnitude > best) {
                    best = magnitude;
                }
            }
            return best;
        }

        private static void fft(double[] real, double[] imaginary) {
            int n = real.length;
            int j = 0;
            for (int i = 1; i < n; i++) {
                int bit = n >> 1;
                while ((j & bit) != 0) {
                    j ^= bit;
                    bit >>= 1;
                }
                j ^= bit;
                if (i < j) {
                    double tempReal = real[i];
                    real[i] = real[j];
                    real[j] = tempReal;
                    double tempImaginary = imaginary[i];
                    imaginary[i] = imaginary[j];
                    imaginary[j] = tempImaginary;
                }
            }

            for (int length = 2; length <= n; length <<= 1) {
                double angle = -2.0 * Math.PI / length;
                double wLengthReal = Math.cos(angle);
                double wLengthImaginary = Math.sin(angle);
                for (int start = 0; start < n; start += length) {
                    double wReal = 1.0;
                    double wImaginary = 0.0;
                    for (int offset = 0; offset < length / 2; offset++) {
                        int even = start + offset;
                        int odd = even + length / 2;
                        double oddReal = real[odd] * wReal - imaginary[odd] * wImaginary;
                        double oddImaginary = real[odd] * wImaginary + imaginary[odd] * wReal;

                        real[odd] = real[even] - oddReal;
                        imaginary[odd] = imaginary[even] - oddImaginary;
                        real[even] += oddReal;
                        imaginary[even] += oddImaginary;

                        double nextWReal = wReal * wLengthReal - wImaginary * wLengthImaginary;
                        wImaginary = wReal * wLengthImaginary + wImaginary * wLengthReal;
                        wReal = nextWReal;
                    }
                }
            }
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    private static void sleepQuietly(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        stopReceiver();
        transmitterExecutor.shutdownNow();
        receiverExecutor.shutdownNow();
        try {
            transmitterExecutor.awaitTermination(500, TimeUnit.MILLISECONDS);
            receiverExecutor.awaitTermination(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}

```

## `app/src/main/res/drawable/avatar_circle.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <solid android:color="@color/primary_soft" />
</shape>

```

## `app/src/main/res/drawable/channel_badge_background.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/primary_soft" />
    <corners android:radius="10dp" />
    <padding android:left="7dp" android:top="3dp" android:right="7dp" android:bottom="3dp" />
</shape>

```

## `app/src/main/res/drawable/ic_add.xml`

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="@color/white" android:pathData="M11,5h2v6h6v2h-6v6h-2v-6H5v-2h6z" />
</vector>

```

## `app/src/main/res/drawable/ic_app.xml`

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#FF635BFF" android:pathData="M0,0h108v108h-108z" />
    <path android:fillColor="#FFFFFFFF" android:pathData="M20,54c7,-17 17,-26 30,-26 15,0 18,17 31,17 4,0 7,-2 10,-5v11c-3,2 -7,3 -11,3 -16,0 -19,-17 -31,-17 -8,0 -15,7 -21,20 -4,8 -5,15 -5,23H13c0,-9 2,-18 7,-26zM17,69c8,-15 17,-22 28,-22 15,0 18,17 31,17 6,0 11,-3 15,-8v12c-4,3 -9,5 -15,5 -16,0 -19,-17 -31,-17 -7,0 -13,5 -19,16 -2,4 -3,8 -4,12H12c1,-5 2,-10 5,-15z" />
</vector>

```

## `app/src/main/res/drawable/ic_back.xml`

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="@color/text_primary" android:pathData="M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.42,-1.41L7.83,13H20z" />
</vector>

```

## `app/src/main/res/drawable/ic_search.xml`

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="@color/text_primary" android:pathData="M9.5,3a6.5,6.5 0,1 0,3.98 11.64L19.85,21 21,19.85l-6.36,-6.37A6.5,6.5 0,0 0,9.5 3zM9.5,5a4.5,4.5 0,1 1,0 9,4.5 4.5,0 0,1 0,-9z" />
</vector>

```

## `app/src/main/res/drawable/ic_send.xml`

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="@color/white" android:pathData="M3.4,20.4 22,12 3.4,3.6 3,10.1l13,1.9 -13,1.9z" />
</vector>

```

## `app/src/main/res/drawable/input_background.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/surface" />
    <corners android:radius="28dp" />
    <stroke android:width="1dp" android:color="@color/divider" />
</shape>

```

## `app/src/main/res/drawable/receive_bubble.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/received_bubble" />
    <corners
        android:topLeftRadius="6dp"
        android:topRightRadius="20dp"
        android:bottomLeftRadius="20dp"
        android:bottomRightRadius="20dp" />
    <padding android:left="14dp" android:top="10dp" android:right="14dp" android:bottom="8dp" />
</shape>

```

## `app/src/main/res/drawable/send_bubble.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient
        android:angle="0"
        android:startColor="#FF635BFF"
        android:endColor="#FF7C55E9" />
    <corners
        android:topLeftRadius="20dp"
        android:topRightRadius="6dp"
        android:bottomLeftRadius="20dp"
        android:bottomRightRadius="20dp" />
    <padding android:left="14dp" android:top="10dp" android:right="14dp" android:bottom="8dp" />
</shape>

```

## `app/src/main/res/drawable/spinner_background.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/surface" />
    <corners android:radius="14dp" />
    <stroke android:width="1dp" android:color="@color/divider" />
    <padding android:left="12dp" android:top="4dp" android:right="12dp" android:bottom="4dp" />
</shape>

```

## `app/src/main/res/drawable/status_dot.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <solid android:color="@color/online" />
    <size android:width="8dp" android:height="8dp" />
</shape>

```

## `app/src/main/res/layout/activity_chat.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/chatRoot"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background">

    <androidx.constraintlayout.widget.ConstraintLayout
        android:id="@+id/chatTopBar"
        android:layout_width="0dp"
        android:layout_height="76dp"
        android:background="@color/surface"
        android:elevation="2dp"
        android:paddingEnd="16dp"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent">

        <ImageButton
            android:id="@+id/backButton"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="@string/back"
            android:padding="12dp"
            android:src="@drawable/ic_back"
            app:layout_constraintBottom_toBottomOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toTopOf="parent" />

        <FrameLayout
            android:id="@+id/chatAvatar"
            android:layout_width="46dp"
            android:layout_height="46dp"
            android:background="@drawable/avatar_circle"
            app:layout_constraintBottom_toBottomOf="parent"
            app:layout_constraintStart_toEndOf="@id/backButton"
            app:layout_constraintTop_toTopOf="parent">

            <TextView
                android:id="@+id/avatarInitial"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:gravity="center"
                android:textColor="@color/primary"
                android:textSize="18sp"
                android:textStyle="bold" />

            <View
                android:layout_width="11dp"
                android:layout_height="11dp"
                android:layout_gravity="end|bottom"
                android:background="@drawable/status_dot" />
        </FrameLayout>

        <TextView
            android:id="@+id/nicknameText"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="12dp"
            android:ellipsize="end"
            android:maxLines="1"
            android:textColor="@color/text_primary"
            android:textSize="17sp"
            android:textStyle="bold"
            app:layout_constraintBottom_toTopOf="@id/statusText"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toEndOf="@id/chatAvatar"
            app:layout_constraintTop_toTopOf="@id/chatAvatar"
            app:layout_constraintVertical_chainStyle="packed" />

        <TextView
            android:id="@+id/statusText"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="12dp"
            android:ellipsize="end"
            android:maxLines="1"
            android:text="@string/microphone_off"
            android:textColor="@color/text_secondary"
            android:textSize="12sp"
            app:layout_constraintBottom_toBottomOf="@id/chatAvatar"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toEndOf="@id/chatAvatar"
            app:layout_constraintTop_toBottomOf="@id/nicknameText" />

    </androidx.constraintlayout.widget.ConstraintLayout>

    <Spinner
        android:id="@+id/channelSpinner"
        android:layout_width="0dp"
        android:layout_height="48dp"
        android:layout_marginStart="16dp"
        android:layout_marginTop="12dp"
        android:layout_marginEnd="16dp"
        android:background="@drawable/spinner_background"
        android:entries="@array/channel_labels"
        android:paddingStart="12dp"
        android:paddingEnd="12dp"
        android:spinnerMode="dropdown"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/chatTopBar" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/messagesRecyclerView"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:clipToPadding="false"
        android:overScrollMode="never"
        android:paddingStart="12dp"
        android:paddingTop="14dp"
        android:paddingEnd="12dp"
        android:paddingBottom="14dp"
        app:layout_constraintBottom_toTopOf="@id/inputCard"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/channelSpinner" />

    <androidx.constraintlayout.widget.ConstraintLayout
        android:id="@+id/inputCard"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="12dp"
        android:layout_marginEnd="12dp"
        android:layout_marginBottom="12dp"
        android:background="@drawable/input_background"
        android:elevation="3dp"
        android:minHeight="58dp"
        android:paddingStart="16dp"
        android:paddingTop="5dp"
        android:paddingEnd="5dp"
        android:paddingBottom="5dp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent">

        <EditText
            android:id="@+id/messageInput"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:background="@null"
            android:gravity="center_vertical"
            android:hint="@string/message_hint"
            android:imeOptions="actionSend"
            android:inputType="textCapSentences|textMultiLine"
            android:maxLines="4"
            android:minHeight="48dp"
            android:paddingTop="8dp"
            android:paddingBottom="8dp"
            android:textColor="@color/text_primary"
            android:textColorHint="@color/text_secondary"
            android:textSize="16sp"
            app:layout_constraintBottom_toBottomOf="parent"
            app:layout_constraintEnd_toStartOf="@id/sendButton"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toTopOf="parent" />

        <com.google.android.material.floatingactionbutton.FloatingActionButton
            android:id="@+id/sendButton"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:contentDescription="@string/send"
            android:src="@drawable/ic_send"
            app:backgroundTint="@color/primary"
            app:elevation="0dp"
            app:fabCustomSize="48dp"
            app:layout_constraintBottom_toBottomOf="parent"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintTop_toTopOf="parent"
            app:maxImageSize="22dp"
            app:tint="@color/white" />

    </androidx.constraintlayout.widget.ConstraintLayout>

</androidx.constraintlayout.widget.ConstraintLayout>

```

## `app/src/main/res/layout/activity_main.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/mainRoot"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background">

    <androidx.constraintlayout.widget.ConstraintLayout
        android:id="@+id/topBar"
        android:layout_width="0dp"
        android:layout_height="76dp"
        android:background="@color/surface"
        android:elevation="2dp"
        android:paddingStart="20dp"
        android:paddingEnd="12dp"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent">

        <TextView
            android:id="@+id/titleText"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:text="@string/app_name"
            android:textColor="@color/text_primary"
            android:textSize="23sp"
            android:textStyle="bold"
            app:layout_constraintBottom_toBottomOf="parent"
            app:layout_constraintEnd_toStartOf="@id/searchButton"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toTopOf="parent" />

        <ImageButton
            android:id="@+id/searchButton"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="@string/search"
            android:padding="12dp"
            android:src="@drawable/ic_search"
            app:layout_constraintBottom_toBottomOf="parent"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintTop_toTopOf="parent" />

    </androidx.constraintlayout.widget.ConstraintLayout>

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/chatListRecyclerView"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:clipToPadding="false"
        android:overScrollMode="never"
        android:paddingTop="8dp"
        android:paddingBottom="104dp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/topBar" />

    <TextView
        android:id="@+id/emptyText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:gravity="center"
        android:maxWidth="260dp"
        android:text="@string/no_conversations"
        android:textColor="@color/text_secondary"
        android:textSize="15sp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/topBar" />

    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/newChatFab"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginEnd="22dp"
        android:layout_marginBottom="24dp"
        android:contentDescription="@string/new_chat"
        android:src="@drawable/ic_add"
        app:backgroundTint="@color/primary"
        app:elevation="8dp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:tint="@color/white" />

</androidx.constraintlayout.widget.ConstraintLayout>

```

## `app/src/main/res/layout/item_chat_list.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/chatItemRoot"
    android:layout_width="match_parent"
    android:layout_height="84dp"
    android:background="@color/surface"
    android:clickable="true"
    android:focusable="true"
    android:foreground="?attr/selectableItemBackground"
    android:paddingStart="16dp"
    android:paddingEnd="16dp">

    <FrameLayout
        android:id="@+id/avatarContainer"
        android:layout_width="56dp"
        android:layout_height="56dp"
        android:background="@drawable/avatar_circle"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent">

        <TextView
            android:id="@+id/avatarInitial"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:gravity="center"
            android:textColor="@color/primary"
            android:textSize="21sp"
            android:textStyle="bold" />
    </FrameLayout>

    <TextView
        android:id="@+id/nicknameText"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="14dp"
        android:layout_marginEnd="8dp"
        android:ellipsize="end"
        android:maxLines="1"
        android:textColor="@color/text_primary"
        android:textSize="16sp"
        android:textStyle="bold"
        app:layout_constraintBottom_toTopOf="@id/lastMessageText"
        app:layout_constraintEnd_toStartOf="@id/timeText"
        app:layout_constraintStart_toEndOf="@id/avatarContainer"
        app:layout_constraintTop_toTopOf="@id/avatarContainer"
        app:layout_constraintVertical_chainStyle="packed" />

    <TextView
        android:id="@+id/lastMessageText"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="14dp"
        android:layout_marginTop="5dp"
        android:layout_marginEnd="8dp"
        android:ellipsize="end"
        android:maxLines="1"
        android:textColor="@color/text_secondary"
        android:textSize="14sp"
        app:layout_constraintBottom_toBottomOf="@id/avatarContainer"
        app:layout_constraintEnd_toStartOf="@id/channelText"
        app:layout_constraintStart_toEndOf="@id/avatarContainer"
        app:layout_constraintTop_toBottomOf="@id/nicknameText" />

    <TextView
        android:id="@+id/timeText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textColor="@color/text_secondary"
        android:textSize="12sp"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintTop_toTopOf="@id/avatarContainer" />

    <TextView
        android:id="@+id/channelText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:background="@drawable/channel_badge_background"
        android:textColor="@color/primary"
        android:textSize="10sp"
        android:textStyle="bold"
        app:layout_constraintBottom_toBottomOf="@id/avatarContainer"
        app:layout_constraintEnd_toEndOf="parent" />

    <View
        android:layout_width="0dp"
        android:layout_height="1dp"
        android:background="@color/divider"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="@id/nicknameText" />

</androidx.constraintlayout.widget.ConstraintLayout>

```

## `app/src/main/res/layout/item_message.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/messageRow"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingTop="3dp"
    android:paddingBottom="3dp">

    <LinearLayout
        android:id="@+id/bubbleContainer"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="vertical">

        <TextView
            android:id="@+id/messageText"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:lineSpacingExtra="1dp"
            android:maxWidth="300dp"
            android:textSize="16sp" />

        <TextView
            android:id="@+id/messageTime"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="end"
            android:layout_marginTop="2dp"
            android:textSize="10sp" />

    </LinearLayout>

</LinearLayout>

```

## `app/src/main/res/values/colors.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="white">#FFFFFFFF</color>
    <color name="black">#FF111318</color>
    <color name="background">#FFF7F8FC</color>
    <color name="surface">#FFFFFFFF</color>
    <color name="primary">#FF635BFF</color>
    <color name="primary_dark">#FF4D45E6</color>
    <color name="primary_soft">#FFE9E7FF</color>
    <color name="text_primary">#FF15171C</color>
    <color name="text_secondary">#FF777B87</color>
    <color name="divider">#FFE8EAF0</color>
    <color name="received_bubble">#FFEAECF2</color>
    <color name="online">#FF35C46A</color>
    <color name="danger">#FFE34B5F</color>
    <color name="transparent">#00000000</color>
</resources>

```

## `app/src/main/res/values/strings.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Ultrasonic Messenger</string>
    <string name="search">Szukaj</string>
    <string name="new_chat">Nowa wiadomość</string>
    <string name="no_conversations">Brak rozmów. Naciśnij +, aby rozpocząć.</string>
    <string name="online">Online</string>
    <string name="listening">Nasłuchiwanie</string>
    <string name="microphone_off">Mikrofon wyłączony</string>
    <string name="transmitting">Nadawanie dźwiękiem…</string>
    <string name="message_hint">Napisz wiadomość</string>
    <string name="send">Wyślij</string>
    <string name="back">Wstecz</string>
    <string name="choose_channel">Kanał częstotliwości</string>
    <string name="nickname">Nick rozmówcy</string>
    <string name="create">Utwórz</string>
    <string name="cancel">Anuluj</string>
    <string name="permission_title">Dostęp do mikrofonu</string>
    <string name="permission_message">Aplikacja potrzebuje mikrofonu, aby odbierać wiadomości zakodowane w wysokich częstotliwościach.</string>
    <string name="permission_denied">Bez mikrofonu odbieranie wiadomości jest niemożliwe.</string>
    <string name="invalid_nickname">Wpisz nick rozmówcy.</string>
    <string name="message_too_long">Wiadomość jest za długa. Maksymalnie 240 bajtów UTF-8.</string>
    <string name="busy">Modem nadaje już inną wiadomość.</string>
    <string name="search_hint">Wpisz nick</string>
    <string name="channel_badge">CH %1$d</string>
    <string name="channel_status">Kanał %1$d • %2$s</string>

    <string-array name="channel_labels">
        <item>Kanał 1 • okolice 17 kHz</item>
        <item>Kanał 2 • okolice 18 kHz</item>
        <item>Kanał 3 • okolice 19 kHz</item>
    </string-array>
</resources>

```

## `app/src/main/res/values/themes.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.UltrasonicMessenger" parent="Theme.MaterialComponents.Light.NoActionBar">
        <item name="colorPrimary">@color/primary</item>
        <item name="colorPrimaryVariant">@color/primary_dark</item>
        <item name="colorSecondary">@color/primary</item>
        <item name="android:fontFamily">sans</item>
        <item name="fontFamily">sans</item>
        <item name="android:windowLightStatusBar">true</item>
        <item name="android:statusBarColor">@color/surface</item>
        <item name="android:navigationBarColor">@color/surface</item>
        <item name="android:windowBackground">@color/background</item>
        <item name="android:windowActionModeOverlay">true</item>
    </style>
</resources>

```

## `build.gradle`

```groovy
plugins {
    id 'com.android.application' version '8.13.2' apply false
}

```

## `gradle/wrapper/gradle-wrapper.properties`

```text
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.13-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists

```

## `gradle.properties`

```text
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true

```

## `settings.gradle`

```groovy
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = 'UltrasonicMessenger'
include ':app'

```
