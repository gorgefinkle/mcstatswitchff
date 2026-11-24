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

def run_until_markers(command, log_path, markers, env, timeout=420, on_line=None):
    reached = set()
    error_terms = ("FF-NATIVE-WORKER START_FAILURE",
                   "FF-NATIVE-WORKER FRAME_FAILURE",
                   "FF-NATIVE-WORKER NO_NATIVE_DECISION",
                   "FF-NATIVE-WORKER NO_NATIVE_ATTACK",
                   "FF-NATIVE-WORKER NATIVE_CAPTURE_INCOMPLETE",
                   "FF-NATIVE-WORKER MOB_DIED",
                   "FF-NATIVE-WORKER WORLD_REGISTRATION_FAILED",
                   "FF-NATIVE-WORKER MIRROR_DELTA_FAILURE",
                   "FF-NATIVE-WORKER MIRROR_INBOX_CLEANUP_FAILURE",
                   "FF-NATIVE-WORKER MIRROR_CLEANUP_FAILURE")
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
                if on_line is not None:
                    on_line(line)
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
        ("minecraft:zombie","minecraft:piglin","minecraft:villager",True),
        ("minecraft:zombie","minecraft:player","minecraft:villager",True),
    ):
        run_case(opts,donor,recipient,stimulus,require_attack)
    run_reciprocal_mob_pair(opts)
    return 0

