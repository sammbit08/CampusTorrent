package edu.campustorrent.pieces;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Rebuilds a file from an in-memory set of pieces, in index order, verifying every piece. Owner: Priyanshu Nayak. */
public final class Reassembler {
    private Reassembler() { }

    /**
     * @param pieces map from piece index to piece bytes (insertion order does not matter)
     * @throws IOException if a piece is missing or fails verification; nothing is left at {@code out} in that case
     */
    public static void reassemble(Manifest manifest, Map<Integer, byte[]> pieces, Path out) throws IOException {
        for (int i = 0; i < manifest.pieceCount(); i++) {
            byte[] p = pieces.get(i);
            if (p == null) throw new IOException("Missing piece " + i);
            if (!manifest.verifyPiece(i, p)) throw new IOException("Piece " + i + " failed verification");
        }
        Path tmp = out.resolveSibling(out.getFileName() + ".tmp");
        try (OutputStream os = Files.newOutputStream(tmp)) {
            for (int i = 0; i < manifest.pieceCount(); i++) os.write(pieces.get(i));
        }
        Files.move(tmp, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
