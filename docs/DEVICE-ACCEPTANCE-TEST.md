# Device acceptance test — OPPO PKT110 / ColorOS 16

This checklist is for the first debug APK. It is not evidence that the module has already passed these tests.

## Before installation

- Confirm the phone can be recovered if a system_server hook causes a boot problem.
- Keep KernelSU/Vector recovery or safe-mode access available.
- Save important local notification history before replacing an earlier build.
- Install only the debug APK from a successful GitHub Actions run for the exact commit being tested.

## Install and activate

1. Install `app-debug.apk`.
2. Open Vector 2.2 and enable ToastNotificationLogger.
3. Confirm the module scope includes the virtual `system` target (system_server).
4. Reboot the phone.
5. Open ToastNotificationLogger. An empty history is expected until events are captured.

## Verify hook installation

Capture logs after reboot:

```sh
adb logcat -v threadtime -s ToastNotificationLogger
```

Expected informational messages include installation of:
- shared ColorOS `enqueueToast` hook;
- notification post/update hook;
- notification removal hook;
- notification post caller deoptimization.

If one hook is missing, save the complete module log before proceeding.

## Functional tests

Use a non-excluded application for each test.

| Test | Action | Expected history |
|---|---|---|
| Text Toast | Trigger a normal text Toast | One `TOAST_TEXT_REQUESTED` entry with package and text |
| Custom Toast | Trigger a custom-view Toast, if an app still uses one | One `TOAST_CUSTOM_REQUESTED` entry; text may be unavailable |
| Notification post | Post a new notification | One `NOTIFICATION_POSTED` entry |
| Notification update | Update the same notification key | One `NOTIFICATION_UPDATED` entry |
| App cancellation | Cancel a notification from its app | One `NOTIFICATION_REMOVED` entry |
| User dismissal | Swipe away a dismissible notification | One `NOTIFICATION_REMOVED` entry |
| Exclusion | Add a package to Excluded apps, then repeat its Toast/notification tests | No event from that package is persisted |
| Retention | Select a retention period and apply it | Entries older than the selected period are deleted |
| CSV | Export history with the document picker | CSV opens and contains the event fields |

For notification updates, keep the notification ID/key stable in the test app. Replacing it with a new key is a new post, not an update.

## Stability checks

- Use the phone normally for at least 30 minutes after initial tests.
- Confirm system UI, notification shade, lock screen, calls, and app switching remain normal.
- Check for crashes:

```sh
adb logcat -b crash -d
```

- Check the event history after force-stopping and reopening the logger app.
- Reboot once and confirm persisted history remains.
- Watch for queue-full or transport warnings. A nonzero drop count needs investigation.

## Rollback

If system_server becomes unstable:

1. Disable ToastNotificationLogger in Vector and reboot.
2. If Android cannot boot normally, use the already-prepared KernelSU/Vector recovery path to disable the module.
3. Do not keep testing a build that causes repeated system_server crashes. Save logs and report the exact build/commit.

## Known limitations

- A custom Toast may expose only an `ITransientNotification` callback; its displayed text is not guaranteed to be recoverable.
- Snackbar, app-drawn overlays, and arbitrary custom windows are outside the current hook coverage.
- The cancellation reason field is provisional until confirmed on this exact ColorOS build.
- This checklist must be completed before calling the module production-ready.
