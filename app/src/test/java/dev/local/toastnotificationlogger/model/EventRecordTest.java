package dev.local.toastnotificationlogger.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public final class EventRecordTest {
    @Test
    public void preservesImmutableSnapshotFields() {
        EventRecord event = new EventRecord(
                1234L, EventType.TOAST_TEXT_REQUESTED, "example.app", "Example",
                null, "hello", null, null, 0, null);

        assertEquals(1234L, event.timestampMillis);
        assertEquals(EventType.TOAST_TEXT_REQUESTED, event.type);
        assertEquals("example.app", event.packageName);
        assertEquals("hello", event.text);
        assertEquals(Integer.valueOf(0), event.userId);
        assertNull(event.notificationKey);
        assertNull(event.removalReason);
    }

    @Test
    public void rejectsMissingEventType() {
        try {
            new EventRecord(1L, null, null, null, null, null, null, null, null, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertEquals("type must not be null", expected.getMessage());
        }
    }
}
