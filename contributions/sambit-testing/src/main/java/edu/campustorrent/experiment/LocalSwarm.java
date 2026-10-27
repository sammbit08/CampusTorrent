package edu.campustorrent.experiment;

import edu.campustorrent.peer.Peer;
import edu.campustorrent.peer.PeerConfig;
import edu.campustorrent.pieces.FileSplitter;
import edu.campustorrent.pieces.Manifest;
import edu.campustorrent.scheduler.Strategy;
import edu.campustorrent.tracker.TrackerServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Starts a tracker plus seeders and leechers inside one JVM on ephemeral localhost ports, waits for the
 * leechers, verifies each downloaded file byte-for-byte against the source and returns the measured metrics.
 * Used by the integration tests and by {@link ExperimentRunner}. Owner: Sambit Kumar Mohapatra.
 */
public final class LocalSwarm {
    private LocalSwarm() { }

    public static final class Options {
        public int seeders = 2;
        /** Contributing leechers (they upload to others). */
        public int leechers = 4;
        /** Additional leechers that never upload. */
        public int freeRiders = 0;
        public long fileSize = 2L * 1024 * 1024;
        public int pieceSize = 64 * 1024;
        public Strategy strategy = Strategy.RAREST_FIRST;
        /** Simulated upload bandwidth per peer in bytes/second; 0 = unlimited. */
        public long uploadBytesPerSec = 0;
        public boolean chokingEnabled = true;
        public int unchokeSlots = 2;
        public int optimisticSlots = 1;
        public int roundMillis = 300;
        public long timeoutMillis = 120_000;
        public long seedBase = 1;
        /** Fault injection: the first seeder corrupts every piece it sends. */
        public boolean corruptFirstSeeder = false;
    }

    public record PeerResult(String peerId, String role, boolean freeRider, boolean completed, boolean verified,
                             long durationMs, long bytesDownloaded, long bytesUploaded, long pieces,
                             long retries, long hashFailures, long errors, long chokedWaits) { }

    /** Runs one swarm; every file lives under {@code workDir}. Returns one result per leecher and seeder. */
    public static List<PeerResult> run(Options o, Path workDir) throws Exception {
        Path source = workDir.resolve("source").resolve("swarmfile.bin");
        SampleFileGenerator.generate(source, o.fileSize, 1000 + o.seedBase);
        Manifest manifest = FileSplitter.createManifest(source, o.pieceSize);
        Path manifestPath = workDir.resolve("swarm.manifest");
        manifest.save(manifestPath);

        List<Peer> peers = new ArrayList<>();
        List<Peer> leechers = new ArrayList<>();
        TrackerServer tracker = new TrackerServer("127.0.0.1", 0, 60_000);
        try {
            tracker.start();
            for (int i = 0; i < o.seeders; i++) {
                PeerConfig c = base(o, "S" + (i + 1), manifestPath, tracker.getPort());
                c.role = PeerConfig.Role.SEEDER;
                c.sourceFile = source;
                c.corruptUploads = o.corruptFirstSeeder && i == 0;
                c.seed = o.seedBase * 1000 + 500 + i;
                Peer p = new Peer(c);
                p.start();
                peers.add(p);
            }
            int total = o.leechers + o.freeRiders;
            for (int i = 0; i < total; i++) {
                boolean fr = i >= o.leechers;
                String id = fr ? "FR" + (i - o.leechers + 1) : "L" + (i + 1);
                PeerConfig c = base(o, id, manifestPath, tracker.getPort());
                c.role = PeerConfig.Role.LEECHER;
                c.outputDir = workDir.resolve("out").resolve(id);
                c.freeRider = fr;
                c.strategy = o.strategy;
                c.seed = o.seedBase * 1000 + i + 1;
                Peer p = new Peer(c);
                p.start();
                peers.add(p);
                leechers.add(p);
            }
            long deadline = System.nanoTime() + o.timeoutMillis * 1_000_000L;
            for (Peer l : leechers) {
                long left = Math.max(1, (deadline - System.nanoTime()) / 1_000_000L);
                l.awaitComplete(left);
            }
            List<PeerResult> results = new ArrayList<>();
            for (Peer p : peers) {
                boolean seeder = p.config().role == PeerConfig.Role.SEEDER;
                boolean done = p.isComplete();
                boolean verified = seeder || (done && Files.mismatch(source, p.outputFile()) == -1L);
                var m = p.metrics();
                results.add(new PeerResult(p.config().peerId, seeder ? "seeder" : "leecher", p.config().freeRider,
                        done, verified, seeder ? 0 : m.durationMillis(), m.bytesDownloaded.get(), m.bytesUploaded.get(),
                        m.piecesCompleted.get(), m.retries.get(), m.hashFailures.get(), m.errors.get(), m.chokedWaits.get()));
            }
            return results;
        } finally {
            for (Peer p : peers) p.close();
            tracker.close();
        }
    }

    private static PeerConfig base(Options o, String id, Path manifestPath, int trackerPort) {
        PeerConfig c = new PeerConfig();
        c.peerId = id;
        c.manifestPath = manifestPath;
        c.trackerPort = trackerPort;
        c.uploadBytesPerSec = o.uploadBytesPerSec;
        c.chokingEnabled = o.chokingEnabled;
        c.unchokeSlots = o.unchokeSlots;
        c.optimisticSlots = o.optimisticSlots;
        c.roundMillis = o.roundMillis;
        c.discoveryIntervalMs = 200;
        return c;
    }
}
