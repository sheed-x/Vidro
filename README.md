# Vidro

A fast, private video player for Android: offline by design, with no ads and no tracking.

**مشغّل فيديو سريع وخاص لأندرويد، يعمل بدون إنترنت، وبدون إعلانات أو تتبّع.**

## Features

- **170+ video formats:** MKV, MP4, AVI, WMV, RMVB, FLV, WebM, TS, MOV and more, powered by mpv/FFmpeg
- **Hardware and software decoding:** H.264, HEVC, VP9 and AV1 up to 4K, with automatic fallback
- **Subtitles:** SRT, ASS/SSA, VTT, SUB, SMI and embedded tracks
  - Correct right-to-left Arabic
  - Two subtitles at once
  - Sync, encoding and style controls
- **Audio:** equalizer and effects, boost to 200%, A/V sync, track switching, AC3/DTS passthrough
- **Gestures:** seek, brightness, volume, pinch-zoom, double-tap and hold-to-speed
- **Floating window and background play:** with media controls in the notification
- **Library:**
  - Folders and all-videos views
  - Search and natural sorting
  - Resume points
  - Continue watching
- **Interface:** Arabic and English, light and dark themes

## Privacy

Vidro reads your videos locally and never uploads anything. Its only network use is an optional
check for new releases of this repository, which you can turn off in **Settings → About**.

## Requirements

Android 7.0 (API 24) or newer.

## Building

```bash
./gradlew assembleDebug
```

## Licenses

Vidro uses [mpv](https://mpv.io) and [FFmpeg](https://ffmpeg.org) (LGPL 2.1+) through
[libmpv-android](https://github.com/jarnedemeulemeester/libmpv-android) (MIT), Jetpack Compose
and AndroidX (Apache 2.0), Coil and Haze (Apache 2.0), and the Google Sans Flex font (SIL OFL 1.1).
The full list is in the app under **Settings → About → Open-source licenses**.
