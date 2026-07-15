# BeatLy

Nowoczesny lokalny odtwarzacz muzyki dla Androida napisany w Kotlinie, Jetpack Compose, Room i Media3.

## Co jest zaimplementowane

- Room: `Song`, `SongDao`, `AppDatabase`, `RoomSongRepository`.
- Offline-first: pobrane pliki są zapisywane w katalogu aplikacji `files/music`, a okładki w `files/artwork`.
- Downloader: legalne bezpośrednie linki audio `.mp3`, `.m4a`, `.ogg`, `.opus`, `.flac`, `.wav`.
- Metadata: `MediaMetadataRetriever` wyciąga tytuł, wykonawcę, czas trwania i embedded artwork.
- Media3: `PlaybackService : MediaSessionService`, `ExoPlayer`, `MediaSession`, metadata z tytułem, artystą, albumem, `artworkUri` i `artworkData`.
- UI: ciemny Spotify-like Compose, biblioteka, wyszukiwarka, dialog dodawania, mini-player, ekran odtwarzacza, slider, serduszko.

## Ważne o linkach z platform

YouTube, Spotify, TikTok i SoundCloud nie są tu obsługiwane jako downloader MP3, bo to wymaga obchodzenia platformowych zasad albo zewnętrznego konwertera. Architektura jest przygotowana pod legalny resolver: podmień implementację `LinkResolver`, gdy masz własne źródło audio z prawami do pobrania.

## Uruchomienie

1. Otwórz folder w Android Studio.
2. Upewnij się, że masz JDK 21 i Android SDK 36.
3. Zrób Gradle Sync.
4. Uruchom moduł `app`.

## Przykładowe linki testowe

Używaj bezpośrednich linków do plików audio, np. public domain albo własny plik z serwera:

```text
https://twoj-serwer.pl/audio/test.mp3
```

## Gdzie dodać własny resolver

Plik: `app/src/main/java/com/tc20/localmusic/download/DirectAudioLinkResolver.kt`

Możesz zrobić klasę np. `MyLegalResolver : LinkResolver`, która zwróci `ResolvedAudio(audioUrl = ..., suggestedTitle = ..., suggestedArtist = ..., artworkUrl = ...)`.

## APK Bot

Dodałem automatyczny build APK przez GitHub Actions.

- Workflow: `.github/workflows/apk-bot.yml`
- Ręczny opis: `BUILD_BOT.md`
- Lokalny watcher: `tools/apk_bot.py`
- Jednorazowy build: `tools/build-apk.ps1` albo `tools/build-apk.sh`

Po wrzuceniu projektu na GitHub APK będzie dostępne w **Actions → APK Bot - Build Android APK → Artifacts**.
