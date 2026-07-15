# Ultrasonic Chat — kompletna struktura i zawartość plików

## Struktura projektu

```text
.gitignore
README.md
app/build.gradle
app/proguard-rules.pro
app/src/main/AndroidManifest.xml
app/src/main/java/com/przyklad/soundboard/ChatMessage.java
app/src/main/java/com/przyklad/soundboard/MainActivity.java
app/src/main/java/com/przyklad/soundboard/MessageAdapter.java
app/src/main/java/com/przyklad/soundboard/UltrasonicModem.java
app/src/main/res/drawable/ic_app.xml
app/src/main/res/drawable/ic_send.xml
app/src/main/res/drawable/input_container.xml
app/src/main/res/drawable/receive_bubble.xml
app/src/main/res/drawable/send_bubble.xml
app/src/main/res/drawable/send_button_background.xml
app/src/main/res/drawable/status_dot.xml
app/src/main/res/layout/activity_main.xml
app/src/main/res/layout/item_message.xml
app/src/main/res/values-night/themes.xml
app/src/main/res/values/colors.xml
app/src/main/res/values/strings.xml
app/src/main/res/values/themes.xml
build.gradle
gradle.properties
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
gradlew
gradlew.bat
settings.gradle
```

## Plik binarny Gradle Wrapper

`gradle/wrapper/gradle-wrapper.jar` jest dołączony do projektu i ZIP-a.
SHA-256: `423cb469ccc0ecc31f0e4e1c309976198ccb734cdcbb7029d4bda0f18f57e8d9`

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

rootProject.name = "Ultrasonic Chat"
include ':app'
```

## `build.gradle`

```groovy
plugins {
    id 'com.android.application' version '8.13.2' apply false
}
```

## `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
```

## `gradle/wrapper/gradle-wrapper.properties`

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.13-bin.zip
distributionSha256Sum=20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
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
        targetSdk 36
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
        // Gradle może działać na JDK 22, ale kod Androida jest kompilowany
        // do oficjalnie wspieranego poziomu bytecode Java 17.
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
        android:required="true" />

    <uses-feature
        android:name="android.hardware.audio.output"
        android:required="true" />

    <application
        android:allowBackup="true"
        android:icon="@drawable/ic_app"
        android:label="@string/app_name"
        android:roundIcon="@drawable/ic_app"
        android:supportsRtl="true"
        android:theme="@style/Theme.UltrasonicChat">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>

</manifest>
```

## `app/src/main/java/com/przyklad/soundboard/ChatMessage.java`

```java
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
```

## `app/src/main/java/com/przyklad/soundboard/MessageAdapter.java`

```java
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
```

## `app/src/main/java/com/przyklad/soundboard/UltrasonicModem.java`

