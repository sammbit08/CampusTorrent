# Viva preparation

**One-sentence summary.** CampusTorrent splits a file into hashed pieces; a tracker tells peers who has the file; peers fetch missing pieces from each other over TCP, verifying every piece by SHA-256, choosing pieces with a pluggable strategy (rarest-first by default) and serving mainly peers that contribute.

## Architecture
Tracker + peers (seeder = has all pieces, leecher = missing some). See `docs/architecture.md`. A leecher that finishes keeps seeding. The experiment harness runs everything in one JVM on localhost.

## Sockets
* TCP `ServerSocket` per tracker and per peer; `Socket` for outgoing connections.
* Read timeouts (`setSoTimeout`) so a silent peer cannot hang us; connect timeouts; try-with-resources / `finally` to close.
* Framing: TCP is a byte stream, so every message is length-prefixed; the length is validated before allocating.
* Why TCP and not UDP for pieces: reliability and ordering come for free; UDP would need sequence numbers, retransmission and checksums. We hash-verify anyway.

## Tracker vs peer
The tracker is a directory: `ANNOUNCE`, `GET_PEERS`, `LEAVE`, entries expire after a TTL. It never sees file data, so it is not a bottleneck and not a point of trust. All data moves peer to peer.

## Hashing
Manifest = name, size, piece size, count, SHA-256 per piece. fileId = SHA-256 of the manifest, so equal ids mean equal hashes. Every received piece is checked for index, length and hash before being written; corrupted or wrongly indexed data is rejected (`PieceStoreTest`, and the fault-injection integration test). The finished file is re-verified before renaming `.part`.

## Rarest-first
Count, for each piece, how many connected peers have it; request the piece with the lowest count. Intent: keep scarce pieces replicated so the swarm survives seeders leaving and peers always have something to trade. Ties are broken deterministically (rotated scan from a per-peer offset). Random and sequential exist for comparison. **Be ready to explain our measured result:** with a seeder that never leaves, random beat rarest-first on this laptop, and sequential was slowest; see `docs/testing-and-experiments.md`.

## Choking
A peer only serves *unchoked* requesters. Leecher: top contributors (by bytes they sent us, decayed each round) plus one rotating optimistic slot. Seeder: rotates through all peers. Disconnected peers free their slot. This is **simple reciprocity, not full BitTorrent tit-for-tat**. Measured: a free-rider was *not* noticeably slower in our setup; choking mostly protected contributors from the free-rider's drain (high variance, n=3).

## Concurrency
One thread per connection on the serving side, one worker per remote peer on the downloading side. `DownloadState.claim` is synchronized so two workers never request the same piece; `PieceStore`, `Availability`, `ChokingPolicy` are synchronized; counters are atomic; the tracker registry is a `ConcurrentHashMap`.

## Limitations (say them before they ask)
Localhost only; simulated upload cap; simplified choking; no NAT traversal, DHT, resume, or encryption; pull-based bitfields (up to 200 ms stale); experiments use small files and n=3 runs, so differences smaller than the reported std-dev are not significant.

## Experiment design
Vary one factor at a time (strategy, seeders, free-rider/choking), fix file size, piece size and upload cap, seed per run, repeat 3 times, record to CSV, report mean +/- sample std-dev, say what was *not* supported.

## Likely questions
* *What stops a malicious peer from sending garbage?* Hash check; after 3 bad pieces we drop it for 5 s.
* *What if the tracker dies?* Existing connections continue; new discovery stops; peers log announce failures.
* *Why is the last piece shorter?* `size mod pieceSize`; handled by `Manifest.pieceLength`.
* *How is path traversal prevented?* `PathSafety.sanitizeFileName` (whitelist) + `resolveInside` normalisation check.
