package edu.campustorrent.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Minimal synchronized logger. Owner: Vabyasha Mohapatra. */
public final class Log {
    public enum Level { DEBUG, INFO, WARN, ERROR, OFF }

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static volatile Level level = Level.INFO;

    private Log() { }

    public static void setLevel(Level l) { level = l; }

    public static void debug(String tag, String msg) { log(Level.DEBUG, tag, msg); }
    public static void info(String tag, String msg) { log(Level.INFO, tag, msg); }
    public static void warn(String tag, String msg) { log(Level.WARN, tag, msg); }
    public static void error(String tag, String msg) { log(Level.ERROR, tag, msg); }

    private static void log(Level l, String tag, String msg) {
        if (l.ordinal() < level.ordinal()) return;
        String line = LocalTime.now().format(FMT) + " " + l + " [" + tag + "] " + msg;
        synchronized (Log.class) {
            (l.ordinal() >= Level.WARN.ordinal() ? System.err : System.out).println(line);
        }
    }
}
