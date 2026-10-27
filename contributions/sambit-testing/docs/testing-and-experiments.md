# Testing and experiments

## 1. Automated tests

Run everything (needs a JDK 21 and Maven, no internet except for Maven downloading JUnit once):

```bash
mvn -v && java -version
mvn clean verify
```

| Test class | Owner | What it proves |
|---|---|---|
| `ManifestAndSplitterTest` | Priyanshu | fixed-size splitting, short last piece, SHA-256 per piece, manifest round-trip, tamper/limit rejection, path safety |
| `PieceStoreTest` | Priyanshu | out-of-order pieces reassemble to an identical file; corrupt / wrongly indexed / wrong-length pieces rejected; seeder rejects a source that does not match the manifest |
| `PieceSelectorTest` | Priyanshu | rarest-first, tie-break rotation, random reproducibility, sequential, never picks owned/in-progress pieces |
| `TrackerTest` | Samridhi | registry, expiry with a fake clock, TCP announce/get/leave, bad ports/ids/commands, overlong input, 40 concurrent announces, tracker down |
| `ChokingPolicyTest` | Samridhi | unchoke slots, contributor priority, rotation, free-rider gets only the rotating slot, slot freed on disconnect |
| `ProtocolTest` | Vabyasha | codec round trip, bad lengths / unknown types / truncation, handshake success / version mismatch / file mismatch / timeout, invalid indices, garbage input, abrupt disconnect, choke, free-rider |
| `SwarmIntegrationTest` | Sambit | tracker + 2 seeders + 4 leechers byte-for-byte; every strategy with 6 peers; 1 seeder + 7 leechers; free-rider never uploads; corrupting seeder is detected; no seeder times out |
| `ExperimentSupportTest` | Sambit | CSV escaping/header handling; deterministic sample files |

Tests use only localhost and ephemeral ports (port 0), temp directories, and no internet.

### What was actually executed while generating this project

* The code was compiled with the JDK 21 compiler module (`java -m jdk.compiler/com.sun.tools.javac.Main`).
* **Maven and the real JUnit 5 jars were not available in that sandbox, so `mvn test` was never run.** To still execute the test logic, a tiny local stand-in implementing only the JUnit calls used (`@Test`, `@BeforeEach`, `@AfterEach`, `@TempDir`, and the `Assertions` methods used) was written outside the repository, and **all 66 tests passed with it, repeated 8 times in a row**. This is strong evidence but not the same as `mvn test`. Please run `mvn clean verify` yourselves and treat that as the authoritative result.
* `scripts/run-demo.sh` was run once end to end: tracker, 2 seeders and 4 leechers as 7 separate JVM processes; all 4 downloads matched the original SHA-256.
* The Windows PowerShell scripts were written but **not executed** (no PowerShell in the sandbox).
* While testing, three real defects were found and fixed: dead peers kept their unchoke slots; bitfields were refreshed only when a worker was idle; and rarest-first tie-break offsets came from `java.util.Random` with consecutive seeds (all leechers got the same offset, so they walked the file in lockstep). The last one made rarest-first look as bad as sequential until it was found.

## 2. Hypotheses (from the proposal)

* **H1** Rarest-first is faster than random or sequential selection.
* **H2** More seeders mean faster downloads.
* **H3** A free-rider ends up slower than contributors because of choking.

## 3. Variables

| | |
|---|---|
| Independent | selection strategy (H1); number of seeders 1/2/3 (H2); free-rider present, choking on/off, 1 or 2 seeders (H3) |
| Controlled | file 2 MiB pseudo-random (32 pieces of 64 KiB), upload cap 1 MiB/s per peer, choking round 300 ms, 2+1 unchoke slots, localhost |
| Seeded | per-run seed -> file contents, random selection, tie-break offsets |
| Dependent | download duration per leecher (ms), plus bytes up/down, pieces, retries, errors, choked waits |

Peer counts: strategy and seeders experiments use 1 (or n) seeder(s) and 5 leechers; the free-rider experiment uses 4 contributing leechers + 1 free-rider with 2 seeders (and again with 1). `ExperimentRunner` always uses one run = fresh tracker, fresh peers, fresh temp directory.

The upload cap is the **only simulated element**; without it a 2 MiB transfer on loopback finishes in tens of milliseconds and no strategy matters.

## 4. Procedure

```bash
./scripts/run-experiments.sh all                  # RUNS=3 by default; override with env vars RUNS, LEECHERS, FILE_KB, PIECE_KB, UPLOAD_KBPS
./scripts/run-experiments.sh strategy             # or seeders | freerider
java -cp target/classes edu.campustorrent.experiment.ExperimentRunner --experiment freerider --fr-seeders 1 --append --out results/experiments.csv
python3 scripts/plot_results.py --csv results/experiments.csv --out results/plots     # pip install matplotlib
```

PowerShell: `.\scripts\run-experiments.ps1 -Experiment all -Runs 3`, then `python scripts\plot_results.py`.

