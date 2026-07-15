#!/usr/bin/env python3
"""
Local APK Bot

Dzialanie:
- obserwuje pliki projektu Android,
- po zmianie odpala build APK,
- kopiuje wynik do dist/BeatLy-debug.apk.

Wymaga lokalnie Android SDK + Gradle albo gradlew w projekcie.
Na GitHubie uzywaj .github/workflows/apk-bot.yml.
"""
from __future__ import annotations

import hashlib
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WATCH_DIRS = [ROOT / "app" / "src"]
WATCH_FILES = [
    ROOT / "build.gradle.kts",
    ROOT / "settings.gradle.kts",
    ROOT / "gradle.properties",
    ROOT / "app" / "build.gradle.kts",
]
OUTPUT_APK = ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
DIST_APK = ROOT / "dist" / "BeatLy-debug.apk"


def file_hash(path: Path) -> str:
    h = hashlib.sha256()
    h.update(str(path.relative_to(ROOT)).encode("utf-8", errors="ignore"))
    try:
        h.update(path.read_bytes())
    except FileNotFoundError:
        h.update(b"missing")
    return h.hexdigest()


def snapshot() -> str:
    hashes: list[str] = []
    for file in WATCH_FILES:
        hashes.append(file_hash(file))
    for directory in WATCH_DIRS:
        if directory.exists():
            for file in sorted(directory.rglob("*")):
                if file.is_file() and file.suffix.lower() in {".kt", ".xml", ".kts", ".properties"}:
                    hashes.append(file_hash(file))
    return hashlib.sha256("".join(hashes).encode()).hexdigest()


def gradle_command() -> list[str]:
    if os.name == "nt":
        wrapper = ROOT / "gradlew.bat"
        if wrapper.exists():
            return [str(wrapper)]
    else:
        wrapper = ROOT / "gradlew"
        if wrapper.exists():
            return [str(wrapper)]
    return ["gradle"]


def build() -> bool:
    print("\n[APK BOT] Buduje APK...", flush=True)
    command = gradle_command() + ["--no-daemon", "--stacktrace", "clean", ":app:assembleDebug"]
    result = subprocess.run(command, cwd=ROOT)
    if result.returncode != 0:
        print("[APK BOT] Build padl. Sprawdz blad powyzej.", flush=True)
        return False

    if not OUTPUT_APK.exists():
        print(f"[APK BOT] Nie znaleziono APK: {OUTPUT_APK}", flush=True)
        return False

    DIST_APK.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(OUTPUT_APK, DIST_APK)
    print(f"[APK BOT] Gotowe: {DIST_APK}", flush=True)
    return True


def main() -> int:
    print("[APK BOT] Start. Ctrl+C konczy prace.")
    print(f"[APK BOT] Projekt: {ROOT}")
    last = ""
    try:
        while True:
            current = snapshot()
            if current != last:
                last = current
                build()
            time.sleep(5)
    except KeyboardInterrupt:
        print("\n[APK BOT] Zatrzymany.")
        return 0


if __name__ == "__main__":
    sys.exit(main())
