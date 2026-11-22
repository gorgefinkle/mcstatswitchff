# Freaky Friday R0.15.4 — Native Worker P01 (two real NeoForge JVMs)

**Verified:** GitHub Actions run 45000351006, commit
`12184c9da7a59e7b5711eab41f4581f37579098b`: **all 9 jobs passed**.
Both Minecraft 26.2 and 26.3 passed separate worker/receiver processes.

## Implemented, observed, and actually tested

- The same compiled NeoForge mod has an `aiWorker` run configuration with
  `gameDirectory = run-ai-worker`. This uses a **different operating-system
  process and disposable Minecraft world** from the receiving GameTest server.
  Worker code refuses to run when that special JVM property is set outside
  the dedicated worker game directory. The regular player-facing mod does
  NOT enable the worker.
- The independent native worker creates **real registered Zombie and Villager
  entities**, leaves the Zombie's normal Minecraft AI enabled, and uses a
  shade-protected, force-loaded stone arena. No Goal tick is invoked manually.
  It waits until the world entity manager registers the mobs and then
  **observes** the Zombie's real target acquisition and in-progress navigation.
  The decision is not scripted or produced by a fake Goal.
- The worker captures this first supported **target-and-navigation observation
  only**, creates a bounded, immutable `LOOK_AT` / `MOVE_TO` intent frame,
  signs it with a 256-bit session HMAC key, and writes it to an atomic binary
  output file inside its OWN disposable game directory.
- CI stops that worker process and separately starts **another real
  NeoForge GameTest JVM**. It hands over only the descriptor and binary
  frame; there are no shared Minecraft-world or Java object references.
  The receiving server constructs a real Cow, matches the original body ID,
  authenticates the signed frame, enforces the mind assignment and tick,
  rejects a tampered frame and a replay, then runs the Cow's own native body
  capability (look) without granting it donor anatomy.
- The tests demonstrate an observed real AI decision crossing process
  boundaries and affecting a real recipient's supported mechanism. The
  receiver does **not** run the donor Mob, Goal or Brain. Both NeoForge
  GameTests verify body identity/health preservation.
- The native-worker job is a required CI gate for **both** Minecraft 26.2
  and 26.3, in addition to the original command, construction, collision,
  world-effect escape, signed-frame, and 100-tick dedicated-server checks.

### Evidence from CI

Minecraft 26.2: Zombie target observed for 14 ticks and navigation active for
2, a 205-byte signed frame produced, independent recipient execution verified,
all 11 required receiver GameTests passed.

Minecraft 26.3: Zombie target observed for 6 ticks and navigation active for
2, same 205-byte signed frame and real recipient checks passed, all 11
receiver GameTests passed.

## Run the test without manual mobs, book selection, or worlds

```shell
python3 scripts/test-native-worker-e2e.py --minecraft 26.2
python3 scripts/test-native-worker-e2e.py --minecraft 26.3
```

Requires Java 25 and the same NeoForge/Gradle environment as the rest of
this repository. The script creates disposable world data and a random
session secret, automatically launches and stops **both** JVMs, and fails
when native AI observation, packet creation, receiver authentication,
physical body action or any of the 11 required GameTests fails. Use only
a disposable CI workspace. The worker session key is passed in the process
environment and never written to the fixture or printed.

The build jobs independently generate a **single full version-specific ZIP**
containing one current, versioned JAR, a complete nested buildable SOURCE.zip,
checksums, and installation notes.

## Strict limitations; do NOT label this a complete mind swap

This is **P01**: one-shot, observational capture of vanilla target/navigation,
with an offline (sequential-process) file handoff between distinct Minecraft
servers. It is not a running networked session and does NOT yet preserve the
donor's entire GoalSelector, Brain, boss phases, combat abilities or arbitrary
modded Java side effects. The packet's destination coordinates come from the
worker test arena, not a synchronized live-recipient observation model; native
MOVE_TO may legitimately report BLOCKED in the different receiving world.

Dragon ↔ Chicken detached-host creation and command paths were verified
earlier, but **their native AI has not yet been successfully transferred
through this worker**. They need more general movement/phase observation,
world snapshots and capability coverage, with no per-pair special rules.

Separate JVM and different game directories prevent accidental live
`ServerLevel` references. They do **NOT** constitute a complete OS security
sandbox for malicious mods: same-user filesystem or network calls could still
affect production resources. For modded AI, a separately permissioned
container/OS account and restricted filesystem/network policy is still
mandatory. Do not run untrusted modded code alongside any production world
using this proof of concept.

The next engineering gate is continuous, synchronized worker observations,
real session lifecycle/cancellation/watchdogs, generic observational adapters
for Dragon/Chicken movement and boss phases, and protected OS-level worker
deployment. Never claim full dynamic mod compatibility or a finished native
mind swap on this P01 checkpoint.
