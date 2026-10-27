package edu.campustorrent.tracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Thread-safe registry: file id -> (peer id -> address + last-seen time).
 * Registrations expire after {@code ttlMillis} without a re-announce, which is how crashed or
 * disconnected peers disappear. The clock is injectable so expiry can be tested without sleeping.
 * Owner: Samridhi Padhy.
 */
public final class PeerRegistry {
    public static final int MAX_FILES = 1000;
    public static final int MAX_PEERS_PER_FILE = 500;

    private static final class Entry {
        final PeerAddress address;
        final long lastSeen;
        Entry(PeerAddress address, long lastSeen) { this.address = address; this.lastSeen = lastSeen; }
    }

    private final Map<String, Map<String, Entry>> files = new ConcurrentHashMap<>();
    private final long ttlMillis;
    private final LongSupplier clock;

    public PeerRegistry(long ttlMillis, LongSupplier clockMillis) {
        if (ttlMillis < 1) throw new IllegalArgumentException("ttl must be positive");
        this.ttlMillis = ttlMillis;
        this.clock = clockMillis;
    }

    public PeerRegistry(long ttlMillis) { this(ttlMillis, System::currentTimeMillis); }

    /** Adds or refreshes a registration. Returns false if a size limit would be exceeded. */
    public boolean announce(String fileId, PeerAddress address) {
        if (!files.containsKey(fileId) && files.size() >= MAX_FILES) return false;
        Map<String, Entry> peers = files.computeIfAbsent(fileId, k -> new ConcurrentHashMap<>());
        if (!peers.containsKey(address.peerId()) && peers.size() >= MAX_PEERS_PER_FILE) return false;
        peers.put(address.peerId(), new Entry(address, clock.getAsLong()));
        return true;
    }

    public void remove(String fileId, String peerId) {
        Map<String, Entry> peers = files.get(fileId);
        if (peers != null) {
            peers.remove(peerId);
            if (peers.isEmpty()) files.remove(fileId, peers);
        }
    }

    /** Live (non-expired) peers for a file, sorted by peer id, optionally excluding the asker. */
    public List<PeerAddress> getPeers(String fileId, String excludePeerId) {
        Map<String, Entry> peers = files.get(fileId);
        List<PeerAddress> out = new ArrayList<>();
        if (peers == null) return out;
        long now = clock.getAsLong();
        for (Entry e : peers.values()) {
            if (now - e.lastSeen > ttlMillis) continue;
            if (e.address.peerId().equals(excludePeerId)) continue;
            out.add(e.address);
        }
        out.sort(Comparator.comparing(PeerAddress::peerId));
        return out;
    }

    /** Removes expired entries; returns how many were removed. */
    public int purgeExpired() {
        long now = clock.getAsLong();
        int removed = 0;
        for (Map.Entry<String, Map<String, Entry>> fe : files.entrySet()) {
            Map<String, Entry> peers = fe.getValue();
            for (Map.Entry<String, Entry> pe : peers.entrySet()) {
                if (now - pe.getValue().lastSeen > ttlMillis && peers.remove(pe.getKey(), pe.getValue())) removed++;
            }
            if (peers.isEmpty()) files.remove(fe.getKey(), peers);
        }
        return removed;
    }

    public int countPeers(String fileId) { return getPeers(fileId, null).size(); }
}
