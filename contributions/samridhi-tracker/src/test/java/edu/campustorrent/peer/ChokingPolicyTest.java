package edu.campustorrent.peer;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Owner: Samridhi Padhy. */
class ChokingPolicyTest {

    @Test
    void disabledPolicyUnchokesEveryone() {
        ChokingPolicy p = new ChokingPolicy(false, 1, 0, 0, false);
        assertTrue(p.isUnchoked("anyone"));
    }

    @Test
    void newPeersAreUnchokedOnlyWhileSlotsAreFree() {
        ChokingPolicy p = new ChokingPolicy(true, 2, 1, 0, false);
        for (String id : new String[]{"A", "B", "C", "D"}) p.peerSeen(id);
        assertTrue(p.isUnchoked("A"));
        assertTrue(p.isUnchoked("B"));
        assertTrue(p.isUnchoked("C"));
        assertFalse(p.isUnchoked("D"), "only 3 slots in total");
        assertFalse(p.isUnchoked("stranger"), "unknown peers are choked");
    }

    @Test
    void topContributorsAreUnchokedAndOptimisticSlotRotatesDeterministically() {
        ChokingPolicy p = new ChokingPolicy(true, 1, 1, 0, false);
        for (String id : new String[]{"A", "B", "C", "D"}) p.peerSeen(id);
        p.recordDownloaded("A", 100);
        p.recordDownloaded("B", 50);
        p.recomputeRound();                       // round 1: A by score, optimistic = rest[B,C,D] start 1 -> C
        assertEquals(Set.of("A", "C"), p.unchokedSnapshot());
        p.recomputeRound();                       // round 2: A still best, optimistic start 2 -> D
        assertEquals(Set.of("A", "D"), p.unchokedSnapshot());
    }

    @Test
    void freeRiderOnlyGetsOptimisticSlotsWhileContributorsAlwaysDo() {
        ChokingPolicy p = new ChokingPolicy(true, 2, 1, 0, false);
        for (String id : new String[]{"A", "B", "F", "G", "H"}) p.peerSeen(id);
        Map<String, Integer> unchokedRounds = new HashMap<>();
        for (int round = 0; round < 9; round++) {
            p.recordDownloaded("A", 1000);        // A and B contribute every round
            p.recordDownloaded("B", 500);
            p.recomputeRound();
            for (String id : p.unchokedSnapshot()) unchokedRounds.merge(id, 1, Integer::sum);
        }
        assertEquals(9, (int) unchokedRounds.get("A"));
        assertEquals(9, (int) unchokedRounds.get("B"));
        assertEquals(3, (int) unchokedRounds.get("F"));  // non-contributors share one rotating slot: 9 rounds / 3 peers
        assertEquals(3, (int) unchokedRounds.get("G"));
        assertEquals(3, (int) unchokedRounds.get("H"));
    }

    @Test
    void seederRotatesThroughAllPeers() {
        ChokingPolicy p = new ChokingPolicy(true, 1, 1, 0, true);
        for (String id : new String[]{"P1", "P2", "P3", "P4"}) p.peerSeen(id);
        Set<String> served = new java.util.HashSet<>();
        for (int r = 0; r < 4; r++) {
            p.recomputeRound();
            assertEquals(2, p.unchokedSnapshot().size());
            served.addAll(p.unchokedSnapshot());
        }
        assertEquals(4, served.size());
    }

    @Test
    void disconnectedPeerFreesItsSlotForAWaitingPeer() {
        ChokingPolicy p = new ChokingPolicy(true, 1, 0, 0, false);
        p.peerSeen("A");
        p.peerSeen("B");
        assertTrue(p.isUnchoked("A"));
        assertFalse(p.isUnchoked("B"));
        p.peerGone("A");
        assertFalse(p.isUnchoked("A"));
        assertTrue(p.isUnchoked("B"), "slot must pass to the waiting peer immediately");
        p.recomputeRound();
        assertFalse(p.unchokedSnapshot().contains("A"), "a disconnected peer must never be selected again");
    }

    @Test
    void peerWithTwoConnectionsStaysUntilTheLastOneCloses() {
        ChokingPolicy p = new ChokingPolicy(true, 1, 0, 0, false);
        p.peerSeen("A");
        p.peerSeen("A");
        p.peerGone("A");
        assertTrue(p.isUnchoked("A"));
        p.peerGone("A");
        assertFalse(p.isUnchoked("A"));
    }

    @Test
    void rejectsConfigurationWithoutAnySlot() {
        assertThrows(IllegalArgumentException.class, () -> new ChokingPolicy(true, 0, 0, 0, false));
    }
}
