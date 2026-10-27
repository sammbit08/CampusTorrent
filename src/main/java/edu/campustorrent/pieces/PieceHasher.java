package edu.campustorrent.pieces;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 helpers. A new MessageDigest is created per call because MessageDigest is not thread-safe. Owner: Priyanshu Nayak. */
public final class PieceHasher {
    public static final int HASH_LENGTH = 32;

    private PieceHasher() { }

    public static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }

    public static byte[] sha256(byte[] data) {
        return sha256(data, 0, data.length);
    }

    public static byte[] sha256(byte[] data, int offset, int length) {
        MessageDigest md = newDigest();
        md.update(data, offset, length);
        return md.digest();
    }

    public static String toHex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    public static byte[] fromHex(String hex) {
        return HexFormat.of().parseHex(hex);
    }
}
