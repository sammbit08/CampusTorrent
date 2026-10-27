package edu.campustorrent.pieces;

import static org.junit.jupiter.api.Assertions.*;

import edu.campustorrent.util.PathSafety;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Owner: Priyanshu Nayak. */
class ManifestAndSplitterTest {

    static byte[] randomBytes(int n, long seed) {
        byte[] b = new byte[n];
        new Random(seed).nextBytes(b);
        return b;
    }

    @Test
    void splitProducesFixedSizePiecesWithShortLastPiece(@TempDir Path dir) throws Exception {
        byte[] data = randomBytes(10_000, 1);
        Path f = dir.resolve("a.bin");
        Files.write(f, data);
        List<byte[]> pieces = FileSplitter.split(f, 4096);
        assertEquals(3, pieces.size());
        assertEquals(4096, pieces.get(0).length);
        assertEquals(4096, pieces.get(1).length);
        assertEquals(10_000 - 2 * 4096, pieces.get(2).length);
        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        for (byte[] p : pieces) joined.write(p);
        assertArrayEquals(data, joined.toByteArray());
    }

    @Test
    void manifestContainsNameSizePieceSizeCountAndSha256PerPiece(@TempDir Path dir) throws Exception {
        byte[] data = randomBytes(10_000, 2);
        Path f = dir.resolve("notes.pdf");
        Files.write(f, data);
        Manifest m = FileSplitter.createManifest(f, 4096);
        assertEquals("notes.pdf", m.fileName());
        assertEquals(10_000L, m.totalSize());
        assertEquals(4096, m.pieceSize());
        assertEquals(3, m.pieceCount());
        List<byte[]> pieces = FileSplitter.split(f, 4096);
        for (int i = 0; i < 3; i++) {
            assertArrayEquals(PieceHasher.sha256(pieces.get(i)), m.hash(i));
            assertEquals(pieces.get(i).length, m.pieceLength(i));
            assertEquals(4096L * i, m.pieceOffset(i));
        }
    }

    @Test
    void sha256MatchesKnownVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                PieceHasher.toHex(PieceHasher.sha256("abc".getBytes())));
    }

    @Test
    void manifestSurvivesSaveAndLoadWithSameFileId(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("x.bin");
        Files.write(f, randomBytes(5000, 3));
        Manifest m = FileSplitter.createManifest(f, 1000);
        Path mf = dir.resolve("x.manifest");
        m.save(mf);
        Manifest back = Manifest.load(mf);
        assertEquals(m.fileId(), back.fileId());
        assertEquals(m.toText(), back.toText());
        assertEquals(5, back.pieceCount());
    }

    @Test
    void fileIdChangesWhenContentChanges(@TempDir Path dir) throws Exception {
        byte[] data = randomBytes(5000, 4);
        Path f = dir.resolve("a.bin");
        Files.write(f, data);
        String id1 = FileSplitter.createManifest(f, 1000).fileId();
        data[4999] ^= 1;
        Files.write(f, data);
        assertFalse(id1.equals(FileSplitter.createManifest(f, 1000).fileId()));
    }

    @Test
    void verifyPieceRejectsCorruptionWrongIndexAndWrongLength(@TempDir Path dir) throws Exception {
        byte[] data = randomBytes(3000, 5);
        Path f = dir.resolve("a.bin");
        Files.write(f, data);
        Manifest m = FileSplitter.createManifest(f, 1000);
        byte[] p0 = FileSplitter.readPiece(f, m, 0);
        byte[] p1 = FileSplitter.readPiece(f, m, 1);
        assertTrue(m.verifyPiece(0, p0));
        assertFalse(m.verifyPiece(1, p0), "piece 0 data must not pass as piece 1");
        assertFalse(m.verifyPiece(0, p1));
        byte[] bad = p0.clone();
        bad[10] ^= 0x01;
        assertFalse(m.verifyPiece(0, bad));
        assertFalse(m.verifyPiece(0, new byte[999]));
        assertFalse(m.verifyPiece(-1, p0));
        assertFalse(m.verifyPiece(3, p0));
        assertFalse(m.verifyPiece(0, null));
    }

    @Test
    void parseRejectsMalformedManifests(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("a.bin");
        Files.write(f, randomBytes(3000, 6));
        String good = FileSplitter.createManifest(f, 1000).toText();
        assertThrows(IllegalArgumentException.class, () -> Manifest.parse("garbage"));
        assertThrows(IllegalArgumentException.class, () -> Manifest.parse(good.replace("MANIFEST 1", "MANIFEST 9")));
        assertThrows(IllegalArgumentException.class, () -> Manifest.parse(good.replace("pieceCount=3", "pieceCount=4")));
        assertThrows(IllegalArgumentException.class, () -> Manifest.parse(good.replace("size=3000", "size=9000")));
        assertThrows(IllegalArgumentException.class, () -> Manifest.parse(good.replace("name=a.bin", "name=../evil.bin")));
        assertThrows(IllegalArgumentException.class, () -> Manifest.parse(good.substring(0, good.length() - 70)));
        assertThrows(IllegalArgumentException.class, () -> Manifest.parse(good + "extra=1\n"));
        assertThrows(IllegalArgumentException.class, () -> Manifest.parse(good.replaceFirst("hash.0=[0-9a-f]{2}", "hash.0=zz")));
    }

    @Test
    void constructorEnforcesLimits() {
        List<byte[]> one = List.of(new byte[32]);
        assertThrows(IllegalArgumentException.class, () -> new Manifest("a.bin", 0, 10, one));
        assertThrows(IllegalArgumentException.class, () -> new Manifest("a.bin", 10, 0, one));
        assertThrows(IllegalArgumentException.class, () -> new Manifest("a.bin", 10, Manifest.MAX_PIECE_SIZE + 1, one));
        assertThrows(IllegalArgumentException.class, () -> new Manifest("a.bin", 100, 10, one));
        assertThrows(IllegalArgumentException.class, () -> new Manifest("a.bin", 10, 10, List.of(new byte[5])));
        assertThrows(IllegalArgumentException.class, () -> new Manifest("../a.bin", 10, 10, one));
        List<byte[]> tooMany = new ArrayList<>();
        assertThrows(IllegalArgumentException.class, () -> new Manifest("a.bin", Manifest.MAX_PIECE_COUNT + 1L, 1, tooMany));
    }

    @Test
    void pathSafetyAcceptsPlainNamesAndRejectsTraversal(@TempDir Path dir) {
        assertEquals("notes.pdf", PathSafety.sanitizeFileName("notes.pdf"));
        assertEquals("My Notes-1.txt", PathSafety.sanitizeFileName("My Notes-1.txt"));
        for (String bad : new String[]{"../x", "a/b", "a\\b", ".hidden", "", "con.txt", "x..y", "trailing.", "a\u0000b", "C:evil"}) {
            assertThrows(IllegalArgumentException.class, () -> PathSafety.sanitizeFileName(bad), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> PathSafety.sanitizeFileName(null));
        Path p = PathSafety.resolveInside(dir, "ok.bin");
        assertEquals(dir.toAbsolutePath().normalize().resolve("ok.bin"), p);
        assertThrows(IllegalArgumentException.class, () -> PathSafety.resolveInside(dir, "../ok.bin"));
    }
}
