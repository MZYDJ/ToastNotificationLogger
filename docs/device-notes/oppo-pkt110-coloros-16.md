# OPPO PKT110 / ColorOS 16 framework notes

These notes record the current investigation baseline. They are not a runtime compatibility claim.

## Device baseline supplied by the project owner

- Model: OPPO PKT110
- Device code: OP5DCBL1
- Android: 16 / API 36
- Build fingerprint: `OPPO/PKT110/OP5DCBL1:16/BP2A.250605.015/V.fe29a6_15261ca_1521ddf:user/release-keys`
- Kernel: `6.6.118-android15-8-ge58033dc8ea6-abogki498046332-4k`
- Framework: KernelSU + NeoZygisk, Vector 2.2 (API 102)
- Framework archive supplied for analysis: `services.jar` (must remain outside this public repository)

## Confirmed static findings

The exact DEX descriptors, visibility, return types, and selected method-body call chains are documented in [ColorOS 16 NMS reverse-engineering notes](../reverse-engineering/coloros16-notificationmanager.md).

Key corrections from the initial candidate list:

- The Toast methods share a private eight-argument implementation. Hooking the two public methods separately would duplicate events.
- The notification enqueue implementation has a private **13-argument** method (including `PostNotificationTracker`), not a 12-argument method.
- The public 10-argument and 11-argument enqueue overloads delegate through one another into that private implementation. Do not hook all three.
- The full private eight-argument `cancelNotificationLocked` contains the removal path; the six-argument overload is a short wrapper.
- Cancellation-reason argument mapping is not yet approved and must not be guessed.

## Important limitations

Static DEX inspection is not runtime validation. Before enabling hooks, verify:

1. Full call-site coverage and the exact old/new record semantics for post/update.
2. Cancellation reason, stats, and flag parameter mapping across all call sites.
3. Whether the callback executes while NMS notification locks are held.
4. Vector 2.2 system_server scope and class-loader behavior on this exact device.
5. Hook behavior after reboot and across process/service restarts.

AOSP Android 16 source is reference material only. Do not copy AOSP assumptions into this ROM without comparing the vendor implementation.

## Hooking strategy constraints

- The module uses libxposed's virtual `system` scope target for system_server, not the regular `android` package scope.
- Prefer the narrowest stable server-side interception point that observes the relevant lifecycle fact.
- For text Toast, the server-side text argument is available at the shared implementation.
- For custom Toast, the server receives an `ITransientNotification` callback object; displayed text is not guaranteed to be extractable.
- Do not claim coverage of Snackbar, app-drawn overlays, or arbitrary custom windows.
- Hook callbacks must copy only immutable primitive/string fields and enqueue them to a bounded asynchronous transport.
- Never perform SQLite, file, network, or synchronous app-process IPC while holding system-server notification locks.
- A dropped-event counter and bounded queue policy are required; silent unbounded memory growth is unacceptable.

## Data not included

The original `services.jar`, device logs, personal identifiers, and build artifacts are intentionally excluded from this public repository.