def run_case(opts, donor, recipient, stimulus, require_attack):
    """Do not change capture/execution rules for different mob types."""
    case_tag=donor.split(":")[-1]+"-to-"+recipient.split(":")[-1]
    fixture_log=ROOT / ("native-worker-fixture-"+case_tag+".log")
    next_fixture_log=ROOT / ("native-worker-next-fixture-"+case_tag+".log")
    worker_log=ROOT / ("native-worker-server-"+case_tag+".log")
    receiver_log=ROOT / ("native-worker-receiver-"+case_tag+".log")
    print("FF-NATIVE-WORKER CASE_START direction="+case_tag
        +" requireAttack="+str(require_attack).lower(),flush=True)
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
        "sensesFile=ff-worker-senses.bin\n"
        "sensesInboxFile=ff-worker-senses-inbox.bin\n"
        "stimulus=" + stimulus + "\n"
        "stimulusId=" + str(stimulus_id) + "\n"
        "requireAttack=" + str(require_attack).lower() + "\n"
    )
    WORKER_CONFIG.write_text(props, encoding="utf-8")
    (WORKER_DIR / "eula.txt").write_text("eula=true\n",encoding="utf-8")
    (WORKER_DIR / "server.properties").write_text(
        "online-mode=false\nserver-port=0\n"
        "level-name=ff-native-ai-ci-worker\n"
        "difficulty=normal\nlevel-seed=0\nlevel-type=minecraft:flat\ngenerate-structures=false\n"
        "simulation-distance=3\nview-distance=3\n"
        "max-players=1\nspawn-protection=0\n",
        encoding="utf-8",
    )
    env = os.environ.copy()
    env["FF_WORKER_KEY_HEX"] = key_hex
    cmd_base = ["bash","./gradlew"]
    arguments = ["-PtargetMinecraft=" + opts.minecraft,
                 "--no-daemon","--stacktrace"]

    senses_file=WORKER_DIR / "ff-worker-senses.bin"
    senses_next_pending=WORKER_DIR / "ff-worker-senses-next.pending"
    senses_inbox=WORKER_DIR / "ff-worker-senses-inbox.bin"
    first_frame=WORKER_DIR / "ff-worker-frame-first.bin"
    second_frame=WORKER_DIR / "ff-worker-frame-second.bin"
    inbox_processing=senses_inbox.with_name(senses_inbox.name+".processing")
    # Do not let an interrupted prior run contaminate this authenticated session.
    for stale in (WORKER_OUTPUT,WORKER_OUTPUT.with_name(WORKER_OUTPUT.name+".tmp"),
                  senses_file,senses_next_pending,senses_inbox,inbox_processing,
                  first_frame,second_frame):
        stale.unlink(missing_ok=True)
    try:
        fixture_args=[
            "-PffFixtureBody="+str(body),
            "-PffFixtureMind="+str(mind),
            "-PffFixtureStimulusId="+str(stimulus_id),
            "-PffFixtureTick="+str(tick),
            "-PffFixtureRecipient="+recipient,
            "-PffFixtureStimulus="+stimulus,
            "-PffFixtureOutput="+str(senses_file),
        ]
        run_until_markers(
            cmd_base + ["writeNativeMindSenseFixture"] + arguments + fixture_args,
            fixture_log,
            ("FF-SENSE fixture_written PASS",),
            env,
            timeout=180,
        )
        next_fixture_args=[
            "-PffFixtureBody="+str(body),
            "-PffFixtureMind="+str(mind),
            "-PffFixtureStimulusId="+str(stimulus_id),
            "-PffFixtureTick="+str(tick+1),
            "-PffFixtureRecipient="+recipient,
            "-PffFixtureStimulus="+stimulus,
            "-PffFixtureTargetDx=6",
            "-PffFixtureOutput="+str(senses_next_pending),
        ]
        run_until_markers(
            cmd_base + ["writeNativeMindSenseFixture"] + arguments + next_fixture_args,
            next_fixture_log,
            ("FF-SENSE fixture_written PASS",),
            env,
            timeout=180,
        )
        print("FF-NATIVE-WORKER starting distinct disposable NeoForge producer")
        worker_markers = [
            "FF-NATIVE-WORKER READY",
            "FF-NATIVE-WORKER MIRROR_RECONCILE PASS",
            "FF-NATIVE-WORKER ENTITIES_SPAWNED PASS",
            "FF-NATIVE-WORKER MIRROR_DELTA_APPLIED PASS",
            "FF-NATIVE-WORKER FRAME_WRITTEN PASS frameIndex=0 recipientTick=1700",
            "FF-NATIVE-WORKER FRAME_WRITTEN PASS frameIndex=1 recipientTick=1701",
        ]
        if require_attack:
            worker_markers.append("FF-NATIVE-WORKER NATIVE_ATTACK_OBSERVED")
        published={"value":False}
        def publish_next_senses(line):
            if "FF-NATIVE-WORKER FRAME_WRITTEN PASS frameIndex=0" in line and not published["value"]:
                shutil.copyfile(WORKER_OUTPUT,first_frame)
                os.replace(senses_next_pending,senses_inbox)
                published["value"]=True
                print("FF-NATIVE-WORKER PUBLISHED_NEXT_SENSES tick="+str(tick+1),flush=True)
            elif "FF-NATIVE-WORKER FRAME_WRITTEN PASS frameIndex=1 recipientTick=1701" in line:
                shutil.copyfile(WORKER_OUTPUT,second_frame)
        run_until_markers(
            cmd_base + ["runAiWorker"] + arguments,
            worker_log,
            tuple(worker_markers),
            env,
            on_line=publish_next_senses,
        )
        if not published["value"]:
            raise RuntimeError("Worker never emitted its first action frame for sensory update")
        if not first_frame.is_file() or not second_frame.is_file() or not WORKER_OUTPUT.is_file():
            raise RuntimeError("Persistent worker did not emit and retain both adjacent-tick action frames")
        size = first_frame.stat().st_size
        if size < 64 or size > 65536:
            raise RuntimeError("Worker output is outside binary size bounds")
        shutil.copyfile(WORKER_CONFIG,RECEIVER_CONFIG)
        shutil.copyfile(first_frame,RECEIVER_OUTPUT)
        shutil.copyfile(second_frame,RECEIVER_DIR / "ff-worker-frame-next.bin")

        print("FF-NATIVE-WORKER starting independent NeoForge receiver GameTest")
        run_until_markers(
            cmd_base + ["runGameTestServer"] + arguments,
            receiver_log,
            ("FF-NATIVE-WORKER TWO_JVM_LIMITED_INTENT_CAPTURE PASS",
             "ALL_REQUIRED_GAME_TESTS_PASSED"),
            env,
        )
        print("FF-NATIVE-WORKER TWO_PROCESS_LIMITED_CAPTURE_CI PASS"
            " minecraft=" + opts.minecraft +
            " donor="+donor+" recipient="+recipient+
            " nativeAttackCaptured="+str(require_attack).lower()+
            " nativeDonorActuallyMoved=true"
            " signedRelativeActionsMovedRealRecipient=true"
            " recipientSnapshotMaterialized=true"
            " persistentAdjacentSnapshotApplied=true"
            " persistentSessionAcceptedAdjacentSensoryTick=true"
            " secondNativeFrameForUpdatedRecipientWorld=true"
            " sensoryTicks=1700,1701"
            " samePhysicalWorld=false noArbitraryModIsolationClaim=true")
        print("FF-NATIVE-WORKER CASE_COMPLETE direction="+case_tag
            +" nativeAttackCaptured="+str(require_attack).lower(),flush=True)
    finally:
        # Never store the session fixture with normal game-test logs or
        # accidentally reuse old signed packets in future tests.
        for item in (RECEIVER_CONFIG, RECEIVER_OUTPUT,
                     RECEIVER_DIR / "ff-worker-frame-next.bin", senses_file,
                     senses_next_pending,senses_inbox,inbox_processing,
                     first_frame,second_frame):
            try:
                item.unlink(missing_ok=True)
            except OSError:
                pass



