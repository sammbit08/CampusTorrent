package edu.campustorrent.tracker;

import java.io.IOException;

/** The tracker answered with an ERR line. Owner: Samridhi Padhy. */
public final class TrackerException extends IOException {
    private static final long serialVersionUID = 1L;

    private final String code;

    public TrackerException(String code, String message) {
        super(code + ": " + message);
        this.code = code;
    }

    public String code() { return code; }
}
