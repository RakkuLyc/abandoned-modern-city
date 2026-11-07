#!/usr/bin/env bash
# In-world test battery: one fresh world per seed on the dedicated dev server.
#   tools/selftest.sh [scope] [seed...]      scope = full (default) or quick
# Each run writes run/selftest/<seed>/report.txt (ends with "RESULT: PASS" or "RESULT: FAIL") and PNG renders.
set -u
cd "$(dirname "$0")/.."
SCOPE="${1:-full}"
shift || true
SEEDS=("$@")
if [ ${#SEEDS[@]} -eq 0 ]; then
  SEEDS=(20261027 -3297019724594858492 3478477863671517581 1)
fi
mkdir -p run
if command -v powershell >/dev/null 2>&1; then
  running=$(powershell -NoProfile -Command "(Get-CimInstance Win32_Process -Filter \"Name = 'java.exe'\" | Where-Object { \$_.CommandLine -match 'devlaunch' }).Count" | tr -d '\r')
  if [ "${running:-0}" != "0" ] && [ -n "$running" ]; then echo "a dev server is already running; stop it first"; exit 2; fi
fi
echo "eula=true" > run/eula.txt
overall=0
for SEED in "${SEEDS[@]}"; do
  WORLD="selftest_world_${SEED}"
  OUT="selftest/${SEED}"
  rm -rf "run/${WORLD}" "run/${OUT}"
  mkdir -p "run/${OUT}"
  cat > run/server.properties <<PROPS
level-name=${WORLD}
level-seed=${SEED}
online-mode=false
max-tick-time=-1
spawn-protection=0
view-distance=6
simulation-distance=4
motd=AMCS self-test
server-port=25591
enable-query=false
enable-rcon=false
PROPS
  echo "=== seed ${SEED} (scope ${SCOPE}) ==="
  ./gradlew runServer --console=plain "-PselfTest=${OUT}/report.txt" "-PselfTestScope=${SCOPE}" > "run/${OUT}/server.log" 2>&1
  if [ -f "run/${OUT}/report.txt" ]; then
    grep -E "^RESULT|generation errors|mean" "run/${OUT}/report.txt"
    grep -q "^RESULT: PASS" "run/${OUT}/report.txt" || overall=1
  else
    echo "no report (see run/${OUT}/server.log)"
    overall=1
  fi
done
exit $overall
