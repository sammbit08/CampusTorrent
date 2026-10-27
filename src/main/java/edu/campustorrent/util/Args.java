package edu.campustorrent.util;

import java.util.HashMap;
import java.util.Map;

/** Tiny "--key value" / "--flag" command line parser. Owner: Vabyasha Mohapatra. */
public final class Args {
    private final Map<String, String> values = new HashMap<>();

    public Args(String[] argv) {
        for (int i = 0; i < argv.length; i++) {
            String a = argv[i];
            if (!a.startsWith("--") || a.length() < 3) {
                throw new IllegalArgumentException("Unexpected argument: " + a);
            }
            String key = a.substring(2);
            if (i + 1 < argv.length && !argv[i + 1].startsWith("--")) {
                values.put(key, argv[++i]);
            } else {
                values.put(key, "true");
            }
        }
    }

    public boolean has(String key) { return values.containsKey(key); }

    public String get(String key, String def) { return values.getOrDefault(key, def); }

    public String require(String key) {
        String v = values.get(key);
        if (v == null) throw new IllegalArgumentException("Missing required option --" + key);
        return v;
    }

    public int getInt(String key, int def) {
        String v = values.get(key);
        if (v == null) return def;
        try { return Integer.parseInt(v); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("--" + key + " must be an integer: " + v); }
    }

    public long getLong(String key, long def) {
        String v = values.get(key);
        if (v == null) return def;
        try { return Long.parseLong(v); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("--" + key + " must be a number: " + v); }
    }

    public boolean getFlag(String key) { return "true".equalsIgnoreCase(values.get(key)); }
}
