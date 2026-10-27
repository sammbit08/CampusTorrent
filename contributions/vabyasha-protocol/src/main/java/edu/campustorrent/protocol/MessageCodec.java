package edu.campustorrent.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Length-prefixed framing.
 * <pre>
 *   +----------------+-----------+-----------------+
 *   | length (int32) | type (u8) | payload (n bytes)|     length = 1 + n, big-endian
 *   +----------------+-----------+-----------------+
 * </pre>
 * The length is validated BEFORE any payload buffer is allocated, so a hostile peer cannot make us
 * allocate gigabytes. Owner: Vabyasha Mohapatra.
 */
public final class MessageCodec {
    /** Largest legal frame: a 4 MiB piece + type + index, with a little slack. */
    public static final int MAX_FRAME_BYTES = 4 * 1024 * 1024 + 1024;

    private MessageCodec() { }

    public static void write(DataOutputStream out, Message m) throws IOException {
        byte[] p = m.payload();
        int len = 1 + p.length;
        if (len > MAX_FRAME_BYTES) throw new IllegalArgumentException("Message too large: " + len);
        out.writeInt(len);
        out.writeByte(m.type().id());
        out.write(p);
        out.flush();
    }

    /**
     * @throws java.io.EOFException       if the stream ends (peer disconnected)
     * @throws java.net.SocketTimeoutException if the socket read timeout expires
     * @throws ProtocolException          for a bad length, unknown type or malformed fixed-size payload
     */
    public static Message read(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len < 1 || len > MAX_FRAME_BYTES) throw new ProtocolException("Invalid frame length " + len);
        MessageType type = MessageType.fromId(in.readUnsignedByte());
        byte[] payload = new byte[len - 1];
        in.readFully(payload);
        return Message.of(type, payload);
    }
}
