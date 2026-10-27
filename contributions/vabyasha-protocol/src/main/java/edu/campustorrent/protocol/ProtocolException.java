package edu.campustorrent.protocol;

import java.io.IOException;

/** The remote side violated the CampusTorrent peer protocol (bad frame, bad handshake, ...). Owner: Vabyasha Mohapatra. */
public class ProtocolException extends IOException {
    private static final long serialVersionUID = 1L;

    public ProtocolException(String message) { super(message); }
}
