#!/usr/bin/env python3
"""Two REAL, separate NeoForge JVMs: native donor AI producer -> body receiver.

This disposable CI harness observes native Zombie pursuit AND a real melee
damage event against a mapped Villager, then asks a second NeoForge server to
execute the authenticated attack intent through an unrelated physical body.
The reverse Chicken test requires native passive navigation, not an attack.

This is still bounded observation, not arbitrary modded ability capture or a
live continuous mind-swap. OS-level restrictions remain a deployment concern.
"""
import argparse
import os
from pathlib import Path
import queue
import re
import secrets
import shutil
import signal
import subprocess
import sys
import threading
import time
import uuid

ROOT = Path(__file__).resolve().parent.parent
WORKER_DIR = ROOT / "run-ai-worker"
RECEIVER_DIR = ROOT / "run"
WORKER_OUTPUT = WORKER_DIR / "ff-worker-frame.bin"
WORKER_CONFIG = WORKER_DIR / "ff-worker-request.properties"
RECEIVER_OUTPUT = RECEIVER_DIR / "ff-worker-frame.bin"
RECEIVER_CONFIG = RECEIVER_DIR / "ff-worker-request.properties"

def run_until_markers(command, log_path, markers, env, timeout=420):
    reached = set()
    error_terms = ("FF-NATIVE-WORKER START_FAILURE",
                   "FF-NATIVE-WORKER FRAME_FAILURE",
                   "FF-NATIVE-WORKER NO_NATIVE_DECISION",
                   "FF-NATIVE-WORKER NO_NATIVE_ATTACK",
                   "FF-NATIVE-WORKER NATIVE_CAPTURE_INCOMPLETE",
                   "FF-NATIVE-WORKER MOB_DIED",
                   "FF-NATIVE-WORKER WORLD_REGISTRATION_FAILED")
    process = subprocess.Popen(
        command, cwd=ROOT, env=env, stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT, text=True, encoding="utf-8",
        errors="replace", bufsize=1, start_new_session=True,
    )
    lines = queue.Queue()
    def reader():
        try:
            for line in process.stdout:
                lines.put(line)
        finally:
            lines.put(None)
    threading.Thread(target=reader, daemon=True).start()
    failure = None
    deadline = time.monotonic() + timeout
    try:
        with log_path.open("w", encoding="utf-8") as log:
            while time.monotonic() < deadline:
                try:
                    line = lines.get(timeout=0.5)
                except queue.Empty:
                    if process.poll() is not None:
                        failure = "Server process exited before all markers"
                        break
                    continue
                if line is None:
                    failure = "Server output ended before all markers"
                    break
                print(line, end="", flush=True)
                log.write(line)
                log.flush()
                if any(term in line for term in error_terms):
                    failure = "Explicit native AI worker failure in server output"
                    break
                if "Encountered an unexpected exception" in line:
                    failure = "Unexpected Minecraft exception"
                    break
                for marker in markers:
                    if marker in line:
                        reached.add(marker)
                # GameTest count rises whenever a new required regression is
                # added. Demand a complete successful run with at least the
                # current twelve tests instead of pinning a stale exact count.
                completed = re.search(r"All (\d+) required tests passed", line)
                if completed and int(completed.group(1)) >= 12:
                    reached.add("ALL_REQUIRED_GAME_TESTS_PASSED")
                if len(reached) == len(markers):
                    break
            if time.monotonic() >= deadline and len(reached) < len(markers):
                failure = "Server readiness/AI timeout"
    finally:
        if process.poll() is None:
            try:
                os.killpg(process.pid, signal.SIGINT)
            except ProcessLookupError:
                pass
            try:
                process.wait(timeout=14)
            except subprocess.TimeoutExpired:
                try:
                    os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                process.wait(timeout=8)
    missing = set(markers) - reached
    if failure or missing:
        raise RuntimeError(str(failure or "Missing required markers") +
            ": " + ", ".join(sorted(missing)))

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--minecraft", choices=("26.2","26.3"), required=True)
    opts = parser.parse_args()
    for folder in (WORKER_DIR, RECEIVER_DIR):
        folder.mkdir(parents=True, exist_ok=True)

    # Both roles use exactly the same native-host and body-port code.
    for donor,recipient,stimulus,require_attack in (
        ("minecraft:zombie","minecraft:chicken","minecraft:villager",True),
        ("minecraft:chicken","minecraft:zombie","minecraft:villager",False),
    ):
        run_case(opts,donor,recipient,stimulus,require_attack)
    return 0