def run_reciprocal_mob_pair(opts):
    """Two isolated native mind workers drive opposite original physical mobs."""
    reverse_dir=ROOT/"run-ai-worker-reverse"
    for folder in (WORKER_DIR,reverse_dir,RECEIVER_DIR):
        folder.mkdir(parents=True,exist_ok=True)
    for directory in (WORKER_DIR,reverse_dir):
        world=directory/"ff-native-ai-reciprocal-worker"
        if world.exists(): shutil.rmtree(world)

    key_hex=secrets.token_hex(32)
    env=os.environ.copy()
    env["FF_WORKER_KEY_HEX"]=key_hex
    cmd_base=["bash","./gradlew"]
    arguments=["-PtargetMinecraft="+opts.minecraft,"--no-daemon","--stacktrace"]
    chicken_body,zombie_body=uuid.uuid4(),uuid.uuid4()
    chicken_mind,zombie_mind=uuid.uuid4(),uuid.uuid4()
    villager_id=uuid.uuid4()
    zombie_session,chicken_session=uuid.uuid4(),uuid.uuid4()
    tick=2300

    def produce(directory,task,donor,recipient,mind,body,session,require_attack,target_dx):
        request=directory/"ff-worker-request.properties"
        senses=directory/"ff-worker-senses.bin"
        pending=directory/"ff-worker-senses-next.pending"
        inbox=directory/"ff-worker-senses-inbox.bin"
        output=directory/"ff-worker-frame.bin"
        first=directory/"ff-worker-frame-first.bin"
        second=directory/"ff-worker-frame-second.bin"
        processing=inbox.with_name(inbox.name+".processing")
        for old in (request,senses,pending,inbox,processing,output,
                    output.with_name(output.name+".tmp"),first,second):
            old.unlink(missing_ok=True)
        request.write_text(
            "session="+str(session)+"\n"
            "recipientBody="+str(body)+"\n"
            "sourceMind="+str(mind)+"\n"
            "recipientTick="+str(tick)+"\n"
            "donor="+donor+"\n"
            "recipient="+recipient+"\n"
            "sensesFile=ff-worker-senses.bin\n"
            "sensesInboxFile=ff-worker-senses-inbox.bin\n"
            "stimulus=minecraft:villager\n"
            "stimulusId="+str(villager_id)+"\n"
            "requireAttack="+str(require_attack).lower()+"\n",
            encoding="utf-8")
        (directory/"eula.txt").write_text("eula=true\n",encoding="utf-8")
        (directory/"server.properties").write_text(
            "online-mode=false\nserver-port=0\n"
            "level-name=ff-native-ai-reciprocal-worker\n"
            "difficulty=normal\nlevel-seed=0\nlevel-type=minecraft:flat\ngenerate-structures=false\n"
            "simulation-distance=3\nview-distance=3\n"
            "max-players=1\nspawn-protection=0\n",
            encoding="utf-8")
        tag=donor.split(":")[-1]+"-mind-to-"+recipient.split(":")[-1]
        def fixture(at_tick,target_dx,path):
            return [
                "-PffFixtureBody="+str(body),
                "-PffFixtureMind="+str(mind),
                "-PffFixtureStimulusId="+str(villager_id),
                "-PffFixtureTick="+str(at_tick),
                "-PffFixtureRecipient="+recipient,
                "-PffFixtureStimulus=minecraft:villager",
                "-PffFixtureTargetDx="+str(target_dx),
                "-PffFixtureOutput="+str(path),
            ]
        run_until_markers(
            cmd_base+["writeNativeMindSenseFixture"]+arguments+fixture(tick,target_dx,senses),
            ROOT/("reciprocal-fixture-"+tag+".log"),
            ("FF-SENSE fixture_written PASS",),env,timeout=180)
        run_until_markers(
            cmd_base+["writeNativeMindSenseFixture"]+arguments
                +fixture(tick+1,target_dx-(2 if target_dx>0 else -2),pending),
            ROOT/("reciprocal-next-fixture-"+tag+".log"),
            ("FF-SENSE fixture_written PASS",),env,timeout=180)
        markers=[
            "FF-NATIVE-WORKER READY",
            "FF-NATIVE-WORKER MIRROR_RECONCILE PASS",
            "FF-NATIVE-WORKER ENTITIES_SPAWNED PASS",
            "FF-NATIVE-WORKER MIRROR_DELTA_APPLIED PASS",
            "FF-NATIVE-WORKER FRAME_WRITTEN PASS frameIndex=0 recipientTick="+str(tick),
            "FF-NATIVE-WORKER FRAME_WRITTEN PASS frameIndex=1 recipientTick="+str(tick+1),
        ]
        if require_attack: markers.append("FF-NATIVE-WORKER NATIVE_ATTACK_OBSERVED")
        published={"value":False}
        def publish(line):
            if "FF-NATIVE-WORKER FRAME_WRITTEN PASS frameIndex=0" in line and not published["value"]:
                shutil.copyfile(output,first)
                os.replace(pending,inbox)
                published["value"]=True
            elif "FF-NATIVE-WORKER FRAME_WRITTEN PASS frameIndex=1 recipientTick="+str(tick+1) in line:
                shutil.copyfile(output,second)
        run_until_markers(
            cmd_base+[task]+arguments,
            ROOT/("reciprocal-worker-"+tag+".log"),
            tuple(markers),env,on_line=publish)
        if not published["value"] or not first.is_file() or not second.is_file():
            raise RuntimeError("Reciprocal worker did not emit both adjacent-tick intent frames")
        return request,first,second

    try:
        zombie_request,chicken_first,chicken_second=produce(
            WORKER_DIR,"runAiWorker","minecraft:zombie","minecraft:chicken",
            zombie_mind,chicken_body,zombie_session,True,8)
        chicken_request,zombie_first,zombie_second=produce(
            reverse_dir,"runAiWorkerReverse","minecraft:chicken","minecraft:zombie",
            chicken_mind,zombie_body,chicken_session,False,-8)
        shutil.copyfile(zombie_request,RECEIVER_CONFIG)
        shutil.copyfile(chicken_first,RECEIVER_OUTPUT)
        shutil.copyfile(chicken_second,RECEIVER_DIR/"ff-worker-frame-next.bin")
        shutil.copyfile(chicken_request,RECEIVER_DIR/"ff-worker-request-reverse.properties")
        shutil.copyfile(zombie_first,RECEIVER_DIR/"ff-worker-frame-reverse.bin")
        shutil.copyfile(zombie_second,RECEIVER_DIR/"ff-worker-frame-next-reverse.bin")
        reciprocal_env=env.copy()
        reciprocal_env["FF_RECIPROCAL_E2E"]="true"
        run_until_markers(
            cmd_base+["runGameTestServer"]+arguments,
            ROOT/"reciprocal-mob-receiver.log",
            ("FF-NATIVE-WORKER RECIPROCAL_MOB_PAIR PASS",
             "ALL_REQUIRED_GAME_TESTS_PASSED"),
            reciprocal_env)
        print("FF-NATIVE-WORKER RECIPROCAL_MOB_TO_MOB PASS"
            +" minecraft="+opts.minecraft
            +" zombieMindToChickenBody=true"
            +" chickenMindToZombieBody=true"
            +" independentWorkers=2"
            +" framesPerDirection=2"
            +" sharedPhysicalReceiverWorld=true",flush=True)
    finally:
        for item in (
            RECEIVER_CONFIG,RECEIVER_OUTPUT,RECEIVER_DIR/"ff-worker-frame-next.bin",
            RECEIVER_DIR/"ff-worker-request-reverse.properties",
            RECEIVER_DIR/"ff-worker-frame-reverse.bin",
            RECEIVER_DIR/"ff-worker-frame-next-reverse.bin",
        ):
            try:item.unlink(missing_ok=True)
            except OSError:pass

if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:
        print("FAIL two-NeoForge-JVM native AI worker integration: " + str(exc),
              file=sys.stderr)
        sys.exit(1)
