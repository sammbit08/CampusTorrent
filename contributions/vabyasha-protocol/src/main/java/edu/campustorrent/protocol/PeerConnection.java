package edu.campustorrent.protocol;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;

/**
 * A framed, timeout-protected TCP connection to another peer.
 * The handshake is symmetric: both sides send HANDSHAKE first, then each validates what it received.
 * Owner: Vabyasha Mohapatra.
 */
public final class PeerConnection implements Closeable {
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;

    public PeerConnection(Socket socket, int readTimeoutMs) throws IOException {
        this.socket = socket;
        socket.setSoTimeout(readTimeoutMs);
        socket.setTcpNoDelay(true);
        this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    public static PeerConnection connect(String host, int port, int connectTimeoutMs, int readTimeoutMs) throws IOException {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            return new PeerConnection(s, readTimeoutMs);
        } catch (IOException e) {
            s.close();
            throw e;
        }
    }

    public synchronized void send(Message m) throws IOException { MessageCodec.write(out, m); }

    public Message receive() throws IOException { return MessageCodec.read(in); }

    public String remoteAddress() { return String.valueOf(socket.getRemoteSocketAddress()); }

    /**
     * Sends our HANDSHAKE, reads theirs, and checks magic, version and file id.
     * On a mismatch an ERROR message is sent (best effort) and a ProtocolException is thrown.
     * If the other side answers with an ERROR instead of a HANDSHAKE, its reason is reported.
     */
    public Message.Handshake handshake(byte[] fileId, String peerId) throws IOException {
        send(Message.handshake(fileId, peerId));
        Message m;
        try {
            m = receive();
        } catch (ProtocolException e) {
            reject(ErrorCode.BAD_REQUEST, "bad handshake frame");
            throw e;
        }
        if (m.type() == MessageType.ERROR) {
            Message.ErrorPayload e = m.asError();
            throw new ProtocolException("Remote rejected handshake: " + ErrorCode.name(e.code()) + " " + e.reason());
        }
        Message.Handshake theirs;
        try {
            theirs = m.asHandshake();
        } catch (ProtocolException e) {
            reject(ErrorCode.BAD_REQUEST, "bad handshake");
            throw e;
        }
        if (theirs.version() != Message.PROTOCOL_VERSION) {
            reject(ErrorCode.VERSION_MISMATCH, "unsupported protocol version " + theirs.version());
            throw new ProtocolException("Protocol version mismatch: remote uses " + theirs.version());
        }
        if (!Arrays.equals(theirs.fileId(), fileId)) {
            reject(ErrorCode.UNKNOWN_FILE, "file id not served here");
            throw new ProtocolException("File id mismatch in handshake");
        }
        return theirs;
    }

    private void reject(int code, String reason) {
        try { send(Message.error(code, reason)); } catch (IOException ignored) { /* peer already gone */ }
    }

    @Override
    public void close() {
        try { socket.close(); } catch (IOException ignored) { }
    }
}
