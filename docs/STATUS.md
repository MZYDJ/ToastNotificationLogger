# Development status

Last updated: 2026-11-10

## Verified

- Public repository exists; active branch is `main`.
- GitHub Actions run #7 passed on commit `19784979e8fe65c2a47234ee175c3416e749fb44`: JVM unit tests, debug APK assembly, and artifact upload.
- Owner-supplied OPPO PKT110 / ColorOS 16.1.0 / Android 16 target details are recorded.
- The supplied `services.jar` was inspected locally; its SHA-256 and derived method findings are recorded in `docs/reverse-engineering/coloros16-notificationmanager.md`. The original firmware archive is not published.
- ColorOS DEX inspection confirmed that the Toast public entry methods delegate to one private shared implementation, and that the notification enqueue overloads delegate to one private implementation.
- libxposed API documentation identifies `system` as the virtual scope target for system_server. The module scope has been corrected from `android` to `system`.

## Implemented in repository

- Android application module and minimal development-status screen.
- libxposed API 102 entry class and module metadata.
- Narrow system_server scope declaration.
- CI workflow for JVM unit tests and debug APK assembly.
- Initial event type enum and bootstrap tests.
- ColorOS 16 static reverse-engineering notes.

## Not implemented yet

- Runtime Toast hooks.
- Runtime notification post/update/remove hooks.
- Bounded asynchronous transport from system_server to the app process.
- SQLite persistence, retention, filtering, detail view, exclusions, or CSV export.
- User-facing event browser and configuration UI.
- Runtime permission / framework compatibility checks.
- Release signing and installable release distribution.

## Validation matrix

| Layer | Status | Notes |
|---|---|---|
| Repository metadata | Passed | Public, default branch `main` |
| Static firmware inspection | Partial | DEX descriptors and selected method-body call chains inspected; lock/call-site semantics remain |
| JVM unit tests | Passed | GitHub Actions run #7 |
| Debug APK assembly | Passed | GitHub Actions run #7 |
| APK install/runtime | Not run | No target-device installation yet |
| Hook behavior | Not run | Hooks remain disabled |
| Production acceptance | Not run | No end-to-end capture or stability tests |

## Next acceptance gates

1. Complete DEX call-site inspection for notification lifecycle and cancellation-reason mapping.
2. Verify Vector 2.2 / libxposed API 102 system_server loading and class-loader behavior.
3. Implement defensive hooks that copy immutable fields only; no database, disk, or synchronous app IPC in NMS callbacks.
4. Implement bounded transport with overflow accounting and process-death/backpressure tests.
5. Implement local persistence, retention, search/filter, details, exclusions, and CSV export.
6. Build in CI; inspect APK metadata and module registration.
7. Install on the target device and test text Toast, custom Toast, notification post, update, app cancel, user dismiss, and system cancel separately.
8. Verify timestamps, package identity, cancellation reason, deduplication, and behavior after reboot.
9. Publish an installable release only after device acceptance passes.
