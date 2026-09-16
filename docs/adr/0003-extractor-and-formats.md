# ADR 0003: Extractor and exact source-format selection

- Status: Accepted
- Date: 2026-09-15

## Context

Web pages expose multiple progressive and adaptive source representations. The product must expose all of them, include audio-only sources and avoid silently replacing an explicit user choice.

## Decision

- Use the four-ABI `io.github.junkfood02.youtubedl-android:library:0.18.1` package and legacy native-library packaging. Its embedded Python/yt-dlp runtime initializes lazily only after a direct-media probe fails.
- Request a single complete JSON object from yt-dlp and normalize every returned format in original order. Preserve protocol, direct/manifest URL, HTTP headers, resolution, FPS, video/audio codecs and bitrates, dynamic range, bit depth, language, exact/approximate size, subtitles, thumbnails and live status.
- Display DRM-tagged sources but prevent selection. The app does not attempt DRM circumvention.
- Evaluate local decoder/container compatibility and show recommendation and compatibility information while leaving every source row visible.
- Queue progressive or audio-only formats directly. A video-only source cannot be queued until the user explicitly chooses a companion audio source.
- Persist the exact format spec (`videoId` or `videoId+audioId`) and source request headers. Approximate sizes remain estimates and are never used as byte-exact verification targets.

## Consequences

Extractor startup costs are paid only for web pages. Exact choices can be audited and resumed without a hidden downgrade. Adaptive sources are represented durably but need the Phase 4 fragment and FFmpeg pipeline before producing their final output.
