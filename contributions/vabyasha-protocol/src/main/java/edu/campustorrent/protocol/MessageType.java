package edu.campustorrent.protocol;

/** Wire message types (see docs/protocol.md). Owner: Vabyasha Mohapatra. */
public enum MessageType {
    HANDSHAKE(1), BITFIELD(2), REQUEST(3), PIECE(4), CHOKE(5), UNCHOKE(6), ERROR(7), SHUTDOWN(8), GET_BITFIELD(9);

    private final int id;

    MessageType(int id) { this.id = id; }

    public int id() { return id; }

    public static MessageType fromId(int id) throws ProtocolException {
        for (MessageType t : values()) if (t.id == id) return t;
        throw new ProtocolException("Unknown message type " + id);
    }
}
