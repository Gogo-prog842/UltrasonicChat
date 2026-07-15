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
