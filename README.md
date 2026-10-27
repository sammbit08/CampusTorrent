# CampusTorrent

*A Peer-to-Peer System for Distributed Academic Resource Sharing*

A BitTorrent-lite peer-to-peer system for distributing study material (CS-30003, Coding Assignment 1, project P8, Path A).

**Team CS33:** Samridhi Padhy (24051045), Vabyasha Mohapatra (24051070), Priyanshu Nayak (24051118), Sambit Kumar Mohapatra (24051426).

**Repository:** https://github.com/sammbit08/CampusTorrent

Pure Java + Maven, no BitTorrent/P2P library. Python (matplotlib) is used only for plotting.

## What it does

A tracker lists which peers have a file; peers split the file into SHA-256-hashed pieces and exchange them over TCP with a small versioned protocol. Leechers select pieces using rarest-first (or random/sequential for comparison), verify every piece, and serve other peers according to a simple choking policy.

See `docs/architecture.md` and `docs/protocol.md` for details.

## Prerequisites

- JDK 21 (or a compatible version configured in `pom.xml`) and Maven 3.8+.
- Python 3 with matplotlib for plots: `pip install matplotlib`.
- No internet needed at runtime. Maven needs internet once to download dependencies.

## Quick start — Windows PowerShell

```powershell
java -version
mvn -v
mvn clean verify
.\scripts\run-demo.ps1
.\scripts\run-experiments.ps1 -Experiment all
python scripts\plot_results.py
```

If PowerShell blocks scripts, use `Set-ExecutionPolicy -Scope Process Bypass`.

## Quick start — Linux/macOS

```bash
java -version
mvn -v
mvn clean verify
./scripts/run-demo.sh
./scripts/run-experiments.sh all
python3 scripts/plot_results.py
```

If Maven is unavailable, `scripts/build.sh` or `scripts/build.ps1` can compile the project; Maven is required to run the Maven test suite.

## Project layout

- `src/main/java/` — Java application source, including tracker, peer networking, pieces, scheduler, experiments, utilities, and GUI.
- `src/test/java/` — automated tests.
- `scripts/` — build, demo, verification, experiment, and plotting scripts.
- `docs/` — architecture, protocol, project structure, ownership, testing, and presentation notes.
- `config/` and `samples/` — example settings and sample resources.
- `contributions/` — team members' hand-off snapshots.
- `results/` — experiment results and plots.

## Tests and verification

Run `mvn clean verify` to compile and run the configured test suite. See `docs/testing-and-experiments.md` for the test coverage and details about which tests have actually been executed.

The local demo uses a tracker, two seeders, and four leechers, then verifies the downloaded files against the original using SHA-256.

## Limitations

- Designed for a controlled localhost demonstration; no NAT traversal, DHT, encryption, resume, or multi-file torrents.
- TCP is used for piece transfer.
- Choking uses a simplified reciprocity policy rather than full BitTorrent tit-for-tat.
- Bandwidth limits are simulated, and single-computer measurements are not representative of real-world network performance.

## Contributing

See `docs/git-workflow.md` for branch, ownership, and merge instructions.
