package edu.campustorrent.peer;

import edu.campustorrent.pieces.Manifest;
import edu.campustorrent.pieces.PieceStore;
import edu.campustorrent.scheduler.Availability;
import edu.campustorrent.scheduler.PieceSelector;
import edu.campustorrent.tracker.TrackerClient;
import edu.campustorrent.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.SplittableRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One CampusTorrent peer (seeder or leecher): wires together the piece store, upload server,
 * downloader, choking policy and tracker client. Owner: Vabyasha Mohapatra (integration owner).
 *
 * Lifecycle: {@code new Peer(cfg)} -> {@code start()} -> {@code awaitComplete(ms)} (leechers) -> {@code close()}.
 * After a leecher completes it keeps uploading (it is now a seeder) until {@code close()}.
 */
public final class Peer implements Closeable {
    private final PeerConfig cfg;
    private final Manifest manifest;
    private final PeerMetrics metrics = new PeerMetrics();
    private final CountDownLatch complete = new CountDownLatch(1);
    private final AtomicBoolean finished = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    private PieceStore store;
    private ChokingPolicy policy;
    private ServerSocket serverSocket;
    private UploadServer uploads;
    private Downloader downloader;
    private TrackerClient tracker;
    private ScheduledExecutorService timers;

    public Peer(PeerConfig cfg) throws IOException {
        cfg.validate();
        this.cfg = cfg;
        this.manifest = Manifest.load(cfg.manifestPath);
    }

    public void start() throws IOException {
        boolean seeder = cfg.role == PeerConfig.Role.SEEDER;
        store = seeder ? PieceStore.createSeeder(manifest, cfg.sourceFile)
                       : PieceStore.createLeecher(manifest, cfg.outputDir);
        policy = new ChokingPolicy(cfg.chokingEnabled, cfg.unchokeSlots, cfg.optimisticSlots, cfg.seed, seeder);
        serverSocket = new ServerSocket(cfg.port, 50, InetAddress.getByName(cfg.bindHost));
        uploads = new UploadServer(cfg, manifest, store, policy, metrics, serverSocket);
        uploads.start();

        timers = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, cfg.peerId + "-timer");
            t.setDaemon(true);
            return t;
        });
        timers.scheduleWithFixedDelay(policy::recomputeRound, cfg.roundMillis, cfg.roundMillis, TimeUnit.MILLISECONDS);

        if (cfg.trackerPort != 0) {
            tracker = new TrackerClient(cfg.trackerHost, cfg.trackerPort, cfg.connectTimeoutMs);
            announce();
            timers.scheduleWithFixedDelay(this::announce, cfg.announceIntervalMs, cfg.announceIntervalMs, TimeUnit.MILLISECONDS);
        }

        Log.info(cfg.peerId, cfg.role + " listening on " + cfg.bindHost + ":" + getPort()
                + " file=" + manifest.fileName() + " pieces=" + manifest.pieceCount()
                + (seeder ? "" : " strategy=" + cfg.strategy.label()) + (cfg.freeRider ? " FREE-RIDER" : ""));

        if (seeder) {
            complete.countDown();
        } else {
            metrics.markStart();
            Availability availability = new Availability(manifest.pieceCount());
            // SplittableRandom (not java.util.Random): Random gives near-identical first values for consecutive seeds,
            // which would make all leechers start at the same piece and defeat the tie-break rotation.
            int offset = cfg.seed == 0 ? 0 : new SplittableRandom(cfg.seed).nextInt(manifest.pieceCount());
            PieceSelector selector = new PieceSelector(cfg.strategy, cfg.seed, offset);
            DownloadState state = new DownloadState(store, availability, selector);
            downloader = new Downloader(cfg, manifest, store, availability, state, policy, metrics, tracker, this::onDownloadComplete);
            downloader.start();
            timers.scheduleWithFixedDelay(() -> {
                if (!finished.get()) {
                    Log.info(cfg.peerId, "progress " + store.haveCount() + "/" + manifest.pieceCount() + " pieces");
                }
            }, 2, 2, TimeUnit.SECONDS);
        }
    }

    private void announce() {
        try {
            tracker.announce(manifest.fileId(), cfg.peerId, getPort());
        } catch (IOException e) {
            metrics.errors.incrementAndGet();
            Log.warn(cfg.peerId, "announce failed: " + e.getMessage());
        }
    }

    private void onDownloadComplete() {
        if (!finished.compareAndSet(false, true)) return;
        try {
            Path out = store.finish();
            metrics.markEnd();
            policy.setSeeding(true);
            Log.info(cfg.peerId, "download complete and verified -> " + out + " in " + metrics.durationMillis() + " ms");
            complete.countDown();
        } catch (IOException e) {
            Log.error(cfg.peerId, "final verification failed: " + e.getMessage());
        }
    }

    /** Waits until the file is complete and verified (always true immediately for seeders). */
    public boolean awaitComplete(long timeoutMillis) throws InterruptedException {
        return complete.await(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public boolean isComplete() { return complete.getCount() == 0; }
    public int getPort() { return serverSocket.getLocalPort(); }
    public PeerMetrics metrics() { return metrics; }
    public PeerConfig config() { return cfg; }
    public Manifest manifest() { return manifest; }
    public Path outputFile() { return store.outputFile(); }
    public ChokingPolicy choking() { return policy; }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (downloader != null) downloader.stop();
        if (tracker != null) {
            try { tracker.leave(manifest.fileId(), cfg.peerId); } catch (IOException ignored) { /* tracker may be gone */ }
        }
        if (uploads != null) uploads.stop();
        if (timers != null) timers.shutdownNow();
        try { if (store != null) store.close(); } catch (IOException ignored) { }
    }
}
