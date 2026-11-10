package dev.local.toastnotificationlogger.storage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import dev.local.toastnotificationlogger.model.EventRecord;
import dev.local.toastnotificationlogger.model.EventType;

import java.util.ArrayList;
import java.util.List;

/**
 * App-process-only SQLite persistence.
 *
 * Capture hooks must never call this class from system_server. Events must first
 * cross the bounded asynchronous transport and be persisted by the app process.
 */
public final class EventDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "toast_notification_events.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_EVENTS = "events";
    private static final String COL_ID = "_id";
    private static final String COL_TIME = "timestamp_ms";
    private static final String COL_TYPE = "event_type";
    private static final String COL_PACKAGE = "package_name";
    private static final String COL_APP = "app_name";
    private static final String COL_TITLE = "title";
    private static final String COL_TEXT = "text";
    private static final String COL_KEY = "notification_key";
    private static final String COL_NOTIFICATION_ID = "notification_id";
    private static final String COL_USER_ID = "user_id";
    private static final String COL_REMOVAL_REASON = "removal_reason";

    public EventDatabase(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_EVENTS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_TIME + " INTEGER NOT NULL, "
                + COL_TYPE + " TEXT NOT NULL, "
                + COL_PACKAGE + " TEXT, "
                + COL_APP + " TEXT, "
                + COL_TITLE + " TEXT, "
                + COL_TEXT + " TEXT, "
                + COL_KEY + " TEXT, "
                + COL_NOTIFICATION_ID + " INTEGER, "
                + COL_USER_ID + " INTEGER, "
                + COL_REMOVAL_REASON + " INTEGER"
                + ")");
        db.execSQL("CREATE INDEX idx_events_time ON " + TABLE_EVENTS
                + "(" + COL_TIME + " DESC)");
        db.execSQL("CREATE INDEX idx_events_package_time ON " + TABLE_EVENTS
                + "(" + COL_PACKAGE + ", " + COL_TIME + " DESC)");
        db.execSQL("CREATE INDEX idx_events_type_time ON " + TABLE_EVENTS
                + "(" + COL_TYPE + ", " + COL_TIME + " DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Schema migrations must be additive and preserve user history.
        // No destructive fallback is permitted here.
        throw new IllegalStateException("Unsupported database upgrade: "
                + oldVersion + " -> " + newVersion);
    }

    public long insert(EventRecord event) {
        if (event == null) throw new IllegalArgumentException("event must not be null");
        ContentValues values = new ContentValues();
        values.put(COL_TIME, event.timestampMillis);
        values.put(COL_TYPE, event.type.name());
        putNullable(values, COL_PACKAGE, event.packageName);
        putNullable(values, COL_APP, event.appName);
        putNullable(values, COL_TITLE, event.title);
        putNullable(values, COL_TEXT, event.text);
        putNullable(values, COL_KEY, event.notificationKey);
        putNullable(values, COL_NOTIFICATION_ID, event.notificationId);
        putNullable(values, COL_USER_ID, event.userId);
        putNullable(values, COL_REMOVAL_REASON, event.removalReason);
        return getWritableDatabase().insertOrThrow(TABLE_EVENTS, null, values);
    }

    public List<EventRecord> queryRecent(
            int limit, String packageName, EventType type, long fromMillis, long toMillis) {
        if (limit < 1) throw new IllegalArgumentException("limit must be positive");
        StringBuilder selection = new StringBuilder(COL_TIME + " >= ? AND " + COL_TIME + " <= ?");
        List<String> args = new ArrayList<>();
        args.add(Long.toString(fromMillis));
        args.add(Long.toString(toMillis));
        if (packageName != null && !packageName.isEmpty()) {
            selection.append(" AND ").append(COL_PACKAGE).append(" = ?");
            args.add(packageName);
        }
        if (type != null) {
            selection.append(" AND ").append(COL_TYPE).append(" = ?");
            args.add(type.name());
        }

        List<EventRecord> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                TABLE_EVENTS, null, selection.toString(), args.toArray(new String[0]),
                null, null, COL_TIME + " DESC, " + COL_ID + " DESC",
                Integer.toString(limit))) {
            while (cursor.moveToNext()) {
                result.add(new EventRecord(
                        cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIME)),
                        EventType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(COL_TYPE))),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_PACKAGE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_APP)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_TEXT)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_KEY)),
                        nullableInt(cursor, COL_NOTIFICATION_ID),
                        nullableInt(cursor, COL_USER_ID),
                        nullableInt(cursor, COL_REMOVAL_REASON)));
            }
        }
        return result;
    }

    public int deleteOlderThan(long cutoffMillis) {
        return getWritableDatabase().delete(
                TABLE_EVENTS, COL_TIME + " < ?", new String[]{Long.toString(cutoffMillis)});
    }

    public int deleteAll() {
        return getWritableDatabase().delete(TABLE_EVENTS, null, null);
    }

    private static void putNullable(ContentValues values, String key, String value) {
        if (value == null) values.putNull(key); else values.put(key, value);
    }

    private static void putNullable(ContentValues values, String key, Integer value) {
        if (value == null) values.putNull(key); else values.put(key, value);
    }

    private static Integer nullableInt(Cursor cursor, String column) {
        int index = cursor.getColumnIndexOrThrow(column);
        return cursor.isNull(index) ? null : cursor.getInt(index);
    }
}
