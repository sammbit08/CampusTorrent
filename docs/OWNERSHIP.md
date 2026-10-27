# File ownership

Single source of truth: `docs/ownership.csv` (generated table below). **Integration owner: Vabyasha Mohapatra**, who merges the shared files and the final build. Sambit owns tests, harness, experiments and scripts only, never other members' core implementation files.

Merge order: 1 `feature/piece-hashing` (Priyanshu), 2 `feature/tracker-discovery` (Samridhi), 3 `feature/peer-protocol` (Vabyasha), 4 `feature/integration-experiments` (Sambit). See `docs/git-workflow.md`.

Boundary decisions made where the proposal was ambiguous: piece *selection* (`scheduler/`) belongs to Priyanshu; the download loop that calls it belongs to Vabyasha; the choking policy was assigned to Samridhi (tracker/discovery is the lighter workload); shared utilities were split by who needs them most (`Validation` Samridhi, `PathSafety` Priyanshu, `Args`/`Log`/`RateLimiter` Vabyasha).

| File | Owner |
|---|---|
| `src/main/java/edu/campustorrent/tracker/LineIO.java` | Samridhi Padhy |
| `src/main/java/edu/campustorrent/tracker/PeerAddress.java` | Samridhi Padhy |
| `src/main/java/edu/campustorrent/tracker/PeerRegistry.java` | Samridhi Padhy |
| `src/main/java/edu/campustorrent/tracker/TrackerClient.java` | Samridhi Padhy |
| `src/main/java/edu/campustorrent/tracker/TrackerException.java` | Samridhi Padhy |
| `src/main/java/edu/campustorrent/tracker/TrackerMain.java` | Samridhi Padhy |
| `src/main/java/edu/campustorrent/tracker/TrackerProtocol.java` | Samridhi Padhy |
| `src/main/java/edu/campustorrent/tracker/TrackerServer.java` | Samridhi Padhy |
| `src/main/java/edu/campustorrent/peer/ChokingPolicy.java` | Samridhi Padhy |
| `src/main/java/edu/campustorrent/util/Validation.java` | Samridhi Padhy |
| `src/test/java/edu/campustorrent/tracker/TrackerTest.java` | Samridhi Padhy |
| `src/test/java/edu/campustorrent/peer/ChokingPolicyTest.java` | Samridhi Padhy |
| `docs/interfaces/tracker-api.md` | Samridhi Padhy |
| `src/main/java/edu/campustorrent/protocol/ErrorCode.java` | Vabyasha Mohapatra |
| `src/main/java/edu/campustorrent/protocol/Message.java` | Vabyasha Mohapatra |
| `src/main/java/edu/campustorrent/protocol/MessageCodec.java` | Vabyasha Mohapatra |
| `src/main/java/edu/campustorrent/protocol/MessageType.java` | Vabyasha Mohapatra |
| `src/main/java/edu/campustorrent/protocol/PeerConnection.java` | Vabyasha Mohapatra |
| `src/main/java/edu/campustorrent/protocol/ProtocolException.java` | Vabyasha Mohapatra |
| `src/main/java/edu/campustorrent/peer/Peer.java` | Vabyasha Mohapatra |
| `src/main/java/edu/campustorrent/peer/PeerConfig.java` | Vabyasha Mohapatra |
| `src/main/java/edu/campustorrent/peer/PeerMetrics.java` | Vabyasha Mohapatra |
| `src/main/java/edu/campustorrent/peer/PeerMain.java` | Vabyasha Mohapatra |
| `src/main/java/edu/campustorrent/peer/UploadServer.java` | Vabyasha Mohapatra |
| `src/main/java/edu/campustorrent/peer/Downloader.java` | Vabyasha Mohapatra |
| `src/main/java/edu/campustorrent/peer/DownloadState.java` | Vabyasha Mohapatra |
| `src/main/java/edu/campustorrent/util/Args.java` | Vabyasha Mohapatra |
| `src/main/java/edu/campustorrent/util/Log.java` | Vabyasha Mohapatra |
| `src/main/java/edu/campustorrent/util/RateLimiter.java` | Vabyasha Mohapatra |
| `src/test/java/edu/campustorrent/protocol/ProtocolTest.java` | Vabyasha Mohapatra |
| `docs/protocol.md` | Vabyasha Mohapatra |
| `docs/interfaces/protocol-api.md` | Vabyasha Mohapatra |
| `docs/interfaces/peer-integration.md` | Vabyasha Mohapatra |
| `src/main/java/edu/campustorrent/pieces/PieceHasher.java` | Priyanshu Nayak |
| `src/main/java/edu/campustorrent/pieces/Manifest.java` | Priyanshu Nayak |
| `src/main/java/edu/campustorrent/pieces/FileSplitter.java` | Priyanshu Nayak |
| `src/main/java/edu/campustorrent/pieces/Reassembler.java` | Priyanshu Nayak |
| `src/main/java/edu/campustorrent/pieces/PieceStore.java` | Priyanshu Nayak |
| `src/main/java/edu/campustorrent/pieces/MakeManifest.java` | Priyanshu Nayak |
| `src/main/java/edu/campustorrent/scheduler/Strategy.java` | Priyanshu Nayak |
| `src/main/java/edu/campustorrent/scheduler/Availability.java` | Priyanshu Nayak |
| `src/main/java/edu/campustorrent/scheduler/PieceSelector.java` | Priyanshu Nayak |
| `src/main/java/edu/campustorrent/util/PathSafety.java` | Priyanshu Nayak |
| `src/test/java/edu/campustorrent/pieces/ManifestAndSplitterTest.java` | Priyanshu Nayak |
| `src/test/java/edu/campustorrent/pieces/PieceStoreTest.java` | Priyanshu Nayak |
| `src/test/java/edu/campustorrent/scheduler/PieceSelectorTest.java` | Priyanshu Nayak |
| `docs/interfaces/pieces-api.md` | Priyanshu Nayak |
| `src/main/java/edu/campustorrent/experiment/CsvWriter.java` | Sambit Kumar Mohapatra |
| `src/main/java/edu/campustorrent/experiment/SampleFileGenerator.java` | Sambit Kumar Mohapatra |
| `src/main/java/edu/campustorrent/experiment/LocalSwarm.java` | Sambit Kumar Mohapatra |
| `src/main/java/edu/campustorrent/experiment/ExperimentRunner.java` | Sambit Kumar Mohapatra |
| `src/test/java/edu/campustorrent/integration/SwarmIntegrationTest.java` | Sambit Kumar Mohapatra |
| `src/test/java/edu/campustorrent/experiment/ExperimentSupportTest.java` | Sambit Kumar Mohapatra |
| `scripts/build.ps1` | Sambit Kumar Mohapatra |
| `scripts/build.sh` | Sambit Kumar Mohapatra |
| `scripts/plot_results.py` | Sambit Kumar Mohapatra |
| `scripts/run-demo.ps1` | Sambit Kumar Mohapatra |
| `scripts/run-demo.sh` | Sambit Kumar Mohapatra |
| `scripts/run-experiments.ps1` | Sambit Kumar Mohapatra |
| `scripts/run-experiments.sh` | Sambit Kumar Mohapatra |
| `scripts/sync-contribution-packs.sh` | Sambit Kumar Mohapatra |
| `scripts/verify-downloads.ps1` | Sambit Kumar Mohapatra |
| `scripts/verify-downloads.sh` | Sambit Kumar Mohapatra |
| `docs/testing-and-experiments.md` | Sambit Kumar Mohapatra |
| `docs/interfaces/harness-api.md` | Sambit Kumar Mohapatra |
| `pom.xml` | Shared (integration owner: Vabyasha Mohapatra) |
| `.gitignore` | Shared (integration owner: Vabyasha Mohapatra) |
| `README.md` | Shared (integration owner: Vabyasha Mohapatra) |
| `config/peer.example.properties` | Shared (integration owner: Vabyasha Mohapatra) |
| `samples/study_notes.txt` | Shared (integration owner: Vabyasha Mohapatra) |
| `docs/architecture.md` | Shared (integration owner: Vabyasha Mohapatra) |
| `docs/git-workflow.md` | Shared (integration owner: Vabyasha Mohapatra) |
| `docs/viva-prep.md` | Shared (integration owner: Vabyasha Mohapatra) |
| `docs/acceptance-checklist.md` | Shared (integration owner: Vabyasha Mohapatra) |
| `docs/OWNERSHIP.md` | Shared (integration owner: Vabyasha Mohapatra) |
| `docs/ownership.csv` | Shared (integration owner: Vabyasha Mohapatra) |
| `docs/project-tree.txt` | Shared (integration owner: Vabyasha Mohapatra) |

Generated output not listed above: `contributions/<member>/` (snapshots, regenerate with `scripts/sync-contribution-packs.sh`), `results/` (measured CSV and plots), `target/`, `demo-work/`.
