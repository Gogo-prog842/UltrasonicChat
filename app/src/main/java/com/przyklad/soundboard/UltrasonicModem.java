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
