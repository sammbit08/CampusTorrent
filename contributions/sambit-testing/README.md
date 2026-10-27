# Contribution pack: Sambit Kumar Mohapatra (24051426), multi-peer testing and experiments

> **This folder is a hand-off snapshot.** The repository root is canonical; every file here is a byte-identical copy of the same repo path (`./scripts/sync-contribution-packs.sh --check`). You do **not** own other members' core implementation files.

Branch: `feature/integration-experiments` (merge step 4, last).

## Your responsibility
Integration/system tests, the local multi-peer harness, the experiment runner and CSV metrics, plotting, run scripts and the reproducibility guide.

## Files you own
| File | Purpose |
|---|---|
| `.../experiment/LocalSwarm.java` | tracker + seeders + leechers in one JVM; byte-for-byte verification; returns metrics |
| `.../experiment/ExperimentRunner.java` | strategy / seeders / free-rider experiments -> CSV |
| `.../experiment/CsvWriter.java`, `SampleFileGenerator.java` | CSV output, reproducible sample files |
| `src/test/.../integration/SwarmIntegrationTest.java`, `.../experiment/ExperimentSupportTest.java` | integration tests |
| `scripts/*` (`run-demo`, `verify-downloads`, `run-experiments`, `build` in `.sh` and `.ps1`; `plot_results.py`; `sync-contribution-packs.sh`) | run, verify, plot |
| `docs/testing-and-experiments.md`, `docs/interfaces/harness-api.md` | hypotheses, variables, procedure, CSV schema, results, contract |

## Dependencies on others
Everything: `Peer`/`PeerConfig`/`PeerMetrics` (Vabyasha), `TrackerServer` (Samridhi), `FileSplitter`/`Manifest`/`Strategy` (Priyanshu). You only call their public APIs.

## Build and test your part
Merge the other three branches first, then:
```bash
mvn clean verify
./scripts/run-demo.sh            # PowerShell: .\scripts\run-demo.ps1
./scripts/run-experiments.sh all # writes results/experiments.csv
python3 scripts/plot_results.py  # writes results/plots/*.png
```

## Suggested commits
1. `Add SampleFileGenerator and CsvWriter`
2. `Add LocalSwarm harness with byte-for-byte verification`
3. `Add integration tests: 2 seeders + 4 leechers, all strategies, free-rider, corrupt seeder`
4. `Add ExperimentRunner`
5. `Add run, verify and build scripts for Linux/macOS and Windows`
6. `Add plotting script`
7. `Add measured results and testing-and-experiments.md` (only results you measured yourself)

Git commands: `docs/git-workflow.md`.

## Acceptance checks
- [ ] Tracker + 2 seeders + 4 leechers: every output equals the source byte for byte.
- [ ] Each strategy and each seeder count produces CSV rows (`completed=true`, `verified=true`).
- [ ] A free-rider uploads 0 bytes; its time is reported next to contributors'.
- [ ] A corrupting seeder never causes a corrupt file; with only that seeder the leecher does not complete.
- [ ] `plot_results.py` runs from your CSV; no number in the report is invented.

## Reproducibility
Seeds, file size, piece size, upload cap and slot settings are options (`--runs --leechers --file-kb --piece-kb --upload-kbps --round-ms --fr-seeders`). Repeat runs, keep the machine idle, quote mean and std-dev. Replace `results/` with your own measurements before submitting; the numbers shipped in `docs/testing-and-experiments.md` came from a different machine.

## Shared files: merge carefully
`pom.xml`, `README.md`, `.gitignore`, `docs/architecture.md`, `docs/git-workflow.md`, `docs/viva-prep.md`, `docs/acceptance-checklist.md`, `docs/OWNERSHIP.md`: integration owner (Vabyasha). `results/` is generated output: commit it from one person's branch only.

## Viva points
Experiment design (one factor at a time, fixed sizes, seeds, repeats), why an upload cap is needed on loopback, what each result does and does not show (random beat rarest-first; free-rider not slower; why), limits of single-laptop testing.
