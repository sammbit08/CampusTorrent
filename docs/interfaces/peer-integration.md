# Interface contract: peer integration (integration owner: Vabyasha Mohapatra)

`Peer` is the single entry point used by the CLI, the tests and the experiment harness.

```java
// edu.campustorrent.peer
final class PeerConfig { /* public fields with defaults; validate(); static fromArgs(Args) */ }
final class Peer implements Closeable {
    Peer(PeerConfig cfg) throws IOException;          // loads the manifest, validates the config
    void start() throws IOException;                  // seeder: verifies source file; leecher: starts discovery + workers
    boolean awaitComplete(long timeoutMillis);        // true once the file is complete AND verified
    boolean isComplete(); int getPort(); Path outputFile(); PeerMetrics metrics(); ChokingPolicy choking();
    void close();                                     // idempotent; sends LEAVE to the tracker
}
final class PeerMetrics { AtomicLong bytesDownloaded, bytesUploaded, piecesCompleted, retries, hashFailures, errors, chokedWaits; long durationMillis(); }
```

Wiring (in `Peer.start`): `PieceStore` <- `Manifest`; `ChokingPolicy`; `UploadServer(store, policy)`; for leechers `Availability` + `PieceSelector` + `DownloadState` + `Downloader(tracker)`.
`trackerPort = 0` disables the tracker (seeders only; used by protocol tests).
