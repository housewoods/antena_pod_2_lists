# AntennaPod mit 2 Playlists

AntennaPod-Fork mit einer **zweiten Playlist („Playliste 2")**, die auch eine **Wiedergabequelle** ist.

## Features
- Originale Warteschlange (Playlist 1) bleibt unverändert
- „Playliste 2" im Navigationsmenü: Drag & Drop, Kontextmenü, Mehrfachauswahl, Leeren
- Episoden-Menü: „Zu Playliste 2 hinzufügen" / „Aus Playliste 2 entfernen"
- **Wiedergabe läuft automatisch in Playliste 2 weiter**, wenn die Warteschlange keinen Nachfolger hat (Media3- und Legacy-Player)

## APK
`AntennaPod-2-Playlists-v1.0.0.apk` (liegt direkt in diesem Repo)

Debug-Build (`de.danoeh.antennapod.debug`), per Sideloading installierbar (Android 6.0+, minSdk 23). Kann parallel zur offiziellen AntennaPod-App installiert werden (eigene App-ID mit `.debug`-Suffix, separate Daten).

## Build selbst kompilieren
```bash
git clone https://github.com/housewoods/antena_pod_2_lists.git
cd antena_pod_2_lists
./gradlew :app:assembleFreeDebug
# APK: app/build/outputs/apk/free/debug/app-free-debug.apk
```
Benötigt: JDK 21, Android SDK (Platform 36, Build-Tools 36.0.0).
