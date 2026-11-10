package dev.local.toastnotificationlogger.transport;

import dev.local.toastnotificationlogger.model.EventRecord;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Non-blocking, bounded handoff queue for system_server capture callbacks.
 *
 * Producers must use offer(), never put(). A full queue drops the newest event
 * and increments a counter instead of blocking a framework service thread.
 * Only the app-process consumer may perform IPC or persistence.
 */
public final class BoundedEventQueue {
    public static final int DEFAULT_CAPACITY = 256;

    private final ArrayBlockingQueue<EventRecord> queue;
    private final AtomicLong dropped = new AtomicLong();

    public BoundedEventQueue() {
        this(DEFAULT_CAPACITY);
    }

    public BoundedEventQueue(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        queue = new ArrayBlockingQueue<>(capacity);
    }

    public boolean offer(EventRecord event) {
        if (event == null) throw new IllegalArgumentException("event must not be null");
        boolean accepted = queue.offer(event);
        if (!accepted) dropped.incrementAndGet();
        return accepted;
    }

    public EventRecord poll() {
        return queue.poll();
    }

    public EventRecord poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    public int size() {
        return queue.size();
    }

    public int remainingCapacity() {
        return queue.remainingCapacity();
    }

    public long getDroppedCount() {
        return dropped.get();
    }
}
