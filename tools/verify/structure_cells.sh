#!/bin/bash
# Every cell of the 52 structures in the 4 rotations (plain code, 3 copies, hard flag, opening, overhanging leaves) as
# a jar builds them, one line each, to compare two versions with compare_structure_cells.py.
#   tools/verify/structure_cells.sh <jar> <out file>
# Copy the jar of the version before the change (build/libs/*.jar is replaced by the next build). ~1 minute, ~17 million
# lines (~1.5 GB).
set -e
jar="$1"
out="$2"
if [ -z "$jar" ] || [ -z "$out" ]; then
  echo "usage: $0 <jar> <out file>" >&2
  exit 1
fi
here="$(cd "$(dirname "$0")" && pwd)"
classes="$(mktemp -d)"
javac -cp "$jar" -d "$classes" "$here/java/com/abandonedmoderncity/core/StructureCells.java"
java -cp "$classes:$jar" com.abandonedmoderncity.core.StructureCells "$out"
rm -rf "$classes"
echo "wrote $out"
