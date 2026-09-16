# ADR 0006: Lazy browser and encrypted extractor sessions

- Status: Accepted
- Date: 2026-09-16

## Decision

- Instantiate WebView only in a dedicated, non-exported browser activity. App startup and non-browser screens do not create it.
- Keep login cookies in an AES-GCM vault whose key is held by Android Keystore and whose ciphertext files live under `noBackupFilesDir`.
- Clear WebView's runtime cookie/storage state on browser exit. Restore a site's encrypted cookie header only while browsing that site.
- Never put cookie values, passwords or authorization headers in Room, intents, UI diagnostics or logs. Persist only the user-approved session hostname on a download request.
- Export a private Netscape cookie file to cache immediately before yt-dlp analysis/download and delete it immediately afterward.
- Incognito starts and ends with cleared runtime state, never restores or persists the vault, and disables extractor-session export.
- Detect observable media requests without intercepting or rewriting response bodies. Keep the actual signed URL only in memory and show a redacted form.
- Explicitly release WebView resources on exit: stop, blank, clear history, detach child views and destroy.

## Consequences

Authenticated sources work only when the user enables the session bridge and yt-dlp supports that source's cookies. CAPTCHA, DRM and other access controls are not bypassed. WebView media detection is opportunistic and cannot see every encrypted or application-internal stream.
