package edu.campustorrent.protocol;

import static org.junit.jupiter.api.Assertions.*;

import edu.campustorrent.peer.Peer;
import edu.campustorrent.peer.PeerConfig;
import edu.campustorrent.pieces.FileSplitter;
import edu.campustorrent.pieces.Manifest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

/** Owner: Vabyasha Mohapatra. */
class ProtocolTest {
    private final List<Peer> peers = new ArrayList<>();
    private Manifest manifest;
    private Path source;

    @AfterEach
    void cleanup() {
        for (Peer p : peers) p.close();
    }

    /** Starts a tracker-less seeder with 5 pieces of 4096 bytes (the last is shorter). */
    private Peer startSeeder(Path dir, boolean freeRider, int unchokeSlots) throws Exception {
        source = dir.resolve("data.bin");
        byte[] data = new byte[20_000];
        new Random(5).nextBytes(data);
        Files.write(source, data);
        manifest = FileSplitter.createManifest(source, 4096);
        Path mf = dir.resolve("data.manifest");
        manifest.save(mf);
        PeerConfig c = new PeerConfig();
        c.peerId = "seed";
        c.role = PeerConfig.Role.SEEDER;
        c.manifestPath = mf;
        c.sourceFile = source;
        c.trackerPort = 0;
        c.freeRider = freeRider;
        c.unchokeSlots = unchokeSlots;
        c.optimisticSlots = 0;
        c.roundMillis = 600_000;   // no rotation during a test: slot assignment must be deterministic
        c.socketTimeoutMs = 3000;
        Peer p = new Peer(c);
        p.start();
        peers.add(p);
        return p;
    }

    private PeerConnection connect(Peer p, String id) throws IOException {
        PeerConnection c = PeerConnection.connect("127.0.0.1", p.getPort(), 2000, 3000);
        c.handshake(manifest.fileIdBytes(), id);
        return c;
    }

    // ------------------------------------------------------------------ framing / codec

    @Test
    void everyMessageTypeSurvivesEncodeAndDecode() throws Exception {
        byte[] fileId = new byte[32];
        new Random(1).nextBytes(fileId);
        boolean[] bits = {true, false, true, true, false, false, false, true, true, false};
        List<Message> all = List.of(Message.handshake(fileId, "peer-1"), Message.bitfield(bits), Message.getBitfield(),
                Message.request(7), Message.piece(3, new byte[]{1, 2, 3}), Message.choke(), Message.unchoke(),
                Message.error(ErrorCode.BAD_INDEX, "nope"), Message.shutdown());
        for (Message m : all) {
            Message back = MessageCodec.read(new DataInputStream(new ByteArrayInputStream(m.toFrameBytes())));
            assertEquals(m.type(), back.type());
            assertArrayEquals(m.payload(), back.payload());
        }
        Message.Handshake h = all.get(0).asHandshake();
        assertEquals("peer-1", h.peerId());
        assertEquals(Message.PROTOCOL_VERSION, h.version());
        assertArrayEquals(fileId, h.fileId());
        boolean[] decoded = all.get(1).asBitfield(10);
        for (int i = 0; i < 10; i++) assertEquals(bits[i], decoded[i]);
        assertEquals(7, all.get(3).asIndex());
        assertEquals(3, all.get(4).asPiece().index());
        assertArrayEquals(new byte[]{1, 2, 3}, all.get(4).asPiece().data());
        assertEquals(ErrorCode.BAD_INDEX, all.get(7).asError().code());
        assertEquals("nope", all.get(7).asError().reason());
    }

    private static DataInputStream in(byte[] b) { return new DataInputStream(new ByteArrayInputStream(b)); }

