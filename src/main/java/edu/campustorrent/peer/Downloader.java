package edu.campustorrent.peer;

import edu.campustorrent.pieces.Manifest;
import edu.campustorrent.pieces.PieceStore;
import edu.campustorrent.protocol.ErrorCode;
import edu.campustorrent.protocol.Message;
import edu.campustorrent.protocol.MessageType;
import edu.campustorrent.protocol.PeerConnection;
import edu.campustorrent.protocol.ProtocolException;
import edu.campustorrent.scheduler.Availability;
import edu.campustorrent.tracker.PeerAddress;
import edu.campustorrent.tracker.TrackerClient;
import edu.campustorrent.util.Log;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Leecher side. A discovery thread asks the tracker for peers; for every new peer a worker thread
 * connects, handshakes, learns the peer's bitfield and then repeatedly claims a piece from
 * {@link DownloadState}, requests it, verifies it and stores it. Owner: Vabyasha Mohapatra.
 */
final class Downloader {
    private static final long NORMAL_COOLDOWN_MS = 500;
    private static final long BAD_PEER_COOLDOWN_MS = 5000;
    private static final int MAX_HASH_FAILURES_PER_CONNECTION = 3;
    private static final long BITFIELD_REFRESH_MS = 200;

    private final PeerConfig cfg;
    private final Manifest manifest;
    private final PieceStore store;
    private final Availability availability;
    private final DownloadState state;
    private final ChokingPolicy policy;
    private final PeerMetrics metrics;
    private final TrackerClient tracker;
    private final Runnable onComplete;
    private final String tag;

    private final Set<String> active = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> cooldownUntil = new ConcurrentHashMap<>();
    private final ExecutorService workers;
    private volatile boolean running = true;
    private Thread discoveryThread;

