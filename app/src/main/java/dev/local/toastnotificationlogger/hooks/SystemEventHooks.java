package dev.local.toastnotificationlogger.hooks;

import android.app.Notification;
import android.content.Context;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import dev.local.toastnotificationlogger.model.EventRecord;
import dev.local.toastnotificationlogger.model.EventType;
import dev.local.toastnotificationlogger.transport.SystemEventDispatcher;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedInterface.ExceptionMode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicLong;

/** Installs the ColorOS 16 NotificationManagerService capture hooks. */
final class SystemEventHooks {
    private static final String TAG = "ToastNotificationLogger";
    private static final String NMS = "com.android.server.notification.NotificationManagerService";
    private static final String RECORD =
            "com.android.server.notification.NotificationRecord";
    private static final int MAX_TEXT_LENGTH = 4000;
    private static final AtomicLong UNQUEUED_WARNINGS = new AtomicLong();

    private final XposedInterface xposed;

    SystemEventHooks(XposedInterface xposed) {
        this.xposed = xposed;
    }

    void install(ClassLoader classLoader) {
        // Allocate the daemon worker before any notification-lock callback can run.
        SystemEventDispatcher.start();
        installToast(classLoader);
        installNotificationPost(classLoader);
        installNotificationRemoval(classLoader);
    }