```java
package com.przyklad.soundboard;

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
 * Prosty modem akustyczny pracujący w paśmie 18–20 kHz.
 *
 * Format ramki:
 *  - START x3
 *  - długość payloadu: 1 bajt zapisany jako dwa tony (nibble high/low)
 *  - tekst UTF-8, każdy bajt jako dwa tony
 *  - CRC-8 jako dwa tony
 *  - END x2
 *
 * 16 wartości nibble jest mapowanych na 18 200–19 700 Hz co 100 Hz.
 * Osobne częstotliwości 18 000 Hz i 19 900 Hz oznaczają START/END.
 */
public final class UltrasonicModem implements AutoCloseable {

    public interface Callback {
        void onReceiverStateChanged(boolean active);

        void onTransmissionStateChanged(boolean active);

        void onMessageReceived(@NonNull String message);

        void onError(@NonNull String message);
    }

    public static final int MAX_PAYLOAD_BYTES = 120;

    private static final int SAMPLE_RATE = 48_000;
    private static final int TONE_DURATION_MS = 300;
    private static final int GAP_DURATION_MS = 100;
    private static final int ANALYSIS_WINDOW_MS = 50;

    private static final int START_SYMBOL = 16;
    private static final int END_SYMBOL = 17;
    private static final int SYMBOL_NONE = -1;

    private static final double START_FREQUENCY = 18_000.0;
    private static final double DATA_BASE_FREQUENCY = 18_200.0;
    private static final double DATA_STEP_FREQUENCY = 100.0;
    private static final double END_FREQUENCY = 19_900.0;

    private static final int TONE_SAMPLES = SAMPLE_RATE * TONE_DURATION_MS / 1000;
    private static final int GAP_SAMPLES = SAMPLE_RATE * GAP_DURATION_MS / 1000;
    private static final int WINDOW_SAMPLES = SAMPLE_RATE * ANALYSIS_WINDOW_MS / 1000;

    private static final double MIN_DETECTED_AMPLITUDE = 90.0;
    private static final double MIN_DOMINANCE_RATIO = 1.65;
    private static final int REQUIRED_STABLE_WINDOWS = 3;

    private final Callback callback;
    private final ExecutorService transmitterExecutor =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "ultrasonic-tx"));
    private final ExecutorService receiverExecutor =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "ultrasonic-rx"));

    private final AtomicBoolean transmitting = new AtomicBoolean(false);
    private final AtomicBoolean receiverRequested = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicInteger receiverGeneration = new AtomicInteger(0);

    private final FrequencyDetector detector = new FrequencyDetector();
    private final FrameDecoder decoder = new FrameDecoder();
    private final short[][] cachedTones = new short[18][];
    private final short[] silence = new short[GAP_SAMPLES];

    private volatile AudioRecord audioRecord;
    private volatile String lastReceivedMessage = "";
    private volatile long lastReceivedAtMs = 0L;

    public UltrasonicModem(@NonNull Callback callback) {
        this.callback = callback;
        cacheToneSamples();
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

        transmitterExecutor.execute(() -> {
            callback.onTransmissionStateChanged(true);
            decoder.reset();

            try {
                transmitFrame(payload);
            } catch (Exception exception) {
                callback.onError("Błąd nadawania: " + safeMessage(exception));
            } finally {
                sleepQuietly(450);
                decoder.reset();
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
        if (!receiverRequested.getAndSet(false)) {
            return;
        }

        receiverGeneration.incrementAndGet();
        AudioRecord record = audioRecord;
        if (record != null) {
            try {
                record.stop();
            } catch (IllegalStateException ignored) {
                // Rekorder mógł już zostać zatrzymany przez pętlę odbiornika.
            }
        }
    }

    public boolean isTransmitting() {
        return transmitting.get();
    }

    private void transmitFrame(byte[] payload) {
        int minBuffer = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );

        if (minBuffer <= 0) {
            throw new IllegalStateException("AudioTrack nie zwrócił poprawnego bufora");
        }

        int bufferSize = Math.max(minBuffer, TONE_SAMPLES * 2);

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
                .setBufferSizeInBytes(bufferSize)
                .build();

        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            track.release();
            throw new IllegalStateException("Nie udało się uruchomić AudioTrack");
        }

        try {
            track.setVolume(0.85f);
            track.play();

            writeSymbol(track, START_SYMBOL);
            writeSymbol(track, START_SYMBOL);
            writeSymbol(track, START_SYMBOL);

            writeByte(track, payload.length);
            for (byte value : payload) {
                writeByte(track, value & 0xFF);
            }

            writeByte(track, crc8(payload));
            writeSymbol(track, END_SYMBOL);
            writeSymbol(track, END_SYMBOL);
        } finally {
            try {
                track.stop();
            } catch (IllegalStateException ignored) {
                // Ignorujemy, bo zasób i tak jest zwalniany.
            }
            track.flush();
            track.release();
        }
    }

    private void writeByte(AudioTrack track, int unsignedByte) {
        writeSymbol(track, (unsignedByte >>> 4) & 0x0F);
        writeSymbol(track, unsignedByte & 0x0F);
    }

    private void writeSymbol(AudioTrack track, int symbol) {
        writeFully(track, cachedTones[symbol]);
        writeFully(track, silence);
    }

    private static void writeFully(AudioTrack track, short[] samples) {
        int offset = 0;
        while (offset < samples.length) {
            int written = track.write(
                    samples,
                    offset,
                    samples.length - offset,
                    AudioTrack.WRITE_BLOCKING
            );

            if (written < 0) {
                throw new IllegalStateException("AudioTrack.write(): " + written);
            }
            offset += written;
        }
    }

    private void receiverLoop(int generation) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        AudioRecord record = null;

        if (!receiverRequested.get()
                || generation != receiverGeneration.get()
                || closed.get()) {
            return;
        }

        try {
            record = createAudioRecord();
            audioRecord = record;
            record.startRecording();
            callback.onReceiverStateChanged(true);

            short[] window = new short[WINDOW_SAMPLES];
            SymbolDebouncer debouncer = new SymbolDebouncer(decoder);

            while (receiverRequested.get()
                    && generation == receiverGeneration.get()
                    && !closed.get()) {
                int read;
                try {
                    read = record.read(window, 0, window.length, AudioRecord.READ_BLOCKING);
                } catch (IllegalStateException exception) {
                    if (receiverRequested.get()
                            && generation == receiverGeneration.get()) {
                        throw exception;
                    }
                    break;
                }

                if (read <= 0) {
                    if (receiverRequested.get()
                            && generation == receiverGeneration.get()) {
                        callback.onError("AudioRecord.read(): " + read);
                    }
                    continue;
                }

                if (transmitting.get()) {
                    debouncer.reset();
                    decoder.reset();
                    continue;
                }

                int symbol = detector.detect(window, read);
                debouncer.accept(symbol);
            }
        } catch (SecurityException exception) {
            if (generation == receiverGeneration.get() && !closed.get()) {
                callback.onError("Brak uprawnienia do mikrofonu");
            }
        } catch (Exception exception) {
            if (receiverRequested.get()
                    && generation == receiverGeneration.get()
                    && !closed.get()) {
                callback.onError("Błąd odbiornika: " + safeMessage(exception));
            }
        } finally {
            if (generation == receiverGeneration.get()) {
                receiverRequested.set(false);
            }
            audioRecord = null;

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
                callback.onReceiverStateChanged(false);
            }
        }
    }

    private static AudioRecord createAudioRecord() {
        int minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );

        if (minBuffer <= 0) {
            throw new IllegalStateException("AudioRecord nie zwrócił poprawnego bufora");
        }

        int bufferBytes = Math.max(minBuffer, WINDOW_SAMPLES * 2 * 4);

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

    private void cacheToneSamples() {
        for (int symbol = 0; symbol < cachedTones.length; symbol++) {
            cachedTones[symbol] = generateTone(frequencyForSymbol(symbol));
        }
    }

    private static short[] generateTone(double frequency) {
        short[] samples = new short[TONE_SAMPLES];
        int fadeSamples = SAMPLE_RATE / 100; // 10 ms
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

    private static double frequencyForSymbol(int symbol) {
        if (symbol >= 0 && symbol <= 15) {
            return DATA_BASE_FREQUENCY + symbol * DATA_STEP_FREQUENCY;
        }
        if (symbol == START_SYMBOL) {
            return START_FREQUENCY;
        }
        if (symbol == END_SYMBOL) {
            return END_FREQUENCY;
        }
        throw new IllegalArgumentException("Nieznany symbol: " + symbol);
    }

    private static int crc8(byte[] data) {
        int crc = 0x00;
        for (byte value : data) {
            crc ^= value & 0xFF;
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 0x80) != 0) {
                    crc = ((crc << 1) ^ 0x07) & 0xFF;
                } else {
                    crc = (crc << 1) & 0xFF;
                }
            }
        }
        return crc;
    }

    private void deliverDecodedMessage(String message) {
        long now = System.currentTimeMillis();
        if (message.equals(lastReceivedMessage) && now - lastReceivedAtMs < 2_000L) {
            return;
        }

        lastReceivedMessage = message;
        lastReceivedAtMs = now;
        callback.onMessageReceived(message);
    }

    private final class FrameDecoder {
        private static final int WAITING_FOR_START = 0;
        private static final int READING_DATA = 1;
        private static final int WAITING_FOR_END = 2;

        private int state = WAITING_FOR_START;
        private int startCount = 0;
        private int endCount = 0;
        private int pendingHighNibble = -1;
        private int expectedPayloadLength = -1;
        private final ByteArrayOutputStream frameBytes = new ByteArrayOutputStream();

        synchronized void acceptSymbol(int symbol) {
            switch (state) {
                case WAITING_FOR_START -> acceptStart(symbol);
                case READING_DATA -> acceptData(symbol);
                case WAITING_FOR_END -> acceptEnd(symbol);
                default -> reset();
            }
        }

        private void acceptStart(int symbol) {
            if (symbol == START_SYMBOL) {
                startCount++;
                if (startCount >= 3) {
                    state = READING_DATA;
                    startCount = 0;
                    pendingHighNibble = -1;
                    expectedPayloadLength = -1;
                    frameBytes.reset();
                }
            } else {
                startCount = 0;
            }
        }

        private void acceptData(int symbol) {
            if (symbol < 0 || symbol > 15) {
                if (symbol == START_SYMBOL) {
                    reset();
                    startCount = 1;
                } else {
                    reset();
                }
                return;
            }

            if (pendingHighNibble < 0) {
                pendingHighNibble = symbol;
                return;
            }

            int value = (pendingHighNibble << 4) | symbol;
            pendingHighNibble = -1;

            if (expectedPayloadLength < 0) {
                if (value <= 0 || value > MAX_PAYLOAD_BYTES) {
                    reset();
                    return;
                }
                expectedPayloadLength = value;
                return;
            }

            frameBytes.write(value);
            if (frameBytes.size() == expectedPayloadLength + 1) {
                state = WAITING_FOR_END;
                endCount = 0;
            } else if (frameBytes.size() > expectedPayloadLength + 1) {
                reset();
            }
        }

        private void acceptEnd(int symbol) {
            if (symbol != END_SYMBOL) {
                reset();
                return;
            }

            endCount++;
            if (endCount < 2) {
                return;
            }

            byte[] bytes = frameBytes.toByteArray();
            if (bytes.length != expectedPayloadLength + 1) {
                reset();
                return;
            }

            byte[] payload = Arrays.copyOf(bytes, expectedPayloadLength);
            int receivedCrc = bytes[bytes.length - 1] & 0xFF;
            int calculatedCrc = crc8(payload);

            if (receivedCrc == calculatedCrc) {
                try {
                    String message = StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(payload))
                            .toString();
                    deliverDecodedMessage(message);
                } catch (CharacterCodingException ignored) {
                    callback.onError("Odebrano ramkę z niepoprawnym UTF-8");
                }
            }

            reset();
        }

        synchronized void reset() {
            state = WAITING_FOR_START;
            startCount = 0;
            endCount = 0;
            pendingHighNibble = -1;
            expectedPayloadLength = -1;
            frameBytes.reset();
        }
    }

    private static final class SymbolDebouncer {
        private final FrameDecoder decoder;
        private int candidate = SYMBOL_NONE;
        private int stableWindows = 0;
        private boolean emitted = false;

        SymbolDebouncer(FrameDecoder decoder) {
            this.decoder = decoder;
        }

        void accept(int symbol) {
            if (symbol == SYMBOL_NONE) {
                reset();
                return;
            }

            if (symbol != candidate) {
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

    private static final class FrequencyDetector {
        private final double[] frequencies = new double[18];

        FrequencyDetector() {
            for (int symbol = 0; symbol <= 15; symbol++) {
                frequencies[symbol] = frequencyForSymbol(symbol);
            }
            frequencies[START_SYMBOL] = START_FREQUENCY;
            frequencies[END_SYMBOL] = END_FREQUENCY;
        }

        int detect(short[] samples, int length) {
            double bestAmplitude = 0.0;
            double secondAmplitude = 0.0;
            int bestSymbol = SYMBOL_NONE;

            for (int symbol = 0; symbol < frequencies.length; symbol++) {
                double amplitude = goertzelAmplitude(samples, length, frequencies[symbol]);
                if (amplitude > bestAmplitude) {
                    secondAmplitude = bestAmplitude;
                    bestAmplitude = amplitude;
                    bestSymbol = symbol;
                } else if (amplitude > secondAmplitude) {
                    secondAmplitude = amplitude;
                }
            }

            double dominance = secondAmplitude <= 0.0001
                    ? Double.POSITIVE_INFINITY
                    : bestAmplitude / secondAmplitude;

            if (bestAmplitude < MIN_DETECTED_AMPLITUDE
                    || dominance < MIN_DOMINANCE_RATIO) {
                return SYMBOL_NONE;
            }

            return bestSymbol;
        }

        private static double goertzelAmplitude(
                short[] samples,
                int length,
                double frequency
        ) {
            double omega = 2.0 * Math.PI * frequency / SAMPLE_RATE;
            double coefficient = 2.0 * Math.cos(omega);
            double previous = 0.0;
            double previous2 = 0.0;

            for (int i = 0; i < length; i++) {
                double current = samples[i] + coefficient * previous - previous2;
                previous2 = previous;
                previous = current;
            }

            double power = previous2 * previous2
                    + previous * previous
                    - coefficient * previous * previous2;

            return 2.0 * Math.sqrt(Math.max(power, 0.0)) / length;
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

## `app/src/main/java/com/przyklad/soundboard/MainActivity.java`

```java
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
```

## `app/src/main/res/layout/activity_main.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/rootContainer"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background">

    <LinearLayout
        android:id="@+id/headerContainer"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingStart="20dp"
        android:paddingTop="18dp"
        android:paddingEnd="20dp"
        android:paddingBottom="14dp"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent">

        <ImageView
            android:layout_width="44dp"
            android:layout_height="44dp"
            android:contentDescription="@string/app_name"
            android:src="@drawable/ic_app" />

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="12dp"
            android:layout_weight="1"
            android:orientation="vertical">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/app_name"
                android:textColor="@color/text_primary"
                android:textSize="20sp"
                android:textStyle="bold" />

            <LinearLayout
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="3dp"
                android:gravity="center_vertical"
                android:orientation="horizontal">

                <View
                    android:id="@+id/statusDot"
                    android:layout_width="8dp"
                    android:layout_height="8dp"
                    android:background="@drawable/status_dot" />

                <TextView
                    android:id="@+id/statusText"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="7dp"
                    android:text="@string/subtitle_waiting"
                    android:textColor="@color/text_secondary"
                    android:textSize="13sp" />
            </LinearLayout>
        </LinearLayout>
    </LinearLayout>

    <TextView
        android:id="@+id/emptyText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:gravity="center"
        android:text="@string/empty_chat"
        android:textColor="@color/text_secondary"
        android:textSize="15sp"
        app:layout_constraintBottom_toBottomOf="@id/messagesList"
        app:layout_constraintEnd_toEndOf="@id/messagesList"
        app:layout_constraintStart_toStartOf="@id/messagesList"
        app:layout_constraintTop_toTopOf="@id/messagesList" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/messagesList"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:clipToPadding="false"
        android:overScrollMode="never"
        android:paddingTop="8dp"
        android:paddingBottom="12dp"
        app:layout_constraintBottom_toTopOf="@id/composerContainer"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/headerContainer" />

    <LinearLayout
        android:id="@+id/composerContainer"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="12dp"
        android:layout_marginEnd="12dp"
        android:layout_marginBottom="12dp"
        android:background="@drawable/input_container"
        android:gravity="bottom|center_vertical"
        android:orientation="horizontal"
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
            android:layout_weight="1"
            android:background="@null"
            android:hint="@string/message_hint"
            android:inputType="textCapSentences|textMultiLine"
            android:maxLines="4"
            android:minHeight="48dp"
            android:paddingTop="12dp"
            android:paddingEnd="10dp"
            android:paddingBottom="12dp"
            android:textColor="@color/text_primary"
            android:textColorHint="@color/text_secondary"
            android:textSize="16sp" />

        <ImageButton
            android:id="@+id/sendButton"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:background="@drawable/send_button_background"
            android:contentDescription="@string/send"
            android:padding="12dp"
            android:src="@drawable/ic_send" />
    </LinearLayout>

