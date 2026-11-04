# Cinema TV

A remote-first Android TV launcher with a lightweight cinematic home screen.

[正體中文](README.zh-TW.md) · [Build](docs/BUILDING.md) · [Compatibility](docs/COMPATIBILITY.md) · [Safety and permissions](docs/SAFETY-AND-PERMISSIONS.md)

Cinema TV is an independent fork derived from [CleanYourTV](https://github.com/Gathin23/cleanyourtv). It is not an official CleanYourTV, UBOX, Google, or streaming-service release.

![Cinema TV public native preview](docs/images/home-native-preview.png)

Native-view preview with synthetic sample apps and the restored, owner-authorized original artwork; not an actual-device screenshot.

## Features

- Seven visible carousel cards, restrained perspective, glass highlights and app-color lighting.
- Explicit remote focus zones, app library/search, custom shortcut ordering and up to four real recent items.
- Paired sunrise/sunset themes, configurable automatic switching, custom wallpaper and recent-item images.
- Native settings, return-focus/scroll memory, short interruptible transitions and generated UI sounds.
- Network status shortcut and optional startup app selection.
- Collapsed developer tools for wireless ADB and experimental boot-appearance management. Ordinary launcher use needs no Root; developer tools have separate requirements and confirmations.

The public edition preserves the original project default backgrounds. The owner confirmed project-generated/owned artwork and authorized MIT redistribution; per-file hashes and the statement are recorded in [asset provenance](docs/ASSETS.md). Device records and user-selected private images remain excluded.

## Build

Requirements: JDK 21, Android SDK platform 34/build-tools 34.0.0, Python 3.9+, and the included Gradle 8.9 wrapper. First builds need access to the official dependency repositories; the app itself has no INTERNET permission.

```sh
python3 tools/check_public_tree.py
python3 -m unittest discover -s tests -v
python3 tools/build.py --unsigned
```

This runs `testReleaseUnitTest lintRelease assembleRelease`. The release APK is **unsigned by default** and cannot be installed until signed. For a locally signed debug build, use `python3 tools/build.py assembleDebug`. Read [signing and package identity](docs/BUILDING.md) first; a different certificate cannot replace an existing installation under the same package ID.

No supplied build command installs the app, chooses HOME, requests Root or changes a device. Preserve your OEM launcher and use your system's Home app picker only when ready. [Installation and return to OEM](docs/INSTALLATION.md).

## Current baseline

Current source: `7.0.0-beta.2-original-art` / versionCode 73, based on the private v71 engineering snapshot. A maintainer-signed sideload Beta APK is prepared; see [Beta installation](docs/BETA2-INSTALLATION.md) and current validation before distribution. The Java namespace and historical default application ID are retained for compatibility; see the build guide before overriding them.

Local validation and remaining gaps are recorded in [release readiness](docs/RELEASE-READINESS.md). Historical prototype testing does not establish that this public asset/build variant works on every TV. Root boot-logo writes are not a generic firmware recovery feature.

## Contribute

See [CONTRIBUTING.md](CONTRIBUTING.md), [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md), [SECURITY.md](SECURITY.md) and the issue templates. CI builds/tests the source without release signing credentials and never creates a public release automatically.

## License

Project code and original generated artwork/audio: [MIT](LICENSE), preserving Copyright 2026 Gathin and Cinema TV contributors. Fonts, emoji and dependency licenses are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Third-party apps, firmware, credentials and media content are not included.
