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
