#!/usr/bin/env bash
# The ROOT of the repository is canonical. contributions/<member>/ are handoff snapshots of each member's own files.
#   ./scripts/sync-contribution-packs.sh          re-copies the owned files into every pack (README.md of each pack is kept)
#   ./scripts/sync-contribution-packs.sh --check  fails if any pack file differs from the root (use before a hand-off)
# The mapping file -> owner lives in docs/ownership.csv.
set -euo pipefail
cd "$(dirname "$0")/.."
MODE="${1:-sync}"
status=0
if [ "$MODE" = "sync" ]; then
  for d in contributions/*/; do
    find "$d" -mindepth 1 ! -name README.md -delete 2>/dev/null || true
  done
fi
while IFS=, read -r path owner; do
  case "$path" in \#*|"") continue ;; esac
  [ "$owner" = "shared" ] && continue
  dest="contributions/$owner/$path"
  if [ "$MODE" = "--check" ]; then
    if ! cmp -s "$path" "$dest"; then echo "DIFFERS/MISSING: $dest"; status=1; fi
  else
    mkdir -p "$(dirname "$dest")"; cp "$path" "$dest"
  fi
done < docs/ownership.csv
[ "$MODE" = "--check" ] && { [ $status -eq 0 ] && echo "All contribution packs match the root." ; exit $status; }
echo "Contribution packs synchronised."
