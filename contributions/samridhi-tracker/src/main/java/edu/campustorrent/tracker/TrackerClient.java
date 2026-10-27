package edu.campustorrent.tracker;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Client side of the tracker protocol; one short TCP connection per call. Owner: Samridhi Padhy. */
public final class TrackerClient {
    private static final int MAX_PEERS_IN_RESPONSE = 1000;

    private final String host;
    private final int port;
    private final int timeoutMs;

    public TrackerClient(String host, int port, int timeoutMs) {
        this.host = host;
        this.port = port;
        this.timeoutMs = timeoutMs;
    }

    public void announce(String fileId, String peerId, int listenPort) throws IOException {
        try (Socket s = open()) {
            send(s, "ANNOUNCE " + fileId + " " + peerId + " " + listenPort);
            expectOk(s.getInputStream());
        }
    }

    public List<PeerAddress> getPeers(String fileId, String excludePeerId) throws IOException {
        try (Socket s = open()) {
            send(s, "GET_PEERS " + fileId + (excludePeerId == null ? "" : " " + excludePeerId));
            InputStream in = s.getInputStream();
            String first = readResponseLine(in);
            String[] h = first.split(" ");
            if (h.length != 2 || !h[0].equals("PEERS")) throw new IOException("Unexpected tracker response: " + first);
            int n;
            try { n = Integer.parseInt(h[1]); } catch (NumberFormatException e) { throw new IOException("Bad peer count: " + first); }
            if (n < 0 || n > MAX_PEERS_IN_RESPONSE) throw new IOException("Unreasonable peer count: " + n);
            List<PeerAddress> peers = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                String line = readResponseLine(in);
                String[] p = line.split(" ");
                if (p.length != 3) throw new IOException("Bad peer line: " + line);
                try {
                    peers.add(new PeerAddress(p[0], p[1], Integer.parseInt(p[2])));
                } catch (NumberFormatException e) {
                    throw new IOException("Bad peer port: " + line);
                }
            }
            return peers;
        }
    }

    public void leave(String fileId, String peerId) throws IOException {
        try (Socket s = open()) {
            send(s, "LEAVE " + fileId + " " + peerId);
            expectOk(s.getInputStream());
        }
    }

    private Socket open() throws IOException {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            s.setSoTimeout(timeoutMs);
            return s;
        } catch (IOException e) {
            s.close();
            throw e;
        }
    }

    private static void send(Socket s, String line) throws IOException {
        s.getOutputStream().write((line + "\n").getBytes(StandardCharsets.UTF_8));
        s.getOutputStream().flush();
    }

    private static String readResponseLine(InputStream in) throws IOException {
        String line = LineIO.readLine(in, TrackerProtocol.MAX_LINE);
        if (line == null) throw new IOException("Tracker closed the connection");
        if (line.startsWith("ERR ")) {
            String[] p = line.split(" ", 3);
            throw new TrackerException(p.length > 1 ? p[1] : "ERR", p.length > 2 ? p[2] : "");
        }
        return line;
    }

    private static void expectOk(InputStream in) throws IOException {
        String line = readResponseLine(in);
        if (!line.equals("OK")) throw new IOException("Unexpected tracker response: " + line);
    }
}
