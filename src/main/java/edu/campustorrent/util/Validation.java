package edu.campustorrent.util;

import java.util.regex.Pattern;

/**
 * Input validation shared by the tracker, the protocol layer and the peer.
 * Owner: Samridhi Padhy.
 */
public final class Validation {
    private static final Pattern PEER_ID = Pattern.compile("[A-Za-z0-9._-]{1,32}");
    private static final Pattern FILE_ID = Pattern.compile("[0-9a-f]{64}");

    private Validation() { }

    /** A peer id is 1-32 characters from [A-Za-z0-9._-]. */
    public static boolean isValidPeerId(String s) {
        return s != null && PEER_ID.matcher(s).matches();
    }

    /** A file id is the lowercase hex SHA-256 of the manifest (64 hex characters). */
    public static boolean isValidFileId(String s) {
        return s != null && FILE_ID.matcher(s).matches();
    }

    public static boolean isValidPort(int port) {
        return port >= 1 && port <= 65535;
    }

    public static String requirePeerId(String s) {
        if (!isValidPeerId(s)) {
            throw new IllegalArgumentException("Invalid peer id (use 1-32 chars of A-Z a-z 0-9 . _ -): " + s);
        }
        return s;
    }

    public static String requireFileId(String s) {
        if (!isValidFileId(s)) {
            throw new IllegalArgumentException("Invalid file id (expected 64 lowercase hex chars)");
        }
        return s;
    }
}