</androidx.constraintlayout.widget.ConstraintLayout>
```

## `app/src/main/res/layout/item_message.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:paddingStart="12dp"
    android:paddingTop="4dp"
    android:paddingEnd="12dp"
    android:paddingBottom="4dp">

    <TextView
        android:id="@+id/messageText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:maxWidth="320dp"
        android:paddingStart="14dp"
        android:paddingTop="10dp"
        android:paddingEnd="14dp"
        android:paddingBottom="10dp"
        android:textIsSelectable="true"
        android:textSize="16sp" />

</FrameLayout>
```

## `app/src/main/res/drawable/send_bubble.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/primary" />
    <corners
        android:topLeftRadius="20dp"
        android:topRightRadius="20dp"
        android:bottomLeftRadius="20dp"
        android:bottomRightRadius="6dp" />
</shape>
```

## `app/src/main/res/drawable/receive_bubble.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/surface_soft" />
    <stroke
        android:width="1dp"
        android:color="@color/surface_border" />
    <corners
        android:topLeftRadius="20dp"
        android:topRightRadius="20dp"
        android:bottomLeftRadius="6dp"
        android:bottomRightRadius="20dp" />
</shape>
```

## `app/src/main/res/drawable/input_container.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/surface" />
    <stroke
        android:width="1dp"
        android:color="@color/surface_border" />
    <corners android:radius="28dp" />
