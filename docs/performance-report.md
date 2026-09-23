# Phase 9 performance report

Date: 2026-09-22

## Test environment

- Android 17 / API 37 x86_64 AVD, 4 virtual CPU cores, approximately 4 GB RAM
- Benchmark app: minified `benchmark` variant with baseline/startup profiles
- AndroidX Macrobenchmark, partial compilation, five iterations
- `EMULATOR` benchmark warning suppressed explicitly

These measurements prove that the benchmark harness runs and detect regressions. They are **not physical-phone measurements** and cannot validate battery drain, device heat, 2 GB low-RAM behavior or the Master Plan's phone startup targets.

## Results

The values below are populated from `com.clickdownloader.benchmark-benchmarkData.json` after the final Phase 9 gate.

| Metric | Result |
| --- | ---: |
| Cold startup, median | 1,123.30 ms |
| Warm startup, median | 326.81 ms |
| Home navigation frame CPU duration, p50 / p90 / p95 | 48.51 / 64.66 / 65.22 ms |
| UI process total PSS after launch | 29,061 KB |
| Engine process at idle startup | Not running (expected) |

The emulator met the numerical startup targets but had poor navigation frame timing. Host scheduling and software-rendered emulator graphics distort these numbers, so they are recorded without presenting them as phone performance. The physical-device gate below remains authoritative.

## Release APK size

| ABI | Unsigned release size |
| --- | ---: |
| armeabi-v7a | 36.88 MB |
| arm64-v8a | 39.04 MB |
| x86 | 40.65 MB |
| x86_64 | 41.45 MB |
| universal | 137.45 MB |

The primary arm64 APK is about 72% smaller than the universal package while preserving the same feature and original-quality pipeline.

## Implemented optimizations

- Extractor, FFmpeg and WebView are not initialized at app startup.
- Heavy extraction/download/conversion work is isolated from the UI process.
- Baseline and startup profiles cover application launch and primary navigation.
- Progress updates are throttled and lists use lazy containers with stable identifiers.
- Release builds use R8, resource shrinking and per-ABI packaging.
- Adaptive fragment concurrency is bounded from 1 to 8 and reduced for low-RAM or thermal pressure.
- Low-RAM mode serializes heavy jobs; optional low-battery pause avoids wasteful work.

## Physical-device gate still required

Run the benchmark variant on representative 2 GB, 4 GB and 8 GB+ ARM phones without suppressing benchmark errors. Record cold/warm startup, frame timing, `adb shell dumpsys meminfo com.clickdownloader.app`, battery percentage/energy over a fixed download, and thermal status before/during/after a 4K merge. A release cannot be called final until that matrix and a signed APK install/update are tested on a physical phone.
