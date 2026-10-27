package edu.campustorrent.peer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Simple, deterministic reciprocity policy (NOT full BitTorrent tit-for-tat).
 *
 * <ul>
 *   <li>Every remote peer that connects to us is "seen". We only serve piece requests from
 *       peers that are currently <i>unchoked</i>; everyone else gets a CHOKE reply.</li>
 *   <li>Each round (recomputeRound) a leecher gives each peer a score = score/2 + bytes that peer
 *       uploaded to us during the round. The top {@code regularSlots} peers with a positive score are
 *       unchoked (reciprocity), plus {@code optimisticSlots} peers chosen from the rest by deterministic
 *       rotation (so newcomers and free-riders still get an occasional chance).</li>
 *   <li>A seeder has nothing to gain from anyone, so it rotates through all peers, serving
 *       regularSlots + optimisticSlots at a time.</li>
 *   <li>Only currently connected peers are candidates. When a peer disconnects ({@link #peerGone}) its slot
 *       is freed at once and handed to a waiting connected peer.</li>
 *   <li>A newly seen peer is unchoked immediately if there is a free slot.</li>
 *   <li>If choking is disabled everyone is always unchoked (used as the baseline in experiments).</li>
 * </ul>
 * The rotation start is (round + seed) mod candidates, so a run is reproducible for the same event
 * sequence. Owner: Samridhi Padhy.
 */
public final class ChokingPolicy {
    private final boolean enabled;
    private final int regularSlots;
    private final int optimisticSlots;
    private final long seed;

    private final Map<String, Double> score = new HashMap<>();
    private final Map<String, Long> roundBytes = new HashMap<>();
    private final Map<String, Integer> connections = new HashMap<>();
    private final Set<String> unchoked = new HashSet<>();
    private boolean seeding;
    private long round;

    public ChokingPolicy(boolean enabled, int regularSlots, int optimisticSlots, long seed, boolean seeding) {
        if (regularSlots < 0 || optimisticSlots < 0 || regularSlots + optimisticSlots < 1) {
            throw new IllegalArgumentException("Need at least one unchoke slot");
        }
        this.enabled = enabled;
        this.regularSlots = regularSlots;
        this.optimisticSlots = optimisticSlots;
        this.seed = seed;
        this.seeding = seeding;
    }

    public synchronized void setSeeding(boolean seeding) { this.seeding = seeding; }

    /** Called when a remote peer opens a connection to us. */
    public synchronized void peerSeen(String peerId) {
        connections.merge(peerId, 1, Integer::sum);
        score.putIfAbsent(peerId, 0.0);
        roundBytes.putIfAbsent(peerId, 0L);
        if (!unchoked.contains(peerId) && unchoked.size() < regularSlots + optimisticSlots) unchoked.add(peerId);
    }

    /** Called when one of that peer's connections to us has ended; frees its slot when the last one closes. */
    public synchronized void peerGone(String peerId) {
        Integer n = connections.get(peerId);
        if (n == null) return;
        if (n > 1) {
            connections.put(peerId, n - 1);
            return;
        }
        connections.remove(peerId);
        if (unchoked.remove(peerId)) {
            List<String> waiting = new ArrayList<>(connections.keySet());
            waiting.sort(Comparator.naturalOrder());
            for (String id : waiting) {
                if (unchoked.size() >= regularSlots + optimisticSlots) break;
                unchoked.add(id);
            }
        }
    }

    /** Called when {@code peerId} uploaded {@code bytes} of verified data to us. */
    public synchronized void recordDownloaded(String peerId, long bytes) {
        roundBytes.merge(peerId, bytes, Long::sum);
        score.putIfAbsent(peerId, 0.0);
    }

    public synchronized boolean isUnchoked(String peerId) {
        return !enabled || unchoked.contains(peerId);
    }

    public synchronized Set<String> unchokedSnapshot() { return new HashSet<>(unchoked); }

    /** Recomputes the unchoked set. Called by a timer in the peer; also called directly by tests. */
    public synchronized void recomputeRound() {
        round++;
        for (Map.Entry<String, Double> e : score.entrySet()) {
            long b = roundBytes.getOrDefault(e.getKey(), 0L);
            e.setValue(e.getValue() / 2.0 + b);
            roundBytes.put(e.getKey(), 0L);
        }
        Set<String> next = new HashSet<>();
        List<String> ids = new ArrayList<>(connections.keySet());
        if (seeding) {
            ids.sort(Comparator.naturalOrder());
            pickRotating(ids, regularSlots + optimisticSlots, next);
        } else {
            List<String> byScore = new ArrayList<>(ids);
            byScore.sort(Comparator.comparing((String id) -> -score.get(id)).thenComparing(Comparator.naturalOrder()));
            for (String id : byScore) {
                if (next.size() >= regularSlots) break;
                if (score.get(id) > 0) next.add(id);
            }
            List<String> rest = new ArrayList<>();
            for (String id : byScore) if (!next.contains(id)) rest.add(id);
            rest.sort(Comparator.naturalOrder());
            pickRotating(rest, optimisticSlots, next);
        }
        unchoked.clear();
        unchoked.addAll(next);
    }

    private void pickRotating(List<String> candidates, int count, Set<String> out) {
        int n = candidates.size();
        if (n == 0) return;
        int start = (int) Math.floorMod(round + seed, (long) n);
        for (int k = 0; k < Math.min(count, n); k++) out.add(candidates.get((start + k) % n));
    }
}