</shape>
```

## `app/src/main/res/drawable/send_button_background.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_pressed="true">
        <shape android:shape="oval">
            <solid android:color="@color/primary_pressed" />
        </shape>
    </item>
    <item>
        <shape android:shape="oval">
            <solid android:color="@color/primary" />
        </shape>
    </item>
</selector>
```

## `app/src/main/res/drawable/status_dot.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="@color/status_green" />
</shape>
```

## `app/src/main/res/drawable/ic_send.xml`

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M2.01,21L23,12 2.01,3 2,10l15,2 -15,2z" />
</vector>
```

## `app/src/main/res/drawable/ic_app.xml`

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#746BFF"
        android:pathData="M54,4A50,50 0,1 0,54 104A50,50 0,1 0,54 4" />
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M24,58h8v-8h-8zM38,66h8V42h-8zM52,76h8V32h-8zM66,66h8V42h-8zM80,58h8v-8h-8z" />
</vector>
```

## `app/src/main/res/values/colors.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="background">#121212</color>
    <color name="surface">#1A1B1F</color>
    <color name="surface_soft">#23252B</color>
    <color name="surface_border">#30323A</color>
    <color name="primary">#746BFF</color>
    <color name="primary_pressed">#6258EA</color>
    <color name="text_primary">#F7F7FA</color>
    <color name="text_secondary">#A8AAB3</color>
    <color name="received_text">#F1F1F4</color>
    <color name="status_green">#5DD39E</color>
    <color name="status_red">#FF6B6B</color>
    <color name="status_yellow">#F6C85F</color>
    <color name="transparent">#00000000</color>
