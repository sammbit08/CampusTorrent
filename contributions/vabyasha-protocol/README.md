# Contribution pack: Vabyasha Mohapatra (24051070), peer protocol and peer integration (integration owner)

> **This folder is a hand-off snapshot.** The repository root is canonical; every file here is a byte-identical copy of the same repo path (`./scripts/sync-contribution-packs.sh --check`).

Branch: `feature/peer-protocol`. You are also the **integration owner**: you merge the shared files and the final integrated build.

## Your responsibility
Versioned peer protocol over TCP (framing, handshake, messages, errors, shutdown), the peer's client/server messaging (`UploadServer`, `Downloader`), the `Peer` that wires every module together, the CLI, and `docs/protocol.md`.

## Files you own
| File | Purpose |
|---|---|
| `.../protocol/MessageType.java`, `Message.java`, `MessageCodec.java`, `ErrorCode.java`, `ProtocolException.java` | message set, payload layouts, framing with size limits |
| `.../protocol/PeerConnection.java` | socket + timeouts + symmetric handshake |
| `.../peer/UploadServer.java` | serving side: handshake, bitfield, requests, choke replies, throttle |
| `.../peer/Downloader.java`, `DownloadState.java` | discovery thread, per-peer workers, piece claiming, verification flow |
| `.../peer/Peer.java`, `PeerConfig.java`, `PeerMetrics.java`, `PeerMain.java` | lifecycle, settings, counters, CLI |
| `.../util/Args.java`, `Log.java`, `RateLimiter.java` | CLI parsing, logging, simulated bandwidth |
| `src/test/java/edu/campustorrent/protocol/ProtocolTest.java` | protocol tests |
| `docs/protocol.md`, `docs/interfaces/protocol-api.md`, `docs/interfaces/peer-integration.md` | documentation and contracts |

## Public API
See `docs/interfaces/protocol-api.md` and `docs/interfaces/peer-integration.md`: `Message` factories/parsers, `MessageCodec.read/write`, `PeerConnection.connect/handshake/send/receive`, `Peer(PeerConfig)`, `start()`, `awaitComplete()`, `metrics()`, `close()`.

## Dependencies on other members' interfaces
* Priyanshu: `Manifest`, `PieceStore`, `Availability`, `PieceSelector`, `Strategy`, `PathSafety` (`docs/interfaces/pieces-api.md`).
* Samridhi: `TrackerClient`, `PeerAddress`, `ChokingPolicy`, `Validation` (`docs/interfaces/tracker-api.md`).
* Sambit uses your `Peer` (`LocalSwarm`).

## Build and test your part
Merge Priyanshu's and Samridhi's branches first, then:
```bash
mvn -q -Dtest='ProtocolTest' test
```

## Suggested commits
1. `Add message types, payload layouts and error codes`
2. `Add length-prefixed framing with size limits`
3. `Add PeerConnection with timeouts and symmetric versioned handshake`
4. `Add UploadServer with choke handling and simulated bandwidth`
5. `Add Downloader workers with verification and retry accounting`
6. `Add Peer, PeerConfig and PeerMain`
7. `Add protocol tests and docs/protocol.md`
8. `Add pom.xml, README, shared docs` (integration owner only)

Git commands: `docs/git-workflow.md`. Merge step 3 (after Priyanshu and Samridhi). Do **not** re-add Samridhi's `ChokingPolicy.java`.

## Acceptance checks
- [ ] Handshake succeeds; wrong version or file id is rejected on both sides.
- [ ] Zero / negative / 2 GiB lengths, unknown types, truncated frames and HTTP garbage never crash a peer.
- [ ] Invalid piece indices get `ERROR BAD_INDEX`; valid ones get a hash-verified piece.
- [ ] Silent peers cause a timeout, not a hang; abrupt disconnects are survived.
- [ ] A leecher never stores a piece that fails index/length/hash checks.

## Shared files you merge (integration owner)
`pom.xml`, `README.md`, `.gitignore`, `config/`, `samples/`, `docs/architecture.md`, `docs/git-workflow.md`, `docs/viva-prep.md`, `docs/acceptance-checklist.md`, `docs/OWNERSHIP.md`, `docs/ownership.csv`. After the final merge: `mvn clean verify`, `./scripts/run-demo.sh`, `./scripts/sync-contribution-packs.sh --check`.

## Viva points
Why length-prefix framing; why validate length before allocating; symmetric handshake and versioning; why TCP not UDP; how timeouts/disconnects are handled; thread-per-connection model; how workers avoid requesting the same piece.
