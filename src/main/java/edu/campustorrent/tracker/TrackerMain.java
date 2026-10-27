package edu.campustorrent.tracker;

import edu.campustorrent.util.Args;

/** CLI: starts a tracker. Usage: --host 127.0.0.1 --port 6969 --ttl-seconds 30. Owner: Samridhi Padhy. */
public final class TrackerMain {
    private TrackerMain() { }

    public static void main(String[] argv) throws Exception {
        try {
            Args a = new Args(argv);
            String host = a.get("host", "127.0.0.1");
            int port = a.getInt("port", 6969);
            long ttl = a.getLong("ttl-seconds", 30) * 1000L;
            TrackerServer server = new TrackerServer(host, port, ttl);
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(server::close));
            Thread.currentThread().join();
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("Usage: TrackerMain [--host 127.0.0.1] [--port 6969] [--ttl-seconds 30]");
            System.exit(2);
        }
    }
}
