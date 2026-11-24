#!/usr/bin/env python3
"""Fail-closed regression for reciprocal swap entry points.

The old gameplay path launched extra NeoForge server JVMs. The isolated worker
prototype may remain as explicit research/test code, but ordinary book-driven
Player<->Mob and Mob<->Mob requests must never enter it or mutate real bodies.
"""
from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[1]


def method_body(source: str, signature: str) -> str:
    start = source.find(signature)
    if start < 0:
        raise AssertionError(f"Missing method signature: {signature}")
    opening = source.find("{", start)
    if opening < 0:
        raise AssertionError(f"Missing method body: {signature}")
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1:index]
    raise AssertionError(f"Unclosed method body: {signature}")


def main() -> int:
    supervisor_path = ROOT / "src/main/java/dev/freakyfriday/mind/NativeMindWorkerSupervisor.java"
    sessions_path = ROOT / "src/main/java/dev/freakyfriday/mind/MindSwapSessions.java"
    supervisor = supervisor_path.read_text(encoding="utf-8")
    sessions = sessions_path.read_text(encoding="utf-8")

    checks = {
        "Player->Mob": "public static synchronized String startPlayerMob(ServerPlayer player,Mob mob)",
        "Mob<->Mob": "public static synchronized String startMobPair(ServerPlayer requester,Mob first,Mob second)",
    }
    for label, signature in checks.items():
        body = method_body(supervisor, signature)
        forbidden = ("launchWorker(", "new ProcessBuilder", ".start()", "setNoAi(",
                     "PlayerMobPossession.begin", "bridge.exchange(")
        for token in forbidden:
            if token in body:
                raise AssertionError(f"{label} entry point reaches forbidden operation: {token}")
        if "temporarily unavailable" not in body or "No bodies were changed." not in body:
            raise AssertionError(f"{label} entry point must fail closed with an explicit user diagnostic")

    if "NativeMindWorkerSupervisor.startPlayerMob(p,mob)" not in sessions:
        raise AssertionError("The book selection path no longer routes Player<->Mob through the guarded entry point")
    if "NativeMindWorkerSupervisor.startMobPair(p,mobA,mobB)" not in sessions:
        raise AssertionError("The book selection path no longer routes Mob<->Mob through the guarded entry point")
    if "PlayerMobPossession.begin(" in sessions or "PlayerMobPossession.beginReciprocal(" in sessions:
        raise AssertionError("Book selection bypasses the fail-closed reciprocal supervisor")

    # The research worker implementation is retained for isolated CI fixtures,
    # but its launcher must remain private and unreferenced by the gameplay API.
    if supervisor.count("launchWorker(") != 1:
        raise AssertionError("A gameplay call site can reach the child-JVM worker launcher")
    if "new ProcessBuilder(command)" not in supervisor:
        raise AssertionError("Worker launch implementation changed; re-audit its isolation boundary")

    # No other production source may start a child process as a side effect.
    main_sources = ROOT / "src/main/java"
    for path in main_sources.rglob("*.java"):
        if path == supervisor_path:
            continue
        source = path.read_text(encoding="utf-8", errors="replace")
        if "new ProcessBuilder" in source:
            raise AssertionError(f"Unexpected child-process launcher in production source: {path.relative_to(ROOT)}")

    print("FF-SAFETY reciprocal_entrypoints_fail_closed PASS")
    print("FF-SAFETY no_gameplay_worker_launch_callsite PASS")
    print("FF-SAFETY no_body_ai_mutation_before_rejection PASS")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"FF-SAFETY FAIL {type(error).__name__}: {error}", file=sys.stderr)
        raise