    private void installToast(ClassLoader loader) {
        try {
            Class<?> stub = Class.forName(NMS + "$12", false, loader);
            Method target = null;
            for (Method method : stub.getDeclaredMethods()) {
                Class<?>[] p = method.getParameterTypes();
                if (method.getName().equals("enqueueToast")
                        && p.length == 8
                        && p[2] == CharSequence.class
                        && p[0] == String.class) {
                    target = method;
                    break;
                }
            }
            if (target == null) throw new NoSuchMethodException("shared enqueueToast/8");
            xposed.hook(target)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        if (Boolean.TRUE.equals(result)) {
                            try {
                                String packageName = safeString(chain.getArg(0));
                                CharSequence suppliedText = (CharSequence) chain.getArg(2);
                                String text = safeText(suppliedText);
                                Integer userId = safeInteger(chain.getArg(6));
                                EventType type = suppliedText == null
                                        ? EventType.TOAST_CUSTOM_REQUESTED
                                        : EventType.TOAST_TEXT_REQUESTED;
                                EventRecord event = new EventRecord(
                                        System.currentTimeMillis(), type, packageName,
                                        null, null, text, null, null, userId, null);
                                submit(chain.getThisObject(), event);
                            } catch (RuntimeException failure) {
                                xposed.log(android.util.Log.WARN, TAG,
                                        "Unable to snapshot Toast request: " + failure);
                            }
                        }
                        return result;
                    });
            xposed.log(android.util.Log.INFO, TAG,
                    "Installed shared ColorOS enqueueToast hook: " + target);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            xposed.log(android.util.Log.ERROR, TAG,
                    "Unable to install Toast hook", failure);
        }
    }

    private void installNotificationPost(ClassLoader loader) {
        try {
            Class<?> nms = Class.forName(NMS, false, loader);
            Method target = null;
            for (Method method : nms.getDeclaredMethods()) {
                Class<?>[] p = method.getParameterTypes();
                if (method.getName().equals("notifyListenersPostedAndLogLocked")
                        && p.length == 4
                        && p[0].getName().equals(RECORD)
                        && p[1].getName().equals(RECORD)) {
                    target = method;
                    break;
                }
            }
            if (target == null) throw new NoSuchMethodException("notifyListenersPostedAndLogLocked/4");
            xposed.hook(target)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            Object currentRecord = chain.getArg(0);
                            Object previousRecord = chain.getArg(1);
                            if (currentRecord != null) {
                                EventType type = previousRecord == null
                                        ? EventType.NOTIFICATION_POSTED
                                        : EventType.NOTIFICATION_UPDATED;
                                EventRecord event = snapshotNotification(currentRecord, type, null);
                                submit(chain.getThisObject(), event);
                            }
                        } catch (ReflectiveOperationException | RuntimeException failure) {
                            xposed.log(android.util.Log.WARN, TAG,
                                    "Unable to snapshot posted notification: " + failure);
                        }
                        return result;
                    });
            deoptimizePostCallers(nms);
            xposed.log(android.util.Log.INFO, TAG,
                    "Installed notification post/update hook: " + target);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            xposed.log(android.util.Log.ERROR, TAG,
                    "Unable to install notification post/update hook", failure);
        }
    }

    /**
     * The post callback is very small and can be inlined by ART. Deoptimize its
     * synthetic access bridge and the NMS implementation that calls that bridge
     * so the hooked method remains an actual invocation.
     */
    private void deoptimizePostCallers(Class<?> nms) {
        for (Method method : nms.getDeclaredMethods()) {
            String name = method.getName();
            boolean bridge = name.equals("-$Nest$mnotifyListenersPostedAndLogLocked");
            boolean implementation = name.equals("enqueueNotificationInternal")
                    && method.getParameterTypes().length == 13;
            if (!bridge && !implementation) continue;
            try {
                boolean deoptimized = xposed.deoptimize(method);
                xposed.log(android.util.Log.INFO, TAG,
                        "Post-hook caller deoptimization " + deoptimized + ": " + method);
            } catch (RuntimeException failure) {
                xposed.log(android.util.Log.WARN, TAG,
                        "Unable to deoptimize post-hook caller: " + method, failure);
            }
        }
    }

    private void installNotificationRemoval(ClassLoader loader) {
        try {
            Class<?> nms = Class.forName(NMS, false, loader);
            Method target = null;
            for (Method method : nms.getDeclaredMethods()) {
                Class<?>[] p = method.getParameterTypes();
                if (method.getName().equals("cancelNotificationLocked")
                        && p.length == 8
                        && p[0].getName().equals(RECORD)) {
                    target = method;
                    break;
                }
            }
            if (target == null) throw new NoSuchMethodException("cancelNotificationLocked/8");
            xposed.hook(target)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            Object record = chain.getArg(0);
                            Integer reason = safeInteger(chain.getArg(2));
                            if (record != null) {
                                EventRecord event = snapshotNotification(
                                        record, EventType.NOTIFICATION_REMOVED, reason);
                                submit(chain.getThisObject(), event);
                            }
                        } catch (ReflectiveOperationException | RuntimeException failure) {
                            xposed.log(android.util.Log.WARN, TAG,
                                    "Unable to snapshot removed notification: " + failure);
                        }
                        return result;
                    });
            xposed.log(android.util.Log.INFO, TAG,
                    "Installed notification removal hook: " + target);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            xposed.log(android.util.Log.ERROR, TAG,
                    "Unable to install notification removal hook", failure);
        }
    }

    private void submit(Object serviceObject, EventRecord event) {
        Context context = findSystemContext(serviceObject, 0);
        if (context == null || !SystemEventDispatcher.submit(context, event)) {
            long count = UNQUEUED_WARNINGS.incrementAndGet();
            if (count == 1 || count % 100 == 0) {
                xposed.log(android.util.Log.WARN, TAG,
                        "Event not queued (count=" + count
                                + "); context unavailable or queue full");
            }
        }
    }

    private static EventRecord snapshotNotification(
            Object record, EventType type, Integer removalReason)
            throws ReflectiveOperationException {
        Method getSbn = record.getClass().getMethod("getSbn");
        StatusBarNotification sbn = (StatusBarNotification) getSbn.invoke(record);
        if (sbn == null) return null;
        Notification notification = sbn.getNotification();
        Bundle extras = notification == null ? null : notification.extras;
        String title = extras == null ? null
                : safeText(extras.getCharSequence(Notification.EXTRA_TITLE));
        CharSequence body = extras == null ? null
                : extras.getCharSequence(Notification.EXTRA_TEXT);
        String text = safeText(body);
        String key = sbn.getKey();
        return new EventRecord(
                System.currentTimeMillis(), type, sbn.getPackageName(), null,
                title, text, key, sbn.getId(), sbn.getUserId(), removalReason);
    }

    private static Context findSystemContext(Object candidate, int depth) {
        if (candidate == null || depth > 2) return null;
        if (candidate instanceof Context) return (Context) candidate;
        for (Class<?> type = candidate.getClass();
             type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                try {
                    if (Context.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        Object value = field.get(candidate);
                        if (value instanceof Context) return (Context) value;
                    } else if (depth < 2
                            && field.getType().getName().startsWith(NMS)) {
                        field.setAccessible(true);
                        Context nested = findSystemContext(field.get(candidate), depth + 1);
                        if (nested != null) return nested;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Try the next field; context discovery is best-effort.
                }
            }
        }
        return null;
    }

    private static Integer safeInteger(Object value) {
        return value instanceof Integer ? (Integer) value : null;
    }

    private static String safeString(Object value) {
        return value instanceof String ? (String) value : null;
    }

    private static String safeText(CharSequence value) {
        if (value == null) return null;
        try {
            int length = Math.min(value.length(), MAX_TEXT_LENGTH);
            return TextUtils.substring(value, 0, length);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
