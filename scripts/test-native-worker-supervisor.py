#!/usr/bin/env python3
"""Exercise live reciprocal workers from an ordinary dedicated NeoForge server."""
import argparse, os, queue, signal, subprocess, sys, threading, time
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent
MARKERS=(
    "FF-SUPERVISOR PLAYER_MOB_RECIPROCAL PASS",
    "FF-SUPERVISOR MOB_MOB_RECIPROCAL PASS",
    "FF-SUPERVISOR ALL_PAIRINGS PASS",
)

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--minecraft",choices=["26.2","26.3"],required=True)
    options=parser.parse_args()
    run=ROOT/"run-supervisor-smoke"
    run.mkdir(parents=True,exist_ok=True)
    (run/"eula.txt").write_text("eula=true\n",encoding="utf-8")
    (run/"server.properties").write_text(
        "online-mode=false\nserver-port=0\nmax-players=1\n"
        "level-name=ff-supervisor-smoke\ndifficulty=normal\n"
        "gamemode=survival\nspawn-monsters=false\nspawn-animals=false\n"
        "simulation-distance=4\nview-distance=4\n"
        "enable-rcon=false\nspawn-protection=0\n",
        encoding="utf-8")
    command=["bash","./gradlew","runSupervisorSmoke",
        "-PtargetMinecraft="+options.minecraft,"--no-daemon","--stacktrace"]
    output=ROOT/"supervisor-smoke.log"
    lines=queue.Queue()
    process=subprocess.Popen(command,cwd=ROOT,stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,text=True,encoding="utf-8",errors="replace",
        bufsize=1,start_new_session=True)
    def reader():
        try:
            for line in process.stdout: lines.put(line)
        finally: lines.put(None)
    threading.Thread(target=reader,daemon=True).start()
    found=set(); deadline=time.monotonic()+1800; failed=None
    try:
        with output.open("w",encoding="utf-8") as log:
            while time.monotonic()<deadline:
                try: line=lines.get(timeout=0.5)
                except queue.Empty:
                    if process.poll() is not None:
                        failed="server exited before completing all reciprocal cases"
                        break
                    continue
                if line is None:
                    failed="server output closed before all reciprocal cases completed"
                    break
                print(line,end="",flush=True);log.write(line);log.flush()
                for marker in MARKERS:
                    if marker in line: found.add(marker)
                if "FF-SUPERVISOR PLAYER_MOB FAIL" in line or "FF-SUPERVISOR MOB_MOB FAIL" in line:
                    failed=line.strip();break
                if "Encountered an unexpected exception" in line:
                    failed="NeoForge raised an unexpected server exception";break
                if len(found)==len(MARKERS): break
    finally:
        if process.poll() is None:
            try: os.killpg(process.pid,signal.SIGINT)
            except ProcessLookupError: pass
            try: process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                try: os.killpg(process.pid,signal.SIGKILL)
                except ProcessLookupError: pass
                process.wait(timeout=10)
    missing=set(MARKERS)-found
    if missing:
        print("FAIL reciprocal supervisor smoke",options.minecraft,
            "reason=",failed,"missing=",sorted(missing),file=sys.stderr)
        return 1
    print("FF-SUPERVISOR-TEST Minecraft",options.minecraft,
        "PASS: live Player↔Mob and bilateral Mob↔Mob child workers moved the original physical bodies.")
    return 0

if __name__=="__main__":
    sys.exit(main())