</resources>
```

## `app/src/main/res/values/strings.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Ultrasonic Chat</string>
    <string name="subtitle_waiting">Nasłuchiwanie • 18–20 kHz</string>
    <string name="message_hint">Napisz wiadomość…</string>
    <string name="send">Wyślij</string>
    <string name="empty_chat">Brak wiadomości\nWyślij tekst do drugiego telefonu.</string>
    <string name="microphone_permission_title">Dostęp do mikrofonu</string>
    <string name="microphone_permission_message">Mikrofon jest potrzebny do odbierania wiadomości zakodowanych w wysokich częstotliwościach.</string>
</resources>
```

## `app/src/main/res/values/themes.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.UltrasonicChat" parent="Theme.Material3.Dark.NoActionBar">
        <item name="android:fontFamily">sans</item>
        <item name="android:colorAccent">@color/primary</item>
        <item name="android:windowBackground">@color/background</item>
        <item name="android:statusBarColor">@color/background</item>
        <item name="android:navigationBarColor">@color/background</item>
        <item name="android:windowLightStatusBar">false</item>
        <item name="android:windowLightNavigationBar">false</item>
    </style>
</resources>
```

## `app/src/main/res/values-night/themes.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.UltrasonicChat" parent="Theme.Material3.Dark.NoActionBar">
        <item name="android:fontFamily">sans</item>
        <item name="android:colorAccent">@color/primary</item>
        <item name="android:windowBackground">@color/background</item>
        <item name="android:statusBarColor">@color/background</item>
        <item name="android:navigationBarColor">@color/background</item>
        <item name="android:windowLightStatusBar">false</item>
        <item name="android:windowLightNavigationBar">false</item>
    </style>
