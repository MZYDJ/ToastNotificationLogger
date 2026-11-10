package dev.local.toastnotificationlogger.model;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class EventTypeTest {
    @Test
    public void eventTypesHaveStablePersistedNames() {
        assertEquals(EventType.TOAST_TEXT_REQUESTED,
                EventType.valueOf("TOAST_TEXT_REQUESTED"));
        assertEquals(EventType.NOTIFICATION_UPDATED,
                EventType.valueOf("NOTIFICATION_UPDATED"));
        assertEquals(EventType.NOTIFICATION_REMOVED,
                EventType.valueOf("NOTIFICATION_REMOVED"));
    }
}
