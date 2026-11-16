#!/bin/bash
# Walks again <copies> copies of every building of a jar with their barrels and drawn chests in place: every chest
# reached, someone able to stand in front of it, no container cut off (ChestDrawTest does 40 copies).
#   tools/verify/chest_walk.sh <jar> [copies, default 200]
# ~3 minutes for 200 copies. Ends with RESULT: PASS or FAIL (exit code 1).
set -e
jar="$1"
copies="${2:-200}"
if [ -z "$jar" ]; then
  echo "usage: $0 <jar> [copies]" >&2
  exit 1
fi
here="$(cd "$(dirname "$0")" && pwd)"
classes="$(mktemp -d)"
javac -cp "$jar" -d "$classes" "$here/java/com/abandonedmoderncity/core/ChestWalk.java"
status=0
java -cp "$classes:$jar" com.abandonedmoderncity.core.ChestWalk "$copies" || status=$?
rm -rf "$classes"
exit $status
