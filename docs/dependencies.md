# Dependency and license record

All dependency versions are exact and centralized in `gradle/libs.versions.toml`; Gradle dependency locking and SHA-256 verification metadata are generated with the build.

| Dependency family | Phase 1 purpose | License |
|---|---|---|
| Android Gradle Plugin / Android build tools | Android build pipeline | Apache-2.0 / Android SDK terms |
| Kotlin and Compose compiler plugin | Kotlin compiler and Compose integration | Apache-2.0 |
| AndroidX Core, Activity, AppCompat, Lifecycle, Navigation, Room, DataStore | Android UI, lifecycle, navigation and persistence | Apache-2.0 |
| Jetpack Compose and Material 3 | UI toolkit and design system | Apache-2.0 |
| Kotlin coroutines | Asynchronous work and Flow | Apache-2.0 |
| JUnit 4 | Local tests | Eclipse Public License 1.0 |
| AndroidX Test / Espresso | Instrumented UI tests | Apache-2.0 |

No extractor, Python runtime, FFmpeg binary, media codec bundle, analytics SDK or advertising SDK is included in Phase 1.

