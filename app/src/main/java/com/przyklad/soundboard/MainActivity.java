package com.przyklad.soundboard;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_MICROPHONE_CODE = 101;
    private EditText inputMessage;
    private TextView textReceived;
    private Button buttonSend;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        inputMessage = findViewById(R.id.input_message);
        textReceived = findViewById(R.id.text_received);
        buttonSend = findViewById(R.id.button_send);

        // Sprawdzamy uprawnienia do mikrofonu (potrzebne do późniejszego odbioru)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE_CODE);
        }

        buttonSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = inputMessage.getText().toString();
                if (!message.isEmpty()) {
                    sendUltrasonicMessage(message);
                }
            }
        });
    }

    // Metoda generująca fale dźwiękowe dla każdej litery w tle
    private void sendUltrasonicMessage(final String message) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < message.length(); i++) {
                    char character = message.charAt(i);
                    // Mapujemy znak ASCII na częstotliwość powyżej 18 kHz (np. litera 'A' [65] to 18000 + 65*20 = 19300 Hz)
                    int frequency = 18000 + ((int) character * 20); 
                    playTone(frequency, 300); // Graj dźwięk przez 300 ms
                    try {
                        Thread.sleep(100); // Przerwa między znakami
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    // Algorytm generujący czystą falę sinusoidalną o konkretnej częstotliwości
    private void playTone(double frequency, int durationMs) {
        int sampleRate = 44100;
        int numSamples = (durationMs * sampleRate) / 1000;
        double[] sample = new double[numSamples];
        byte[] generatedSnd = new byte[2 * numSamples];

        // Obliczanie wartości sinusoidy
        for (int i = 0; i < numSamples; ++i) {
            sample[i] = Math.sin(2 * Math.PI * i / (sampleRate / frequency));
        }

        // Pakowanie do formatu 16-bit PCM (dwubajtowego)
        int idx = 0;
        for (final double dVal : sample) {
            final short val = (short) ((dVal * 32767));
            generatedSnd[idx++] = (byte) (val & 0x00ff);
            generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);
        }

        // Odtwarzanie wygenerowanej paczki danych audio
        AudioTrack audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                generatedSnd.length,
                AudioTrack.MODE_STATIC
        );
        audioTrack.write(generatedSnd, 0, generatedSnd.length);
        audioTrack.play();
        
        try {
            Thread.sleep(durationMs);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        audioTrack.release();
    }
}