</resources>
```

## `README.md`

```markdown
# Ultrasonic Chat

Aplikacja Android w Javie przesyłająca krótkie wiadomości tekstowe przez wysokie częstotliwości dźwiękowe 18–20 kHz.

## Uruchomienie

1. Otwórz katalog projektu w Android Studio.
2. Ustaw Gradle JDK na JDK 22 albo wbudowany JBR zgodny z projektem.
3. Zainstaluj Android SDK 36.
4. Wykonaj synchronizację Gradle albo uruchom `gradlew.bat assembleDebug` w Windows.
5. Uruchom aplikację na dwóch fizycznych telefonach.
6. Zezwól na mikrofon i ustaw głośność multimediów mniej więcej na 70–90%.

## Protokół

- próbkowanie: 48 kHz, PCM 16-bit mono,
- ton: 300 ms,
- przerwa: 100 ms,
- START: 18 000 Hz, trzy powtórzenia,
- dane: 16 symboli 18 200–19 700 Hz, krok 100 Hz,
- END: 19 900 Hz, dwa powtórzenia,
- tekst: UTF-8,
- kontrola błędów: CRC-8.

Każdy bajt jest dzielony na dwa półbajty. Jest to stabilniejsze niż bezpośrednie `18000 + char * 20`, które dla części ASCII przekracza 20 kHz, a dla UTF-8 wymagałoby jeszcze większego pasma.

