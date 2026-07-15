# APK Bot

Ten projekt ma dwa tryby automatycznego budowania APK.

## 1. GitHub Actions, czyli najwygodniejszy bot

Plik:

```text
.github/workflows/apk-bot.yml
```

Bot odpala się automatycznie po `push` na branch `main` albo `master`, a także ręcznie z zakładki **Actions** przez **Run workflow**.

Po udanym buildzie pobierasz APK tak:

1. Wejdź w repozytorium na GitHubie.
2. Kliknij **Actions**.
3. Wejdź w najnowszy workflow **APK Bot - Build Android APK**.
4. Na dole w **Artifacts** pobierz **BeatLy-debug-apk**.
5. W środku będzie `BeatLy-debug.apk`.

Workflow używa:

- JDK 21,
- Android SDK platform `android-36`,
- Build Tools `36.0.0`,
- Gradle `9.4.1`, bo Android Gradle Plugin 9.2 wymaga minimum Gradle 9.4.1.

## 2. Lokalny bot na PC

Uruchom z katalogu projektu:

### Windows PowerShell

```powershell
python .\tools\apk_bot.py
```

### Linux / macOS / Git Bash

```bash
python3 ./tools/apk_bot.py
```

Bot obserwuje pliki aplikacji. Gdy coś zmienisz, sam odpala:

```bash
gradle --no-daemon --stacktrace clean :app:assembleDebug
```

Wynik kopiuje do:

```text
dist/BeatLy-debug.apk
```

## Jednorazowy build lokalny

Windows:

```powershell
.\tools\build-apk.ps1
```

Linux / Git Bash:

```bash
./tools/build-apk.sh
```

## Ważne

Lokalny build wymaga Android SDK i Gradle/Gradle Wrappera. Na GitHub Actions wszystko instaluje się automatycznie.
