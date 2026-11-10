package dev.local.toastnotificationlogger.model;

/**
 * Immutable event snapshot crossing from the capture layer into app-process storage.
 *
 * This class deliberately contains only primitive and immutable string values.
 * Never retain framework Notification / NotificationRecord / Binder objects here.
 */
public final class EventRecord {
    public final long timestampMillis;
    public final EventType type;
    public final String packageName;
    public final String appName;
    public final String title;
    public final String text;
    public final String notificationKey;
    public final Integer notificationId;
    public final Integer userId;
    public final Integer removalReason;

    public EventRecord(
            long timestampMillis,
            EventType type,
            String packageName,
            String appName,
            String title,
            String text,
            String notificationKey,
            Integer notificationId,
            Integer userId,
            Integer removalReason) {
        if (type == null) throw new IllegalArgumentException("type must not be null");
        this.timestampMillis = timestampMillis;
        this.type = type;
        this.packageName = packageName;
        this.appName = appName;
        this.title = title;
        this.text = text;
        this.notificationKey = notificationKey;
        this.notificationId = notificationId;
        this.userId = userId;
        this.removalReason = removalReason;
    }
}
