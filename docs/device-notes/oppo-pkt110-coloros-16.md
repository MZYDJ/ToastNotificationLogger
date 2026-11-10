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

## Previously identified candidate classes and methods

The earlier static inspection of the supplied `services.jar` reported:

- `com.android.server.notification.NotificationManagerService` in `classes2.dex`.
- OPPO extension interface `com.android.server.notification.INotificationManagerServiceExt`.
- Text Toast entry candidate:
  `NotificationManagerService$12.enqueueTextToast(String, IBinder, CharSequence, int, boolean, int, ITransientNotificationCallback)`.
- Custom Toast entry candidate:
  `NotificationManagerService$12.enqueueToast(String, IBinder, ITransientNotification, int, boolean, int)`.
- Notification enqueue candidates in `NotificationManagerService.enqueueNotificationInternal(...)`, with 10-, 11-, and 12-argument overloads reported.
- A later notification-post candidate:
  `notifyListenersPostedAndLogLocked(NotificationRecord, NotificationRecord, PostNotificationTracker, NotificationReported)`.
- Cancellation candidates:
  `cancelNotificationInternal(String, String, int, int, String, int, int, int)` and two `cancelNotificationLocked` overloads, one of which was reported with an 8-argument signature and a `NotificationRecord` first parameter.

## Important limitations

The signatures above are leads from prior analysis, not an approved hook contract. Before implementation, re-open the exact uploaded archive and verify:

1. Full JVM descriptors, visibility, declaring class, and return types.
2. Method bodies and the exact point at which the operation is accepted or rejected.
3. Whether overloads delegate to one another (to avoid duplicate events).
4. Whether cancellation is initiated by app, user dismissal, timeout, package removal, or system policy, and which argument/record fields distinguish those reasons.
5. Whether OEM extension hooks alter the AOSP call sequence.
6. Whether the callback executes under notification locks.

AOSP Android 16 source is reference material only. Do not copy AOSP assumptions into this ROM without comparing the vendor implementation.

## Hooking strategy constraints

- Prefer the narrowest stable server-side interception point that observes the final accepted event.
- For text Toast, the server-side text argument may be directly available.
- For custom Toast, the server receives an `ITransientNotification` callback object; its displayed text is not guaranteed to be extractable.
- Do not claim coverage of Snackbar, app-drawn overlays, or arbitrary custom windows.
- Hook callbacks must copy only immutable primitive/string fields and enqueue them to a bounded asynchronous transport.
- Never perform SQLite, file, network, or package-manager work while holding system-server notification locks.
- A dropped-event counter and bounded queue policy are required; silent unbounded memory growth is unacceptable.

## Data not included

The original `services.jar`, device logs, personal identifiers, and build artifacts are intentionally excluded from this public repository.
