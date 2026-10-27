package edu.campustorrent.protocol;

import edu.campustorrent.util.Validation;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * One protocol message: a type plus an opaque payload with a type-specific layout
 * (documented in docs/protocol.md). Build with the static factories, read with the {@code as...} methods.
 * Owner: Vabyasha Mohapatra.
 */
public final class Message {
    public static final int PROTOCOL_VERSION = 1;
    private static final byte[] MAGIC = {'C', 'T', 'O', 'R'};
    private static final int MAX_ERROR_TEXT = 200;

    private final MessageType type;
    private final byte[] payload;

    private Message(MessageType type, byte[] payload) {
        this.type = type;
        this.payload = payload;
    }

    public MessageType type() { return type; }
    /** The raw payload; callers must not modify it. */
    public byte[] payload() { return payload; }

    /** Builds a message from a type and payload, enforcing the fixed payload sizes. */
    public static Message of(MessageType type, byte[] payload) throws ProtocolException {
        switch (type) {
            case CHOKE: case UNCHOKE: case SHUTDOWN: case GET_BITFIELD:
                if (payload.length != 0) throw new ProtocolException(type + " must have an empty payload");
                break;
            case REQUEST:
                if (payload.length != 4) throw new ProtocolException("REQUEST payload must be 4 bytes");
                break;
            default:
                break;
        }
        return new Message(type, payload);
    }

    // ---------------------------------------------------------------- factories

    public static Message handshake(byte[] fileId, String peerId) {
        return handshake(PROTOCOL_VERSION, fileId, peerId);
    }

    public static Message handshake(int version, byte[] fileId, String peerId) {
        if (fileId.length != 32) throw new IllegalArgumentException("file id must be 32 bytes");
        Validation.requirePeerId(peerId);
        byte[] id = peerId.getBytes(StandardCharsets.US_ASCII);
        ByteBuffer b = ByteBuffer.allocate(4 + 1 + 32 + 1 + id.length);
        b.put(MAGIC).put((byte) version).put(fileId).put((byte) id.length).put(id);
        return new Message(MessageType.HANDSHAKE, b.array());
    }

    public static Message bitfield(boolean[] bits) {
        byte[] out = new byte[(bits.length + 7) / 8];
        for (int i = 0; i < bits.length; i++) if (bits[i]) out[i / 8] |= (byte) (0x80 >> (i % 8));
        return new Message(MessageType.BITFIELD, out);
    }

    public static Message getBitfield() { return new Message(MessageType.GET_BITFIELD, new byte[0]); }

    public static Message request(int index) {
        return new Message(MessageType.REQUEST, ByteBuffer.allocate(4).putInt(index).array());
    }

    public static Message piece(int index, byte[] data) {
        return new Message(MessageType.PIECE, ByteBuffer.allocate(4 + data.length).putInt(index).put(data).array());
    }

    public static Message choke() { return new Message(MessageType.CHOKE, new byte[0]); }
    public static Message unchoke() { return new Message(MessageType.UNCHOKE, new byte[0]); }
    public static Message shutdown() { return new Message(MessageType.SHUTDOWN, new byte[0]); }

    public static Message error(int code, String reason) {
        byte[] text = reason.getBytes(StandardCharsets.UTF_8);
        if (text.length > MAX_ERROR_TEXT) text = Arrays.copyOf(text, MAX_ERROR_TEXT);
        return new Message(MessageType.ERROR, ByteBuffer.allocate(4 + text.length).putInt(code).put(text).array());
    }

    // ---------------------------------------------------------------- parsers

    public record Handshake(int version, byte[] fileId, String peerId) { }
    public record PiecePayload(int index, byte[] data) { }
    public record ErrorPayload(int code, String reason) { }

    public Handshake asHandshake() throws ProtocolException {
        expect(MessageType.HANDSHAKE);
        if (payload.length < 38) throw new ProtocolException("Handshake too short");
        if (!Arrays.equals(Arrays.copyOfRange(payload, 0, 4), MAGIC)) throw new ProtocolException("Bad handshake magic");
        int version = payload[4] & 0xff;
        byte[] fileId = Arrays.copyOfRange(payload, 5, 37);
        int idLen = payload[37] & 0xff;
        if (payload.length != 38 + idLen) throw new ProtocolException("Handshake length mismatch");
        String peerId = new String(payload, 38, idLen, StandardCharsets.US_ASCII);
        if (!Validation.isValidPeerId(peerId)) throw new ProtocolException("Invalid peer id in handshake");
        return new Handshake(version, fileId, peerId);
    }

    /** @param pieceCount the number of pieces in the manifest; the payload size and padding bits are validated against it */
    public boolean[] asBitfield(int pieceCount) throws ProtocolException {
        expect(MessageType.BITFIELD);
        if (payload.length != (pieceCount + 7) / 8) throw new ProtocolException("Bitfield has wrong length");
        boolean[] bits = new boolean[pieceCount];
        for (int i = 0; i < pieceCount; i++) bits[i] = (payload[i / 8] & (0x80 >> (i % 8))) != 0;
        int pad = payload.length * 8 - pieceCount;
        if (pad > 0 && (payload[payload.length - 1] & ((1 << pad) - 1)) != 0) {
            throw new ProtocolException("Bitfield has non-zero padding bits");
        }
        return bits;
    }

    public int asIndex() throws ProtocolException {
        expect(MessageType.REQUEST);
        return ByteBuffer.wrap(payload).getInt();
    }

    public PiecePayload asPiece() throws ProtocolException {
        expect(MessageType.PIECE);
        if (payload.length < 5) throw new ProtocolException("PIECE message too short");
        ByteBuffer b = ByteBuffer.wrap(payload);
        int index = b.getInt();
        byte[] data = new byte[payload.length - 4];
        b.get(data);
        return new PiecePayload(index, data);
    }

    public ErrorPayload asError() throws ProtocolException {
        expect(MessageType.ERROR);
        if (payload.length < 4) throw new ProtocolException("ERROR message too short");
        ByteBuffer b = ByteBuffer.wrap(payload);
        int code = b.getInt();
        String reason = new String(payload, 4, payload.length - 4, StandardCharsets.UTF_8);
        return new ErrorPayload(code, reason);
    }

    private void expect(MessageType t) throws ProtocolException {
        if (type != t) throw new ProtocolException("Expected " + t + " but message is " + type);
    }

    @Override
    public String toString() { return type + "(" + payload.length + " bytes)"; }

    /** Used by tests to serialise without a socket. */
    byte[] toFrameBytes() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        MessageCodec.write(new DataOutputStream(bos), this);
        return bos.toByteArray();
    }
}