    Downloader(PeerConfig cfg, Manifest manifest, PieceStore store, Availability availability, DownloadState state,
               ChokingPolicy policy, PeerMetrics metrics, TrackerClient tracker, Runnable onComplete) {
        this.cfg = cfg;
        this.manifest = manifest;
        this.store = store;
        this.availability = availability;
        this.state = state;
        this.policy = policy;
        this.metrics = metrics;
        this.tracker = tracker;
        this.onComplete = onComplete;
        this.tag = cfg.peerId + "/download";
        this.workers = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, tag);
            t.setDaemon(true);
            return t;
        });
    }

    void start() {
        discoveryThread = new Thread(this::discoveryLoop, tag + "-discovery");
        discoveryThread.setDaemon(true);
        discoveryThread.start();
    }

    void stop() {
        running = false;
        workers.shutdownNow();
        if (discoveryThread != null) discoveryThread.interrupt();
    }

    private void discoveryLoop() {
        while (running && !store.isComplete()) {
            try {
                List<PeerAddress> peers = tracker.getPeers(manifest.fileId(), cfg.peerId);
                long now = System.currentTimeMillis();
                for (PeerAddress p : peers) {
                    if (p.peerId().equals(cfg.peerId)) continue;
                    if (cooldownUntil.getOrDefault(p.peerId(), 0L) > now) continue;
                    if (active.add(p.peerId())) {
                        try {
                            workers.execute(() -> talkTo(p));
                        } catch (RuntimeException rejected) {
                            active.remove(p.peerId());
                        }
                    }
                }
            } catch (IOException e) {
                metrics.errors.incrementAndGet();
                Log.warn(tag, "tracker query failed: " + e.getMessage());
            }
            try {
                Thread.sleep(cfg.discoveryIntervalMs);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void talkTo(PeerAddress addr) {
        String remoteId = addr.peerId();
        int claimed = -1;
        long cooldown = NORMAL_COOLDOWN_MS;
        PeerConnection conn = null;
        try {
            conn = PeerConnection.connect(addr.host(), addr.port(), cfg.connectTimeoutMs, cfg.socketTimeoutMs);
            Message.Handshake hs = conn.handshake(manifest.fileIdBytes(), cfg.peerId);
            remoteId = hs.peerId();
            availability.setBitfield(remoteId, conn.receive().asBitfield(manifest.pieceCount()));
            MessageType status = conn.receive().type();
            if (status != MessageType.CHOKE && status != MessageType.UNCHOKE) {
                throw new ProtocolException("Expected CHOKE/UNCHOKE status but got " + status);
            }
            Log.debug(tag, "connected to " + remoteId);

            long lastRefresh = System.currentTimeMillis();
            int hashFailures = 0;
            while (running && !store.isComplete()) {
                // Keep this peer's bitfield fresh even while we are busy: rarest-first is only as good
                // as the availability counts it works from.
                if (System.currentTimeMillis() - lastRefresh >= BITFIELD_REFRESH_MS) {
                    refreshBitfield(conn, remoteId);
                    lastRefresh = System.currentTimeMillis();
                }
                claimed = state.claim(remoteId);
                if (claimed < 0) {
                    Thread.sleep(60);
                    continue;
                }
                conn.send(Message.request(claimed));
                Message resp = conn.receive();
                switch (resp.type()) {
                    case PIECE: {
                        Message.PiecePayload p = resp.asPiece();
                        if (p.index() != claimed) {
                            throw new ProtocolException("Peer sent piece " + p.index() + " but " + claimed + " was requested");
                        }
                        PieceStore.AcceptResult r = store.acceptPiece(claimed, p.data());
                        if (r == PieceStore.AcceptResult.ACCEPTED) {
                            metrics.bytesDownloaded.addAndGet(p.data().length);
                            metrics.piecesCompleted.incrementAndGet();
                            policy.recordDownloaded(remoteId, p.data().length);
                            Log.debug(tag, "piece " + claimed + " verified from " + remoteId
                                    + " (" + store.haveCount() + "/" + manifest.pieceCount() + ")");
                        } else if (r != PieceStore.AcceptResult.DUPLICATE) {
                            metrics.retries.incrementAndGet();
                            if (r == PieceStore.AcceptResult.BAD_HASH) metrics.hashFailures.incrementAndGet();
                            Log.warn(tag, "rejected piece " + claimed + " from " + remoteId + ": " + r);
                            if (++hashFailures >= MAX_HASH_FAILURES_PER_CONNECTION) {
                                cooldown = BAD_PEER_COOLDOWN_MS;
                                throw new ProtocolException("Peer " + remoteId + " keeps sending invalid pieces");
                            }
                        }
                        state.release(claimed);
                        claimed = -1;
                        if (store.isComplete()) onComplete.run();
                        break;
                    }
                    case CHOKE:
                        metrics.chokedWaits.incrementAndGet();
                        state.release(claimed);
                        claimed = -1;
                        Thread.sleep(Math.max(50, cfg.roundMillis / 4));
                        break;
                    case ERROR: {
                        Message.ErrorPayload e = resp.asError();
                        metrics.errors.incrementAndGet();
                        Log.warn(tag, remoteId + " answered " + ErrorCode.name(e.code()) + ": " + e.reason());
                        state.release(claimed);
                        claimed = -1;
                        if (e.code() == ErrorCode.NOT_HAVE) refreshBitfield(conn, remoteId);
                        else throw new ProtocolException("Remote error " + ErrorCode.name(e.code()));
                        break;
                    }
                    default:
                        throw new ProtocolException("Unexpected " + resp.type() + " in reply to REQUEST");
                }
            }
            try { conn.send(Message.shutdown()); } catch (IOException ignored) { }
        } catch (SocketTimeoutException e) {
            if (running) {
                metrics.retries.incrementAndGet();
                metrics.errors.incrementAndGet();
                Log.warn(tag, "timeout talking to " + remoteId);
            }
        } catch (ProtocolException e) {
            metrics.errors.incrementAndGet();
            if (cooldown < BAD_PEER_COOLDOWN_MS) cooldown = BAD_PEER_COOLDOWN_MS;
            Log.warn(tag, "protocol error with " + remoteId + ": " + e.getMessage());
        } catch (EOFException | SocketException e) {
            if (running) {
                metrics.errors.incrementAndGet();
                Log.debug(tag, "connection to " + remoteId + " ended: " + e.getMessage());
            }
        } catch (IOException e) {
            if (running) {
                metrics.errors.incrementAndGet();
                Log.warn(tag, "I/O error with " + remoteId + ": " + e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (claimed >= 0) state.release(claimed);
            availability.removePeer(remoteId);
            if (conn != null) conn.close();
            cooldownUntil.put(addr.peerId(), System.currentTimeMillis() + cooldown);
            active.remove(addr.peerId());
        }
    }

    private void refreshBitfield(PeerConnection conn, String remoteId) throws IOException {
        conn.send(Message.getBitfield());
        availability.setBitfield(remoteId, conn.receive().asBitfield(manifest.pieceCount()));
    }
}
