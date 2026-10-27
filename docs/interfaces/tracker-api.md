# Interface contract: tracker and choking (provider: Samridhi Padhy)

Consumer: Vabyasha (`peer.Peer`, `peer.Downloader`, `peer.UploadServer`), Sambit (`LocalSwarm`).

```java
// edu.campustorrent.tracker
record PeerAddress(String peerId, String host, int port) {}
final class TrackerServer implements Closeable {
    TrackerServer(String bindHost, int port /*0 = any*/, long ttlMillis);
    void start() throws IOException; int getPort(); PeerRegistry registry(); void close();
}
final class TrackerClient {
    TrackerClient(String host, int port, int timeoutMs);
    void announce(String fileId, String peerId, int listenPort) throws IOException;   // TrackerException on ERR
    List<PeerAddress> getPeers(String fileId, String excludePeerId) throws IOException;
    void leave(String fileId, String peerId) throws IOException;
}
final class PeerRegistry {                   // thread-safe, injectable clock for tests
    PeerRegistry(long ttlMillis, LongSupplier clockMillis);
    boolean announce(String fileId, PeerAddress a); void remove(String fileId, String peerId);
    List<PeerAddress> getPeers(String fileId, String excludePeerId); int purgeExpired();
}
// edu.campustorrent.peer
final class ChokingPolicy {                  // thread-safe, deterministic
    ChokingPolicy(boolean enabled, int regularSlots, int optimisticSlots, long seed, boolean seeding);
    void peerSeen(String peerId);            // an inbound connection opened
    void peerGone(String peerId);            // an inbound connection closed (frees the slot)
    void recordDownloaded(String peerId, long bytes);   // that peer uploaded verified bytes to us
    boolean isUnchoked(String peerId); void setSeeding(boolean s); void recomputeRound();
}
// edu.campustorrent.util
final class Validation { static boolean isValidPeerId(String s); static boolean isValidFileId(String s); static boolean isValidPort(int p); ... }
```

Wire format: see "Tracker protocol" in `docs/protocol.md`.
