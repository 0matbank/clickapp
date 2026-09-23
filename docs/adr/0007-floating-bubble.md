# ADR 0007: User-controlled floating bubble

## Decision

The bubble is an explicitly started foreground `specialUse` overlay service. It has no boot receiver, scheduled worker, or application-start hook, so disabling it leaves no bubble process or listener running. Overlay permission is requested only from the Settings toggle.

The bubble reads the clipboard only after a user tap, accepts only HTTP(S), and opens the normal analysis pipeline. If a usable URL is unavailable, the app explains the Copy Link / Android Share fallback. Long-press disables and stops the service. Drag position is edge-snapped and persisted locally; size and opacity are DataStore settings.

The installable release deliberately does not declare an Accessibility Service. Google Play Protect can block unverified sideloaded apps that request sensitive access, while foreground-app filtering is not essential to downloading. The bubble therefore remains a user-enabled overlay and reads clipboard text only after an explicit tap; Paste and Android Share remain the primary fallbacks.

## Consequences

- Android displays the required ongoing notification while the overlay is active.
- The user must grant overlay permission manually.
- Foreground-package allowlisting is not offered in the installable release because it would require sensitive Accessibility access.
- Android sandboxing prevents the bubble from discovering another app's current URL directly.
