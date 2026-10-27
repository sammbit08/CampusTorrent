package edu.campustorrent.pieces;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Splits a file into fixed-size pieces and builds its manifest. Owner: Priyanshu Nayak. */
public final class FileSplitter {
    private FileSplitter() { }

    /** Streams through the file once, hashing each piece, and returns the manifest. */
    public static Manifest createManifest(Path file, int pieceSize) throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("Not a regular file: " + file);
        long size = Files.size(file);
        if (pieceSize < 1 || pieceSize > Manifest.MAX_PIECE_SIZE) {
            throw new IllegalArgumentException("Piece size must be 1.." + Manifest.MAX_PIECE_SIZE);
        }
        List<byte[]> hashes = new ArrayList<>();
        long total = 0;
        try (InputStream in = Files.newInputStream(file)) {
            while (true) {
                byte[] buf = in.readNBytes(pieceSize);
                if (buf.length == 0) break;
                hashes.add(PieceHasher.sha256(buf));
                total += buf.length;
            }
        }
        if (total != size) throw new IOException("File changed while reading: " + file);
        return new Manifest(file.getFileName().toString(), size, pieceSize, hashes);
    }

    /** Reads all pieces into memory. Intended for tests and small files only. */
    public static List<byte[]> split(Path file, int pieceSize) throws IOException {
        List<byte[]> pieces = new ArrayList<>();
        try (InputStream in = Files.newInputStream(file)) {
            while (true) {
                byte[] buf = in.readNBytes(pieceSize);
                if (buf.length == 0) break;
                pieces.add(buf);
            }
        }
        return pieces;
    }

    /** Reads piece {@code index} from a complete source file. */
    public static byte[] readPiece(Path file, Manifest manifest, int index) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            return readPiece(raf, manifest, index);
        }
    }

    static byte[] readPiece(RandomAccessFile raf, Manifest manifest, int index) throws IOException {
        byte[] buf = new byte[manifest.pieceLength(index)];
        raf.seek(manifest.pieceOffset(index));
        raf.readFully(buf);
        return buf;
    }
}
