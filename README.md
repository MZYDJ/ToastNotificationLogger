# ToastNotificationLogger

An Android Xposed/LSPosed-style module for locally recording system Toast events and notification lifecycle events.

> **Status: early development.** This repository is being bootstrapped. The current code and build workflow are not yet a validated, installable release. Device-specific hooks must be verified against the target ROM before being considered production-ready.

## Goals

- Capture text Toast requests at the system service boundary where available.
- Capture notification post/update and cancellation events.
- Preserve package, app label (when resolvable), title, text, notification key/ID, timestamps, event type, and cancellation metadata where available.
- Persist events locally and provide filtering, detail view, retention controls, exclusions, and CSV export.
- Keep hook callbacks lightweight: copy immutable event data and hand it to an asynchronous process; never perform database or disk I/O while holding system-server locks.

## Compatibility

The initial investigation target is OPPO PKT110 running ColorOS 16 / Android 16 (API 36), KernelSU with NeoZygisk, and Vector 2.2 / libxposed API 102. This is a development target, not a compatibility guarantee.

AOSP source is useful as a reference but is not a substitute for inspecting the exact framework implementation shipped by an OEM ROM. See [device notes](docs/device-notes/oppo-pkt110-coloros-16.md).

## Build

The project includes the official Gradle 8.13 Wrapper; no separate Gradle installation is needed. Install Android SDK Platform 36, then run:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

The debug APK is not a signed production release. Install only on a test device and keep a recovery path available.

## Safety and privacy

- Event contents may contain private messages, one-time codes, or other sensitive text.
- Data is intended to remain on-device. Do not upload logs or database exports publicly.
- The module must not collect notification contents from apps excluded by the user.
- Do not commit device dumps, `services.jar`, signing keys, tokens, or personal logs.

## Development status

See [docs/STATUS.md](docs/STATUS.md) for verified, implemented, and outstanding work. The target-device procedure is in [docs/DEVICE-ACCEPTANCE-TEST.md](docs/DEVICE-ACCEPTANCE-TEST.md). Contributions should include tests and clearly distinguish static inspection from runtime validation.

## License

Apache-2.0. See [LICENSE](LICENSE).
