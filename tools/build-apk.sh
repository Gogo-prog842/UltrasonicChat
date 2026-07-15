#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -x "./gradlew" ]]; then
  GRADLE="./gradlew"
else
  GRADLE="gradle"
fi

$GRADLE --no-daemon --stacktrace clean :app:assembleDebug
mkdir -p dist
cp app/build/outputs/apk/debug/app-debug.apk dist/BeatLy-debug.apk
echo "Gotowe: dist/BeatLy-debug.apk"