Keep the machine otherwise idle, close other heavy programs, and use at least 3 runs (more is better). Timings vary between machines; always quote your own CSV.

## 5. CSV schema (`results/experiments.csv`, one row per peer per run)

`experiment, config, run, strategy, seeders, leechers, free_riders, choking, file_size_bytes, piece_size_bytes, piece_count, upload_limit_bytes_per_sec, peer_id, role, is_free_rider, completed, verified, duration_ms, bytes_downloaded, bytes_uploaded, pieces_completed, retries, hash_failures, errors, choked_waits, timestamp_utc`

* `leechers` = contributing leechers; free-riders are counted separately in `free_riders`.
* `verified` = downloaded file equals the source byte for byte.
* `duration_ms` = from the leecher's `start()` to a verified, renamed file (0 for seeders).
* `retries` = repeated piece attempts (bad hash/length, request timeout); `errors` = connection/protocol errors; `choked_waits` = requests answered with CHOKE.

## 6. Measured results (this repository's `results/experiments.csv`)

Measured by running the commands above once in a Linux sandbox with Java 21.0.10; **3 runs per configuration**, mean of the contributing leechers per run, then mean +/- sample standard deviation across the 3 runs. All 228 rows have `completed=true` and `verified=true`, with 0 retries and 0 errors. Another machine will give different absolute numbers; with n=3 treat differences smaller than about one standard deviation as noise.

**Strategy (1 seeder, 5 leechers):**

| Strategy | Download time (s) | Bytes from seeder / from leechers (MB per run) |
|---|---|---|
| rarest-first | 4.18 +/- 0.51 | 4.59 / 5.90 |
| random | 2.87 +/- 0.15 | 3.17 / 7.32 |
| sequential | 7.09 +/- 0.11 | 7.62 / 2.86 |

**Seeders (rarest-first, 5 leechers):** 1 seeder 4.13 +/- 0.44 s; 2 seeders 2.26 +/- 0.12 s; 3 seeders 1.69 +/- 0.15 s.

**Free-rider (4 contributors + 1 free-rider):**

| Setup | Contributors (s) | Free-rider (s) |
|---|---|---|
| 2 seeders, choking on | 2.28 +/- 0.24 | 2.32 +/- 0.19 |
| 2 seeders, choking off | 2.95 +/- 0.35 | 2.60 +/- 0.36 |
| 2 seeders, no free-rider (baseline) | 2.34 +/- 0.53 | n/a |
| 1 seeder, choking on | 4.40 +/- 0.91 | 4.08 +/- 0.40 |
| 1 seeder, choking off | 5.82 +/- 0.97 | 4.99 +/- 0.63 |
| 1 seeder, no free-rider (baseline) | 4.57 +/- 1.12 | n/a |

The free-rider uploaded 0 bytes in every run.

### Interpretation (what the data does and does not support)

* **H2 supported.** More seeders gave clearly faster downloads (4.1 -> 2.3 -> 1.7 s, differences larger than the spread). Each seeder adds upload capacity in this bandwidth-capped setup.
* **H1 partly supported, partly contradicted.** Rarest-first beat sequential decisively (4.2 vs 7.1 s): sequential makes every leecher want the same piece, so leechers have nothing to trade (only 2.9 MB moved leecher-to-leecher versus 5.9 MB). But **random was faster than rarest-first** (2.9 vs 4.2 s). A plausible reason, not tested: our seeder never leaves, so scarcity never matters, and rarest-first deliberately pulls pieces that nobody else has from the seeder first, while random spreads leechers' needs more evenly early on. Rarest-first is designed to protect swarms whose seeders leave or fail; this experiment does not test that situation, so it cannot show the benefit. A follow-up would stop the seeder after a fixed time and compare completion rates.
* **H3 not supported in this setup.** The free-rider was not slower than contributors with choking on (2.32 vs 2.28 s; 4.08 vs 4.40 s). Likely reasons (untested): both seeders serve everyone by rotation regardless of contribution, and the file is so small the whole run lasts only a handful of choking rounds. What the data does show is a protective effect: with choking *off* the contributors slowed down relative to the no-free-rider baseline (2.95 vs 2.34 s; 5.82 vs 4.57 s), and with choking *on* they did not. That effect is within about one standard deviation for n=3, so it is suggestive, not conclusive. More runs, larger files, fewer unchoke slots and longer rounds are the next things to try.

## 7. Reproducibility notes

* A run's seed is its run number: it fixes the source file bytes, the random-selection sequence and the rarest-first tie-break offsets. Thread scheduling and the OS still add timing noise, so repeated runs differ slightly; this is why we report mean and standard deviation.
* Piece selection, choking rotation and the tracker are deterministic given the same event order; unit tests rely only on that deterministic logic.
* To rerun a single configuration, use `--experiment <name> --runs N --out <file>` (use `--append` to add to an existing CSV).
