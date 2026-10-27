package edu.campustorrent.util;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Prevents path traversal when a file name comes from a manifest or from the network.
 * Owner: Priyanshu Nayak.
 */
public final class PathSafety {
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._ -]{0,99}");
    private static final Set<String> RESERVED = Set.of("CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private PathSafety() { }

    /**
     * Returns the name unchanged if it is a plain, safe file name; otherwise throws.
     * Rules: 1-100 chars, starts with a letter/digit, only letters, digits, '.', '_', '-', ' ',
     * no "..", no trailing dot/space, not a Windows reserved device name.
     */
    public static String sanitizeFileName(String name) {
        if (name == null || !SAFE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Unsafe file name: " + name);
        }
        if (name.contains("..") || name.endsWith(".") || name.endsWith(" ")) {
            throw new IllegalArgumentException("Unsafe file name: " + name);
        }
        String stem = name.contains(".") ? name.substring(0, name.indexOf('.')) : name;
        if (RESERVED.contains(stem.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Reserved file name: " + name);
        }
        return name;
    }

    /** Resolves a file name inside a directory and guarantees the result stays directly inside it. */
    public static Path resolveInside(Path dir, String fileName) {
        sanitizeFileName(fileName);
        Path base = dir.toAbsolutePath().normalize();
        Path resolved = base.resolve(fileName).normalize();
        if (!base.equals(resolved.getParent())) {
            throw new IllegalArgumentException("Path escapes output directory: " + fileName);
        }
        return resolved;
    }
}
