package edu.campustorrent.integration;

import static org.junit.jupiter.api.Assertions.*;

import edu.campustorrent.experiment.LocalSwarm;
import edu.campustorrent.experiment.LocalSwarm.PeerResult;
import edu.campustorrent.scheduler.Strategy;
import edu.campustorrent.util.Log;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * End-to-end tests: real tracker, real seeders and leechers, real sockets on localhost.
 * Every completed download is compared byte-for-byte with the source (done inside LocalSwarm).
 * Owner: Sambit Kumar Mohapatra.
 */
class SwarmIntegrationTest {

    @BeforeEach
    void quietLogs() { Log.setLevel(Log.Level.ERROR); }

    private static LocalSwarm.Options small() {
        LocalSwarm.Options o = new LocalSwarm.Options();
        o.fileSize = 600_000 + 123;           // not a multiple of the piece size on purpose
        o.pieceSize = 32 * 1024;
        o.timeoutMillis = 60_000;
        return o;
    }

    private static long sum(List<PeerResult> rs, java.util.function.ToLongFunction<PeerResult> f) {
        return rs.stream().mapToLong(f).sum();
    }

    @Test
    void trackerTwoSeedersAndFourLeechersAllDownloadIdenticalFiles(@TempDir Path dir) throws Exception {
        LocalSwarm.Options o = small();               // 2 seeders + 4 leechers = 6 peers
        List<PeerResult> rs = LocalSwarm.run(o, dir);
        assertEquals(6, rs.size());
        int leechers = 0;
        for (PeerResult r : rs) {
            if (!r.role().equals("leecher")) continue;
            leechers++;
            assertTrue(r.completed(), r.peerId() + " should finish");
            assertTrue(r.verified(), r.peerId() + " must match the source byte for byte");
            assertEquals(o.fileSize, r.bytesDownloaded());
            assertEquals(0L, r.hashFailures());
            assertTrue(r.durationMs() >= 0);
        }
        assertEquals(4, leechers);
        assertTrue(sum(rs, PeerResult::bytesUploaded) >= 4 * o.fileSize, "uploads must cover all downloads");
    }

    @Test
    void everySelectionStrategyCompletesWithSixPeers(@TempDir Path dir) throws Exception {
        for (Strategy s : Strategy.values()) {
            LocalSwarm.Options o = small();
            o.strategy = s;
            o.seedBase = 7;
            List<PeerResult> rs = LocalSwarm.run(o, Files.createDirectory(dir.resolve(s.label())));
            for (PeerResult r : rs) assertTrue(r.completed() && r.verified(), s + " / " + r.peerId());
        }
    }

    @Test
    void largerSwarmWithOneSeederAndSevenLeechersCompletes(@TempDir Path dir) throws Exception {
        LocalSwarm.Options o = small();
        o.seeders = 1;
        o.leechers = 7;
        List<PeerResult> rs = LocalSwarm.run(o, dir);
        assertEquals(8, rs.size());
        for (PeerResult r : rs) assertTrue(r.completed() && r.verified(), r.peerId());
    }

    @Test
    void freeRiderNeverUploadsButContributorsDoAndEveryoneVerifies(@TempDir Path dir) throws Exception {
        LocalSwarm.Options o = small();
        o.leechers = 3;
        o.freeRiders = 1;
        List<PeerResult> rs = LocalSwarm.run(o, dir);
        for (PeerResult r : rs) {
            assertTrue(r.completed() && r.verified(), r.peerId());
            if (r.freeRider()) assertEquals(0L, r.bytesUploaded(), "free-rider must not upload");
        }
        long contributorUpload = rs.stream().filter(r -> r.role().equals("leecher") && !r.freeRider())
                .mapToLong(PeerResult::bytesUploaded).sum();
        assertTrue(contributorUpload > 0, "contributing leechers should upload to each other");
    }

    @Test
    void corruptingSeederIsDetectedAndHonestSeederStillDeliversCorrectFile(@TempDir Path dir) throws Exception {
        LocalSwarm.Options o = small();
        o.seeders = 2;
        o.leechers = 2;
        o.corruptFirstSeeder = true;
        List<PeerResult> rs = LocalSwarm.run(o, dir);
        for (PeerResult r : rs) {
            if (r.role().equals("leecher")) assertTrue(r.completed() && r.verified(), r.peerId() + " must end with a correct file");
        }
    }

    @Test
    void leecherNeverAcceptsFileFromOnlyACorruptingSeeder(@TempDir Path dir) throws Exception {
        LocalSwarm.Options o = small();
        o.seeders = 1;
        o.leechers = 1;
        o.corruptFirstSeeder = true;
        o.timeoutMillis = 3000;
        List<PeerResult> rs = LocalSwarm.run(o, dir);
        PeerResult leecher = rs.stream().filter(r -> r.role().equals("leecher")).findFirst().orElseThrow();
        assertFalse(leecher.completed());
        assertEquals(0L, leecher.bytesDownloaded(), "no corrupted byte may be accepted");
        assertTrue(leecher.hashFailures() > 0, "corruption must have been detected");
        assertFalse(Files.exists(dir.resolve("out").resolve("L1").resolve("swarmfile.bin")), "no final file may be produced");
    }

    @Test
    void leecherWithNoSeedersTimesOutInsteadOfHangingForever(@TempDir Path dir) throws Exception {
        LocalSwarm.Options o = small();
        o.seeders = 0;
        o.leechers = 1;
        o.timeoutMillis = 1500;
        List<PeerResult> rs = LocalSwarm.run(o, dir);
        assertEquals(1, rs.size());
        assertFalse(rs.get(0).completed());
    }
}
