#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

RUN_DIR="runs/server"
LOG_FILE="$RUN_DIR/logs/latest.log"
CONSOLE_LOG="build/reports/freakyfriday/server-mixin-smoke-console.log"
mkdir -p "$RUN_DIR" "$(dirname "$CONSOLE_LOG")"
printf 'eula=true\n' > "$RUN_DIR/eula.txt"
cat > "$RUN_DIR/server.properties" <<'EOF'
online-mode=false
difficulty=normal
spawn-monsters=true
spawn-animals=true
EOF
rm -f "$LOG_FILE" "$CONSOLE_LOG"

# Feed a spawn command only after the dedicated server announces it is ready.
# The server console is stdin for the Gradle runServer Java process.
set +e
(
  ready=0
  for _ in $(seq 1 120); do
    if [[ -f "$LOG_FILE" ]] && grep -q 'Done (' "$LOG_FILE"; then
      ready=1
      break
    fi
    sleep 1
  done
  if [[ "$ready" -eq 1 ]]; then
    printf 'forceload add 0 0\n'
    printf 'summon minecraft:zombie 0 80 0\n'
    sleep 6
  fi
  printf 'stop\n'
) | timeout --signal=INT --kill-after=15s 180s ./gradlew runServer --no-daemon --console=plain 2>&1 | tee "$CONSOLE_LOG"
statuses=("${PIPESTATUS[@]}")
set -e
server_status="${statuses[1]:-1}"
if [[ "$server_status" -ne 0 && "$server_status" -ne 124 && "$server_status" -ne 130 ]]; then
  echo "runServer failed with status $server_status"
  exit "$server_status"
fi

if ! grep -Fq 'Freaky Friday 26.2 native Mob AI observer is active' "$CONSOLE_LOG" "$LOG_FILE" 2>/dev/null; then
  echo "The 26.2 server started, but the Mob.serverAiStep observer did not report an active AI tick."
  echo "Inspect $CONSOLE_LOG and $LOG_FILE"
  exit 1
fi

echo "PASS dedicated 26.2 server loaded the Mixin config and executed the Mob.serverAiStep observer."
