package edu.campustorrent.peer;

import edu.campustorrent.pieces.Manifest;
import edu.campustorrent.pieces.PieceStore;
import edu.campustorrent.protocol.ErrorCode;
import edu.campustorrent.protocol.Message;
import edu.campustorrent.protocol.MessageType;
import edu.campustorrent.protocol.PeerConnection;
import edu.campustorrent.protocol.ProtocolException;
import edu.campustorrent.util.Log;
import edu.campustorrent.util.RateLimiter;

import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The listening side of a peer: accepts connections, performs the handshake and answers
 * GET_BITFIELD / REQUEST messages. Applies the choking policy and the simulated upload bandwidth limit.
 * One thread per connection. Owner: Vabyasha Mohapatra.
 */
final class UploadServer {
    private static final int MAX_INBOUND = 64;

    private final PeerConfig cfg;
    private final Manifest manifest;
    private final PieceStore store;
    private final ChokingPolicy policy;
    private final PeerMetrics metrics;
    private final ServerSocket serverSocket;
    private final RateLimiter limiter;
    private final String tag;
    private final AtomicInteger active = new AtomicInteger();
    private final ExecutorService pool;
    private volatile boolean running = true;

    UploadServer(PeerConfig cfg, Manifest manifest, PieceStore store, ChokingPolicy policy,
                 PeerMetrics metrics, ServerSocket serverSocket) {
        this.cfg = cfg;
        this.manifest = manifest;
        this.store = store;
        this.policy = policy;
        this.metrics = metrics;
        this.serverSocket = serverSocket;
        this.limiter = new RateLimiter(cfg.uploadBytesPerSec);
        this.tag = cfg.peerId + "/upload";
        this.pool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, tag);
            t.setDaemon(true);
            return t;
        });
    }

    void start() {
        Thread t = new Thread(this::acceptLoop, tag + "-accept");
        t.setDaemon(true);
        t.start();
    }

    void stop() {
        running = false;
        try { serverSocket.close(); } catch (IOException ignored) { }
        pool.shutdownNow();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket s = serverSocket.accept();
                if (active.get() >= MAX_INBOUND) {
                    s.close();
                    continue;
                }
                pool.execute(() -> handle(s));
            } catch (IOException e) {
                if (running) Log.warn(tag, "accept failed: " + e.getMessage());
            }
        }
    }

    private boolean allowed(String remoteId) {
        return !cfg.freeRider && policy.isUnchoked(remoteId);
    }

    private void handle(Socket socket) {
        active.incrementAndGet();
        PeerConnection conn = null;
        String remote = "?";
        boolean registered = false;
        try {
            conn = new PeerConnection(socket, cfg.socketTimeoutMs);
            Message.Handshake hs = conn.handshake(manifest.fileIdBytes(), cfg.peerId);
            remote = hs.peerId();
            if (remote.equals(cfg.peerId)) throw new ProtocolException("Refusing connection from myself");
            policy.peerSeen(remote);
            registered = true;
            conn.send(Message.bitfield(store.bitfield()));
            conn.send(allowed(remote) ? Message.unchoke() : Message.choke());
            while (running) {
                Message m = conn.receive();
                switch (m.type()) {
                    case REQUEST:
                        serveRequest(conn, remote, m.asIndex());
                        break;
                    case GET_BITFIELD:
                        conn.send(Message.bitfield(store.bitfield()));
                        break;
                    case SHUTDOWN:
                        return;
                    default:
                        conn.send(Message.error(ErrorCode.BAD_REQUEST, "unexpected " + m.type()));
                        return;
                }
            }
        } catch (EOFException | SocketException e) {
            Log.debug(tag, "peer " + remote + " disconnected");
        } catch (SocketTimeoutException e) {
            Log.debug(tag, "peer " + remote + " timed out");
        } catch (ProtocolException e) {
            metrics.errors.incrementAndGet();
            Log.warn(tag, "protocol error from " + remote + ": " + e.getMessage());
            if (conn != null) try { conn.send(Message.error(ErrorCode.BAD_REQUEST, "protocol error")); } catch (IOException ignored) { }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            Log.warn(tag, "I/O error with " + remote + ": " + e.getMessage());
        } finally {
            if (registered) policy.peerGone(remote);
            if (conn != null) conn.close(); else try { socket.close(); } catch (IOException ignored) { }
            active.decrementAndGet();
        }
    }

    private void serveRequest(PeerConnection conn, String remote, int index) throws IOException, InterruptedException {
        if (!manifest.isValidIndex(index)) {
            metrics.errors.incrementAndGet();
            conn.send(Message.error(ErrorCode.BAD_INDEX, "piece index out of range"));
            return;
        }
        if (!store.has(index)) {
            conn.send(Message.error(ErrorCode.NOT_HAVE, "piece not available"));
            return;
        }
        if (!allowed(remote)) {
            conn.send(Message.choke());
            return;
        }
        byte[] data = store.readPiece(index);
        if (cfg.corruptUploads) data[0] ^= 0x55;
        limiter.acquire(data.length);
        conn.send(Message.piece(index, data));
        metrics.bytesUploaded.addAndGet(data.length);
        Log.debug(tag, "sent piece " + index + " to " + remote);
    }
}
