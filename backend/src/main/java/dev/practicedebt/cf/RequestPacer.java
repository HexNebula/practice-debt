package dev.practicedebt.cf;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Enforces a minimum wall-clock gap between outbound calls.
 *
 * <p>Codeforces rate-limits per IP, so the gate is process-wide and deliberately dumb: no token
 * bucket, no burst allowance. Bursting is exactly the behaviour that gets an IP a 403, and the
 * mirror has no deadline that a burst would help meet.
 */
public class RequestPacer {

    private final long minIntervalNanos;

    /** Earliest {@link System#nanoTime()} at which the next call may leave. */
    private long nextSlotNanos = System.nanoTime();

    public RequestPacer(Duration minInterval) {
        this.minIntervalNanos = Math.max(0, minInterval.toNanos());
    }

    /**
     * Blocks until this caller owns the next slot.
     *
     * @throws InterruptedException if interrupted while waiting; the interrupt flag is restored
     *                              by the caller's handler, not swallowed here
     */
    public void acquire() throws InterruptedException {
        long waitNanos;
        synchronized (this) {
            long now = System.nanoTime();
            // nanoTime is monotonic but can wrap; comparing the difference handles that.
            long slot = (nextSlotNanos - now > 0) ? nextSlotNanos : now;
            waitNanos = slot - now;
            nextSlotNanos = slot + minIntervalNanos;
        }
        if (waitNanos > 0) {
            TimeUnit.NANOSECONDS.sleep(waitNanos);
        }
    }
}
