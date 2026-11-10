package dev.local.toastnotificationlogger.transport;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import dev.local.toastnotificationlogger.BuildConfig;
import dev.local.toastnotificationlogger.model.EventRecord;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * System-server producer / app-process provider consumer bridge.
 *
 * Hook callbacks only perform a bounded queue offer. Provider IPC and SQLite
 * work happen on this dedicated daemon thread, never on an NMS callback thread.
 */
public final class SystemEventDispatcher {
    private static final String TAG = "ToastNotificationLogger";
    private static final String AUTHORITY_SUFFIX = ".events";
    private static final String METHOD_RECORD = "record_event";
    private static final BoundedEventQueue QUEUE = new BoundedEventQueue(256);
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final AtomicLong TRANSPORT_FAILURES = new AtomicLong();
    private static volatile Context systemContext;

    private SystemEventDispatcher() {}

    public static boolean submit(Context context, EventRecord event) {
        if (context == null || event == null) return false;
        if (systemContext == null) systemContext = context;
        boolean accepted = QUEUE.offer(event);
        if (accepted) startWorker();
        return accepted;
    }

    public static long getQueueDropCount() {
        return QUEUE.getDroppedCount();
    }

    public static long getTransportFailureCount() {
        return TRANSPORT_FAILURES.get();
    }

    private static void startWorker() {
        if (!STARTED.compareAndSet(false, true)) return;
        Thread worker = new Thread(SystemEventDispatcher::runWorker,
                "ToastNotificationLogger-IPC");
        worker.setDaemon(true);
        worker.start();
    }

    private static void runWorker() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                EventRecord event = QUEUE.poll(5, TimeUnit.SECONDS);
                if (event == null) continue;
                Context context = systemContext;
                if (context == null || !send(context, event)) {
                    TRANSPORT_FAILURES.incrementAndGet();
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException failure) {
                TRANSPORT_FAILURES.incrementAndGet();
                Log.e(TAG, "Event transport worker failed", failure);
            }
        }
    }

    private static boolean send(Context context, EventRecord event) {
        ContentResolver resolver = context.getContentResolver();
        Uri uri = Uri.parse("content://" + BuildConfig.APPLICATION_ID + AUTHORITY_SUFFIX);
        Bundle payload = new Bundle();
        payload.putLong("timestamp_ms", event.timestampMillis);
        payload.putString("event_type", event.type.name());
        put(payload, "package_name", event.packageName);
        put(payload, "app_name", event.appName);
        put(payload, "title", event.title);
        put(payload, "text", event.text);
        put(payload, "notification_key", event.notificationKey);
        put(payload, "notification_id", event.notificationId);
        put(payload, "user_id", event.userId);
        put(payload, "removal_reason", event.removalReason);
        try {
            Bundle response = resolver.call(uri, METHOD_RECORD, null, payload);
            return response != null && response.getBoolean("stored", false);
        } catch (RuntimeException failure) {
            Log.w(TAG, "Unable to deliver captured event to app process", failure);
            return false;
        }
    }

    private static void put(Bundle bundle, String key, String value) {
        if (value != null) bundle.putString(key, value);
    }

    private static void put(Bundle bundle, String key, Integer value) {
        if (value != null) bundle.putInt(key, value);
    }
}