def run_case(opts, donor, recipient, stimulus, require_attack):
    """Do not change capture/execution rules for different mob types."""
    # Each experiment uses a fresh disposable native world. Without this,
    # previously spawned mobs can alter native targeting/wandering when the
    # second species is tested.
    worker_world=WORKER_DIR / "ff-native-ai-ci-worker"
    if worker_world.exists():
        shutil.rmtree(worker_world)
    key_hex = secrets.token_hex(32)
    session = uuid.uuid4()
    body = uuid.uuid4()
    mind = uuid.uuid4()
    stimulus_id = uuid.uuid4()
    tick = 1700
    props = (
        "session=" + str(session) + "\n"
        "recipientBody=" + str(body) + "\n"
        "sourceMind=" + str(mind) + "\n"
        "recipientTick=" + str(tick) + "\n"
        "donor=" + donor + "\n"
        "recipient=" + recipient + "\n"
        "stimulus=" + stimulus + "\n"
        "stimulusId=" + str(stimulus_id) + "\n"
        "requireAttack=" + str(require_attack).lower() + "\n"
    )
    WORKER_CONFIG.write_text(props, encoding="utf-8")
    (WORKER_DIR / "eula.txt").write_text("eula=true\n",encoding="utf-8")
    (WORKER_DIR / "server.properties").write_text(
        "online-mode=false\nserver-port=0\n"
        "level-name=ff-native-ai-ci-worker\n"
        "difficulty=normal\nspawn-monsters=true\n"
        "simulation-distance=3\nview-distance=3\n"
        "max-players=1\nspawn-protection=0\n",
        encoding="utf-8",
    )
    env = os.environ.copy()
    env["FF_WORKER_KEY_HEX"] = key_hex
    cmd_base = ["bash","./gradlew"]
    arguments = ["-PtargetMinecraft=" + opts.minecraft,
                 "--no-daemon","--stacktrace"]

    try:
        if WORKER_OUTPUT.exists():
            WORKER_OUTPUT.unlink()
        print("FF-NATIVE-WORKER starting distinct disposable NeoForge producer")
        worker_markers = [
            "FF-NATIVE-WORKER READY",
            "FF-NATIVE-WORKER NATIVE_NAVIGATION_OBSERVED",
            "FF-NATIVE-WORKER FRAME_WRITTEN PASS",
        ]
        if require_attack:
            worker_markers.append("FF-NATIVE-WORKER NATIVE_ATTACK_OBSERVED")
        run_until_markers(
            cmd_base + ["runAiWorker"] + arguments,
            ROOT / "native-worker-server.log",
            tuple(worker_markers),
            env,
        )
        if not WORKER_OUTPUT.is_file():
            raise RuntimeError("Native AI worker emitted no signed packet")
        size = WORKER_OUTPUT.stat().st_size
        if size < 64 or size > 65536:
            raise RuntimeError("Worker output is outside binary size bounds")
        shutil.copyfile(WORKER_CONFIG,RECEIVER_CONFIG)
        shutil.copyfile(WORKER_OUTPUT,RECEIVER_OUTPUT)

        print("FF-NATIVE-WORKER starting independent NeoForge receiver GameTest")
        run_until_markers(
            cmd_base + ["runGameTestServer"] + arguments,
            ROOT / "native-worker-receiver.log",
            ("FF-NATIVE-WORKER TWO_JVM_E2E PASS",
             "ALL_REQUIRED_GAME_TESTS_PASSED"),
            env,
        )
        print("FF-NATIVE-WORKER TWO_PROCESS_CI PASS"
            " minecraft=" + opts.minecraft +
            " donor="+donor+" recipient="+recipient+
            " nativeAttackCaptured="+str(require_attack).lower()+
            " nativeDonorActuallyMoved=true"
            " signedRelativeActionsMovedRealRecipient=true"
            " samePhysicalWorld=false noArbitraryModIsolationClaim=true")
    finally:
        # Never store the session fixture with normal game-test logs or
        # accidentally reuse old signed packets in future tests.
        for item in (RECEIVER_CONFIG, RECEIVER_OUTPUT):
            try:
                item.unlink(missing_ok=True)
            except OSError:
                pass


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:
        print("FAIL two-NeoForge-JVM native AI worker integration: " + str(exc),
              file=sys.stderr)
        sys.exit(1)
