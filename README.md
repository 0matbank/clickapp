# Click Downloader

Click Downloader is a local-first Android video/audio downloader being implemented phase by phase from `Click_Downloader_Master_Plan_BN.txt`. It is intended for personal sideloading, has no advertising, analytics, mandatory account, cloud resolver, or DRM bypass.

## Current status

Phase 1 — App Foundation:

- Kotlin, Jetpack Compose and Material 3 application shell
- Home, Downloads, Library and Settings navigation
- English and Bangla resources
- System, light, dark and AMOLED themes
- Room schema for persistent jobs and recovery records
- DataStore-backed foundation settings
- MediaStore/Storage Access Framework boundary with persisted directory access
- A local `CREATED` job flow for exercising persistence and UI; no fake download or progress
- Debug and minified release build types
- Unit and instrumented-test foundations

Actual URL analysis, network transfers, sharesheet entry points, notifications, extractor runtimes, FFmpeg, browser, overlay, playback and conversion are intentionally not present before their master-plan phases.

## Prerequisites

- JDK 17
- Android SDK Platform 37.0
- Android SDK Build Tools 37.0.0
- Android Platform Tools

Set `ANDROID_HOME`/`ANDROID_SDK_ROOT`, or create an untracked `local.properties` containing `sdk.dir=...`.

## Build and test

On Windows PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot'
.\gradlew.bat test testDebugUnitTest lintDebug assembleDebug assembleRelease
```

On macOS/Linux:

```bash
./gradlew test testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Run connected UI tests only with an emulator or physical device attached:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

## Signing

No signing key belongs in this repository. Phase 10 will read release signing values from an external, ignored `keystore.properties` file or environment/CI secrets. Losing the private signing key prevents same-package updates, so encrypted offline backups are required before personal release.

## Storage and privacy

The app asks for a download directory only when the user selects **Choose folder**. Phase 1 declares no Internet, notification, overlay, accessibility, or broad storage permissions. Sensitive cookies and session handling are deferred to the dedicated browser/session phase and will not be stored in Room.

## Known limits

This foundation build does not download media. Entering a valid HTTP(S) URL records a local job in the `CREATED` state so persistence, navigation and empty/list states can be tested without pretending that analysis or downloading occurred.
