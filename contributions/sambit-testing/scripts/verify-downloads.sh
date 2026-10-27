#!/usr/bin/env bash
# Usage: verify-downloads.sh <original-file> <downloaded-file>...
# Compares SHA-256 of every downloaded file with the original. Exit code 0 only if all match.
set -euo pipefail
if [ $# -lt 2 ]; then echo "Usage: $0 <original> <downloaded>..." >&2; exit 2; fi
if command -v sha256sum >/dev/null 2>&1; then H() { sha256sum "$1" | cut -d' ' -f1; }; else H() { shasum -a 256 "$1" | cut -d' ' -f1; }; fi
orig="$1"; shift
want="$(H "$orig")"
echo "original  $want  $orig"
bad=0
for f in "$@"; do
  if [ ! -f "$f" ]; then echo "MISSING   $f"; bad=1; continue; fi
  got="$(H "$f")"
  if [ "$got" = "$want" ]; then echo "MATCH     $got  $f"; else echo "MISMATCH  $got  $f"; bad=1; fi
done
exit $bad
