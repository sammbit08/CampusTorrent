#!/usr/bin/env bash
# One-command local demo: tracker + 2 seeders + 4 leechers (7 processes), then SHA-256 verification.
# Every port and size can be overridden, e.g.:  TRACKER_PORT=7000 FILE_KB=4096 ./scripts/run-demo.sh
set -euo pipefail
cd "$(dirname "$0")/.."

TRACKER_PORT="${TRACKER_PORT:-6969}"
SEED_BASE_PORT="${SEED_BASE_PORT:-6101}"
LEECH_BASE_PORT="${LEECH_BASE_PORT:-6201}"
FILE_KB="${FILE_KB:-4096}"
PIECE_KB="${PIECE_KB:-64}"
UPLOAD_KBPS="${UPLOAD_KBPS:-1024}"     # simulated per-peer upload cap so the transfer is observable
STRATEGY="${STRATEGY:-rarest-first}"
WORK="${WORK:-demo-work}"
CP="target/classes"

[ -d "$CP" ] || ./scripts/build.sh
rm -rf "$WORK"; mkdir -p "$WORK/source" "$WORK/logs"

echo "== generating $FILE_KB KiB sample file and manifest"
java -cp "$CP" edu.campustorrent.experiment.SampleFileGenerator --out "$WORK/source/studyguide.bin" --size-kb "$FILE_KB" --seed 42
java -cp "$CP" edu.campustorrent.pieces.MakeManifest --file "$WORK/source/studyguide.bin" --piece-size-kb "$PIECE_KB" --out "$WORK/studyguide.manifest"

PIDS=()
cleanup() { for p in "${PIDS[@]:-}"; do kill "$p" 2>/dev/null || true; done; }
trap cleanup EXIT

echo "== Terminal 1: tracker on port $TRACKER_PORT"
java -cp "$CP" edu.campustorrent.tracker.TrackerMain --port "$TRACKER_PORT" > "$WORK/logs/tracker.log" 2>&1 &
PIDS+=($!)
sleep 1

for i in 1 2; do
  echo "== Terminal $((i+1)): seeder S$i on port $((SEED_BASE_PORT+i-1))"
  java -cp "$CP" edu.campustorrent.peer.PeerMain --role seeder --peer-id "S$i" --port $((SEED_BASE_PORT+i-1)) \
     --tracker-port "$TRACKER_PORT" --manifest "$WORK/studyguide.manifest" --file "$WORK/source/studyguide.bin" \
     --upload-kbps "$UPLOAD_KBPS" > "$WORK/logs/seeder$i.log" 2>&1 &
  PIDS+=($!)
done
sleep 1

LEECH_PIDS=()
for i in 1 2 3 4; do
  echo "== Terminal $((i+3)): leecher L$i on port $((LEECH_BASE_PORT+i-1)), output $WORK/out/L$i"
  java -cp "$CP" edu.campustorrent.peer.PeerMain --role leecher --peer-id "L$i" --port $((LEECH_BASE_PORT+i-1)) \
     --tracker-port "$TRACKER_PORT" --manifest "$WORK/studyguide.manifest" --output-dir "$WORK/out/L$i" \
     --strategy "$STRATEGY" --seed "$i" --upload-kbps "$UPLOAD_KBPS" --linger-seconds 5 --timeout-seconds 180 \
     > "$WORK/logs/leecher$i.log" 2>&1 &
  LEECH_PIDS+=($!)
  PIDS+=($!)
done

echo "== waiting for leechers (logs in $WORK/logs/)"
FAIL=0
for p in "${LEECH_PIDS[@]}"; do wait "$p" || FAIL=1; done
grep -h "SUMMARY" "$WORK"/logs/leecher*.log || true

echo "== verifying downloads"
./scripts/verify-downloads.sh "$WORK/source/studyguide.bin" "$WORK"/out/L*/studyguide.bin || FAIL=1
[ "$FAIL" -eq 0 ] && echo "DEMO SUCCESS" || { echo "DEMO FAILED (see $WORK/logs)"; exit 1; }