## Ważne ograniczenia

Nie każdy telefon poprawnie emituje lub rejestruje 18–20 kHz. Filtry mikrofonu, głośnika, etui, odległość i hałas mogą ograniczać skuteczność. Najlepsze wyniki daje odległość 10–80 cm i ciche pomieszczenie.

Odbiornik działa w osobnym wątku, kiedy ekran aplikacji jest aktywny. Dalsze słuchanie po przejściu aplikacji do tła wymaga foreground service z widocznym powiadomieniem i dodatkowymi uprawnieniami systemowymi.
```

## `.gitignore`

```gitignore
*.iml
.gradle
/local.properties
/.idea
.DS_Store
/build
/captures
.externalNativeBuild
.cxx
app/build
```

## `gradlew`

```text
#!/bin/sh

#
# Copyright © 2015 the original authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# SPDX-License-Identifier: Apache-2.0
#

##############################################################################
#
#   Gradle start up script for POSIX generated by Gradle.
#
#   Important for running:
#
#   (1) You need a POSIX-compliant shell to run this script. If your /bin/sh is
#       noncompliant, but you have some other compliant shell such as ksh or
#       bash, then to run this script, type that shell name before the whole
#       command line, like:
#
#           ksh Gradle
#
#       Busybox and similar reduced shells will NOT work, because this script
#       requires all of these POSIX shell features:
#         * functions;
#         * expansions «$var», «${var}», «${var:-default}», «${var+SET}»,
#           «${var#prefix}», «${var%suffix}», and «$( cmd )»;
#         * compound commands having a testable exit status, especially «case»;
#         * various built-in commands including «command», «set», and «ulimit».
#
#   Important for patching:
#
#   (2) This script targets any POSIX shell, so it avoids extensions provided
#       by Bash, Ksh, etc; in particular arrays are avoided.
#
#       The "traditional" practice of packing multiple parameters into a
#       space-separated string is a well documented source of bugs and security
#       problems, so this is (mostly) avoided, by progressively accumulating
#       options in "$@", and eventually passing that to Java.
#
#       Where the inherited environment variables (DEFAULT_JVM_OPTS, JAVA_OPTS,
#       and GRADLE_OPTS) rely on word-splitting, this is performed explicitly;
#       see the in-line comments for details.
#
#       There are tweaks for specific operating systems such as AIX, CygWin,
#       Darwin, MinGW, and NonStop.
#
#   (3) This script is generated from the Groovy template
#       https://github.com/gradle/gradle/blob/HEAD/platforms/jvm/plugins-application/src/main/resources/org/gradle/api/internal/plugins/unixStartScript.txt
#       within the Gradle project.
#
#       You can find Gradle at https://github.com/gradle/gradle/.
#
##############################################################################

# Attempt to set APP_HOME

# Resolve links: $0 may be a link
app_path=$0

# Need this for daisy-chained symlinks.
while
    APP_HOME=${app_path%"${app_path##*/}"}  # leaves a trailing /; empty if no leading path
    [ -h "$app_path" ]
do
    ls=$( ls -ld "$app_path" )
    link=${ls#*' -> '}
    case $link in             #(
      /*)   app_path=$link ;; #(
      *)    app_path=$APP_HOME$link ;;
    esac
done

# This is normally unused
# shellcheck disable=SC2034
APP_BASE_NAME=${0##*/}
# Discard cd standard output in case $CDPATH is set (https://github.com/gradle/gradle/issues/25036)
APP_HOME=$( cd -P "${APP_HOME:-./}" > /dev/null && printf '%s\n' "$PWD" ) || exit

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD=maximum

warn () {
    echo "$*"
} >&2

die () {
    echo
    echo "$*"
    echo
    exit 1
} >&2

