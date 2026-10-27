# CampusTorrent

A BitTorrent-lite peer-to-peer system for distributing study material (CS-30003, Coding Assignment 1, project P8, Path A).
Team CS33: Samridhi Padhy (24051045), Vabyasha Mohapatra (24051070), Priyanshu Nayak (24051118), Sambit Kumar Mohapatra (24051426).
Repository: https://github.com/sammbit08/CampusTorrent

Pure Java 21 + Maven, no BitTorrent/P2P library. Python (matplotlib) is used only for plotting.

## What it does

A tracker lists which peers have a file; peers split the file into SHA-256-hashed pieces and exchange them over TCP with a small versioned protocol; leechers pick pieces rarest-first (or random / sequential for comparison), verify every piece, and serve other peers according to a simple choking policy. Details: `docs/architecture.md`, `docs/protocol.md`.

## Prerequisites

* JDK 21 (17 works if you change `maven.compiler.release` in `pom.xml`) and Maven 3.8+. Check: `java -version`, `mvn -v`.
* Python 3 with matplotlib for plots: `pip install matplotlib`.
* No internet needed at run time. Maven needs it once to download JUnit.

## Quick start (Linux / macOS)

```bash
java -version && mvn -v
mvn clean verify                      # compile + all tests
./scripts/run-demo.sh                 # tracker + 2 seeders + 4 leechers, then SHA-256 check
./scripts/run-experiments.sh all      # writes results/experiments.csv
python3 scripts/plot_results.py       # writes results/plots/*.png
```

## Quick start (Windows PowerShell)

```powershell
java -version; mvn -v
mvn clean verify
.\scripts\run-demo.ps1
.\scripts\run-experiments.ps1 -Experiment all
python scripts\plot_results.py
```

(If PowerShell blocks scripts: `Set-ExecutionPolicy -Scope Process Bypass`.) No Maven? `scripts/build.sh` / `build.ps1` fall back to plain `javac`; `mvn` is needed for the tests.

## Running the pieces by hand (one terminal each)

```bash
mvn -q compile
mkdir -p demo-work/source
java -cp target/classes edu.campustorrent.experiment.SampleFileGenerator --out demo-work/source/studyguide.bin --size-kb 4096
java -cp target/classes edu.campustorrent.pieces.MakeManifest --file demo-work/source/studyguide.bin --piece-size-kb 64 --out demo-work/studyguide.manifest

# Terminal 1 - tracker
java -cp target/classes edu.campustorrent.tracker.TrackerMain --port 6969
# Terminal 2 - seeder S1            Terminal 3 - seeder S2 (same file, different port)
java -cp target/classes edu.campustorrent.peer.PeerMain --role seeder --peer-id S1 --port 6101 --tracker-port 6969 --manifest demo-work/studyguide.manifest --file demo-work/source/studyguide.bin
java -cp target/classes edu.campustorrent.peer.PeerMain --role seeder --peer-id S2 --port 6102 --tracker-port 6969 --manifest demo-work/studyguide.manifest --file demo-work/source/studyguide.bin
# Terminals 4-7 - leechers L1..L4 (distinct ports and output folders)
java -cp target/classes edu.campustorrent.peer.PeerMain --role leecher --peer-id L1 --port 6201 --tracker-port 6969 --manifest demo-work/studyguide.manifest --output-dir demo-work/out/L1 --linger-seconds 10
# ... repeat with L2/6202, L3/6203, L4/6204
# verify
./scripts/verify-downloads.sh demo-work/source/studyguide.bin demo-work/out/L*/studyguide.bin
```

