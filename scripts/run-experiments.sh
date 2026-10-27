#!/usr/bin/env bash
# Runs the experiments and writes results/experiments.csv. All values are measured, never invented.
# Usage: ./scripts/run-experiments.sh [strategy|seeders|freerider|all]   (env: RUNS, LEECHERS, FILE_KB, PIECE_KB, UPLOAD_KBPS)
set -euo pipefail
cd "$(dirname "$0")/.."
[ -d target/classes ] || ./scripts/build.sh
java -cp target/classes edu.campustorrent.experiment.ExperimentRunner \
  --experiment "${1:-all}" --runs "${RUNS:-3}" --leechers "${LEECHERS:-5}" --file-kb "${FILE_KB:-2048}" \
  --piece-kb "${PIECE_KB:-64}" --upload-kbps "${UPLOAD_KBPS:-1024}" --out "${OUT:-results/experiments.csv}"
echo "Plot with: python3 scripts/plot_results.py --csv ${OUT:-results/experiments.csv} --out results/plots"
