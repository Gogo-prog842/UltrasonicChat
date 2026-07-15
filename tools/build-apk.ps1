$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

if (Test-Path ".\gradlew.bat") {
    $gradle = ".\gradlew.bat"
} else {
    $gradle = "gradle"
}

& $gradle --no-daemon --stacktrace clean :app:assembleDebug
New-Item -ItemType Directory -Force -Path "dist" | Out-Null
Copy-Item "app\build\outputs\apk\debug\app-debug.apk" "dist\BeatLy-debug.apk" -Force
Write-Host "Gotowe: dist\BeatLy-debug.apk"
