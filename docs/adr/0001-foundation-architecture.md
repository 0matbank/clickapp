# ADR 0001: Phase 1 foundation architecture

- Status: Accepted
- Date: 2026-09-15

## Context

The product needs a responsive Android UI while later extractor, browser and media-processing runtimes are large, failure-prone and independently lifecycle-managed. Phase 1 must establish durable jobs, settings and storage boundaries without loading those runtimes.

## Decision

- Use Kotlin, Compose, Material 3 and a single-activity navigation shell.
- Support Android 8.0/API 26 and later; compile and target API 37.
- Keep `core:model` and `core:domain` free of Android APIs.
- Put Room and DataStore implementations in `core:data`, and MediaStore/SAF behavior in `core:storage`.
- Use a small manual `AppContainer` instead of a dependency-injection framework until graph complexity justifies one.
- Create extractor, download, media, browser, benchmark and shared test modules only in the phases where they gain real implementations.
- Keep engine/runtime initialization out of application and activity startup.
- Use an explicit persistent job state machine; a job cannot become `COMPLETED` without passing through `VERIFYING`.

## Consequences

The Phase 1 APK remains free of Python, FFmpeg and WebView initialization. Repository interfaces are replaceable in tests and later process boundaries. Some application wiring is manual, but avoids an annotation-processing dependency and startup work in the UI shell.

