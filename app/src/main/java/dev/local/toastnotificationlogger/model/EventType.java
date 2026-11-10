package dev.local.toastnotificationlogger.model;

/**
 * Stable event categories persisted by the logger.
 *
 * Keep lifecycle semantics separate: a notification update is not a second
 * independent post, and removal reason is metadata on NOTIFICATION_REMOVED.
 */
public enum EventType {
    TOAST_TEXT_REQUESTED,
    TOAST_CUSTOM_REQUESTED,
    NOTIFICATION_POSTED,
    NOTIFICATION_UPDATED,
    NOTIFICATION_REMOVED
}
