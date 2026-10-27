#!/usr/bin/env bash
# Builds target/classes. Uses Maven when available, otherwise falls back to plain javac (JDK 17+ required).
set -euo pipefail
cd "$(dirname "$0")/.."
if command -v mvn >/dev/null 2>&1; then
  mvn -q -DskipTests compile
elif command -v javac >/dev/null 2>&1; then
  rm -rf target/classes && mkdir -p target/classes
  javac -d target/classes $(find src/main -name '*.java')
else
  echo "Neither mvn nor javac found. Install a JDK (21 preferred) and Maven." >&2
  exit 1
fi
echo "Build OK -> target/classes"