# OS specific support (must be 'true' or 'false').
cygwin=false
msys=false
darwin=false
nonstop=false
case "$( uname )" in                #(
  CYGWIN* )         cygwin=true  ;; #(
  Darwin* )         darwin=true  ;; #(
  MSYS* | MINGW* )  msys=true    ;; #(
  NONSTOP* )        nonstop=true ;;
esac



# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses strange locations for the executables
        JAVACMD=$JAVA_HOME/jre/sh/java
    else
        JAVACMD=$JAVA_HOME/bin/java
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD=java
    if ! command -v java >/dev/null 2>&1
    then
        die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
fi

# Increase the maximum file descriptors if we can.
if ! "$cygwin" && ! "$darwin" && ! "$nonstop" ; then
    case $MAX_FD in #(
      max*)
        # In POSIX sh, ulimit -H is undefined. That's why the result is checked to see if it worked.
        # shellcheck disable=SC2039,SC3045
        MAX_FD=$( ulimit -H -n ) ||
            warn "Could not query maximum file descriptor limit"
    esac
    case $MAX_FD in  #(
      '' | soft) :;; #(
      *)
        # In POSIX sh, ulimit -n is undefined. That's why the result is checked to see if it worked.
        # shellcheck disable=SC2039,SC3045
        ulimit -n "$MAX_FD" ||
            warn "Could not set maximum file descriptor limit to $MAX_FD"
    esac
fi

# Collect all arguments for the java command, stacking in reverse order:
#   * args from the command line
#   * the main class name
#   * -classpath
#   * -D...appname settings
#   * --module-path (only if needed)
#   * DEFAULT_JVM_OPTS, JAVA_OPTS, and GRADLE_OPTS environment variables.

# For Cygwin or MSYS, switch paths to Windows format before running java
if "$cygwin" || "$msys" ; then
    APP_HOME=$( cygpath --path --mixed "$APP_HOME" )

    JAVACMD=$( cygpath --unix "$JAVACMD" )

    # Now convert the arguments - kludge to limit ourselves to /bin/sh
    for arg do
        if
            case $arg in                                #(
              -*)   false ;;                            # don't mess with options #(
              /?*)  t=${arg#/} t=/${t%%/*}              # looks like a POSIX filepath
                    [ -e "$t" ] ;;                      #(
              *)    false ;;
            esac
        then
            arg=$( cygpath --path --ignore --mixed "$arg" )
        fi
        # Roll the args list around exactly as many times as the number of
        # args, so each arg winds up back in the position where it started, but
        # possibly modified.
        #
        # NB: a `for` loop captures its iteration list before it begins, so
        # changing the positional parameters here affects neither the number of
        # iterations, nor the values presented in `arg`.
        shift                   # remove old arg
        set -- "$@" "$arg"      # push replacement arg
    done
fi


# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Collect all arguments for the java command:
#   * DEFAULT_JVM_OPTS, JAVA_OPTS, and optsEnvironmentVar are not allowed to contain shell fragments,
#     and any embedded shellness will be escaped.
#   * For example: A user cannot expect ${Hostname} to be expanded, as it is an environment variable and will be
#     treated as '${Hostname}' itself on the command line.

set -- \
        "-Dorg.gradle.appname=$APP_BASE_NAME" \
        -jar "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
        "$@"

# Stop when "xargs" is not available.
if ! command -v xargs >/dev/null 2>&1
then
    die "xargs is not available"
fi

# Use "xargs" to parse quoted args.
#
# With -n1 it outputs one arg per line, with the quotes and backslashes removed.
#
# In Bash we could simply go:
#
#   readarray ARGS < <( xargs -n1 <<<"$var" ) &&
#   set -- "${ARGS[@]}" "$@"
#
# but POSIX shell has neither arrays nor command substitution, so instead we
# post-process each arg (as a line of input to sed) to backslash-escape any
# character that might be a shell metacharacter, then use eval to reverse
# that process (while maintaining the separation between arguments), and wrap
# the whole thing up as a single "set" statement.
#
# This will of course break if any of these variables contains a newline or
# an unmatched quote.
#

eval "set -- $(
        printf '%s\n' "$DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS" |
        xargs -n1 |
        sed ' s~[^-[:alnum:]+,./:=@_]~\\&~g; ' |
        tr '\n' ' '
    )" '"$@"'

exec "$JAVACMD" "$@"
```

## `gradlew.bat`

```bat
@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar


@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%GRADLE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
```
