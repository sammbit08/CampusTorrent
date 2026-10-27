package edu.campustorrent.peer;

import edu.campustorrent.scheduler.Strategy;
import edu.campustorrent.util.Args;
import edu.campustorrent.util.Validation;

import java.nio.file.Path;

/** All settings of one peer. Plain mutable fields with defaults; call {@link #validate()} before use. Owner: Vabyasha Mohapatra. */
public final class PeerConfig {
    public enum Role { SEEDER, LEECHER }

    public String peerId;
    public Role role = Role.LEECHER;
    public String bindHost = "127.0.0.1";
    /** 0 = let the OS choose a free port. */
    public int port = 0;
    public String trackerHost = "127.0.0.1";
    /** 0 = no tracker (only allowed for seeders; used by protocol tests). */
    public int trackerPort = 6969;
    public Path manifestPath;
    /** Seeder only: the complete source file. */
    public Path sourceFile;
    /** Leecher only: directory the file is downloaded into. */
    public Path outputDir;

    public Strategy strategy = Strategy.RAREST_FIRST;
    /** Seed for RANDOM selection and for the rarest-first tie-break rotation (0 = lowest index wins ties). */
    public long seed = 0;

    /** Simulated upload bandwidth in bytes/second shared by all uploads of this peer; 0 = unlimited. */
    public long uploadBytesPerSec = 0;
    public boolean chokingEnabled = true;
    public int unchokeSlots = 2;
    public int optimisticSlots = 1;
    public int roundMillis = 1000;
    /** Never uploads to anyone (answers every request with CHOKE) but still downloads. */
    public boolean freeRider = false;
    /** Test-only fault injection: corrupt every piece this peer uploads. */
    public boolean corruptUploads = false;

    public int socketTimeoutMs = 15000;
    public int connectTimeoutMs = 2000;
    public int discoveryIntervalMs = 500;
    public int announceIntervalMs = 5000;

    public void validate() {
        Validation.requirePeerId(peerId);
        if (manifestPath == null) throw new IllegalArgumentException("manifest path is required");
        if (port < 0 || port > 65535) throw new IllegalArgumentException("port out of range");
        if (trackerPort < 0 || trackerPort > 65535) throw new IllegalArgumentException("tracker port out of range");
        if (role == Role.SEEDER && sourceFile == null) throw new IllegalArgumentException("seeder needs --file (source file)");
        if (role == Role.LEECHER && outputDir == null) throw new IllegalArgumentException("leecher needs --output-dir");
        if (role == Role.LEECHER && trackerPort == 0) throw new IllegalArgumentException("leecher needs a tracker");
        if (unchokeSlots < 0 || optimisticSlots < 0 || unchokeSlots + optimisticSlots < 1) {
            throw new IllegalArgumentException("need at least one unchoke slot");
        }
        if (socketTimeoutMs < 50 || connectTimeoutMs < 50) throw new IllegalArgumentException("timeouts too small");
        if (roundMillis < 10) throw new IllegalArgumentException("round must be >= 10 ms");
    }

    public static PeerConfig fromArgs(Args a) {
        PeerConfig c = new PeerConfig();
        c.peerId = a.require("peer-id");
        String role = a.require("role").toLowerCase();
        if (role.equals("seeder")) c.role = Role.SEEDER;
        else if (role.equals("leecher")) c.role = Role.LEECHER;
        else throw new IllegalArgumentException("--role must be seeder or leecher");
        c.bindHost = a.get("host", c.bindHost);
        c.port = a.getInt("port", 0);
        c.trackerHost = a.get("tracker-host", c.trackerHost);
        c.trackerPort = a.getInt("tracker-port", c.trackerPort);
        c.manifestPath = Path.of(a.require("manifest"));
        if (a.has("file")) c.sourceFile = Path.of(a.get("file", ""));
        if (a.has("output-dir")) c.outputDir = Path.of(a.get("output-dir", ""));
        c.strategy = Strategy.parse(a.get("strategy", "rarest-first"));
        c.seed = a.getLong("seed", 0);
        c.uploadBytesPerSec = a.getLong("upload-kbps", 0) * 1024L;
        c.chokingEnabled = !a.getFlag("no-choking");
        c.unchokeSlots = a.getInt("unchoke-slots", c.unchokeSlots);
        c.optimisticSlots = a.getInt("optimistic-slots", c.optimisticSlots);
        c.roundMillis = a.getInt("round-ms", c.roundMillis);
        c.freeRider = a.getFlag("free-rider");
        c.corruptUploads = a.getFlag("corrupt-uploads");
        c.socketTimeoutMs = a.getInt("socket-timeout-ms", c.socketTimeoutMs);
        c.discoveryIntervalMs = a.getInt("discovery-ms", c.discoveryIntervalMs);
        return c;
    }
}
