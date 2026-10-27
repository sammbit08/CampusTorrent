package edu.campustorrent.scheduler;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Owner: Priyanshu Nayak. */
class PieceSelectorTest {

    private static boolean[] all(int n, boolean v) {
        boolean[] b = new boolean[n];
        java.util.Arrays.fill(b, v);
        return b;
    }

    @Test
    void rarestFirstPicksLowestAvailabilityWithLowestIndexTieBreak() {
        PieceSelector s = new PieceSelector(Strategy.RAREST_FIRST, 0, 0);
        int[] avail = {3, 1, 2, 1};
        assertEquals(1, s.next(all(4, false), new HashSet<>(), all(4, true), avail));
    }

    @Test
    void tieBreakOffsetRotatesWhichTiedPieceWins() {
        int[] avail = {1, 1, 1, 1};
        assertEquals(0, new PieceSelector(Strategy.RAREST_FIRST, 0, 0).next(all(4, false), new HashSet<>(), all(4, true), avail));
        assertEquals(2, new PieceSelector(Strategy.RAREST_FIRST, 0, 2).next(all(4, false), new HashSet<>(), all(4, true), avail));
        assertEquals(3, new PieceSelector(Strategy.RAREST_FIRST, 0, 3).next(all(4, false), new HashSet<>(), all(4, true), avail));
    }

    @Test
    void neverPicksCompleteInProgressOrUnavailableFromPeer() {
        boolean[] have = {true, false, false, false};
        Set<Integer> inProgress = new HashSet<>(Set.of(1));
        boolean[] peerHas = {true, true, false, true};
        int[] avail = {1, 1, 1, 5};
        for (Strategy st : Strategy.values()) {
            PieceSelector s = new PieceSelector(st, 7, 0);
            assertEquals(3, s.next(have, inProgress, peerHas, avail), st.toString());
        }
    }

    @Test
    void returnsMinusOneWhenNothingToRequest() {
        for (Strategy st : Strategy.values()) {
            PieceSelector s = new PieceSelector(st, 1, 0);
            assertEquals(-1, s.next(all(3, true), new HashSet<>(), all(3, true), new int[]{1, 1, 1}));
            assertEquals(-1, s.next(all(3, false), new HashSet<>(), all(3, false), new int[]{0, 0, 0}));
            assertEquals(-1, s.next(all(3, false), new HashSet<>(Set.of(0, 1, 2)), all(3, true), new int[]{1, 1, 1}));
        }
    }

    @Test
    void sequentialAlwaysPicksLowestMissingIndex() {
        PieceSelector s = new PieceSelector(Strategy.SEQUENTIAL, 0, 5);
        boolean[] have = all(6, false);
        for (int expected = 0; expected < 6; expected++) {
            int pick = s.next(have, new HashSet<>(), all(6, true), new int[6]);
            assertEquals(expected, pick);
            have[pick] = true;
        }
    }

    @Test
    void randomIsReproducibleForSameSeedAndOnlyPicksCandidates() {
        PieceSelector a = new PieceSelector(Strategy.RANDOM, 99, 0);
        PieceSelector b = new PieceSelector(Strategy.RANDOM, 99, 0);
        boolean[] haveA = all(20, false);
        boolean[] haveB = all(20, false);
        List<Integer> seqA = new ArrayList<>();
        List<Integer> seqB = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            int pa = a.next(haveA, new HashSet<>(), all(20, true), new int[20]);
            int pb = b.next(haveB, new HashSet<>(), all(20, true), new int[20]);
            assertFalse(haveA[pa], "must not pick a piece we already have");
            haveA[pa] = true;
            haveB[pb] = true;
            seqA.add(pa);
            seqB.add(pb);
        }
        assertEquals(seqA, seqB);
        assertEquals(20, new HashSet<>(seqA).size());
    }

    @Test
    void availabilityCountsAcrossPeersAndForgetsDisconnectedPeers() {
        Availability av = new Availability(3);
        av.setBitfield("A", new boolean[]{true, true, false});
        av.setBitfield("B", new boolean[]{false, true, true});
        assertEquals(2, av.peerCount());
        int[] c = av.counts();
        assertEquals(1, c[0]);
        assertEquals(2, c[1]);
        assertEquals(1, c[2]);
        av.removePeer("A");
        assertEquals(0, av.counts()[0]);
        assertEquals(1, av.counts()[1]);
        assertFalse(av.bitfieldOf("A")[0], "unknown peer has nothing");
        assertThrows(IllegalArgumentException.class, () -> av.setBitfield("C", new boolean[5]));
    }

    @Test
    void strategyParsingAcceptsKnownNamesOnly() {
        assertEquals(Strategy.RAREST_FIRST, Strategy.parse("rarest-first"));
        assertEquals(Strategy.RANDOM, Strategy.parse("RANDOM"));
        assertEquals(Strategy.SEQUENTIAL, Strategy.parse("sequential"));
        assertThrows(IllegalArgumentException.class, () -> Strategy.parse("fastest"));
    }
}
