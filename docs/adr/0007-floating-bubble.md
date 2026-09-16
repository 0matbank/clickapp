# ADR 0007: User-controlled floating bubble

## Decision

The bubble is an explicitly started foreground `specialUse` overlay service. It has no boot receiver, scheduled worker, or application-start hook, so disabling it leaves no bubble process or listener running. Overlay permission is requested only from the Settings toggle.

The bubble reads the clipboard only after a user tap, accepts only HTTP(S), and opens the normal analysis pipeline. If a usable URL is unavailable, the app explains the Copy Link / Android Share fallback. Long-press disables and stops the service. Drag position is edge-snapped and persisted locally; size and opacity are DataStore settings.

Optional accessibility assistance is limited to `TYPE_WINDOW_STATE_CHANGED`, has `canRetrieveWindowContent=false`, and reports only the foreground package name to the already-running bubble service. It exists solely to apply the user-selected app allowlist. It never reads screen text, injects controls, or automates another app. Without it, the core Paste and Share flows remain available.

## Consequences

- Android displays the required ongoing notification while the overlay is active.
- The user must grant overlay permission manually and, separately, enable the optional accessibility service in system settings.
- Package allowlisting cannot be reliable without the optional accessibility service; this limitation is disclosed in the UI.
- Android sandboxing prevents the bubble from discovering another app's current URL directly.
