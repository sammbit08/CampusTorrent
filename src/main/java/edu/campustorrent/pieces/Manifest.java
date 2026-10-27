package edu.campustorrent.pieces;

import edu.campustorrent.util.PathSafety;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Metainfo ("torrent") for one file: name, size, piece size, piece count and a SHA-256 hash per piece.
 * The file id is the SHA-256 of the canonical manifest text, so peers that share a file id agree
 * on every piece hash. Immutable. Owner: Priyanshu Nayak.
 *
 * Text format (UTF-8, one entry per line):
 * <pre>
 * CAMPUSTORRENT-MANIFEST 1
 * name=notes.pdf
 * size=123456
 * pieceSize=65536
 * pieceCount=2
 * hash.0=&lt;64 hex chars&gt;
 * hash.1=&lt;64 hex chars&gt;
 * </pre>
 */
public final class Manifest {
    public static final int MAX_PIECE_SIZE = 4 * 1024 * 1024;
    public static final long MAX_FILE_SIZE = 512L * 1024 * 1024;
    public static final int MAX_PIECE_COUNT = 100_000;
    private static final long MAX_MANIFEST_BYTES = 16L * 1024 * 1024;
    private static final String HEADER = "CAMPUSTORRENT-MANIFEST 1";

    private final String fileName;
    private final long totalSize;
    private final int pieceSize;
    private final int pieceCount;
    private final byte[][] hashes;
    private final String text;
    private final String fileId;

    /** @throws IllegalArgumentException if any field is inconsistent or outside the safety limits */
    public Manifest(String fileName, long totalSize, int pieceSize, List<byte[]> pieceHashes) {
        this.fileName = PathSafety.sanitizeFileName(fileName);
        if (totalSize < 1 || totalSize > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size must be 1.." + MAX_FILE_SIZE + " bytes: " + totalSize);
        }
        if (pieceSize < 1 || pieceSize > MAX_PIECE_SIZE) {
            throw new IllegalArgumentException("Piece size must be 1.." + MAX_PIECE_SIZE + " bytes: " + pieceSize);
        }
        long count = (totalSize + pieceSize - 1) / pieceSize;
        if (count > MAX_PIECE_COUNT) {
            throw new IllegalArgumentException("Too many pieces: " + count + " (max " + MAX_PIECE_COUNT + ")");
        }
        if (pieceHashes == null || pieceHashes.size() != count) {
            throw new IllegalArgumentException("Expected " + count + " piece hashes");
        }
        this.totalSize = totalSize;
        this.pieceSize = pieceSize;
        this.pieceCount = (int) count;
        this.hashes = new byte[pieceCount][];
        for (int i = 0; i < pieceCount; i++) {
            byte[] h = pieceHashes.get(i);
            if (h == null || h.length != PieceHasher.HASH_LENGTH) {
                throw new IllegalArgumentException("Hash " + i + " must be 32 bytes");
            }
            hashes[i] = h.clone();
        }
        this.text = buildText();
        this.fileId = PieceHasher.toHex(PieceHasher.sha256(text.getBytes(StandardCharsets.UTF_8)));
    }

    private String buildText() {
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append('\n');
        sb.append("name=").append(fileName).append('\n');
        sb.append("size=").append(totalSize).append('\n');
        sb.append("pieceSize=").append(pieceSize).append('\n');
        sb.append("pieceCount=").append(pieceCount).append('\n');
        for (int i = 0; i < pieceCount; i++) {
            sb.append("hash.").append(i).append('=').append(PieceHasher.toHex(hashes[i])).append('\n');
        }
        return sb.toString();
    }

    public String fileName() { return fileName; }
    public long totalSize() { return totalSize; }
    public int pieceSize() { return pieceSize; }
    public int pieceCount() { return pieceCount; }
    public String fileId() { return fileId; }
    public byte[] fileIdBytes() { return PieceHasher.fromHex(fileId); }
    public byte[] hash(int index) { checkIndex(index); return hashes[index].clone(); }
    public String toText() { return text; }

    public boolean isValidIndex(int index) { return index >= 0 && index < pieceCount; }

    private void checkIndex(int index) {
        if (!isValidIndex(index)) {
            throw new IndexOutOfBoundsException("Piece index " + index + " outside 0.." + (pieceCount - 1));
        }
    }

    /** Length of piece {@code index}; the last piece may be shorter than the piece size. */
    public int pieceLength(int index) {
        checkIndex(index);
        if (index < pieceCount - 1) return pieceSize;
        return (int) (totalSize - (long) pieceSize * (pieceCount - 1));
    }

    public long pieceOffset(int index) {
        checkIndex(index);
        return (long) pieceSize * index;
    }

    /** True only if the index is valid, the length is right and the SHA-256 matches the manifest. */
    public boolean verifyPiece(int index, byte[] data) {
        if (!isValidIndex(index) || data == null || data.length != pieceLength(index)) return false;
        return MessageDigest.isEqual(PieceHasher.sha256(data), hashes[index]);
    }

    public void save(Path path) throws IOException {
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }

    public static Manifest load(Path path) throws IOException {
        if (Files.size(path) > MAX_MANIFEST_BYTES) {
            throw new IOException("Manifest file too large: " + path);
        }
        return parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    /** Strict parser; any deviation from the format throws IllegalArgumentException. */
    public static Manifest parse(String text) {
        String[] lines = text.split("\n", -1);
        int n = 0;
        if (lines.length < 6 || !HEADER.equals(lines[n++])) {
            throw new IllegalArgumentException("Not a CampusTorrent manifest (bad header)");
        }
        String name = value(lines[n++], "name");
        long size = parseLong(value(lines[n++], "size"), "size");
        int pieceSize = (int) Math.min(Integer.MAX_VALUE, parseLong(value(lines[n++], "pieceSize"), "pieceSize"));
        long declaredCount = parseLong(value(lines[n++], "pieceCount"), "pieceCount");
        if (declaredCount < 1 || declaredCount > MAX_PIECE_COUNT) {
            throw new IllegalArgumentException("Bad pieceCount: " + declaredCount);
        }
        List<byte[]> hashes = new ArrayList<>();
        for (int i = 0; i < declaredCount; i++) {
            if (n >= lines.length) throw new IllegalArgumentException("Manifest truncated at hash " + i);
            String hex = value(lines[n++], "hash." + i);
            if (hex.length() != 64) throw new IllegalArgumentException("Bad hash length at " + i);
            try {
                hashes.add(PieceHasher.fromHex(hex));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Bad hash hex at " + i);
            }
        }
        for (; n < lines.length; n++) {
            if (!lines[n].isEmpty()) throw new IllegalArgumentException("Unexpected trailing line in manifest");
        }
        Manifest m = new Manifest(name, size, pieceSize, hashes);
        if (m.pieceCount != declaredCount) {
            throw new IllegalArgumentException("pieceCount does not match size/pieceSize");
        }
        return m;
    }

    private static String value(String line, String key) {
        String prefix = key + "=";
        if (!line.startsWith(prefix)) throw new IllegalArgumentException("Expected '" + prefix + "' but got: " + line);
        return line.substring(prefix.length());
    }

    private static long parseLong(String s, String what) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Bad number for " + what + ": " + s);
        }
    }
}
