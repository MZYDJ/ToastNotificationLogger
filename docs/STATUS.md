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
- ART deoptimization of the NMS synthetic post bridge and its 13-argument implementation caller to reduce the risk of the small callback being inlined past the hook.
- Bounded non-blocking capture queue (capacity 256, drop-newest policy, drop counter).
- Dedicated daemon worker for ContentProvider IPC; no database or synchronous app-process IPC is called directly by the NMS hook callback.
- Exported ContentProvider protected by `android.permission.DUMP`, with an additional caller-permission check.
- App-process SQLite persistence with timestamp/package/type indexes, recent-event query, retention deletion primitive, and delete-all.
- Local event history list and event detail dialog.
- Exact package-name, event-type, and time-window filters.
- CSV export through Android's document picker, with formula-injection mitigation.
- App labels resolved in the app process for history display and CSV export.
- Configurable retention policy (keep forever / 7 / 30 / 90 / 365 days), persisted locally and applied at startup or on demand.
- Excluded-package list persisted locally and enforced in the app-process provider before SQLite insertion.
- Unit tests for event types, immutable event snapshots, and queue backpressure.

## Important unverified assumptions / risks

- Hook installation and provider access have not been tested on Vector 2.2 / this exact ColorOS build.
- The system-server Context resolver relies on the inner Toast service's outer NMS instance and its inherited Context field; this must be verified at runtime.
- The notification post method uses the Android 16 parameter convention (current record, previous record); the ColorOS DEX forwards the two parameters unchanged to its listener preparation method, but this mapping still needs on-device confirmation.
- Cancellation reason is provisionally read from argument index 2 based on the matching Android 16 method signature. Verify against the exact ColorOS call sites before treating it as authoritative.
- A bounded queue protects system_server from disk/IPC latency, but provider startup, permission enforcement, app-process death, and transport failure recovery still need stress testing.

## Not implemented yet

- Richer notification content extraction (large text, messaging style, and other extras).
- Backup/restore.
- Queue/transport metrics UI and retry/recovery policy.
- Release signing.
- Device-specific runtime compatibility checks and production acceptance.

## Validation matrix

| Layer | Status | Notes |
|---|---|---|
| Repository metadata | Passed | Public, default branch `main` |
| Static firmware inspection | Partial | DEX descriptors and selected call chains inspected |
| JVM unit tests | Passed | Latest source commit `d0c1f21`, Actions run #55 |
| Debug APK assembly | Passed | Latest source commit `d0c1f21`, Actions run #55 |
| APK static inspection | Passed | Xposed entry, `system` scope, and exact synthetic bridge string verified |
| APK install/runtime | Not run | No target-device installation yet |
| Hook behavior | Not run | Static hook code committed; runtime not proven |
| Production acceptance | Not run | No end-to-end capture or stability tests |

## Next acceptance gates

1. Install the debug APK and verify module activation and system_server hook installation in Vector 2.2.
3. Confirm Toast text/custom classification and notification post/update/remove events using controlled test apps.
4. Confirm provider permission, queue overflow accounting, app-process death, reboot persistence, and no system_server stalls.
5. Verify cancellation-reason mapping against ColorOS DEX call sites.
6. Add app labels, filters, retention controls, exclusions, and CSV export.
7. Publish a release only after target-device stability and end-to-end acceptance pass.
