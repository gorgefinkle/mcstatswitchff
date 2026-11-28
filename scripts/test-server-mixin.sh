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

next_request_id = 42

def command(value):
    global next_request_id
    request_id = next_request_id
    next_request_id += 1
    send_packet(sock, request_id, 2, value)
    response_id, _, response = read_packet(sock)
    if response_id != request_id:
        raise SystemExit(f"RCON response mismatch for {value!r}: {response_id}")
    response = response.strip()
    print(f"RCON {value}: {response}")
    return response

def command_until(value, predicate, attempts=40, interval=0.25):
    last = ""
    for _ in range(attempts):
        last = command(value)
        if predicate(last):
            return last
        time.sleep(interval)
    raise SystemExit(
        f"Timed out waiting for RCON command to succeed: {value!r}; last response={last!r}"
    )

command("forceload add 0 0")
command("summon minecraft:zombie 0 80 0")
command("summon minecraft:creeper 3 80 0")
time.sleep(0.5)  # allow the chunk/entity manager to begin its first tick

zombie_selector = "@e[type=minecraft:zombie,limit=1]"
creeper_selector = "@e[type=minecraft:creeper,limit=1]"

# The freshly summoned mobs can take a server tick to become selector-visible.
# Retry only discovery/registration responses; any other command failure stays
# visible and eventually fails the smoke instead of being silently ignored.
prime_command = f"freakyfriday debug-prime-creeper {creeper_selector}"
command_until(prime_command, lambda response: "DEBUG primed Creeper mind " in response)

progress_command = f"freakyfriday debug-creeper-progress {creeper_selector}"
def sample(response):
    import re
    progress_match = re.search(r"DEBUG creeper progress=([0-9.]+)", response)
    ticks_match = re.search(r"entityTicks=(\\d+)", response)
    world_match = re.search(r"worldTick=(\\d+)", response)
    if not progress_match or not ticks_match or not world_match:
        raise SystemExit(f"Missing Creeper progress/tick counters in RCON response: {response!r}")
    return float(progress_match.group(1)), int(ticks_match.group(1)), int(world_match.group(1))

def assert_one_fuse_step_per_world_tick(before, after, label):
    fuse_steps = (after[0] - before[0]) * 28.0  # vanilla Creeper default fuse divisor: maxSwell 30 - 2
    entity_ticks = after[1] - before[1]
    world_ticks = after[2] - before[2]
    if world_ticks <= 0:
        raise SystemExit(f"{label}: no world ticks elapsed between samples")
    if entity_ticks != world_ticks:
        raise SystemExit(
            f"{label}: Mob tickCount advanced {entity_ticks} but world advanced {world_ticks}; "
            "controller clock is not single-stepping"
        )
    if abs(fuse_steps - world_ticks) > 1.1:
        raise SystemExit(
            f"{label}: Creeper fuse advanced {fuse_steps:.2f} steps over {world_ticks} world ticks; "
            "a concrete subclass tick was skipped or executed more than once"
        )

initial = sample(command_until(progress_command, lambda response: "DEBUG creeper progress=" in response))

exchange_command = f"freakyfriday debug-exchange {zombie_selector} {creeper_selector}"
command_until(exchange_command, lambda response: "DEBUG controller/body assignment exchanged." in response)
time.sleep(0.4)
during_swap = sample(command_until(progress_command, lambda response: "DEBUG creeper progress=" in response))
assert_one_fuse_step_per_world_tick(initial, during_swap, "Creeper mind on Zombie body")

command_until(exchange_command, lambda response: "DEBUG controller/body assignment exchanged." in response)
time.sleep(0.4)
after_reverse = sample(command_until(progress_command, lambda response: "DEBUG creeper progress=" in response))
assert_one_fuse_step_per_world_tick(during_swap, after_reverse, "Creeper mind after reversal")

print("PASS Creeper native fuse advanced exactly once per world tick while on Zombie body and after reversal: "
      f"{initial[0]:.3f} -> {during_swap[0]:.3f} -> {after_reverse[0]:.3f}; "
      f"entityTicks={initial[1]}->{during_swap[1]}->{after_reverse[1]}, "
      f"worldTicks={initial[2]}->{during_swap[2]}->{after_reverse[2]}")

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
