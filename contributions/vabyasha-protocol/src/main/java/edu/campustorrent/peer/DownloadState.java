package edu.campustorrent.peer;

import edu.campustorrent.pieces.PieceStore;
import edu.campustorrent.scheduler.Availability;
import edu.campustorrent.scheduler.PieceSelector;

import java.util.HashSet;
import java.util.Set;

/**
 * Shared by all download workers of one leecher: hands out pieces so that two workers never fetch the
 * same piece and never fetch one we already have. Owner: Vabyasha Mohapatra (uses Priyanshu's selector).
 */
final class DownloadState {
    private final PieceStore store;
    private final Availability availability;
    private final PieceSelector selector;
    private final Set<Integer> inProgress = new HashSet<>();

    DownloadState(PieceStore store, Availability availability, PieceSelector selector) {
        this.store = store;
        this.availability = availability;
        this.selector = selector;
    }

    /** Picks and reserves the next piece to request from {@code peerId}; -1 if none. */
    synchronized int claim(String peerId) {
        int idx = selector.next(store.bitfield(), inProgress, availability.bitfieldOf(peerId), availability.counts());
        if (idx >= 0) inProgress.add(idx);
        return idx;
    }

    synchronized void release(int index) { inProgress.remove(index); }
}
