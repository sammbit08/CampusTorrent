package edu.campustorrent.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Chooses the next piece to request from one particular peer.
 *
 * A piece is a candidate only if we do not have it, it is not already in progress, and the peer has it.
 * <ul>
 *   <li>SEQUENTIAL: lowest candidate index.</li>
 *   <li>RANDOM: uniformly random candidate (seeded Random, so reproducible).</li>
 *   <li>RAREST_FIRST: candidate with the lowest availability count. Ties are broken deterministically
 *       by scanning indices in rotated order starting at {@code tieBreakOffset}; offset 0 means
 *       "lowest index wins". Giving different peers different offsets spreads their first requests over
 *       different pieces, which is what lets leechers trade with each other.</li>
 * </ul>
 * Owner: Priyanshu Nayak.
 */
public final class PieceSelector {
    private final Strategy strategy;
    private final Random random;
    private final int tieBreakOffset;

    public PieceSelector(Strategy strategy, long seed, int tieBreakOffset) {
        this.strategy = strategy;
        this.random = new Random(seed);
        this.tieBreakOffset = Math.max(0, tieBreakOffset);
    }

    /** @return the chosen piece index, or -1 if there is nothing to request from this peer */
    public synchronized int next(boolean[] have, Set<Integer> inProgress, boolean[] peerHas, int[] availability) {
        int n = have.length;
        switch (strategy) {
            case SEQUENTIAL:
                for (int i = 0; i < n; i++) if (candidate(i, have, inProgress, peerHas)) return i;
                return -1;
            case RANDOM: {
                List<Integer> c = new ArrayList<>();
                for (int i = 0; i < n; i++) if (candidate(i, have, inProgress, peerHas)) c.add(i);
                return c.isEmpty() ? -1 : c.get(random.nextInt(c.size()));
            }
            case RAREST_FIRST:
            default: {
                int best = -1;
                for (int k = 0; k < n; k++) {
                    int i = (k + tieBreakOffset) % n;
                    if (!candidate(i, have, inProgress, peerHas)) continue;
                    if (best < 0 || availability[i] < availability[best]) best = i;
                }
                return best;
            }
        }
    }

    private static boolean candidate(int i, boolean[] have, Set<Integer> inProgress, boolean[] peerHas) {
        return !have[i] && peerHas[i] && !inProgress.contains(i);
    }
}
