package edu.campustorrent.tracker;

import edu.campustorrent.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * TCP tracker: one request per connection, handled on a small thread pool, with a periodic purge of
 * expired peers. Tracker never carries file data; it only lists peers. Owner: Samridhi Padhy.
 */
public final class TrackerServer implements Closeable {
    private static final String TAG = "tracker";
    private static final int CLIENT_TIMEOUT_MS = 5000;

    private final String bindHost;
    private final int requestedPort;
    private final PeerRegistry registry;
    private ServerSocket server;
    private ExecutorService pool;
    private ScheduledExecutorService janitor;
    private volatile boolean running;

    public TrackerServer(String bindHost, int port, long ttlMillis) {
        this.bindHost = bindHost;
        this.requestedPort = port;
        this.registry = new PeerRegistry(ttlMillis);
    }

    /** Binds and starts accepting. Use port 0 to let the OS choose, then call {@link #getPort()}. */
    public void start() throws IOException {
        server = new ServerSocket(requestedPort, 50, InetAddress.getByName(bindHost));
        running = true;
        pool = Executors.newFixedThreadPool(16, daemon("tracker-worker"));
        janitor = Executors.newSingleThreadScheduledExecutor(daemon("tracker-janitor"));
        janitor.scheduleWithFixedDelay(() -> {
            int n = registry.purgeExpired();
            if (n > 0) Log.info(TAG, "expired " + n + " stale peer registration(s)");
        }, 1, 1, TimeUnit.SECONDS);
        Thread acceptor = new Thread(this::acceptLoop, "tracker-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
        Log.info(TAG, "listening on " + bindHost + ":" + getPort());
    }

    public int getPort() { return server.getLocalPort(); }

    public PeerRegistry registry() { return registry; }

    private void acceptLoop() {
        while (running) {
            try {
                Socket s = server.accept();
                pool.execute(() -> handle(s));
            } catch (IOException e) {
                if (running) Log.warn(TAG, "accept failed: " + e.getMessage());
            }
        }
    }

    private void handle(Socket s) {
        try (Socket sock = s) {
            sock.setSoTimeout(CLIENT_TIMEOUT_MS);
            InputStream in = sock.getInputStream();
            OutputStream out = sock.getOutputStream();
            String response;
            try {
                String line = LineIO.readLine(in, TrackerProtocol.MAX_LINE);
                String host = ((java.net.InetSocketAddress) sock.getRemoteSocketAddress()).getAddress().getHostAddress();
                response = TrackerProtocol.handle(line, host, registry);
            } catch (IOException e) {
                response = TrackerProtocol.err("BAD_REQUEST", "could not read request: " + e.getMessage());
            }
            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            Log.debug(TAG, "client error: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        running = false;
        try { if (server != null) server.close(); } catch (IOException ignored) { }
        if (pool != null) pool.shutdownNow();
        if (janitor != null) janitor.shutdownNow();
    }

    private static java.util.concurrent.ThreadFactory daemon(String name) {
        return r -> { Thread t = new Thread(r, name); t.setDaemon(true); return t; };
    }
}
