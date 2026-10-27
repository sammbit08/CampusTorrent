package edu.campustorrent.tracker;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Owner: Samridhi Padhy. */
class TrackerTest {
    private static final String FILE = "a".repeat(64);
    private static final String OTHER_FILE = "b".repeat(64);

    @Test
    void registryReturnsPeersExcludingTheAskerSortedById() {
        PeerRegistry r = new PeerRegistry(10_000, new AtomicLong(0)::get);
        assertTrue(r.announce(FILE, new PeerAddress("p2", "127.0.0.1", 2002)));
        assertTrue(r.announce(FILE, new PeerAddress("p1", "127.0.0.1", 2001)));
        assertTrue(r.announce(OTHER_FILE, new PeerAddress("p3", "127.0.0.1", 2003)));
        List<PeerAddress> peers = r.getPeers(FILE, "p2");
        assertEquals(1, peers.size());
        assertEquals("p1", peers.get(0).peerId());
        assertEquals(2, r.getPeers(FILE, null).size());
        assertEquals("p1", r.getPeers(FILE, null).get(0).peerId());
        assertEquals(0, r.getPeers("c".repeat(64), null).size());
    }

    @Test
    void staleRegistrationsExpireAndReannounceRefreshesThem() {
        AtomicLong now = new AtomicLong(1000);
        PeerRegistry r = new PeerRegistry(500, now::get);
        r.announce(FILE, new PeerAddress("old", "h", 1));
        r.announce(FILE, new PeerAddress("fresh", "h", 2));
        now.set(1400);
        r.announce(FILE, new PeerAddress("fresh", "h", 2));   // heartbeat
        now.set(1700);                                          // "old" is 700 ms stale, "fresh" 300 ms
        assertEquals(1, r.getPeers(FILE, null).size());
        assertEquals("fresh", r.getPeers(FILE, null).get(0).peerId());
        assertEquals(1, r.purgeExpired());
        now.set(5000);
        assertEquals(1, r.purgeExpired());
        assertEquals(0, r.countPeers(FILE));
    }

    @Test
    void leaveRemovesPeerAndRegistryEnforcesSizeLimit() {
        PeerRegistry r = new PeerRegistry(10_000);
        r.announce(FILE, new PeerAddress("p1", "h", 1));
        r.remove(FILE, "p1");
        assertEquals(0, r.countPeers(FILE));
        for (int i = 0; i < PeerRegistry.MAX_PEERS_PER_FILE; i++) {
            assertTrue(r.announce(FILE, new PeerAddress("p" + i, "h", 1)));
        }
        assertFalse(r.announce(FILE, new PeerAddress("overflow", "h", 1)));
        assertTrue(r.announce(FILE, new PeerAddress("p0", "h", 1)), "refreshing an existing peer is still allowed");
    }

    @Test
    void protocolGivesClearErrorsForBadInput() {
        PeerRegistry r = new PeerRegistry(10_000);
        assertEquals("OK\n", TrackerProtocol.handle("ANNOUNCE " + FILE + " peer1 6001", "127.0.0.1", r));
        assertTrue(TrackerProtocol.handle("ANNOUNCE " + FILE + " peer1 0", "h", r).startsWith("ERR BAD_PORT"));
        assertTrue(TrackerProtocol.handle("ANNOUNCE " + FILE + " peer1 70000", "h", r).startsWith("ERR BAD_PORT"));
        assertTrue(TrackerProtocol.handle("ANNOUNCE " + FILE + " peer1 abc", "h", r).startsWith("ERR BAD_PORT"));
        assertTrue(TrackerProtocol.handle("ANNOUNCE short peer1 6001", "h", r).startsWith("ERR BAD_ID"));
        assertTrue(TrackerProtocol.handle("ANNOUNCE " + FILE + " ../evil 6001", "h", r).startsWith("ERR BAD_ID"));
        assertTrue(TrackerProtocol.handle("ANNOUNCE " + FILE, "h", r).startsWith("ERR BAD_REQUEST"));
        assertTrue(TrackerProtocol.handle("HELLO", "h", r).startsWith("ERR UNKNOWN_COMMAND"));
        assertTrue(TrackerProtocol.handle("", "h", r).startsWith("ERR BAD_REQUEST"));
        assertTrue(TrackerProtocol.handle(null, "h", r).startsWith("ERR BAD_REQUEST"));
        assertTrue(TrackerProtocol.handle("GET_PEERS nope", "h", r).startsWith("ERR BAD_ID"));
        assertEquals("PEERS 1\npeer1 127.0.0.1 6001\n", TrackerProtocol.handle("GET_PEERS " + FILE, "x", r));
        assertEquals("PEERS 0\n", TrackerProtocol.handle("GET_PEERS " + FILE + " peer1", "x", r));
        assertEquals("OK\n", TrackerProtocol.handle("LEAVE " + FILE + " peer1", "x", r));
        assertEquals("PEERS 0\n", TrackerProtocol.handle("GET_PEERS " + FILE, "x", r));
    }

