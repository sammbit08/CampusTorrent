package edu.campustorrent.peer;

import java.util.concurrent.atomic.AtomicLong;

/** Counters a peer keeps about its own run; read by the experiment harness. Owner: Vabyasha Mohapatra. */
public final class PeerMetrics {
    /** Verified piece bytes accepted from other peers. */
    public final AtomicLong bytesDownloaded = new AtomicLong();
    /** Piece bytes sent to other peers. */
    public final AtomicLong bytesUploaded = new AtomicLong();
    public final AtomicLong piecesCompleted = new AtomicLong();
    /** Piece attempts that had to be repeated: hash/length failures and request timeouts. */
    public final AtomicLong retries = new AtomicLong();
    public final AtomicLong hashFailures = new AtomicLong();
    /** Connection failures, protocol violations and ERROR replies. */
    public final AtomicLong errors = new AtomicLong();
    /** Times a request was answered with CHOKE (we had to wait). */
    public final AtomicLong chokedWaits = new AtomicLong();

    private volatile long startNanos;
    private volatile long endNanos;

    public void markStart() { startNanos = System.nanoTime(); }
    public void markEnd() { endNanos = System.nanoTime(); }

    /** Download duration in ms, or -1 if the download has not finished. */
    public long durationMillis() {
        if (startNanos == 0 || endNanos == 0) return -1;
        return (endNanos - startNanos) / 1_000_000L;
    }
}
