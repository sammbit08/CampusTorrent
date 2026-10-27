# Interface contract: protocol layer (provider: Vabyasha Mohapatra)

Consumers: the peer classes (also Vabyasha), tests.

```java
// edu.campustorrent.protocol
enum MessageType { HANDSHAKE, BITFIELD, REQUEST, PIECE, CHOKE, UNCHOKE, ERROR, SHUTDOWN, GET_BITFIELD }
final class Message {
    static Message handshake(byte[] fileId32, String peerId); static Message bitfield(boolean[] bits);
    static Message getBitfield(); static Message request(int index); static Message piece(int index, byte[] data);
    static Message choke(); static Message unchoke(); static Message shutdown(); static Message error(int code, String reason);
    MessageType type(); byte[] payload();
    Handshake asHandshake(); boolean[] asBitfield(int pieceCount); int asIndex(); PiecePayload asPiece(); ErrorPayload asError();
}
final class MessageCodec { static void write(DataOutputStream, Message); static Message read(DataInputStream); static final int MAX_FRAME_BYTES; }
final class PeerConnection implements Closeable {
    static PeerConnection connect(String host, int port, int connectTimeoutMs, int readTimeoutMs) throws IOException;
    void send(Message m); Message receive(); Message.Handshake handshake(byte[] fileId, String peerId); void close();
}
class ProtocolException extends IOException {}
```

Rules: `receive()` throws `EOFException` on disconnect, `SocketTimeoutException` on timeout, `ProtocolException` on any malformed input.
Full byte layouts: `docs/protocol.md`.
