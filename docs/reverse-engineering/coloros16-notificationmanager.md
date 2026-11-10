# ColorOS 16 NotificationManagerService reverse-engineering notes

Date: 2026-11-10

Target supplied by project owner: OPPO PKT110 / ColorOS 16.1.0 / Android 16 (API 36).

## Input and handling

- Input: owner-supplied `services.jar`
- SHA-256: `15a86155eba86a48274c4b469a3849a438d11905e1f762e3c916b66f4e34e986`
- Relevant DEX: `classes2.dex` (DEX version 039)
- The original firmware JAR is not included in this public repository.

## Confirmed method descriptors

Descriptors below were extracted from the DEX method/type/proto tables and class-data items, not inferred from AOSP.

### Toast

Class: `com.android.server.notification.NotificationManagerService$12`

1. Public text entry:
   `enqueueTextToast(String, IBinder, CharSequence, int, boolean, int, ITransientNotificationCallback): boolean`
2. Public custom entry:
   `enqueueToast(String, IBinder, ITransientNotification, int, boolean, int): boolean`
3. Private shared implementation:
   `enqueueToast(String, IBinder, CharSequence, ITransientNotification, int, boolean, int, ITransientNotificationCallback): boolean`

The first two methods each have a very short body and invoke the same private eight-argument implementation. This is strong evidence that hooking both public methods would duplicate text-toast events. The shared implementation is the preferred single capture point. Its arguments include both a `CharSequence` and an `ITransientNotification`, allowing the hook to distinguish a text request from a custom view request without trying to introspect the remote view object.

Important semantic boundary: this is an enqueue/request event, not proof that the Toast was actually rendered on screen. The event type should therefore retain the `REQUESTED` wording.

### Notification enqueue

Class: `com.android.server.notification.NotificationManagerService`

The three overloads are:

- Public 10-argument `enqueueNotificationInternal(String, String, int, int, String, int, Notification, int, boolean, boolean): void`
- Public 11-argument `enqueueNotificationInternal(String, String, int, int, String, int, Notification, int, boolean, boolean, boolean): void`
- Private 13-argument implementation `enqueueNotificationInternal(String, String, int, int, String, int, Notification, int, boolean, PostNotificationTracker, boolean, boolean): boolean`

The 10-argument overload calls the 11-argument overload; the 11-argument overload acquires a post tracker and calls the private 13-argument implementation. These delegate calls were found in the DEX invoke instructions. Hooking the overload chain would therefore create duplicates and could record notifications that are later rejected or fail to post.

### Notification posted / updated

Private method:

`notifyListenersPostedAndLogLocked(NotificationRecord, NotificationRecord, PostNotificationTracker, NotificationReported): void`

The method prepares listener notification work and posts it to the NMS worker handler. It is a late lifecycle point and receives both the prior and current `NotificationRecord`. This is a promising point for distinguishing a first post from an update (prior record absent vs present), but its exact call-site relationship and lock context still need to be confirmed before production hook activation.

The method name includes `Locked`; hook code must not perform database, file, binder-to-app, or other potentially blocking work synchronously here. Only copy bounded primitive/string data and enqueue it to a non-blocking in-process queue.

### Notification removal

- Public entry: `cancelNotificationInternal(String, String, int, int, String, int, int, int): void`
- Private full implementation: `cancelNotificationLocked(NotificationRecord, boolean, int, int, int, boolean, String, long): void`
- A second private six-argument overload exists:
  `cancelNotificationLocked(NotificationRecord, boolean, int, boolean, String, long): void`

The full eight-argument method contains the substantive removal path, including a call to `NotificationListeners.notifyRemovedLocked(record, reason, stats)`. The six-argument overload delegates to the full eight-argument method, and that full method invokes the listener removal callback. The exact semantic mapping of the integer/boolean parameters to Android cancellation reason, stats, and flags must be established from all call sites before assigning a user-visible removal reason. Do not guess the reason from parameter position.

## Initial hook selection

| Event | Preferred candidate | Confidence / remaining work |
|---|---|---|
| Text Toast request | Shared private 8-argument `enqueueToast` | High for request capture; verify runtime class/loader and argument values |
| Custom Toast request | Same shared private 8-argument `enqueueToast` | High for request capture; text nullability needs runtime validation |
| Notification posted/updated | `notifyListenersPostedAndLogLocked` | Promising; inspect call sites, old/new record semantics, and lock scope |
| Notification removed | Full private 8-argument `cancelNotificationLocked` | Promising; map cancellation reason and confirm invocation coverage |

## Safety and implementation constraints

- Never write SQLite, files, or perform app-process IPC synchronously in these NMS callbacks.
- Copy only fields needed for the event; avoid retaining mutable `Notification` or `NotificationRecord` objects.
- Use a bounded queue and explicit overflow accounting. A full queue must not stall system_server.
- Treat enqueue/request, posted/updated, and removed as distinct lifecycle facts.
- Deduplicate at the single shared implementation point rather than hooking multiple delegating overloads.
- Custom Toast text may be unavailable; do not claim to capture its rendered contents.
- The source JAR is device-specific and proprietary firmware material; only derived method descriptors and analysis notes are published.

## Verification status

Static DEX structure and method-body invocation inspection completed for the descriptors above. This is not yet runtime validation. Hook activation remains disabled until call-site/argument semantics, libxposed system_server behavior, transport, and device stability are validated.
