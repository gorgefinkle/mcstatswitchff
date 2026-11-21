# Freaky Friday — R0.14.2 Native Mind Host Feasibility

**Development branch:** `feature/native-mind-host-poc`  
**Minecraft targets:** 26.2 and 26.3 / NeoForge / Java 25

## Intended gameplay

One book, two living bodies: exchange **entire mind/controller identity**,
not individual stats, goals, or attributes. The physical body retains
appearance, health, speed, size, inventory, loot and native interactions
such as milking or shearing. A mind can ask for an action; the recipient's
actual anatomy and abilities determine whether it can execute.

No hand-written source-species / recipient-species pairing table.
Modded bodies may register physical capabilities by action ID.

## What R0.14.2 implements

- `MindHostEngine`: isolated-decision **interface**, reciprocal controller
  ownership, bounded actions, one frame per body per tick, explicit failures
  and correct body-only property preservation. Verified with pure Java tests.
- `BodyCapabilityRegistry`: physical action registration by namespaced
  operation (not species pair), including independent third-party providers.
- `MinecraftBodyPort`: native Mob navigation, looking, jumping, and
  recipient-body creeper ignition through capability dispatch.
- **`/freakyfriday hostprobe`**: after selecting a real Mob with the book,
  sends synthetic look/path instructions to the *actual selected body*
  through the new host and body port, reports accepted/blocked actions,
  and verifies body UUID, type and health didn't change. This is a physical
  execution probe, **not a donor AI swap**, and native Mob AI can override it.
- **`/freakyfriday hostprobe40`**: after selecting a real Mob,
  executes a bounded synthetic action source over 40 real entity ticks
  and reports actual displacement, physical body invariants and native
  body-command outcomes. The mob's normal AI remains enabled, so observed
  displacement is not automatically caused by the probe.
- **`/freakyfriday inspect`**: after selecting a Mob, prints its actual
  goal/target goal classes, priorities and running status, Brain activity,
  subclass AI overrides, and **possible world-effect API references**
  discovered from classfile bytes. It executes no extra AI, and is
  a risk survey, **not an isolation certificate**.
- Core tests cover synthetic cross-body decisions, reversal, body invariants,
  partial physical failure, interrupted AI capture, duplicate tick prevention,
  bytecode effect candidates and mod-capability extension.

## Explicitly NOT supported yet

A safe **virtual native AI execution environment** that runs the donor's
original Goal/Brain/subclass code without physical world side effects is
**not implemented**. An ordinary hidden or unspawned Mob can still modify
a real Level through Java calls. That is the critical next engineering gate.

Until native effects are isolated, **selecting two book targets in R0.14.2
does not claim or perform a complete mind swap**. It provides a clear
diagnostic instead of invoking the earlier experimental reflective
Goal/Brain transplant. That original proof remains available on
`feature/whole-mind-swap` for comparison. R0.14.2 does not provide full
cross-species swaps, player control, world persistence, or multiplayer
runtime validation.

See [R0.14.2 timed real-body probe](docs/R0_14_2_TIMED_BODY_PROBE.md) and [R0.14.1 effect audit](docs/R0_14_1_LIVE_PROBE_AND_EFFECT_AUDIT.md)
and [native mind-host feasibility and acceptance gates](docs/R0_14_NATIVE_HOST_FEASIBILITY.md).
Neither CI compilation nor synthetic tests are proof of in-game AI behavior.

## Build and smoke test

```bash
sh scripts/test-core.sh
python3 scripts/self_audit.py
./gradlew build -PtargetMinecraft=26.2 --no-daemon
```

In a **disposable 26.2 world**, hold the Freaky Friday book, select one
Mob, run `/freakyfriday inspect` and `/freakyfriday hostprobe`, and
inspect the messages and `latest.log`. Then cancel the selection with
`/freakyfriday cancel`. Repeat with a third-party modded Mob.
For the full future player-facing concept, see
[whole-mind swap roadmap](docs/WHOLE_MIND_SWAP_R0_13.md).

Known source references: `main` contains R0.12 numerical category
experiments; `feature/whole-mind-swap` contains the previous R0.13
experimental AI transplant; the current branch is a **new hybrid host
architecture**.
