package dev.local.toastnotificationlogger.storage;

import android.Manifest;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import dev.local.toastnotificationlogger.model.EventRecord;
import dev.local.toastnotificationlogger.model.EventType;

/**
 * Private-to-system event ingestion endpoint.
 *
 * The provider is exported only so system_server can reach it. Calls are
 * restricted to callers holding android.permission.DUMP (system UID on AOSP
 * builds); the same check is repeated here instead of trusting manifest policy.
 */
public final class EventCaptureProvider extends ContentProvider {
    private EventDatabase database;

    @Override
    public boolean onCreate() {
        database = new EventDatabase(getContext());
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!"record_event".equals(method)) return super.call(method, arg, extras);
        if (getContext() == null
                || getContext().checkCallingPermission(Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Caller is not allowed to submit system events");
        }
        if (extras == null) throw new IllegalArgumentException("Missing event payload");

        EventType type;
        try {
            type = EventType.valueOf(extras.getString("event_type", ""));
        } catch (IllegalArgumentException invalidType) {
            throw new IllegalArgumentException("Unknown event type", invalidType);
        }

        String packageName = extras.getString("package_name");
        Set<String> excluded = new HashSet<>(getContext().getSharedPreferences(
                "logger_config", android.content.Context.MODE_PRIVATE)
                .getStringSet("excluded_packages", Collections.emptySet()));
        Bundle result = new Bundle();
        if (packageName != null && excluded.contains(packageName)) {
            result.putBoolean("stored", true);
            result.putBoolean("excluded", true);
            return result;
        }

        EventRecord event = new EventRecord(
                extras.getLong("timestamp_ms"),
                type,
                packageName,
                extras.getString("app_name"),
                extras.getString("title"),
                extras.getString("text"),
                extras.getString("notification_key"),
                nullableInt(extras, "notification_id"),
                nullableInt(extras, "user_id"),
                nullableInt(extras, "removal_reason"));
        database.insert(event);
        result.putBoolean("stored", true);
        result.putBoolean("excluded", false);
        return result;
    }

    private static Integer nullableInt(Bundle bundle, String key) {
        return bundle.containsKey(key) ? bundle.getInt(key) : null;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection,
                                String[] selectionArgs) { return 0; }
}
