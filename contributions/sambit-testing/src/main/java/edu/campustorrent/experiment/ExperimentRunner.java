package edu.campustorrent.experiment;

import edu.campustorrent.scheduler.Strategy;
import edu.campustorrent.util.Args;
import edu.campustorrent.util.Log;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Runs the three experiments from the proposal and writes one CSV row per peer per run.
 * <pre>
 *   --experiment strategy|seeders|freerider|all   (default all)
 *   --runs 3 --leechers 5 --file-kb 2048 --piece-kb 64 --upload-kbps 1024 --round-ms 300
 *   --fr-seeders 2   number of seeders in the free-rider experiment (config names get a suffix if not 2)
 *   --timeout-seconds 180 --out results/experiments.csv --append
 * </pre>
 * Nothing here is simulated except the upload bandwidth cap (--upload-kbps), which is needed because
 * loopback is otherwise too fast to show any difference. Owner: Sambit Kumar Mohapatra.
 */
public final class ExperimentRunner {
    static final List<String> HEADER = List.of("experiment", "config", "run", "strategy", "seeders", "leechers",
            "free_riders", "choking", "file_size_bytes", "piece_size_bytes", "piece_count", "upload_limit_bytes_per_sec",
            "peer_id", "role", "is_free_rider", "completed", "verified", "duration_ms", "bytes_downloaded",
            "bytes_uploaded", "pieces_completed", "retries", "hash_failures", "errors", "choked_waits", "timestamp_utc");

    private ExperimentRunner() { }

    private record Config(String experiment, String name, LocalSwarm.Options opts) { }

    public static void main(String[] argv) throws Exception {
        Args a;
        try {
            a = new Args(argv);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
            return;
        }
        Log.setLevel(Log.Level.WARN);
        String which = a.get("experiment", "all");
        int runs = a.getInt("runs", 3);
        int leechers = a.getInt("leechers", 5);
        long fileKb = a.getLong("file-kb", 2048);
        int pieceKb = a.getInt("piece-kb", 64);
        long uploadKbps = a.getLong("upload-kbps", 1024);
        int roundMs = a.getInt("round-ms", 300);
        long timeoutS = a.getLong("timeout-seconds", 180);
        Path out = Path.of(a.get("out", "results/experiments.csv"));

        List<Config> configs = new ArrayList<>();
        if (which.equals("strategy") || which.equals("all")) {
            for (Strategy s : Strategy.values()) {
                LocalSwarm.Options o = common(fileKb, pieceKb, uploadKbps, roundMs, timeoutS);
                o.seeders = 1; o.leechers = leechers; o.strategy = s;
                configs.add(new Config("strategy", s.label(), o));
            }
        }
        if (which.equals("seeders") || which.equals("all")) {
            for (int n = 1; n <= 3; n++) {
                LocalSwarm.Options o = common(fileKb, pieceKb, uploadKbps, roundMs, timeoutS);
                o.seeders = n; o.leechers = leechers;
                configs.add(new Config("seeders", "seeders-" + n, o));
            }
        }
        if (which.equals("freerider") || which.equals("all")) {
            LocalSwarm.Options base = common(fileKb, pieceKb, uploadKbps, roundMs, timeoutS);
            int frSeeders = a.getInt("fr-seeders", 2);
            String suffix = frSeeders == 2 ? "" : "-" + frSeeders + "seeder";
            base.seeders = frSeeders; base.leechers = Math.max(2, leechers - 1);
            LocalSwarm.Options onOpts = copy(base); onOpts.freeRiders = 1; onOpts.chokingEnabled = true;
            LocalSwarm.Options offOpts = copy(base); offOpts.freeRiders = 1; offOpts.chokingEnabled = false;
            LocalSwarm.Options none = copy(base); none.freeRiders = 0; none.chokingEnabled = true;
            configs.add(new Config("freerider", "freerider-choking-on" + suffix, onOpts));
            configs.add(new Config("freerider", "freerider-choking-off" + suffix, offOpts));
            configs.add(new Config("freerider", "baseline-no-freerider" + suffix, none));
        }
        if (configs.isEmpty()) {
            System.err.println("Unknown --experiment " + which + " (use strategy, seeders, freerider or all)");
            System.exit(2);
        }

        Map<String, List<Long>> contributorMeans = new LinkedHashMap<>();
        Map<String, List<Long>> freeRiderTimes = new LinkedHashMap<>();
        try (CsvWriter csv = new CsvWriter(out, HEADER, a.getFlag("append"))) {
            for (Config c : configs) {
                for (int run = 1; run <= runs; run++) {
                    LocalSwarm.Options o = copy(c.opts);
                    o.seedBase = run;
                    Path work = Files.createTempDirectory("campustorrent-exp-");
                    System.out.printf("[%s] %s run %d/%d ...%n", c.experiment, c.name, run, runs);
                    List<LocalSwarm.PeerResult> results = LocalSwarm.run(o, work);
                    String ts = Instant.now().toString();
                    long pieceCount = (o.fileSize + o.pieceSize - 1) / o.pieceSize;
                    List<Long> contributorTimes = new ArrayList<>();
                    for (LocalSwarm.PeerResult r : results) {
                        csv.writeRow(List.of(c.experiment, c.name, String.valueOf(run), o.strategy.label(),
                                String.valueOf(o.seeders), String.valueOf(o.leechers), String.valueOf(o.freeRiders),
                                o.chokingEnabled ? "on" : "off", String.valueOf(o.fileSize), String.valueOf(o.pieceSize),
                                String.valueOf(pieceCount), String.valueOf(o.uploadBytesPerSec), r.peerId(), r.role(),
                                String.valueOf(r.freeRider()), String.valueOf(r.completed()), String.valueOf(r.verified()),
                                String.valueOf(r.durationMs()), String.valueOf(r.bytesDownloaded()), String.valueOf(r.bytesUploaded()),
                                String.valueOf(r.pieces()), String.valueOf(r.retries()), String.valueOf(r.hashFailures()),
                                String.valueOf(r.errors()), String.valueOf(r.chokedWaits()), ts));
                        if (r.role().equals("leecher") && r.completed() && r.verified()) {
                            if (r.freeRider()) freeRiderTimes.computeIfAbsent(c.name, k -> new ArrayList<>()).add(r.durationMs());
                            else contributorTimes.add(r.durationMs());
                        } else if (r.role().equals("leecher")) {
                            System.out.println("  WARNING: " + r.peerId() + " did not finish or failed verification");
                        }
                    }
                    if (!contributorTimes.isEmpty()) {
                        contributorMeans.computeIfAbsent(c.name, k -> new ArrayList<>())
                                .add((long) contributorTimes.stream().mapToLong(Long::longValue).average().orElse(0));
                    }
                    deleteTree(work);
                }
            }
        }
        System.out.println();
        System.out.println("Summary (measured on this machine; mean +/- sample std dev over runs, ms)");
        contributorMeans.forEach((name, v) -> System.out.printf("  %-24s contributors mean download time: %s%n", name, stats(v)));
        freeRiderTimes.forEach((name, v) -> System.out.printf("  %-24s free-rider download time:        %s%n", name, stats(v)));
        System.out.println("CSV written to " + out.toAbsolutePath());
    }

