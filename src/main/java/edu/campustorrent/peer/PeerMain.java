package edu.campustorrent.peer;

import edu.campustorrent.util.Args;
import edu.campustorrent.util.Log;

/**
 * CLI for one peer.
 * Seeder:  --role seeder  --peer-id S1 --manifest m.manifest --file source.bin --port 6101 --tracker-port 6969
 * Leecher: --role leecher --peer-id L1 --manifest m.manifest --output-dir out/L1 --port 6201 --tracker-port 6969
 * Optional: --strategy rarest-first|random|sequential --seed N --upload-kbps N --unchoke-slots N
 *           --optimistic-slots N --round-ms N --no-choking --free-rider --linger-seconds N
 *           --timeout-seconds N --run-seconds N --verbose
 * Exit code (leecher): 0 = downloaded and verified, 1 = timed out / failed, 2 = bad arguments.
 * Owner: Vabyasha Mohapatra.
 */
public final class PeerMain {
    private PeerMain() { }

    public static void main(String[] argv) throws Exception {
        Peer peer;
        Args a;
        try {
            a = new Args(argv);
            if (a.getFlag("verbose")) Log.setLevel(Log.Level.DEBUG);
            peer = new Peer(PeerConfig.fromArgs(a));
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("See README.md for the peer options.");
            System.exit(2);
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(peer::close));
        peer.start();

        PeerConfig cfg = peer.config();
        if (cfg.role == PeerConfig.Role.SEEDER) {
            long runSeconds = a.getLong("run-seconds", 0);
            if (runSeconds > 0) Thread.sleep(runSeconds * 1000L);
            else Thread.currentThread().join();
            peer.close();
            return;
        }

        long timeoutSeconds = a.getLong("timeout-seconds", 300);
        boolean ok = peer.awaitComplete(timeoutSeconds * 1000L);
        PeerMetrics m = peer.metrics();
        if (ok) {
            Log.info(cfg.peerId, "SUMMARY duration=" + m.durationMillis() + "ms downloaded=" + m.bytesDownloaded.get()
                    + "B uploaded=" + m.bytesUploaded.get() + "B pieces=" + m.piecesCompleted.get()
                    + " retries=" + m.retries.get() + " errors=" + m.errors.get() + " chokedWaits=" + m.chokedWaits.get());
        } else {
            Log.error(cfg.peerId, "download did not finish within " + timeoutSeconds + " s");
        }
        long linger = a.getLong("linger-seconds", 0);
        if (ok && linger > 0) {
            Log.info(cfg.peerId, "seeding for " + linger + " more second(s) so others can finish");
            Thread.sleep(linger * 1000L);
        }
        peer.close();
        System.exit(ok ? 0 : 1);
    }
}
