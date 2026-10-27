package edu.campustorrent.scheduler;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks which pieces each connected remote peer has and derives, per piece, how many peers have it.
 * Thread-safe. Owner: Priyanshu Nayak.
 */
public final class Availability {
    private final int pieceCount;
    private final Map<String, boolean[]> peers = new HashMap<>();

    public Availability(int pieceCount) { this.pieceCount = pieceCount; }

    public synchronized void setBitfield(String peerId, boolean[] bits) {
        if (bits.length != pieceCount) throw new IllegalArgumentException("Bitfield length " + bits.length + " != " + pieceCount);
        peers.put(peerId, bits.clone());
    }

    public synchronized void removePeer(String peerId) { peers.remove(peerId); }

    /** A copy of the peer's bitfield, or all-false if the peer is unknown. */
    public synchronized boolean[] bitfieldOf(String peerId) {
        boolean[] b = peers.get(peerId);
        return b == null ? new boolean[pieceCount] : b.clone();
    }

    /** counts[i] = number of known remote peers that have piece i. */
    public synchronized int[] counts() {
        int[] c = new int[pieceCount];
        for (boolean[] b : peers.values()) {
            for (int i = 0; i < pieceCount; i++) if (b[i]) c[i]++;
        }
        return c;
    }

    public synchronized int peerCount() { return peers.size(); }
}
