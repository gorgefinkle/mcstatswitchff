# R0.15.4 independent detached-host regression

Run `python3 scripts/test-detached-probe-lifecycle.py`. The GitHub Actions **core** job executes this before either Minecraft 26.2 or 26.3 NeoForge build starts.

Unlike `scripts/test-minecraft-stubs.py`, this compiles the **actual production** `DetachedMindHostProbe`, `DetachedHostMirrorProbe`, `ProbeBodySnapshot` and `ProbeTimeoutGuard` against a minimal deterministic Minecraft API stand-in. No production probe is replaced.

The harness exercises a Zombie donor and Cow body, fresh native-type host construction, no world registration, 40 mirroring ticks, original body health, duplicate rejection, constructor mutation, constructor exception, mutated recipient during `setPos`, 120-idle-tick expiry, donor unload, unexpected host join, session cleanup and telemetry.

The final **known-negative** check intentionally confirms that a constructor performing an unrelated world write can still pass the existing snapshot gate. That is an **unresolved safety vulnerability**, not proof of isolation. Neither this harness nor the existing CI performs actual donor Goal/Brain execution. A production GameTest using real NeoForge actors, including a modded side-effecting entity type, is still needed before any AI tick can be enabled.

This is a self-contained headless *integration-style* check, not a replacement for real Minecraft execution. CI must report PASS before its assertions are considered verified.
