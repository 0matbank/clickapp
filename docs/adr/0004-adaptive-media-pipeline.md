# ADR 0004: Adaptive media and lossless merge pipeline

- Status: Accepted
- Date: 2026-09-16

## Context

High-quality source formats are commonly HLS/DASH fragments or separate video and audio tracks. Completion must mean a verified playable artifact, not merely downloaded bytes.

## Decision

- Execute yt-dlp against the original page with the exact persisted format spec. No `best` fallback expression is appended.
- Enable yt-dlp continuation, `.part` files and retained fragments. Mirror discovered fragment numbers, paths and byte counts into Room so restart state is inspectable.
- Lazily initialize the bundled four-ABI FFmpeg runtime only for extracted/adaptive processing. Merge/remux uses stream copy; the normal path does not re-encode source video or audio.
- Request metadata, best thumbnail and available manual subtitles and embed them where the selected container supports them. Keep processing sidecars private and remove them only after final publication succeeds.
- On a retry, re-run extraction from the canonical source URL with the same exact format IDs, allowing expired CDN URLs to refresh without changing quality.
- Verify the resulting media with Android's container extractor. Required video/audio tracks and selected dimensions must match before MediaStore/SAF finalization.

## Consequences

HLS, DASH and separate video/audio selections share the durable queue and notification controls. Paused or interrupted jobs retain resumable fragments. Generated 1080p and 4K fixtures test track presence and lossless merge on API 37 without committing media binaries.