    @Test
    void serverAndClientWorkOverTcpAndUseTheRealRemoteAddress() throws Exception {
        try (TrackerServer server = new TrackerServer("127.0.0.1", 0, 60_000)) {
            server.start();
            TrackerClient c = new TrackerClient("127.0.0.1", server.getPort(), 2000);
            c.announce(FILE, "peerA", 7001);
            c.announce(FILE, "peerB", 7002);
            List<PeerAddress> forA = c.getPeers(FILE, "peerA");
            assertEquals(1, forA.size());
            assertEquals(new PeerAddress("peerB", "127.0.0.1", 7002), forA.get(0));
            assertEquals(2, c.getPeers(FILE, null).size());
            c.leave(FILE, "peerB");
            assertEquals(0, c.getPeers(FILE, "peerA").size());
            TrackerException e = assertThrows(TrackerException.class, () -> c.announce(FILE, "peerA", 0));
            assertEquals("BAD_PORT", e.code());
        }
    }

    @Test
    void peersThatStopAnnouncingDisappearFromPeerLists() throws Exception {
        try (TrackerServer server = new TrackerServer("127.0.0.1", 0, 300)) {
            server.start();
            TrackerClient c = new TrackerClient("127.0.0.1", server.getPort(), 2000);
            c.announce(FILE, "ghost", 7001);
            assertEquals(1, c.getPeers(FILE, null).size());
            Thread.sleep(700);
            assertEquals(0, c.getPeers(FILE, null).size());
        }
    }

    @Test
    void overlongOrBinaryRequestsGetAnErrorAndTrackerKeepsWorking() throws Exception {
        try (TrackerServer server = new TrackerServer("127.0.0.1", 0, 60_000)) {
            server.start();
            try (Socket s = new Socket("127.0.0.1", server.getPort())) {
                s.setSoTimeout(3000);
                s.getOutputStream().write(("ANNOUNCE " + "x".repeat(2000) + "\n").getBytes(StandardCharsets.UTF_8));
                s.getOutputStream().flush();
                assertTrue(readAll(s.getInputStream()).startsWith("ERR BAD_REQUEST"));
            }
            try (Socket s = new Socket("127.0.0.1", server.getPort())) {
                s.setSoTimeout(3000);
                s.getOutputStream().write(new byte[]{(byte) 0xff, (byte) 0xfe, 0, 1, '\n'});
                s.getOutputStream().flush();
                assertTrue(readAll(s.getInputStream()).startsWith("ERR"));
            }
            new TrackerClient("127.0.0.1", server.getPort(), 2000).announce(FILE, "stillAlive", 7001);
        }
    }

    @Test
    void manyConcurrentAnnouncesAreAllRecorded() throws Exception {
        try (TrackerServer server = new TrackerServer("127.0.0.1", 0, 60_000)) {
            server.start();
            TrackerClient c = new TrackerClient("127.0.0.1", server.getPort(), 5000);
            ExecutorService pool = Executors.newFixedThreadPool(10);
            List<Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                final int n = i;
                fs.add(pool.submit(() -> { c.announce(FILE, "peer" + n, 8000 + n); return null; }));
            }
            for (Future<?> f : fs) f.get(20, TimeUnit.SECONDS);
            pool.shutdownNow();
            assertEquals(40, c.getPeers(FILE, null).size());
        }
    }

    @Test
    void clientReportsAnIOExceptionWhenTrackerIsDown() throws Exception {
        int port;
        try (TrackerServer server = new TrackerServer("127.0.0.1", 0, 60_000)) {
            server.start();
            port = server.getPort();
        }
        TrackerClient c = new TrackerClient("127.0.0.1", port, 500);
        assertThrows(IOException.class, () -> c.getPeers(FILE, null));
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[512];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toString(StandardCharsets.UTF_8);
    }
}
