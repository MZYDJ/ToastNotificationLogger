package dev.local.toastnotificationlogger.transport;

import dev.local.toastnotificationlogger.model.EventRecord;
import dev.local.toastnotificationlogger.model.EventType;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class BoundedEventQueueTest {
    private static EventRecord event(long time) {
        return new EventRecord(time, EventType.TOAST_TEXT_REQUESTED,
                "example.app", null, null, "text", null, null, 0, null);
    }

    @Test
    public void dropsNewestWithoutBlockingWhenFull() {
        BoundedEventQueue queue = new BoundedEventQueue(1);
        assertTrue(queue.offer(event(1)));
        assertFalse(queue.offer(event(2)));
        assertEquals(1, queue.size());
        assertEquals(0, queue.remainingCapacity());
        assertEquals(1L, queue.getDroppedCount());
        assertEquals(1L, queue.poll().timestampMillis);
        assertNull(queue.poll());
    }

    @Test
    public void acceptsEventsAfterConsumerDrainsQueue() {
        BoundedEventQueue queue = new BoundedEventQueue(1);
        assertTrue(queue.offer(event(1)));
        assertNotNull(queue.poll());
        assertTrue(queue.offer(event(2)));
        assertEquals(0L, queue.getDroppedCount());
        assertEquals(2L, queue.poll().timestampMillis);
    }
}
