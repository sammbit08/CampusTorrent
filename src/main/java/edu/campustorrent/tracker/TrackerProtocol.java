package edu.campustorrent.tracker;

import edu.campustorrent.util.Validation;

import java.util.List;

/**
 * Pure request -> response logic of the tracker (no sockets), so it is easy to unit test.
 *
 * Requests (one line, single spaces, ASCII):
 * <pre>
 *   ANNOUNCE &lt;fileId&gt; &lt;peerId&gt; &lt;port&gt;     -> OK
 *   GET_PEERS &lt;fileId&gt; [excludePeerId]      -> PEERS n  followed by n lines "peerId host port"
 *   LEAVE &lt;fileId&gt; &lt;peerId&gt;               -> OK
 * Errors: ERR &lt;CODE&gt; &lt;text&gt;
 * </pre>
 * The peer's host is taken from the TCP connection, never from the request, so a peer cannot
 * register somebody else's address. Owner: Samridhi Padhy.
 */
public final class TrackerProtocol {
    public static final int MAX_LINE = 256;

    private TrackerProtocol() { }

    /** @return the full response text; always ends with '\n' */
    public static String handle(String line, String remoteHost, PeerRegistry registry) {
        if (line == null || line.isBlank()) return err("BAD_REQUEST", "empty request");
        String[] t = line.trim().split(" ");
        switch (t[0]) {
            case "ANNOUNCE": return announce(t, remoteHost, registry);
            case "GET_PEERS": return getPeers(t, registry);
            case "LEAVE": return leave(t, registry);
            default: return err("UNKNOWN_COMMAND", "unknown command");
        }
    }

    private static String announce(String[] t, String remoteHost, PeerRegistry registry) {
        if (t.length != 4) return err("BAD_REQUEST", "usage: ANNOUNCE <fileId> <peerId> <port>");
        if (!Validation.isValidFileId(t[1])) return err("BAD_ID", "invalid file id");
        if (!Validation.isValidPeerId(t[2])) return err("BAD_ID", "invalid peer id");
        int port;
        try {
            port = Integer.parseInt(t[3]);
        } catch (NumberFormatException e) {
            return err("BAD_PORT", "port is not a number");
        }
        if (!Validation.isValidPort(port)) return err("BAD_PORT", "port out of range 1-65535");
        if (!registry.announce(t[1], new PeerAddress(t[2], remoteHost, port))) {
            return err("FULL", "tracker registry limit reached");
        }
        return "OK\n";
    }

    private static String getPeers(String[] t, PeerRegistry registry) {
        if (t.length < 2 || t.length > 3) return err("BAD_REQUEST", "usage: GET_PEERS <fileId> [excludePeerId]");
        if (!Validation.isValidFileId(t[1])) return err("BAD_ID", "invalid file id");
        String exclude = t.length == 3 ? t[2] : null;
        if (exclude != null && !Validation.isValidPeerId(exclude)) return err("BAD_ID", "invalid peer id");
        List<PeerAddress> peers = registry.getPeers(t[1], exclude);
        StringBuilder sb = new StringBuilder("PEERS ").append(peers.size()).append('\n');
        for (PeerAddress p : peers) {
            sb.append(p.peerId()).append(' ').append(p.host()).append(' ').append(p.port()).append('\n');
        }
        return sb.toString();
    }

    private static String leave(String[] t, PeerRegistry registry) {
        if (t.length != 3) return err("BAD_REQUEST", "usage: LEAVE <fileId> <peerId>");
        if (!Validation.isValidFileId(t[1]) || !Validation.isValidPeerId(t[2])) return err("BAD_ID", "invalid id");
        registry.remove(t[1], t[2]);
        return "OK\n";
    }

    static String err(String code, String text) { return "ERR " + code + " " + text + "\n"; }
}
