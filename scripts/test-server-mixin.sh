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

for request_id, command in (
    (42, "forceload add 0 0"),
    (43, "summon minecraft:zombie 0 80 0"),
    (44, "summon minecraft:creeper 3 80 0"),
    (45, "freakyfriday debug-exchange-loaded"),
    (46, "freakyfriday debug-tick-loaded"),
    (47, "freakyfriday debug-exchange-loaded"),
    (48, "freakyfriday debug-tick-loaded"),
):
    send_packet(sock, request_id, 2, command)
    response_id, _, response = read_packet(sock)
    if response_id != request_id:
        raise SystemExit(f"RCON response mismatch for {command!r}: {response_id}")
    print(f"RCON {command}: {response}")

time.sleep(6)
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

if [[ "$(grep -h -F 'DEBUG exchange:' "$CONSOLE_LOG" "$LOG_FILE" 2>/dev/null | wc -l)" -lt 2 ]]; then
  echo "The debug exchange/reversal commands did not both reach native controller/body binding."
  exit 1
fi
echo "PASS dedicated 26.2 server applied the full Mob tick Mixin, exchanged and reversed Zombie/Creeper controllers, and invoked both body ticks in both assignments."