    private static String stats(List<Long> v) {
        double mean = v.stream().mapToLong(Long::longValue).average().orElse(0);
        double var = 0;
        if (v.size() > 1) {
            for (long x : v) var += (x - mean) * (x - mean);
            var /= (v.size() - 1);
        }
        return String.format("%.0f +/- %.0f (n=%d)", mean, Math.sqrt(var), v.size());
    }

    private static LocalSwarm.Options common(long fileKb, int pieceKb, long uploadKbps, int roundMs, long timeoutS) {
        LocalSwarm.Options o = new LocalSwarm.Options();
        o.fileSize = fileKb * 1024L;
        o.pieceSize = pieceKb * 1024;
        o.uploadBytesPerSec = uploadKbps * 1024L;
        o.roundMillis = roundMs;
        o.timeoutMillis = timeoutS * 1000L;
        return o;
    }

    private static LocalSwarm.Options copy(LocalSwarm.Options s) {
        LocalSwarm.Options o = new LocalSwarm.Options();
        o.seeders = s.seeders; o.leechers = s.leechers; o.freeRiders = s.freeRiders; o.fileSize = s.fileSize;
        o.pieceSize = s.pieceSize; o.strategy = s.strategy; o.uploadBytesPerSec = s.uploadBytesPerSec;
        o.chokingEnabled = s.chokingEnabled; o.unchokeSlots = s.unchokeSlots; o.optimisticSlots = s.optimisticSlots;
        o.roundMillis = s.roundMillis; o.timeoutMillis = s.timeoutMillis; o.seedBase = s.seedBase;
        o.corruptFirstSeeder = s.corruptFirstSeeder;
        return o;
    }

    private static void deleteTree(Path root) {
        try (var walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach((Consumer<Path>) p -> p.toFile().delete());
        } catch (Exception ignored) { }
    }
}
