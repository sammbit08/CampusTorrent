package edu.campustorrent.pieces;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Owner: Priyanshu Nayak. */
class PieceStoreTest {

    private static Path makeSource(Path dir, int size) throws IOException {
        Path f = dir.resolve("src").resolve("data.bin");
        Files.createDirectories(f.getParent());
        Files.write(f, ManifestAndSplitterTest.randomBytes(size, 77));
        return f;
    }

    @Test
    void leecherAcceptsPiecesInAnyOrderAndReassemblesIdenticalFile(@TempDir Path dir) throws Exception {
        Path src = makeSource(dir, 50_000);
        Manifest m = FileSplitter.createManifest(src, 8192);
        List<byte[]> pieces = FileSplitter.split(src, 8192);
        try (PieceStore store = PieceStore.createLeecher(m, dir.resolve("out"))) {
            for (int i = pieces.size() - 1; i >= 0; i--) {          // worst case: reverse order
                assertFalse(store.isComplete());
                assertEquals(PieceStore.AcceptResult.ACCEPTED, store.acceptPiece(i, pieces.get(i)));
            }
            assertTrue(store.isComplete());
            Path out = store.finish();
            assertEquals(-1L, Files.mismatch(src, out), "output must equal the source byte for byte");
            assertFalse(Files.exists(dir.resolve("out").resolve("data.bin.part")), ".part must be renamed away");
            assertArrayEquals(pieces.get(2), store.readPiece(2), "finished store keeps serving pieces");
        }
    }

    @Test
    void rejectsCorruptedWrongIndexWrongLengthAndDuplicates(@TempDir Path dir) throws Exception {
        Path src = makeSource(dir, 3000);
        Manifest m = FileSplitter.createManifest(src, 1000);
        List<byte[]> pieces = FileSplitter.split(src, 1000);
        try (PieceStore store = PieceStore.createLeecher(m, dir.resolve("out"))) {
            byte[] corrupt = pieces.get(0).clone();
            corrupt[5] ^= 0x7f;
            assertEquals(PieceStore.AcceptResult.BAD_HASH, store.acceptPiece(0, corrupt));
            assertFalse(store.has(0));
            assertEquals(PieceStore.AcceptResult.BAD_HASH, store.acceptPiece(1, pieces.get(0)), "wrongly indexed data");
            assertEquals(PieceStore.AcceptResult.BAD_INDEX, store.acceptPiece(-1, pieces.get(0)));
            assertEquals(PieceStore.AcceptResult.BAD_INDEX, store.acceptPiece(3, pieces.get(0)));
            assertEquals(PieceStore.AcceptResult.BAD_LENGTH, store.acceptPiece(0, new byte[10]));
            assertEquals(0, store.haveCount());
            assertEquals(PieceStore.AcceptResult.ACCEPTED, store.acceptPiece(0, pieces.get(0)));
            assertEquals(PieceStore.AcceptResult.DUPLICATE, store.acceptPiece(0, pieces.get(0)));
            assertEquals(1, store.haveCount());
            assertThrows(IllegalStateException.class, store::finish);
            assertThrows(IOException.class, () -> store.readPiece(1));
        }
    }

    @Test
    void seederVerifiesSourceAndServesPieces(@TempDir Path dir) throws Exception {
        Path src = makeSource(dir, 5000);
        Manifest m = FileSplitter.createManifest(src, 1000);
        try (PieceStore seeder = PieceStore.createSeeder(m, src)) {
            assertTrue(seeder.isComplete());
            assertArrayEquals(FileSplitter.readPiece(src, m, 4), seeder.readPiece(4));
        }
    }

    @Test
    void seederRejectsSourceThatDoesNotMatchManifest(@TempDir Path dir) throws Exception {
        Path src = makeSource(dir, 5000);
        Manifest m = FileSplitter.createManifest(src, 1000);
        byte[] data = Files.readAllBytes(src);
        data[2500] ^= 1;
        Files.write(src, data);
        assertThrows(IOException.class, () -> PieceStore.createSeeder(m, src));
        Files.write(src, new byte[10]);
        assertThrows(IOException.class, () -> PieceStore.createSeeder(m, src));
    }

    @Test
    void reassemblerWritesPiecesInIndexOrderRegardlessOfInsertionOrder(@TempDir Path dir) throws Exception {
        Path src = makeSource(dir, 4500);
        Manifest m = FileSplitter.createManifest(src, 1000);
        List<byte[]> pieces = FileSplitter.split(src, 1000);
        Map<Integer, byte[]> shuffled = new LinkedHashMap<>();
        for (int i : new int[]{4, 0, 3, 1, 2}) shuffled.put(i, pieces.get(i));
        Path out = dir.resolve("rebuilt.bin");
        Reassembler.reassemble(m, shuffled, out);
        assertEquals(-1L, Files.mismatch(src, out));
    }

    @Test
    void reassemblerRefusesMissingOrCorruptPieces(@TempDir Path dir) throws Exception {
        Path src = makeSource(dir, 3000);
        Manifest m = FileSplitter.createManifest(src, 1000);
        List<byte[]> pieces = FileSplitter.split(src, 1000);
        Map<Integer, byte[]> missing = new LinkedHashMap<>();
        missing.put(0, pieces.get(0));
        missing.put(2, pieces.get(2));
        Path out = dir.resolve("rebuilt.bin");
        assertThrows(IOException.class, () -> Reassembler.reassemble(m, missing, out));
        Map<Integer, byte[]> corrupt = new LinkedHashMap<>();
        for (int i = 0; i < 3; i++) corrupt.put(i, pieces.get(i).clone());
        corrupt.get(1)[0] ^= 1;
        assertThrows(IOException.class, () -> Reassembler.reassemble(m, corrupt, out));
        assertFalse(Files.exists(out), "nothing may be written when verification fails");
    }
}
