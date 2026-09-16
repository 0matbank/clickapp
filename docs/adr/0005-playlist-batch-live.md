# ADR 0005: Playlist batches and finite live capture

- Status: Accepted
- Date: 2026-09-16

## Decision

- Discover playlist entries with yt-dlp flat-playlist metadata, then let the user explicitly select entries and one visible batch quality rule.
- Analyze every selected entry independently before confirmation. Show the known storage total and clearly identify unknown sizes.
- Persist every item as its own job and request. Preparation or queue failure is collected per item and never rolls back successful siblings.
- Mark the playlist coordinator job separately from verified media outputs once its children are queued.
- Represent active live media as `DownloadKind.LIVE`. The user ends capture with **Stop & Save**; cancellation remains a discard/cancel action.
- Retain MPEG-TS during HLS capture so an interrupted stream can be remuxed with FFmpeg `-c copy`, then apply the same track and dimension verification as other adaptive media.

## Consequences

Batch rules are deterministic and disclosed rather than silent per-item fallback. Exact formats are still persisted for each child job. Live completion is user-defined and cannot promise success for DRM, expired, authenticated, geo-blocked, or unsupported streams.
