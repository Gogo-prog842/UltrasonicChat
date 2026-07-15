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
