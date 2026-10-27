package edu.campustorrent.util;

/**
 * Simple upload throttle used to simulate limited bandwidth on a single laptop
 * (loopback is otherwise so fast that experiments finish in milliseconds).
 * Owner: Vabyasha Mohapatra.
 */
public final class RateLimiter {
    private final long bytesPerSecond;
    private long nextFreeNanos = System.nanoTime();

    /** @param bytesPerSecond limit; 0 or negative means unlimited */
    public RateLimiter(long bytesPerSecond) { this.bytesPerSecond = bytesPerSecond; }

    /** Blocks until sending {@code bytes} more bytes is within the configured rate. */
    public void acquire(long bytes) throws InterruptedException {
        if (bytesPerSecond <= 0) return;
        long waitNanos;
        synchronized (this) {
            long now = System.nanoTime();
            if (nextFreeNanos < now) nextFreeNanos = now;
            waitNanos = nextFreeNanos - now;
            nextFreeNanos += bytes * 1_000_000_000L / bytesPerSecond;
        }
        if (waitNanos > 0) {
            Thread.sleep(waitNanos / 1_000_000L, (int) (waitNanos % 1_000_000L));
        }
    }
}
