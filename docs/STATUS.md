# Development status

Last updated: 2026-11-10

## Verified

- Public repository exists; active branch is `main`.
- GitHub Actions runs #7–#19 passed for the code present in those commits.
- Owner-supplied OPPO PKT110 / ColorOS 16.1.0 / Android 16 target details are recorded.
- The supplied `services.jar` was inspected locally; its SHA-256 and derived method findings are recorded in `docs/reverse-engineering/coloros16-notificationmanager.md`. The original firmware archive is not published.
- DEX inspection confirmed that the Toast public entry methods delegate to one private shared implementation, notification enqueue overloads delegate through a chain, and the full notification-removal method contains the listener-removal call.
- Module scope is set to libxposed's virtual `system` target for system_server.

## Implemented in source (runtime not yet validated)

- libxposed API 102 entry point and system_server hook installer for:
  - shared ColorOS text/custom Toast enqueue implementation;
  - notification post/update lifecycle callback;
  - full notification cancellation implementation.
- Bounded non-blocking capture queue (capacity 256, drop-newest policy, drop counter).
- Dedicated daemon worker for ContentProvider IPC; no database or synchronous app-process IPC is called directly by the NMS hook callback.
- Exported ContentProvider protected by `android.permission.DUMP`, with an additional caller-permission check.
- App-process SQLite persistence with timestamp/package/type indexes, recent-event query, retention deletion primitive, and delete-all.
- Basic local event history list and event detail dialog.
- Unit tests for event types, immutable event snapshots, and queue backpressure.

## Important unverified assumptions / risks

- Hook installation and provider access have not been tested on Vector 2.2 / this exact ColorOS build.
- The system-server Context resolver relies on the inner Toast service's outer NMS instance and its inherited Context field; this must be verified at runtime.
- The notification post method uses the Android 16 parameter convention (current record, previous record); the ColorOS DEX forwards the two parameters unchanged to its listener preparation method, but this mapping still needs on-device confirmation.
- Cancellation reason is provisionally read from argument index 2 based on the matching Android 16 method signature. Verify against the exact ColorOS call sites before treating it as authoritative.
- A bounded queue protects system_server from disk/IPC latency, but provider startup, permission enforcement, app-process death, and transport failure recovery still need stress testing.

## Not implemented yet

- App-label resolution and richer notification content extraction.
- Configurable retention UI, excluded-app management, search filters, CSV export, and backup/restore.
- Queue/transport metrics UI and retry/recovery policy.
- Gradle wrapper and release signing.
- Device-specific runtime compatibility checks and production acceptance.

## Validation matrix

| Layer | Status | Notes |
|---|---|---|
| Repository metadata | Passed | Public, default branch `main` |
| Static firmware inspection | Partial | DEX descriptors and selected call chains inspected |
| JVM unit tests | Passed on prior source | New hook/transport/UI commits await latest CI |
| Debug APK assembly | Passed on prior source | New hook/transport/UI commits await latest CI |
| APK install/runtime | Not run | No target-device installation yet |
| Hook behavior | Not run | Static hook code committed; runtime not proven |
| Production acceptance | Not run | No end-to-end capture or stability tests |

## Next acceptance gates

1. Obtain a clean CI pass for the latest commit and inspect the generated APK.
2. Verify module activation and system_server hook installation in Vector 2.2.
3. Confirm Toast text/custom classification and notification post/update/remove events using controlled test apps.
4. Confirm provider permission, queue overflow accounting, app-process death, reboot persistence, and no system_server stalls.
5. Verify cancellation-reason mapping against ColorOS DEX call sites.
6. Add app labels, filters, retention controls, exclusions, and CSV export.
7. Publish a release only after target-device stability and end-to-end acceptance pass.
