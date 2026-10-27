package edu.campustorrent.tracker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Reads a newline-terminated line with a hard length cap (so a bad peer cannot make us buffer forever). Owner: Samridhi Padhy. */
final class LineIO {
    private LineIO() { }

    /** @return the line without '\n' / '\r', or null on clean EOF before any byte; throws if longer than maxLen */
    static String readLine(InputStream in, int maxLen) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b < 0) {
                if (buf.size() == 0) return null;
                break;
            }
            if (b == '\n') break;
            if (b != '\r') buf.write(b);
            if (buf.size() > maxLen) throw new IOException("Line too long (max " + maxLen + ")");
        }
        return buf.toString(StandardCharsets.UTF_8);
    }
}
