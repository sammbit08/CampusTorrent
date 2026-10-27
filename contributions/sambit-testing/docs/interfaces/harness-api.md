# Interface contract: test harness (provider: Sambit Kumar Mohapatra)

```java
// edu.campustorrent.experiment
final class LocalSwarm {
    static List<PeerResult> run(Options o, Path workDir) throws Exception;   // tracker + seeders + leechers in one JVM
    static final class Options { int seeders, leechers, freeRiders; long fileSize; int pieceSize; Strategy strategy;
        long uploadBytesPerSec; boolean chokingEnabled; int unchokeSlots, optimisticSlots, roundMillis;
        long timeoutMillis, seedBase; boolean corruptFirstSeeder; }
    record PeerResult(String peerId, String role, boolean freeRider, boolean completed, boolean verified, long durationMs,
        long bytesDownloaded, long bytesUploaded, long pieces, long retries, long hashFailures, long errors, long chokedWaits) {}
}
final class ExperimentRunner { public static void main(String[] args); }    // writes the CSV
final class CsvWriter implements Closeable { CsvWriter(Path file, List<String> header, boolean append); void writeRow(List<String> cells); }
final class SampleFileGenerator { static void generate(Path out, long sizeBytes, long seed); }
```

`verified` means: the downloaded file equals the source byte for byte (`Files.mismatch == -1`). Depends on `Peer`, `TrackerServer`, `FileSplitter` and the interfaces above; it does not modify them.
