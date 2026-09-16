# Dependency and license record

All dependency versions are exact and centralized in `gradle/libs.versions.toml`; Gradle dependency locking and SHA-256 verification metadata are generated with the build.

| Dependency family | Purpose | License |
|---|---|---|
| Android Gradle Plugin / Android build tools | Android build pipeline | Apache-2.0 / Android SDK terms |
| Kotlin and Compose compiler plugin | Kotlin compiler and Compose integration | Apache-2.0 |
| AndroidX Core, Activity, AppCompat, Lifecycle, Navigation, Room, DataStore | Android UI, lifecycle, navigation and persistence | Apache-2.0 |
| Jetpack Compose and Material 3 | UI toolkit and design system | Apache-2.0 |
| Kotlin coroutines | Asynchronous work and Flow | Apache-2.0 |
| JUnit 4 | Local tests | Eclipse Public License 1.0 |
| AndroidX Test / Espresso | Instrumented UI tests | Apache-2.0 |
| OkHttp / MockWebServer | HTTP probing, resumable transfer and deterministic transfer tests | Apache-2.0 |
| AndroidX WorkManager | Network-constrained delayed retries and restart recovery | Apache-2.0 |
| AndroidX DocumentFile | User-selected SAF destination finalization | Apache-2.0 |
| youtubedl-android library 0.18.1 (bundled Python, yt-dlp and QuickJS) | On-device webpage extraction and source format discovery | GPL-3.0 |
| youtubedl-android FFmpeg 0.18.1 | Fragment assembly, lossless audio/video merge, remux and metadata/subtitle/thumbnail embedding | GPL-3.0 |
| Jackson 2.11.1 | Parse the extractor's complete JSON result (version aligned with wrapper runtime) | Apache-2.0 |
| Android platform WebView and Keystore APIs | Lazy browser, encrypted session vault and cookie bridge; no additional browser SDK | Android SDK terms |

The extractor runtime is included from Phase 3 and initialized only after a direct-media probe fails. FFmpeg is included from Phase 4 and initialized only when a selected extracted/adaptive job starts. Phase 6 uses only platform WebView/Keystore APIs and adds no third-party browser dependency. Analytics and advertising SDKs are not included. Distributing a build that links these GPL-3.0 components requires GPL-compatible source/license compliance.
