#!/bin/bash
# Bytecode listing of every compiled class, one text file per class (javap -c -p: the instructions of every method,
# no line numbers), to compare a change that should not alter what the code does.
#   tools/verify/javap_dump.sh <out dir> [classes dir]
# The classes dir defaults to build/classes/java/main (run ./gradlew compileJava first). Needs the JDK 21 javap and the
# Minecraft classes that ModDevGradle prepares in build/moddev/artifacts.
set -e
out="$1"
in="${2:-build/classes/java/main}"
if [ -z "$out" ]; then
  echo "usage: $0 <out dir> [classes dir]" >&2
  exit 1
fi
javap="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk}/bin/javap"
mc=$(ls build/moddev/artifacts/neoforge-*-merged.jar 2>/dev/null | head -1)
cp="$(realpath "$in"):$(realpath build/classes/java/main):$mc"
in=$(realpath "$in")
rm -rf "$out"
mkdir -p "$out"
out=$(realpath "$out")
cd "$in"
n=0
while read -r f; do
  c="${f#./}"
  c="${c%.class}"
  "$javap" -c -p -cp "$cp" "${c//\//.}" > "$out/${c//\//_}.txt"
  n=$((n + 1))
done < <(find . -name '*.class' | sort)
echo "$n classes listed in $out"
