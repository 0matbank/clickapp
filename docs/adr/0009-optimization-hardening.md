# ADR 0009: Optimization and recovery hardening

Status: accepted

## Decision

- Keep Compose/UI in the default process and run extraction, downloading and conversion in the shared `:engine` process. Heavy runtimes stay lazy and engine results cross the process boundary through app-private files rather than large Binder payloads.
- Use multi-process Room invalidation and MultiProcess DataStore so settings, job state and recovery remain coherent.
- Preserve an exact selected format expression during expired-link re-extraction. Missing format IDs fail visibly; the app never substitutes a lower or different format.
- Bound retry attempts and classify authentication, expiry, throttling, transient server, storage and verification failures separately. Persist retry state so process death cannot create an infinite loop.
- Stage extractor updates, verify checksums and a Keystore-authenticated manifest, retain a known-good copy, and restore it if validation fails. Updates remain an explicit user action.
- Prefer stream-copy/remux. Device RAM and thermal status reduce concurrent fragments and prevent overlapping heavy conversion/download work; optional low-battery pause is user-controlled.
- Ship ABI-specific APKs plus a universal recovery APK. Release builds retain R8/resource shrinking and include baseline/startup profiles.

## Security boundary

The app does not bypass DRM, CAPTCHA, authentication or access controls. Secrets, cookies and signed URLs are redacted from errors and logs. Exported components are limited to user entry points; the accessibility service is permission-protected.

## Consequences

The isolated engine may be reclaimed independently and therefore every operation must be recoverable from persisted state. ABI APKs reduce installed download size but require selecting the correct device ABI. Emulator measurements are diagnostic only; battery, heat and low-RAM claims require the physical-device matrix.
