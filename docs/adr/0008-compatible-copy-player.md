# ADR 0008: Preserve originals and create explicit compatible copies

## Decision

Downloaded originals remain immutable. The Library opens them with an in-app Media3/ExoPlayer player and offers a separate, explicit compatible-copy action only for video. Device recommendations use decoder MIME, size, frame-rate, and HDR-profile capability checks; unsupported originals remain selectable.

Compatible conversion runs in a foreground media-processing service. Before conversion the UI reports estimated time, temporary-space demand, battery level, and thermal state. Low battery and severe heat block conversion unless the user explicitly changes the corresponding setting. FFmpeg produces a separate MP4 using H.264/AVC, AAC-LC, and 8-bit YUV420p without a scale filter. The processor hashes the original before and after, verifies output tracks and exact dimensions, and only then finalizes the new file.

## Consequences

- A compatible copy is a lossy transcode and is never described as original quality.
- Original quality, codec, frame rate, HDR data, and resolution are not replaced or silently reduced.
- Encoding time, battery drain, and heat vary significantly by device; the displayed time is an estimate.
- Old hardware may still fail smooth 4K/8K playback even when the container is readable.
