# Development status

Last updated: 2026-11-10

## Verified

- Public repository exists and the authenticated maintainer account has write access.
- The initial Android Gradle project structure and libxposed API 102 entry metadata are committed.
- The API dependency coordinate is `io.github.libxposed:api:102.0.0`.
- Target investigation device details were supplied by the project owner.

## Implemented in this bootstrap

- Android application module and a minimal development-status screen.
- libxposed API 102 entry class, registered in `META-INF/xposed/java_init.list`.
- Initial module metadata and a narrow `android` scope declaration.
- CI workflow for JVM unit tests and debug APK assembly.

## Not implemented yet

- Toast hooks.
- Notification post/update/cancel hooks.
- Inter-process event transport.
- SQLite persistence, retention, filtering, detail view, exclusions, or CSV export.
- Runtime permission / framework compatibility checks.
- Release signing and installable release distribution.

## Validation matrix

| Layer | Status | Notes |
|---|---|---|
| Repository metadata | Passed | Public, default branch `main`, write permission confirmed |
| Source bootstrap | Committed | See commit history |
| Local build | Not run | This execution environment does not have Android SDK / Gradle installed |
| GitHub Actions | Pending | Workflow will run after workflow file is committed |
| Device runtime | Not run | No APK has been installed |
| Production acceptance | Not run | No capture tests have been performed |

## Acceptance gates

1. Confirm the exact OPPO framework method descriptors and method bodies from the supplied firmware.
2. Implement hooks with defensive argument extraction and no disk/database work in system-server callbacks.
3. Add a bounded asynchronous transport and test process-death/backpressure behavior.
4. Implement local persistence and UI.
5. Build in CI; inspect APK metadata and module registration.
6. Install on the target device and test text Toast, custom Toast, notification post, update, app cancel, user dismiss, and system cancel separately.
7. Verify captured timestamps, package identity, cancellation reason, deduplication, and behavior after reboot.
8. Only then publish an installable release and mark device acceptance as passed.
