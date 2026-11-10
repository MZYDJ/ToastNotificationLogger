# Performance and resource impact

## What runs on the system-server callback thread

For each captured Toast or notification lifecycle event, the hook:

1. Lets the original framework method run first.
2. Copies a bounded snapshot of the fields needed for history (package, type, timestamp, notification key/ID, title/text and user/reason where available).
3. Offers the snapshot to a fixed-capacity queue (256 entries).
4. Returns without waiting for the app process, SQLite, or disk.

The queue uses non-blocking `offer()`. If it is full, the newest event is dropped and a counter is incremented; the framework callback is not made to wait for storage.

## Work on a separate daemon thread

A single daemon worker waits on the queue and performs ContentProvider IPC to the logger app process. SQLite insertion and exclusion checks happen in that app process. When the phone is idle, the worker blocks in a timed queue poll rather than continuously spinning.

## Expected costs

- Idle: one daemon thread blocked on the queue; no periodic polling loop or scheduled wake-up is used.
- Per event: a small amount of CPU for reflection/field extraction, text snapshotting, object allocation, and a non-blocking queue offer.
- Storage: one SQLite row per retained event plus indexes; storage grows with event volume and retention policy.
- Bursts: queue capacity is 256. If the provider is unavailable or slower than the event rate, events can be dropped rather than blocking system_server.

## Important performance caveat

The current implementation deoptimizes the NMS synthetic post bridge and its 13-argument `enqueueNotificationInternal` caller to prevent ART inlining from bypassing the hooked post callback. Deoptimizing this caller may reduce JIT optimization for a hot notification path. This is a deliberate compatibility workaround, not a measured zero-cost operation.

The actual CPU, battery, memory, notification latency, and dropped-event impact have **not** yet been benchmarked on the OPPO PKT110 / ColorOS 16.1.0. The first device acceptance pass should include normal use, notification bursts, and before/after system responsiveness checks. If measurable impact appears, the first optimization target is to replace broad caller deoptimization with a narrower hook strategy verified against this firmware.

## Current limits

The module has no periodic background service and does not continuously scan apps or notifications. It records events only when the corresponding framework hooks run. It does not currently expose queue depth/drop counters in the UI.
