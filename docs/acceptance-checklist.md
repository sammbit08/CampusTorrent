# Acceptance checklist

Tick each box only after you have run the command yourself. "Verified here" notes what was actually executed while this project was generated (Java 21 runtime, no Maven, no real JUnit available in that sandbox).

## Functionality
- [ ] Tracker registers peers, returns peer lists, expires stale peers. *(TrackerTest; verified here with a local JUnit stand-in)*
- [ ] Manifest holds name, size, piece size, count and SHA-256 per piece. *(ManifestAndSplitterTest)*
- [ ] Corrupted or wrongly indexed pieces are rejected. *(PieceStoreTest, SwarmIntegrationTest)*
- [ ] Protocol: handshake, bitfield, request, piece, error, shutdown, documented in `docs/protocol.md`.
- [ ] Rarest-first, random and sequential selection; no duplicate or in-progress pieces. *(PieceSelectorTest)*
- [ ] Choking limits uploads to contributors plus an optimistic slot. *(ChokingPolicyTest)*
- [ ] Seeder and leecher roles; leecher reassembles the file identically. *(SwarmIntegrationTest; scripts/run-demo.sh run once here)*
- [ ] 6+ peers incl. 2 seeders on one machine. *(trackerTwoSeedersAndFourLeechers..., run-demo.sh = 7 processes)*

## Robustness and security
- [ ] Socket timeouts, message-size limits, index validation, malformed input handled. *(ProtocolTest)*
- [ ] Output names sanitised (path traversal). *(ManifestAndSplitterTest.pathSafety...)*
- [ ] Peers never accept an unverified piece.

## Experiments
- [ ] `./scripts/run-experiments.sh` writes `results/experiments.csv` with measured values.
- [ ] `python3 scripts/plot_results.py` produces PNGs from that CSV.
- [ ] Any numbers quoted in the report come from your own CSV.

## Build and delivery
- [ ] `mvn -v` and `java -version` show Java 21 (or 17 after changing `maven.compiler.release`).
- [ ] `mvn clean verify` passes on **your** machine. *(NOT run here: Maven was unavailable. This is the one thing you must run.)*
- [ ] `./scripts/sync-contribution-packs.sh --check` reports no drift.
- [ ] Each member pushed their branch and opened a PR (`docs/git-workflow.md`).
