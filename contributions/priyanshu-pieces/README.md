# Contribution pack: Priyanshu Nayak (24051118), file splitting, hashing, piece logic and selection

> **This folder is a hand-off snapshot.** The repository root is canonical; every file here is a byte-identical copy of the same repo path (`./scripts/sync-contribution-packs.sh --check`).

Branch: `feature/piece-hashing` (merge step 1, no dependencies).

## Your responsibility
Manifest/metainfo, splitting a file into fixed-size pieces, SHA-256 per piece, verified piece storage and in-order reassembly, availability counts and the three selection strategies (rarest-first with deterministic tie-break, random, sequential), safe file names.

## Files you own
| File | Purpose |
|---|---|
| `.../pieces/Manifest.java` | name, size, piece size, count, hashes; strict parser; fileId; `verifyPiece` |
| `.../pieces/FileSplitter.java` | streams a file into pieces / builds the manifest |
| `.../pieces/PieceHasher.java` | SHA-256 helpers |
| `.../pieces/PieceStore.java` | thread-safe verified storage, `.part` file, final verification and rename; seeder verification |
| `.../pieces/Reassembler.java` | rebuild from a map of pieces in index order |
| `.../pieces/MakeManifest.java` | CLI to create a manifest |
| `.../scheduler/Strategy.java`, `Availability.java`, `PieceSelector.java` | selection |
| `.../util/PathSafety.java` | path-traversal prevention |
| `src/test/.../pieces/ManifestAndSplitterTest.java`, `PieceStoreTest.java`, `src/test/.../scheduler/PieceSelectorTest.java` | tests |
| `docs/interfaces/pieces-api.md` | contract |

## Scheduling boundary
You own *what piece to pick* (`PieceSelector`, `Availability`). The loop that calls it (`peer/DownloadState`, `peer/Downloader`) belongs to Vabyasha.

## Public API
`docs/interfaces/pieces-api.md`: `Manifest`, `FileSplitter.createManifest/split/readPiece`, `PieceStore.createLeecher/createSeeder/acceptPiece/readPiece/bitfield/finish`, `Reassembler.reassemble`, `PieceSelector.next(have, inProgress, peerHas, availability)`.

## Dependencies on others
None. Others depend on you.

## Build and test your part
```bash
mvn -q -Dtest='ManifestAndSplitterTest,PieceStoreTest,PieceSelectorTest' test
java -cp target/classes edu.campustorrent.pieces.MakeManifest --file samples/study_notes.txt --piece-size-kb 1 --out /tmp/notes.manifest
```

## Suggested commits
1. `Add PieceHasher and PathSafety`
2. `Add Manifest with per-piece SHA-256 and strict parser`
3. `Add FileSplitter and MakeManifest CLI`
4. `Add PieceStore with verification, .part file and final check`
5. `Add Reassembler`
6. `Add Strategy, Availability and PieceSelector`
7. `Add pieces and scheduler tests`

Git commands: `docs/git-workflow.md`.

## Acceptance checks
- [ ] Split then reassemble gives a byte-identical file, last piece may be shorter.
- [ ] One flipped byte, a wrong index and a wrong length are all rejected.
- [ ] Seeder refuses a source file that does not match the manifest.
- [ ] Rarest-first picks the lowest-count piece; ties follow the documented rotation; never picks owned or in-progress pieces.
- [ ] `../x`, `a/b`, `.hidden`, `con.txt` are rejected as file names.

## Shared files: merge carefully
`pom.xml`, `README.md`, `.gitignore`, `docs/architecture.md`, `docs/git-workflow.md`, `docs/viva-prep.md`, `docs/acceptance-checklist.md`, `docs/OWNERSHIP.md`: integration owner (Vabyasha) merges them.

## Viva points
Why hash per piece; fileId = hash of manifest; why check index *and* length *and* hash; how pieces are placed by offset so order never matters; rarest-first intuition and our deterministic tie-break (and why the offset comes from `SplittableRandom`, not `Random`).
