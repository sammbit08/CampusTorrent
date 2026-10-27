# Contribution pack: Samridhi Padhy (24051045), tracker, peer discovery and choking policy

> **This folder is a hand-off snapshot.** The repository root is canonical. Every file here is a byte-identical copy of the same path at the project root (`./scripts/sync-contribution-packs.sh --check`). Copy the contents of this folder over a clone of the repository (paths are repo-relative).

Branch: `feature/tracker-discovery`

## Your responsibility
The tracker (register peers, announce which file they have, return peer lists, expire stale registrations, clear error responses, concurrent-safe), plus the choking/reciprocity policy and input validation shared by everyone.

## Files you own
| File | Purpose |
|---|---|
| `src/main/java/edu/campustorrent/tracker/TrackerServer.java` | TCP server, thread pool, janitor |
| `.../tracker/TrackerProtocol.java` | request -> response logic (pure, unit-testable) |
| `.../tracker/PeerRegistry.java` | `ConcurrentHashMap` registry with TTL expiry and size limits |
| `.../tracker/TrackerClient.java`, `TrackerException.java` | client used by peers |
| `.../tracker/PeerAddress.java`, `LineIO.java`, `TrackerMain.java` | record, bounded line reader, CLI |
| `src/main/java/edu/campustorrent/peer/ChokingPolicy.java` | unchoke slots, contributor priority, optimistic rotation, slot release |
| `src/main/java/edu/campustorrent/util/Validation.java` | peer/file id and port validation |
| `src/test/java/edu/campustorrent/tracker/TrackerTest.java`, `.../peer/ChokingPolicyTest.java` | your tests |
| `docs/interfaces/tracker-api.md` | the contract others rely on |

## Public API
`TrackerServer(bindHost, port, ttlMillis)`, `start()`, `getPort()`, `close()`; `TrackerClient.announce/getPeers/leave`; `PeerRegistry.announce/getPeers/remove/purgeExpired`; `ChokingPolicy(enabled, regularSlots, optimisticSlots, seed, seeding)` with `peerSeen/peerGone/recordDownloaded/isUnchoked/recomputeRound`. Wire format: "Tracker protocol" in `docs/protocol.md`.

## Dependencies on others
None for compilation: your files use only the JDK and your own `Validation`. Vabyasha's `Peer`/`UploadServer`/`Downloader` call your client and policy; Sambit's `LocalSwarm` starts your `TrackerServer`.

## Build and test your part
```bash
mvn -q -Dtest='TrackerTest,ChokingPolicyTest' test
```
(Without Maven: compile `src/main` with javac and run the tests from an IDE.) Try it by hand: `java -cp target/classes edu.campustorrent.tracker.TrackerMain --port 6969`, then `printf 'GET_PEERS %064d\n' 0 | nc 127.0.0.1 6969`.

## Suggested commits
1. `Add Validation for peer ids, file ids and ports`
2. `Add concurrent PeerRegistry with TTL expiry and limits`
3. `Add TrackerProtocol request handling with clear error codes`
4. `Add TrackerServer, TrackerClient and TrackerMain`
5. `Add ChokingPolicy with contributor priority and slot release`
6. `Add tracker and choking tests`

Git commands: see `docs/git-workflow.md`. Open a PR `feature/tracker-discovery` -> `main`; this is merge step 2 (after Priyanshu's).

## Acceptance checks
- [ ] Two peers announce; each sees the other, never itself.
- [ ] A peer that stops announcing disappears after the TTL.
- [ ] Oversize / binary / malformed requests return `ERR ...` and the tracker keeps running.
- [ ] 40 concurrent announces are all recorded.
- [ ] Peer limit per file and file limit enforced.
- [ ] Choking: contributors always unchoked; non-contributors share one rotating slot; a disconnected peer frees its slot.

## Shared files: merge carefully
`pom.xml`, `README.md`, `.gitignore`, `docs/architecture.md`, `docs/git-workflow.md`, `docs/viva-prep.md`, `docs/acceptance-checklist.md`, `docs/OWNERSHIP.md`: owned by the integration owner (Vabyasha). Send change requests instead of editing them.

## Viva points
Tracker vs peer (directory only, no data), why entries expire (crashed peers), why the host comes from the socket (cannot be spoofed in the request), concurrency in the registry, what the choking policy does and does *not* do (not full tit-for-tat).
