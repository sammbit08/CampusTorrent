package edu.campustorrent.protocol;

/** Numeric codes carried in ERROR messages. Owner: Vabyasha Mohapatra. */
public final class ErrorCode {
    public static final int BAD_REQUEST = 1;
    public static final int BAD_INDEX = 2;
    public static final int NOT_HAVE = 3;
    public static final int UNKNOWN_FILE = 4;
    public static final int VERSION_MISMATCH = 5;
    public static final int INTERNAL = 6;

    private ErrorCode() { }

    public static String name(int code) {
        switch (code) {
            case BAD_REQUEST: return "BAD_REQUEST";
            case BAD_INDEX: return "BAD_INDEX";
            case NOT_HAVE: return "NOT_HAVE";
            case UNKNOWN_FILE: return "UNKNOWN_FILE";
            case VERSION_MISMATCH: return "VERSION_MISMATCH";
            case INTERNAL: return "INTERNAL";
            default: return "CODE_" + code;
        }
    }
}
