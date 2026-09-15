# ADR 0002: Direct download pipeline

- Status: Accepted
- Date: 2026-09-15

## Context

Phase 2 requires genuine direct-media downloads that remain correct across flaky networks and process restarts. Outputs must never be exposed as complete until their size has been verified and finalization has succeeded.

## Decision

- Probe direct URLs with a one-byte Range GET so redirects, media MIME type, validators and total size are captured without downloading the object twice.
- Persist the normalized request, validators, partial path, attempt count and queue order in Room before starting transfer.
- Write only to app-private `.part` files. Resume with `Range` and `If-Range`; truncate and restart when a server ignores Range, and reject inconsistent `Content-Range` responses.
- Execute transfers serially in a data-sync foreground service. Expose pause, resume, retry and cancel from both UI and notifications.
- Preserve partial bytes by default. Network and rate-limit failures use bounded exponential backoff with jitter through network-constrained WorkManager jobs.
- Finalize to a user-selected SAF tree when present. Otherwise use a pending MediaStore row on Android 10+, verify the destination byte count, publish it, and then remove the private temporary file.
- Treat authentication, expired links, insufficient storage and failed verification as explicit states; do not silently substitute another resource.

## Consequences

Direct audio/video URLs are fully usable and recoverable. Android 8 and 9 need a user-selected SAF directory because scoped MediaStore pending writes are unavailable. Web-page extraction and adaptive manifests remain outside this pipeline until their dedicated phases.
