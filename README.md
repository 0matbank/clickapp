# Click Downloader

Click Downloader is a local-first Android video/audio downloader being implemented phase by phase from `Click_Downloader_Master_Plan_BN.txt`. It is intended for personal sideloading, has no advertising, analytics, mandatory account, cloud resolver, or DRM bypass.

## Current status

Phase 2 — Direct Download Pipeline is complete:

- Real HTTP/HTTPS audio and video probing and byte transfer
- Paste, Android Share and Open-With entry points, including explicit multi-URL choice
- Durable Room-backed queue with pause, resume, retry, cancel and process-restart recovery
- HTTP Range/If-Range continuation with preserved `.part` files and corruption-safe restart when Range is ignored
- Foreground-service progress notifications and notification actions
- Verified MediaStore finalization on Android 10+ and persisted SAF-folder finalization on Android 8+
- Bounded exponential retry with jitter and explicit auth, expired-link, storage and verification failures
- API 37 instrumentation coverage for the real direct-download, share and recovery paths

Phase 3 — Extractor and Source Format Selection is complete:

- Lazy, on-demand embedded yt-dlp/Python extractor (not initialized at app startup)
- Normalized title, creator, duration, live status, thumbnails, subtitles and engine version
- Complete unfiltered source-format list with resolution, FPS, codecs, bitrates, HDR/dynamic range, bit depth, container, language, size and protocol
- Explicit audio-only choices and explicit companion-audio selection for video-only sources
- Device compatibility/remux/conversion/DRM badges and visible recommendations without removing alternatives
- Exact selected format ID (or explicit `video+audio` IDs), source URLs and required request headers persisted before queueing
- DRM formats shown but disabled; no DRM bypass and no silent quality or format substitution

Phase 4 — Adaptive Media Pipeline is complete:

- Exact yt-dlp format-spec execution for progressive, HLS and DASH sources
- Resumable `.part`/fragment transfers with durable per-fragment Room checkpoints
- Separate video/audio retrieval and bundled FFmpeg stream-copy merge/remux
- Metadata, thumbnail and available subtitle embedding without video re-encoding
- Re-extraction from the original page on retry while retaining the exact selected format IDs
- Android `MediaExtractor` verification of required audio/video tracks and selected dimensions before finalization
- API 37 generated 1080p and losslessly merged 4K-with-audio verification tests

Phase 5 — Playlist, Batch and Live is complete:

- Playlist entry discovery with explicit per-item selection
- Visible batch rules for best original video plus audio, best muxed source, or audio-only
- Pre-queue storage estimate and confirmation; unknown source sizes remain clearly marked
- Every selected item is analyzed and queued independently so one extraction or queue failure does not cancel the rest
- Persistent playlist/item-to-job relationships in Room
- Non-DRM live capture over supported extractor streams with explicit **Stop & Save**
- Interrupted MPEG-TS live capture finalized by FFmpeg stream-copy and verified for both video and audio

Phase 6 — Built-in Browser and Session is complete:

- Optional Browser/Incognito entry points; WebView is created only inside `BrowserActivity`
- Address/search, back, forward, refresh, home, desktop/mobile user-agent, share and copy controls
- Downloadable media request detection for common direct, HLS and DASH media
- Login session support with per-site/all-session clear controls and incognito cleanup
- AES-GCM Android Keystore session vault under no-backup storage; cookies and passwords are never stored in Room or logs
- User-controlled **Use login for extraction** bridge; only the non-secret host is persisted with a job
- Private plaintext cookie files exist only around extractor execution and are immediately deleted
- WebView teardown (`stopLoading`, blank navigation, history/view removal and `destroy`) verified on API 37
- Sensitive headers and signed query parameters are redacted from detected-media display and diagnostic text

Earlier foundation work remains in place:

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

Browser/session handling is available through the optional Browser buttons on Home.

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

Phase 6 accepts direct media URLs, supported public web pages, separate audio/video sources, HLS/DASH representations, supported playlists, non-DRM live streams and extractor-supported login sessions. A source can still fail because of DRM, CAPTCHA, geo-blocking, rate limits, expired manifests or website changes. The browser detects observable media requests; it cannot promise access to encrypted media. Android 8/9 requires the user to choose a SAF output folder; Android 10+ defaults to `Downloads/Click Downloader` through MediaStore.