    private static byte[] be(int v) { return new byte[]{(byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v}; }

    @Test
    void codecRejectsBadLengthsUnknownTypesAndTruncatedFrames() {
        assertThrows(ProtocolException.class, () -> MessageCodec.read(in(be(0))));
        assertThrows(ProtocolException.class, () -> MessageCodec.read(in(be(-5))));
        assertThrows(ProtocolException.class, () -> MessageCodec.read(in(be(Integer.MAX_VALUE))), "must not allocate 2 GiB");
        assertThrows(ProtocolException.class, () -> MessageCodec.read(in(be(MessageCodec.MAX_FRAME_BYTES + 1))));
        byte[] unknown = {0, 0, 0, 1, 99};
        assertThrows(ProtocolException.class, () -> MessageCodec.read(in(unknown)));
        byte[] truncated = {0, 0, 0, 10, 4, 1, 2};
        assertThrows(EOFException.class, () -> MessageCodec.read(in(truncated)));
        byte[] badRequest = {0, 0, 0, 3, 3, 0, 1};          // REQUEST must carry exactly 4 bytes
        assertThrows(ProtocolException.class, () -> MessageCodec.read(in(badRequest)));
        byte[] chokeWithPayload = {0, 0, 0, 2, 5, 9};
        assertThrows(ProtocolException.class, () -> MessageCodec.read(in(chokeWithPayload)));
    }

    @Test
    void bitfieldParserValidatesLengthAndPaddingBits() throws Exception {
        assertThrows(ProtocolException.class, () -> Message.bitfield(new boolean[24]).asBitfield(10));
        Message dirtyPadding = Message.of(MessageType.BITFIELD, new byte[]{(byte) 0xff, (byte) 0xff});
        assertThrows(ProtocolException.class, () -> dirtyPadding.asBitfield(10));
        assertThrows(ProtocolException.class, () -> Message.request(1).asBitfield(1));
    }

    @Test
    void handshakeParserRejectsBadMagicAndBadPeerId() throws Exception {
        byte[] p = Message.handshake(new byte[32], "peer1").payload().clone();
        p[0] = 'X';
        assertThrows(ProtocolException.class, () -> Message.of(MessageType.HANDSHAKE, p).asHandshake());
        byte[] q = Message.handshake(new byte[32], "peer1").payload().clone();
        q[38] = '/';
        assertThrows(ProtocolException.class, () -> Message.of(MessageType.HANDSHAKE, q).asHandshake());
        assertThrows(IllegalArgumentException.class, () -> Message.handshake(new byte[32], "bad id!"));
    }

    // ------------------------------------------------------------------ handshake over real sockets

    private static AtomicReference<Object> serveOnce(ServerSocket ss, byte[] fileId, String id) {
        AtomicReference<Object> result = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try (Socket s = ss.accept(); PeerConnection c = new PeerConnection(s, 2000)) {
                result.set(c.handshake(fileId, id));
            } catch (Exception e) {
                result.set(e);
            }
        });
        t.setDaemon(true);
        t.start();
        return result;
    }

    @Test
    void handshakeSucceedsBetweenTwoPeersAndExchangesIds() throws Exception {
        byte[] fileId = new byte[32];
        fileId[0] = 42;
        try (ServerSocket ss = new ServerSocket(0, 5, java.net.InetAddress.getLoopbackAddress())) {
            AtomicReference<Object> server = serveOnce(ss, fileId, "srv");
            try (PeerConnection c = PeerConnection.connect("127.0.0.1", ss.getLocalPort(), 1000, 2000)) {
                Message.Handshake theirs = c.handshake(fileId, "cli");
                assertEquals("srv", theirs.peerId());
            }
            Thread.sleep(200);
            assertTrue(server.get() instanceof Message.Handshake);
            assertEquals("cli", ((Message.Handshake) server.get()).peerId());
        }
    }

    @Test
    void handshakeFailsOnProtocolVersionMismatch() throws Exception {
        byte[] fileId = new byte[32];
        try (ServerSocket ss = new ServerSocket(0, 5, java.net.InetAddress.getLoopbackAddress())) {
            Thread t = new Thread(() -> {
                try (Socket s = ss.accept(); PeerConnection c = new PeerConnection(s, 2000)) {
                    c.send(Message.handshake(99, fileId, "future"));
                    c.receive();
                } catch (Exception ignored) { }
            });
            t.setDaemon(true);
            t.start();
            try (PeerConnection c = PeerConnection.connect("127.0.0.1", ss.getLocalPort(), 1000, 2000)) {
                ProtocolException e = assertThrows(ProtocolException.class, () -> c.handshake(fileId, "cli"));
                assertTrue(e.getMessage().contains("version"));
            }
        }
    }

    @Test
    void handshakeFailsWhenFileIdsDiffer() throws Exception {
        byte[] a = new byte[32];
        byte[] b = new byte[32];
        b[5] = 1;
        try (ServerSocket ss = new ServerSocket(0, 5, java.net.InetAddress.getLoopbackAddress())) {
            AtomicReference<Object> server = serveOnce(ss, a, "srv");
            try (PeerConnection c = PeerConnection.connect("127.0.0.1", ss.getLocalPort(), 1000, 2000)) {
                assertThrows(ProtocolException.class, () -> c.handshake(b, "cli"));
            }
            Thread.sleep(200);
            assertTrue(server.get() instanceof ProtocolException, "the other side must reject as well");
        }
    }

    @Test
    void silentPeerCausesSocketTimeoutInsteadOfHanging() throws Exception {
        try (ServerSocket ss = new ServerSocket(0, 5, java.net.InetAddress.getLoopbackAddress())) {
            Thread t = new Thread(() -> {
                try (Socket s = ss.accept()) { Thread.sleep(2000); } catch (Exception ignored) { }
            });
            t.setDaemon(true);
            t.start();
            long t0 = System.nanoTime();
            try (PeerConnection c = PeerConnection.connect("127.0.0.1", ss.getLocalPort(), 1000, 300)) {
                assertThrows(SocketTimeoutException.class, () -> c.handshake(new byte[32], "cli"));
            }
            assertTrue((System.nanoTime() - t0) / 1_000_000L < 1500, "timeout must fire promptly");
        }
    }

    @Test
    void connectionToClosedPortFailsFast() throws Exception {
        int port;
        try (ServerSocket ss = new ServerSocket(0)) { port = ss.getLocalPort(); }
        final int closed = port;
        assertThrows(IOException.class, () -> PeerConnection.connect("127.0.0.1", closed, 500, 500));
    }

    // ------------------------------------------------------------------ against a real seeder

    @Test
    void seederAnswersInvalidIndicesWithErrorAndValidIndexWithVerifiedPiece(@TempDir Path dir) throws Exception {
        Peer seeder = startSeeder(dir, false, 2);
        try (PeerConnection c = connect(seeder, "cli")) {
            boolean[] bits = c.receive().asBitfield(manifest.pieceCount());
            for (boolean b : bits) assertTrue(b, "seeder has every piece");
            assertEquals(MessageType.UNCHOKE, c.receive().type());
            for (int bad : new int[]{-1, 5, 1000, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                c.send(Message.request(bad));
                Message r = c.receive();
                assertEquals(MessageType.ERROR, r.type());
                assertEquals(ErrorCode.BAD_INDEX, r.asError().code());
            }
            c.send(Message.request(4));                       // last, shorter piece
            Message.PiecePayload p = c.receive().asPiece();
            assertEquals(4, p.index());
            assertEquals(manifest.pieceLength(4), p.data().length);
            assertTrue(manifest.verifyPiece(4, p.data()));
            c.send(Message.getBitfield());
            assertEquals(MessageType.BITFIELD, c.receive().type());
            c.send(Message.shutdown());
        }
    }

    @Test
    void seederRejectsHandshakeForAnotherFile(@TempDir Path dir) throws Exception {
        Peer seeder = startSeeder(dir, false, 2);
        byte[] other = manifest.fileIdBytes().clone();
        other[0] ^= 1;
        try (PeerConnection c = PeerConnection.connect("127.0.0.1", seeder.getPort(), 1000, 2000)) {
            assertThrows(ProtocolException.class, () -> c.handshake(other, "cli"));
        }
        try (PeerConnection ok = connect(seeder, "cli2")) {          // still serving others
            assertEquals(MessageType.BITFIELD, ok.receive().type());
        }
    }

    private static void readUntilClosed(Socket s, List<Message> seen) {
        try {
            DataInputStream in = new DataInputStream(s.getInputStream());
            while (true) seen.add(MessageCodec.read(in));
        } catch (IOException expectedEndOfStream) {
            // EOF, reset or protocol error: we only care that the server closed the connection
        }
    }

    @Test
    void seederSurvivesMalformedFramesAndKeepsServingOthers(@TempDir Path dir) throws Exception {
        Peer seeder = startSeeder(dir, false, 2);
        byte[][] garbage = {
                new byte[]{0, 0, 0, 0},                                     // zero-length frame
                new byte[]{0x7f, (byte) 0xff, (byte) 0xff, (byte) 0xff},     // 2 GiB length
                new byte[]{0, 0, 0, 1, 0x42},                                // unknown type
                "GET / HTTP/1.1\r\nHost: x\r\n\r\n".getBytes(),              // wrong protocol entirely
        };
        for (byte[] g : garbage) {
            try (Socket s = new Socket("127.0.0.1", seeder.getPort())) {
                s.setSoTimeout(3000);
                s.getOutputStream().write(g);
                s.getOutputStream().flush();
                List<Message> seen = new ArrayList<>();
                readUntilClosed(s, seen);
                boolean sawError = false;
                for (Message m : seen) if (m.type() == MessageType.ERROR) sawError = true;
                assertTrue(sawError, "server should answer garbage with an ERROR before closing");
            }
        }
        try (PeerConnection c = connect(seeder, "cli")) {              // seeder still healthy
            assertEquals(MessageType.BITFIELD, c.receive().type());
        }
    }

    @Test
    void seederRejectsUnexpectedMessageTypeAfterHandshake(@TempDir Path dir) throws Exception {
        Peer seeder = startSeeder(dir, false, 2);
        try (PeerConnection c = connect(seeder, "cli")) {
            c.receive();
            c.receive();
            c.send(Message.piece(0, new byte[]{1}));                   // a leecher must never send PIECE
            Message r = c.receive();
            assertEquals(MessageType.ERROR, r.type());
            assertEquals(ErrorCode.BAD_REQUEST, r.asError().code());
        }
    }

    @Test
    void seederSurvivesPeerThatDisconnectsMidRequest(@TempDir Path dir) throws Exception {
        Peer seeder = startSeeder(dir, false, 2);
        for (int i = 0; i < 5; i++) {
            PeerConnection c = connect(seeder, "rude" + i);
            c.receive();
            c.receive();
            c.send(Message.request(0));
            c.close();                                                  // vanish without reading the reply
        }
        Thread.sleep(200);
        try (PeerConnection c = connect(seeder, "polite")) {
            c.receive();
            c.receive();
            c.send(Message.request(1));
            assertTrue(manifest.verifyPiece(1, c.receive().asPiece().data()));
        }
    }

    @Test
    void chokedPeerReceivesChokeInsteadOfData(@TempDir Path dir) throws Exception {
        Peer seeder = startSeeder(dir, false, 1);                      // only one unchoke slot
        try (PeerConnection first = connect(seeder, "first"); PeerConnection second = connect(seeder, "second")) {
            first.receive();
            assertEquals(MessageType.UNCHOKE, first.receive().type());
            second.receive();
            assertEquals(MessageType.CHOKE, second.receive().type());
            second.send(Message.request(0));
            assertEquals(MessageType.CHOKE, second.receive().type());
            first.send(Message.request(0));
            assertEquals(MessageType.PIECE, first.receive().type());
        }
    }

    @Test
    void freeRiderPeerNeverUploads(@TempDir Path dir) throws Exception {
        Peer freeRider = startSeeder(dir, true, 2);
        try (PeerConnection c = connect(freeRider, "cli")) {
            c.receive();
            assertEquals(MessageType.CHOKE, c.receive().type());
            c.send(Message.request(0));
            assertEquals(MessageType.CHOKE, c.receive().type());
        }
        assertEquals(0L, freeRider.metrics().bytesUploaded.get());
    }
}
