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
spawn-monsters=false
spawn-animals=false
doMobSpawning=false
enable-rcon=true
rcon.password=ff-ci-mixin-smoke
rcon.port=25575
broadcast-console-to-ops=false
EOF
rm -f "$LOG_FILE" "$CONSOLE_LOG"

# Launch the real 26.2 dedicated server. RCON is used rather than Gradle stdin:
# JavaExec does not reliably forward the workflow shell's pipe to runServer.
timeout --signal=INT --kill-after=20s 180s ./gradlew runServer --no-daemon --console=plain >"$CONSOLE_LOG" 2>&1 &
SERVER_PID=$!
cleanup() {
  if kill -0 "$SERVER_PID" 2>/dev/null; then
    kill -INT "$SERVER_PID" 2>/dev/null || true
    sleep 2
    kill -KILL "$SERVER_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

ready=0
for _ in $(seq 1 150); do
  if [[ -f "$LOG_FILE" ]] && grep -q 'Done (' "$LOG_FILE"; then
    ready=1
    break
  fi
  if ! kill -0 "$SERVER_PID" 2>/dev/null; then
    echo "runServer exited before startup completed."
    tail -n 120 "$CONSOLE_LOG" || true
    exit 1
  fi
  sleep 1
done
if [[ "$ready" -ne 1 ]]; then
  echo "Timed out waiting for the 26.2 dedicated server to become ready."
  tail -n 160 "$CONSOLE_LOG" || true
  exit 1
fi

python3 - <<'PY'
import socket
import struct
import time

HOST, PORT, PASSWORD = "127.0.0.1", 25575, "ff-ci-mixin-smoke"

def exact(sock, size):
    chunks = []
    remaining = size
    while remaining:
        part = sock.recv(remaining)
        if not part:
            raise RuntimeError("RCON socket closed unexpectedly")
        chunks.append(part)
        remaining -= len(part)
    return b"".join(chunks)

def read_packet(sock):
    size = struct.unpack("<i", exact(sock, 4))[0]
    payload = exact(sock, size)
    request_id, packet_type = struct.unpack("<ii", payload[:8])
    return request_id, packet_type, payload[8:-2].decode("utf-8", "replace")

def send_packet(sock, request_id, packet_type, body):
    encoded = body.encode("utf-8") + b"\0\0"
    payload = struct.pack("<ii", request_id, packet_type) + encoded
    sock.sendall(struct.pack("<i", len(payload)) + payload)

sock = socket.create_connection((HOST, PORT), timeout=4)
sock.settimeout(4)
send_packet(sock, 41, 3, PASSWORD)
auth_id, _, _ = read_packet(sock)
if auth_id != 41:
    raise SystemExit("RCON authentication failed")

def command(request_id, value):
    send_packet(sock, request_id, 2, value)
    response_id, _, response = read_packet(sock)
    if response_id != request_id:
        raise SystemExit(f"RCON response mismatch for {value!r}: {response_id}")
    print(f"RCON {value}: {response}")
    return response

command(42, "forceload add 0 0")
command(43, "summon minecraft:zombie 0 80 0")
command(44, "summon minecraft:creeper 3 80 0")
time.sleep(1.0)  # allow ServerLevel's entity manager to finish registration
zombie_selector = "@e[type=minecraft:zombie,limit=1]"
creeper_selector = "@e[type=minecraft:creeper,limit=1]"
command(45, f"freakyfriday debug-prime-creeper {creeper_selector}")
initial = command(46, f"freakyfriday debug-creeper-progress {creeper_selector}")
command(47, f"freakyfriday debug-exchange {zombie_selector} {creeper_selector}")
time.sleep(0.4)
during_swap = command(48, f"freakyfriday debug-creeper-progress {creeper_selector}")
command(49, f"freakyfriday debug-exchange {zombie_selector} {creeper_selector}")
time.sleep(0.4)
after_reverse = command(50, f"freakyfriday debug-creeper-progress {creeper_selector}")

import re
def progress(response):
    match = re.search(r"DEBUG creeper progress=([0-9.]+)", response)
    if not match:
        raise SystemExit(f"Missing numeric Creeper progress in RCON response: {response!r}")
    return float(match.group(1))

initial_value = progress(initial)
swapped_value = progress(during_swap)
reversed_value = progress(after_reverse)
if not (swapped_value > initial_value and reversed_value > swapped_value):
    raise SystemExit(
        f"Creeper mind state did not advance across exchange/reversal: "
        f"initial={initial_value}, swapped={swapped_value}, reversed={reversed_value}"
    )
print(f"PASS Creeper native fuse state progressed on Zombie body then continued after reversal: "
      f"{initial_value} -> {swapped_value} -> {reversed_value}")

# Stop the server cleanly over its authenticated control channel.
send_packet(sock, 51, 2, "stop")
sock.close()
PY

set +e
wait "$SERVER_PID"
SERVER_STATUS=$?
set -e
trap - EXIT
if [[ "$SERVER_STATUS" -ne 0 ]]; then
  echo "runServer exited with status $SERVER_STATUS after the RCON stop command."
  tail -n 160 "$CONSOLE_LOG" || true
  exit "$SERVER_STATUS"
fi

if ! grep -Fq 'Freaky Friday 26.2 native Mob AI observer is active' "$CONSOLE_LOG" "$LOG_FILE" 2>/dev/null; then
  echo "The 26.2 server accepted the debug exchange/tick commands, but the Mob.serverAiStep observer did not report an active AI tick."
  tail -n 160 "$CONSOLE_LOG" || true
  tail -n 160 "$LOG_FILE" || true
  exit 1
fi

if [[ "$(grep -h -F 'DEBUG exchange result:' "$CONSOLE_LOG" "$LOG_FILE" 2>/dev/null | wc -l)" -lt 2 ]]; then
  echo "The debug exchange/reversal commands did not both reach native controller/body binding."
  exit 1
fi
if ! grep -h -E 'body .*\(entity\.minecraft\.zombie\) now runs controller .*\(entity\.minecraft\.creeper\)' "$CONSOLE_LOG" "$LOG_FILE" >/dev/null 2>&1; then
  echo "The Zombie body was not assigned the Creeper controller during the first exchange."
  exit 1
fi
if ! grep -h -E 'body .*\(entity\.minecraft\.creeper\) now runs controller .*\(entity\.minecraft\.zombie\)' "$CONSOLE_LOG" "$LOG_FILE" >/dev/null 2>&1; then
  echo "The Creeper body was not assigned the Zombie controller during the first exchange."
  exit 1
fi
echo "PASS dedicated 26.2 server loaded all Mixin hooks, exchanged the two controllers, advanced Creeper fuse state while controlling the Zombie body, then reversed and continued the same fuse state."
