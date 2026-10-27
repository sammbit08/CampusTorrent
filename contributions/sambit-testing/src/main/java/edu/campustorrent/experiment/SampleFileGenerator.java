package edu.campustorrent.experiment;

import edu.campustorrent.util.Args;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** Writes a reproducible pseudo-random file (same seed + size = same bytes). Owner: Sambit Kumar Mohapatra. */
public final class SampleFileGenerator {
    private SampleFileGenerator() { }

    public static void generate(Path out, long sizeBytes, long seed) throws IOException {
        if (sizeBytes < 1) throw new IllegalArgumentException("size must be positive");
        if (out.toAbsolutePath().getParent() != null) Files.createDirectories(out.toAbsolutePath().getParent());
        Random r = new Random(seed);
        byte[] buf = new byte[64 * 1024];
        try (OutputStream os = Files.newOutputStream(out)) {
            long left = sizeBytes;
            while (left > 0) {
                r.nextBytes(buf);
                int n = (int) Math.min(buf.length, left);
                os.write(buf, 0, n);
                left -= n;
            }
        }
    }

    public static void main(String[] argv) throws Exception {
        try {
            Args a = new Args(argv);
            Path out = Path.of(a.require("out"));
            long kb = a.getLong("size-kb", 2048);
            generate(out, kb * 1024L, a.getLong("seed", 42));
            System.out.println("Wrote " + kb + " KiB to " + out);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("Usage: SampleFileGenerator --out <file> [--size-kb 2048] [--seed 42]");
            System.exit(2);
        }
    }
}
