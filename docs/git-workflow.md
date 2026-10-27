# Git workflow, branch ownership and merge plan

Repository: https://github.com/sammbit08/CampusTorrent (nothing in this project has been pushed by the tooling that generated it; you push it yourselves with the commands below).

## Ground rules

1. **The repository root is canonical.** `contributions/<member>/` holds *handoff snapshots* of each member's own files. Never edit a pack and the root separately. Edit the root, then run `./scripts/sync-contribution-packs.sh`; `./scripts/sync-contribution-packs.sh --check` verifies there is no drift.
2. **One owner per file** (`docs/ownership.csv`, summarised in `docs/OWNERSHIP.md`). Do not edit someone else's file on your branch; ask them, or open a PR against their branch.
3. **Shared files** (`pom.xml`, `README.md`, `.gitignore`, `docs/architecture.md`, `docs/git-workflow.md`, `docs/viva-prep.md`, `docs/acceptance-checklist.md`, `docs/OWNERSHIP.md`) are merged by the **integration owner, Vabyasha Mohapatra**. Others send change requests (an issue or PR comment) instead of committing to them, so there are never two people overwriting the same lines.
4. No two classes share a fully qualified name. Each class has exactly one owner.
5. Never commit `target/`, `demo-work/`, `*.part` (already in `.gitignore`).

## Branches

| Member | Branch | Role |
|---|---|---|
| Priyanshu Nayak | `feature/piece-hashing` | manifest, splitter, hashing, piece store, selection |
| Samridhi Padhy | `feature/tracker-discovery` | tracker, registry, client, choking policy |
| Vabyasha Mohapatra | `feature/peer-protocol` | protocol, peer integration (also integration owner) |
| Sambit Kumar Mohapatra | `feature/integration-experiments` | harness, tests, experiments, scripts |

## Merge order (and why)

1. `feature/piece-hashing`: no dependencies on other branches.
2. `feature/tracker-discovery`: depends only on `util/Validation` (its own).
3. `feature/peer-protocol`: depends on 1 and 2 (peer classes use pieces, scheduler, tracker client and choking policy). Also carries `pom.xml`, `README.md`, `.gitignore` and the shared docs.
4. `feature/integration-experiments`: depends on everything; merged last. After this merge run the full test suite on `main`.

Each member's branch merges via pull request; at least one teammate reviews. After every merge the next person runs `git pull --rebase origin main` before continuing.

## Commands per member

Replace `<name>` as needed. Run from an empty working folder. If you start from the generated project instead of the repository, copy only *your* files (listed in your pack README) into the clone.

### Priyanshu Nayak: `feature/piece-hashing`

```bash
git clone https://github.com/sammbit08/CampusTorrent.git
cd CampusTorrent
git checkout -b feature/piece-hashing
# copy the contents of contributions/priyanshu-pieces/ over the clone (paths are repo-relative), then:
git add src/main/java/edu/campustorrent/pieces src/main/java/edu/campustorrent/scheduler \
        src/main/java/edu/campustorrent/util/PathSafety.java \
        src/test/java/edu/campustorrent/pieces src/test/java/edu/campustorrent/scheduler \
        docs/interfaces/pieces-api.md
git commit -m "Add manifest, file splitter and SHA-256 piece hashing"      # see the commit sequence in your pack README
git push -u origin feature/piece-hashing
# open a Pull Request: feature/piece-hashing -> main
```

### Samridhi Padhy: `feature/tracker-discovery`

```bash
git clone https://github.com/sammbit08/CampusTorrent.git && cd CampusTorrent
git checkout -b feature/tracker-discovery
git add src/main/java/edu/campustorrent/tracker src/main/java/edu/campustorrent/peer/ChokingPolicy.java \
        src/main/java/edu/campustorrent/util/Validation.java \
        src/test/java/edu/campustorrent/tracker src/test/java/edu/campustorrent/peer/ChokingPolicyTest.java \
        docs/interfaces/tracker-api.md
git commit -m "Add tracker server, peer registry with expiry and tracker client"
git push -u origin feature/tracker-discovery
```

### Vabyasha Mohapatra: `feature/peer-protocol` (integration owner)

```bash
git clone https://github.com/sammbit08/CampusTorrent.git && cd CampusTorrent
git checkout -b feature/peer-protocol
git pull origin main                 # make sure branches 1 and 2 are merged first
git add src/main/java/edu/campustorrent/protocol src/main/java/edu/campustorrent/peer \
        src/main/java/edu/campustorrent/util/Args.java src/main/java/edu/campustorrent/util/Log.java \
        src/main/java/edu/campustorrent/util/RateLimiter.java src/test/java/edu/campustorrent/protocol \
        docs/protocol.md docs/interfaces/protocol-api.md docs/interfaces/peer-integration.md \
        pom.xml README.md .gitignore config samples docs/architecture.md docs/git-workflow.md \
        docs/viva-prep.md docs/acceptance-checklist.md docs/OWNERSHIP.md docs/ownership.csv
# ChokingPolicy.java is Samridhi's file: do NOT re-add it here.
git commit -m "Add peer protocol, upload server, downloader and peer CLI"
git push -u origin feature/peer-protocol
```

### Sambit Kumar Mohapatra: `feature/integration-experiments`

```bash
git clone https://github.com/sammbit08/CampusTorrent.git && cd CampusTorrent
git checkout -b feature/integration-experiments
git pull origin main
git add src/main/java/edu/campustorrent/experiment src/test/java/edu/campustorrent/integration \
        src/test/java/edu/campustorrent/experiment scripts docs/testing-and-experiments.md \
        docs/interfaces/harness-api.md results
git commit -m "Add local swarm harness, experiment runner, integration tests and scripts"
git push -u origin feature/integration-experiments
```

## Sample commit messages

* `Add Manifest with per-piece SHA-256 and strict parser`
* `Reject corrupted and wrongly indexed pieces in PieceStore`
* `Implement rarest-first, random and sequential piece selection`
* `Add concurrent PeerRegistry with TTL expiry`
* `Add tracker request validation and error responses`
* `Free unchoke slot when a peer disconnects`
* `Add length-prefixed message framing with size limits`
* `Add symmetric versioned handshake`
* `Add experiment runner writing per-peer CSV rows`
* `Add integration test: tracker, 2 seeders, 4 leechers`

## Conflict handling

If `git pull --rebase origin main` reports a conflict in a file you do **not** own, do not resolve it in favour of your copy. Abort (`git rebase --abort`), tell the owner, and restore the upstream version with `git checkout origin/main -- <file>`. Conflicts should only ever happen in shared files, which only the integration owner edits.
