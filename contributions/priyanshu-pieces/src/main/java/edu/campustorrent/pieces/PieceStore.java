package edu.campustorrent.pieces;

import edu.campustorrent.util.PathSafety;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Thread-safe storage of verified pieces for one file.
 * A leecher writes accepted pieces into "name.part" at their correct offsets (this is what keeps
 * pieces in the correct order regardless of arrival order) and renames it when everything has
 * been verified. A seeder verifies its source file against the manifest and serves from it.
 * Owner: Priyanshu Nayak.
 */
public final class PieceStore implements Closeable {

    public enum AcceptResult { ACCEPTED, DUPLICATE, BAD_INDEX, BAD_LENGTH, BAD_HASH }

    private final Manifest manifest;
    private final boolean[] have;
    private int haveCount;
    private Path partFile;     // null for seeders / after finish
    private Path finalFile;
    private RandomAccessFile raf;

    private PieceStore(Manifest manifest) {
        this.manifest = manifest;
        this.have = new boolean[manifest.pieceCount()];
    }

    /** Creates an empty store that will download into {@code outputDir/<manifest name>}. */
    public static PieceStore createLeecher(Manifest manifest, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        PieceStore s = new PieceStore(manifest);
        s.finalFile = PathSafety.resolveInside(outputDir, manifest.fileName());
        s.partFile = PathSafety.resolveInside(outputDir, manifest.fileName() + ".part");
        s.raf = new RandomAccessFile(s.partFile.toFile(), "rw");
        s.raf.setLength(0);
        return s;
    }

    /** Opens a complete source file; every piece is hashed and must match the manifest. */
    public static PieceStore createSeeder(Manifest manifest, Path sourceFile) throws IOException {
        if (!Files.isRegularFile(sourceFile) || Files.size(sourceFile) != manifest.totalSize()) {
            throw new IOException("Source file missing or has wrong size for manifest: " + sourceFile);
        }
        PieceStore s = new PieceStore(manifest);
        s.finalFile = sourceFile;
        s.raf = new RandomAccessFile(sourceFile.toFile(), "r");
        try {
            for (int i = 0; i < manifest.pieceCount(); i++) {
                if (!manifest.verifyPiece(i, FileSplitter.readPiece(s.raf, manifest, i))) {
                    throw new IOException("Source file does not match manifest at piece " + i);
                }
            }
        } catch (IOException e) {
            s.raf.close();
            throw e;
        }
        java.util.Arrays.fill(s.have, true);
        s.haveCount = manifest.pieceCount();
        return s;
    }

    public Manifest manifest() { return manifest; }

    /** Verifies index, length and SHA-256; only a fully verified piece is written. */
    public synchronized AcceptResult acceptPiece(int index, byte[] data) throws IOException {
        if (!manifest.isValidIndex(index)) return AcceptResult.BAD_INDEX;
        if (data == null || data.length != manifest.pieceLength(index)) return AcceptResult.BAD_LENGTH;
        if (have[index]) return AcceptResult.DUPLICATE;
        if (partFile == null) return AcceptResult.DUPLICATE; // read-only store (seeder or finished)
        if (!manifest.verifyPiece(index, data)) return AcceptResult.BAD_HASH;
        raf.seek(manifest.pieceOffset(index));
        raf.write(data);
        have[index] = true;
        haveCount++;
        return AcceptResult.ACCEPTED;
    }

    /** Returns piece bytes for upload; throws if we do not have the piece. */
    public synchronized byte[] readPiece(int index) throws IOException {
        if (!manifest.isValidIndex(index) || !have[index]) {
            throw new IOException("Piece not available: " + index);
        }
        return FileSplitter.readPiece(raf, manifest, index);
    }

    public synchronized boolean has(int index) {
        return manifest.isValidIndex(index) && have[index];
    }

    public synchronized boolean[] bitfield() { return have.clone(); }
    public synchronized int haveCount() { return haveCount; }
    public synchronized boolean isComplete() { return haveCount == manifest.pieceCount(); }

    /**
     * Once all pieces are present: re-verifies the whole file from disk, renames "name.part" to the
     * final name and re-opens it read-only so this peer can keep seeding. Idempotent.
     */
    public synchronized Path finish() throws IOException {
        if (!isComplete()) throw new IllegalStateException("Download is not complete");
        if (partFile == null) return finalFile;
        for (int i = 0; i < manifest.pieceCount(); i++) {
            if (!manifest.verifyPiece(i, FileSplitter.readPiece(raf, manifest, i))) {
                throw new IOException("Final verification failed at piece " + i);
            }
        }
        raf.close();
        Files.move(partFile, finalFile, StandardCopyOption.REPLACE_EXISTING);
        partFile = null;
        raf = new RandomAccessFile(finalFile.toFile(), "r");
        return finalFile;
    }

    public Path outputFile() { return finalFile; }

    @Override
    public synchronized void close() throws IOException {
        if (raf != null) raf.close();
    }
}
