# SaveKoro: Android Video Downloader

An Android app to download videos or MP3 audio from Facebook, YouTube, Instagram, TikTok, Pinterest and Threads.
Engine: [yt-dlp](https://github.com/yt-dlp/yt-dlp) + ffmpeg, through the [youtubedl-android](https://github.com/yausername/youtubedl-android) library.
No server needed. Everything downloads directly on the phone.

## 📥 Download
**[⬇️ Download SaveKoro (most phones)](https://github.com/Abusaeed-bd/SaveKoro/releases/latest/download/SaveKoro-arm64.apk)**

Older phones: [armv7](https://github.com/Abusaeed-bd/SaveKoro/releases/latest/download/SaveKoro-armv7.apk) · Any phone: [universal](https://github.com/Abusaeed-bd/SaveKoro/releases/latest/download/SaveKoro-universal.apk) · [All releases](https://github.com/Abusaeed-bd/SaveKoro/releases)

When installing, allow "Install from unknown sources".

## Features
- Paste a link and the platform is detected automatically
- **Share → SaveKoro** from any app fills in the link and loads the video info
- Shows thumbnail, title, uploader and duration
- Quality options: Best / 1080p / 720p / 480p / MP3 audio
- Live progress, time remaining and a cancel button
- Videos are saved to `Movies/SaveKoro` and audio to `Music/SaveKoro`, so they show up in the gallery
- yt-dlp updates itself once a day, and there's a manual update button too
- Supports Android 8.0+ (API 26), with dark mode and Material You colors

## Building
Every push to `main` builds the app on GitHub Actions and publishes a new release automatically.
This requires two repository secrets: `KEYSTORE_BASE64` and `KEYSTORE_PASSWORD`.

To build locally, open the folder in Android Studio, sync, and press Run ▶️. Without a keystore, local builds are signed with the debug key.

## Known limitations
- **Instagram/Threads**: private or login-only posts can't be downloaded yet.
- **Threads**: support depends on yt-dlp and hasn't been fully tested.
- **YouTube**: occasionally asks for a bot check. Tapping "Update engine" usually fixes it.
- A download may stop if the app is closed. Background downloading with a notification is planned.
- The APK is large because it bundles yt-dlp, Python and ffmpeg.

## License & disclaimer
youtubedl-android is licensed under GPL-3.0, and yt-dlp is released under the Unlicense.
Only download content you own or have permission to download. Respect each platform's terms of service and copyright law.
