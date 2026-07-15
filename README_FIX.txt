NAPRAWA BUILD ANDROID

Skopiuj folder app z tej paczki do katalogu głównego repozytorium UltrasonicChat i zaakceptuj zastąpienie plików.

Zastępowane pliki:
- app/src/main/res/values/colors.xml
- app/src/main/res/values/strings.xml

Następnie uruchom:
  git add app/src/main/res/values/colors.xml app/src/main/res/values/strings.xml
  git commit -m "Fix missing Android resources"
  git push origin main

Lokalny test:
  .\gradlew.bat clean assembleDebug
