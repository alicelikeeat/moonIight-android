# 月淡 Android

Android app for 月淡, package `com.moonlight.feed`. Version 6.0 keeps the v5.0 install identity and lines the shell up with [月淡 iOS 1.43](https://github.com/alicelikeeat/moonIight-ios).

The home screen is still a full-screen vertical feed. Discover can switch between one and two columns, and the banner height is adjustable. The bottom dock is five separate glass circles, with a sliding-bar mode in settings. A finger that starts on the dock drags between tabs; a still hold opens that button's ring. Elsewhere, a quick move stays a scroll and a still hold opens the ring. Tap and long-press do not both fire for the same stroke. 水滴融合 is one of the long-press styles and is off unless you choose it.

Bilibili and YouTube play through their official embeds and public APIs. The app does not sign WBI requests, resolve playurl, rip streams, or bypass DRM.

## Build

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Snapshot tests under `*Shots` are excluded. Their reference PNGs were not part of the imported v5.0 tree.

A signed release reads these environment variables and does not store them in the repo:

- `ANDROID_KEYSTORE_PATH`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

## GitHub

Push and pull request runs `.github/workflows/ci.yml`. A tag `v*` runs `.github/workflows/release.yml`. If the signing secrets below are missing, that workflow finishes without building or uploading an APK. Once they exist, it signs `Yuedan-vX.Y.apk` and attaches it to the release:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Gallery albums stay in `Pictures/月光` and `Movies/月光` so an update does not split files already saved by v5.0.
