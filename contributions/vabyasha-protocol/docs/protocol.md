# CampusTorrent peer protocol, version 1

Transport: **TCP**. Integers are big-endian. All sockets use a read timeout (default 15 s, `--socket-timeout-ms`) and a connect timeout (default 2 s).

## Framing

```
+------------------+------------+-------------------+
| length (int32)   | type (u8)  | payload (length-1)|
+------------------+------------+-------------------+
```

`length` counts the type byte plus payload. A frame is rejected *before any payload is read* if `length < 1` or `length > 4 194 304 + 1024` (4 MiB piece plus slack). Unknown types and wrong fixed payload sizes are protocol errors.

## Message types

| id | Name | Payload | Meaning |
|---|---|---|---|
| 1 | HANDSHAKE | `"CTOR"` (4) · version (u8) · fileId (32 raw SHA-256 bytes) · idLen (u8) · peerId (ASCII) | Both sides send it first |
| 2 | BITFIELD | ceil(n/8) bytes, MSB first, padding bits 0 | Which pieces the sender has |
| 3 | REQUEST | index (int32) | Ask for one piece |
| 4 | PIECE | index (int32) · data | Reply to REQUEST |
| 5 | CHOKE | empty | "I will not serve you now" |
| 6 | UNCHOKE | empty | "I will serve you" |
| 7 | ERROR | code (int32) · UTF-8 reason (<= 200 bytes) | Something was wrong |
| 8 | SHUTDOWN | empty | Graceful close |
| 9 | GET_BITFIELD | empty | Ask for a fresh BITFIELD |

peerId: 1-32 chars of `[A-Za-z0-9._-]`. fileId = SHA-256 of the manifest text.

Error codes: 1 BAD_REQUEST, 2 BAD_INDEX, 3 NOT_HAVE, 4 UNKNOWN_FILE, 5 VERSION_MISMATCH, 6 INTERNAL.

## Conversation

1. **Handshake (symmetric).** Each side sends HANDSHAKE, reads the other's, then checks magic, version (must equal 1) and fileId. On mismatch it sends ERROR (`VERSION_MISMATCH` / `UNKNOWN_FILE` / `BAD_REQUEST`) and closes.
2. The **serving side** then sends `BITFIELD` followed by one `UNCHOKE` or `CHOKE` (initial state).
3. The **requesting side** sends `REQUEST i`. The server answers with exactly one of:
   * `PIECE i data`: served;
   * `CHOKE`: not served now, ask again later (the requester backs off about round/4 ms);
   * `ERROR BAD_INDEX`: index out of range (connection stays open);
   * `ERROR NOT_HAVE`: server does not have the piece yet.
4. `GET_BITFIELD` is answered with `BITFIELD`.
5. `SHUTDOWN` closes the conversation. Any other message from a requester gets `ERROR BAD_REQUEST` and the connection is closed.

A requester **never trusts** a PIECE: it checks that the index equals the one requested, that the length equals the manifest's piece length and that SHA-256 equals the manifest hash. A bad piece is discarded and counted (`retries`, `hash_failures`); after 3 bad pieces on one connection the peer is dropped and not contacted again for 5 s.

## Byte-level examples

`REQUEST 7` is 9 bytes:

```
00 00 00 05   length = 5 (type + 4)
03            type REQUEST
00 00 00 07   piece index 7
```

`CHOKE` is 5 bytes: `00 00 00 01 05`.

HANDSHAKE from peer `L1` (payload 40 bytes, so length = 41 = 0x29; 45 bytes on the wire):

```
00 00 00 29 01 43 54 4f 52 01 <32 bytes fileId> 02 4c 31
            |  C  T  O  R  ver                 idLen 'L' '1'
            type
```

## Tracker protocol (separate, text over TCP, one request per connection)

```
ANNOUNCE <fileId> <peerId> <port>   -> OK
GET_PEERS <fileId> [excludePeerId]  -> PEERS n  then n lines "<peerId> <host> <port>"
LEAVE <fileId> <peerId>             -> OK
errors                              -> ERR <CODE> <text>   (BAD_REQUEST, BAD_ID, BAD_PORT, UNKNOWN_COMMAND, FULL)
```

Request lines are at most 256 bytes. The registered host is the address the tracker sees on the TCP connection. Registrations expire after the tracker TTL (default 30 s) unless refreshed; peers re-announce every 5 s.

## Limits (all enforced)

| Item | Limit |
|---|---|
| Frame | 4 MiB + 1 KiB |
| Piece size | 1 byte to 4 MiB |
| File size | 512 MiB, at most 100 000 pieces |
| Manifest file | 16 MiB |
| Inbound connections per peer | 64 |
| Tracker: files / peers per file | 1000 / 500 |

## Test-only fault injection

`--corrupt-uploads` (`PeerConfig.corruptUploads`) makes a peer flip a byte in every piece it sends. It exists only so the tests can prove corrupted pieces are rejected.
