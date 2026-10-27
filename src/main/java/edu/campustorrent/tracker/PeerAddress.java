package edu.campustorrent.tracker;

/** Where a peer can be reached. Owner: Samridhi Padhy. */
public record PeerAddress(String peerId, String host, int port) { }