On PowerShell use `\` paths and `.\scripts\verify-downloads.ps1 -Original ... -Downloads a,b,c`.
Other run parameters: ports and sizes are all options/environment variables (`TRACKER_PORT`, `SEED_BASE_PORT`, `LEECH_BASE_PORT`, `FILE_KB`, `PIECE_KB`, `UPLOAD_KBPS`, `STRATEGY` for `run-demo.sh`; parameters of the same names for `run-demo.ps1`).

### Peer options

| Option | Default | Meaning |
|---|---|---|
| `--role seeder\|leecher`, `--peer-id`, `--manifest` | required | identity and file |
| `--file` | seeder only | complete source file (verified against the manifest at start) |
| `--output-dir` | leecher only | where the download goes (`name.part` then renamed) |
| `--host`, `--port` | 127.0.0.1, 0 | bind address, listening port (0 = free port) |
| `--tracker-host`, `--tracker-port` | 127.0.0.1, 6969 | tracker |
| `--strategy` | rarest-first | `rarest-first`, `random`, `sequential` |
| `--seed` | 0 | seed for random selection and rarest-first tie-break offset |
| `--upload-kbps` | 0 (unlimited) | simulated upload bandwidth cap |
| `--unchoke-slots`, `--optimistic-slots`, `--round-ms` | 2, 1, 1000 | choking policy |
| `--no-choking` | off | serve everybody (experiment baseline) |
| `--free-rider` | off | download but never upload |
| `--linger-seconds`, `--timeout-seconds`, `--run-seconds` | 0, 300, 0 | keep seeding after completion; give-up time; seeder lifetime |
| `--socket-timeout-ms`, `--discovery-ms`, `--verbose` | 15000, 500, off | timeouts, tracker polling, debug logs |

Tracker: `--host`, `--port` (6969), `--ttl-seconds` (30). `config/peer.example.properties` lists the same names as an example.

## Project layout

See `docs/project-tree.txt` for the full tree and `docs/OWNERSHIP.md` for who owns which file.

```
src/main/java/edu/campustorrent/{tracker,protocol,peer,pieces,scheduler,experiment,util}
src/test/java/edu/campustorrent/...      JUnit 5 tests
scripts/                                 demo, verify, experiments, plotting, build (sh + ps1)
docs/                                    architecture, protocol, interfaces/, git workflow, testing, viva, checklist
contributions/<member>/                  hand-off snapshots of each member's own files + a role README
results/                                 measured CSV and plots from the run described in docs/testing-and-experiments.md
samples/, config/                        sample file and example settings
```

The repository root is canonical. `contributions/` are copies for hand-off; `./scripts/sync-contribution-packs.sh --check` verifies they match.

## Tests

`mvn clean verify`. Unit, protocol, integration (tracker + 2 seeders + 4 leechers, byte-for-byte check), a 6-peer run for every strategy, and a 1-seeder + 7-leecher run. All local; no internet. See `docs/testing-and-experiments.md` for what each test proves **and for exactly what was and was not executed when this project was generated** (short version: compiled and 66 tests run repeatedly against a local JUnit stand-in; real `mvn test` not run in that environment; please run it).

## Troubleshooting

* `Address already in use`: choose other ports (`TRACKER_PORT=7000 ./scripts/run-demo.sh`), or stop old Java processes (`pkill -f edu.campustorrent`; on Windows `Get-Process java | Stop-Process`).
* Leecher never finishes: is the tracker running and are manifest paths identical for all peers? Use `--verbose`. Remember the tracker forgets peers after `--ttl-seconds` without a re-announce.
* `Source file does not match manifest`: the seeder's file differs from the one the manifest was made from; recreate the manifest.
* `Unsafe file name`: manifest names may only contain letters, digits, `.`, `_`, `-`, space and must start with a letter or digit.
* `mvn: command not found`: install Maven or use `scripts/build.sh` for compilation only.
* Firewall pop-ups on Windows: allow Java on private networks (everything stays on 127.0.0.1).

## Limitations

* Localhost / controlled demonstration only; no NAT traversal, DHT, encryption, resume, or multi-file torrents.
* TCP for piece data (the proposal's UDP idea was deliberately not implemented; see `docs/architecture.md`).
* Choking is a simple reciprocity policy, **not** BitTorrent's full tit-for-tat.
* Bandwidth is simulated by an upload cap; single-laptop results are not realistic network results.
* Bitfields are pulled every 200 ms rather than pushed (HAVE), so availability can be slightly stale.
* Measured results (`docs/testing-and-experiments.md`) use 2 MiB files and 3 runs: random beat rarest-first, and the free-rider was not slower; those hypotheses were **not** confirmed.

## Contributing

Branches, ownership and merge order: `docs/git-workflow.md`.
