# Interface contract: pieces and scheduler (provider: Priyanshu Nayak)

Consumers: Vabyasha (`peer` package), Sambit (`experiment`).

```java
// edu.campustorrent.pieces
final class Manifest {
    Manifest(String fileName, long totalSize, int pieceSize, List<byte[]> hashes); // validates, throws IllegalArgumentException
    String fileName(); long totalSize(); int pieceSize(); int pieceCount();
    String fileId(); byte[] fileIdBytes();          // SHA-256 of toText()
    int pieceLength(int i); long pieceOffset(int i); byte[] hash(int i);
    boolean isValidIndex(int i); boolean verifyPiece(int i, byte[] data);  // index + length + SHA-256
    String toText(); void save(Path p); static Manifest load(Path p); static Manifest parse(String text);
}
final class FileSplitter {
    static Manifest createManifest(Path file, int pieceSize) throws IOException;
    static List<byte[]> split(Path file, int pieceSize) throws IOException;      // tests / small files
    static byte[] readPiece(Path file, Manifest m, int index) throws IOException;
}
final class PieceStore implements Closeable {                                    // thread-safe
    static PieceStore createLeecher(Manifest m, Path outputDir) throws IOException;
    static PieceStore createSeeder(Manifest m, Path sourceFile) throws IOException; // verifies every piece
    AcceptResult acceptPiece(int index, byte[] data);   // ACCEPTED | DUPLICATE | BAD_INDEX | BAD_LENGTH | BAD_HASH
    byte[] readPiece(int index); boolean has(int i); boolean[] bitfield(); int haveCount(); boolean isComplete();
    Path finish();            // re-verify whole file, rename name.part -> name; idempotent
    Path outputFile();
}
final class Reassembler { static void reassemble(Manifest m, Map<Integer,byte[]> pieces, Path out) throws IOException; }

// edu.campustorrent.scheduler
enum Strategy { RAREST_FIRST, RANDOM, SEQUENTIAL; static Strategy parse(String s); }
final class Availability {                                                         // thread-safe
    Availability(int pieceCount); void setBitfield(String peerId, boolean[] bits); void removePeer(String peerId);
    boolean[] bitfieldOf(String peerId); int[] counts();
}
final class PieceSelector {
    PieceSelector(Strategy s, long seed, int tieBreakOffset);
    int next(boolean[] have, Set<Integer> inProgress, boolean[] peerHas, int[] availability); // -1 = nothing
}
```

Boundary: Priyanshu owns *what to pick*; the loop that calls `PieceSelector` (`peer/DownloadState`, `peer/Downloader`) belongs to Vabyasha.
