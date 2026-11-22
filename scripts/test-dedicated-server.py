#!/usr/bin/env python3
"""Start a REAL NeoForge dedicated server, verify command tree + 100 ticks.

Test world and EULA are created only in ephemeral CI's ./run. Unlike GameTest,
this executes Gradle's normal runServer mode with zero connected players.
The child process tree is terminated after BOTH readiness markers are seen.
No background process or manual user interaction is needed.
"""
import argparse
import os
from pathlib import Path
import queue
import signal
import subprocess
import sys
import threading
import time

ROOT = Path(__file__).resolve().parent.parent
TIMEOUT_SECONDS = 240
MARKERS = (
    "FF-SERVER-SMOKE COMMANDS_READY PASS",
    "FF-SERVER-SMOKE 100_SERVER_TICKS PASS",
)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--minecraft", choices=["26.2", "26.3"], required=True)
    options = parser.parse_args()
    run = ROOT / "run"
    run.mkdir(parents=True, exist_ok=True)
    # This EULA acceptance is for an ephemeral CI process, never a production world.
    (run / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (run / "server.properties").write_text(
        "online-mode=false\n"
        "server-port=0\n"
        "max-players=1\n"
        "level-name=ff-ci-dedicated-smoke\n"
        "simulation-distance=2\n"
        "view-distance=2\n"
        "enable-rcon=false\n"
        "enable-command-block=false\n",
        encoding="utf-8"
    )
    command = [
        "bash", "./gradlew", "runServer",
        "-PtargetMinecraft=" + options.minecraft,
        "--no-daemon", "--stacktrace",
    ]
    output = ROOT / "dedicated-server.log"
    found = set()
    lines = queue.Queue()
    process = subprocess.Popen(
        command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        text=True, encoding="utf-8", errors="replace",
        bufsize=1, start_new_session=True,
    )
    def reader():
        try:
            for line in process.stdout:
                lines.put(line)
        finally:
            lines.put(None)
    threading.Thread(target=reader, daemon=True).start()
    deadline = time.monotonic() + TIMEOUT_SECONDS
    unexpected_exit = None
    try:
        with output.open("w", encoding="utf-8") as log:
            while time.monotonic() < deadline:
                try:
                    line = lines.get(timeout=0.5)
                except queue.Empty:
                    if process.poll() is not None:
                        unexpected_exit = process.returncode
                        break
                    continue
                if line is None:
                    unexpected_exit = process.poll()
                    break
                print(line, end="", flush=True)
                log.write(line)
                log.flush()
                for marker in MARKERS:
                    if marker in line:
                        found.add(marker)
                if "FF-SERVER-SMOKE COMMAND_TREE_MISSING FAIL" in line:
                    raise RuntimeError("Mod command tree missing on real dedicated server")
                if "Encountered an unexpected exception" in line or "Game test server crashed" in line:
                    raise RuntimeError("Unexpected server exception before readiness")
                if len(found) == len(MARKERS):
                    break
    finally:
        if process.poll() is None:
            try:
                os.killpg(process.pid, signal.SIGINT)
            except ProcessLookupError:
                pass
            try:
                process.wait(timeout=12)
            except subprocess.TimeoutExpired:
                try:
                    os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                process.wait(timeout=8)
    missing = set(MARKERS) - found
    if missing:
        print("FAIL real dedicated-server smoke:", options.minecraft,
              "missing markers=", sorted(missing),
              "exit before markers=", unexpected_exit, file=sys.stderr)
        return 1
    print("FF-DEDICATED-TEST Minecraft", options.minecraft,
          "PASS: real runServer launched, command tree verified,"
          " 100 genuine server ticks; clean test process shutdown.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
