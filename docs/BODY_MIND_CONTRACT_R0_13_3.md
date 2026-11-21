# Freaky Friday — Separate Body and Mind Contract (R0.13.3)

## Core rule

**The entity BODY is authoritative for what it physically is and can do. The
swapped MIND is authoritative for what actions it wants to attempt.**

This supersedes the earlier conceptual "use the donor's actual entity and
disguise it" approach because that would inadvertently transplant donor
health, speed, collisions, interactions, loot and other physical properties.

The book still exchanges **one complete mind**, not user-selected stats,
abilities or individual goals.

## Native body-owned state

- Physical Minecraft entity type, UUID, model, dimensions, collision and
  animation rig.
- Health/max health, armor, base and temporary attributes, body speed,
  gravity, resistances, status effects and equipment.
- Inventory, held items, loot/drop tables, drops, XP and death.
- Physical anatomy and mechanical capabilities: cow milking, sheep
  shearing, saddles, creeper fuse/explosion implementation, flight
  mechanisms, physical impacts and footstep sounds.
- Vanilla and modded physical interactions continue to address the body.

## Mind-owned state

- Decisions, drives, personality, aggression, threat interpretation, goals
  and their ordering, Brain memories, sensors, schedules and AI target
  selection.
- Mind identity and controller assignment, or player intent/input for a
  human controller, subject to normal account security.
- Action requests: move to a target, look at something, attempt a jump,
  attack, detonate, speak/react, work, flee, interact, etc.

**A mind request does not grant an ability.** A cow body with a creeper
mind may want to detonate, but cannot physically explode. That means the
body-action request is `UNAVAILABLE`; **the whole mind swap still
succeeds**. If a creeper body receives a cow mind it retains its explosive
mechanism, but that new mind does not normally request a detonation.

The three outcomes at the body interface are `EXECUTED` (the body acted),
`BLOCKED` (it could act but the requested action is not possible now), and
`UNAVAILABLE` (body has no such mechanism). The action catalog is
namespaced so other mods can extend it.

## Implementation checkpoint

- `MindLedger`: atomically exchanges controller ownership.
- `BodyActionGate`: species-independent mind-intent contract that checks
  the receiving body's ability without changing any body-owned state.
- `MinecraftBodyPort`: first native game-facing dispatchers for navigation,
  looking, jumping and, only when the *recipient body* actually is a
  creeper, invoking its actual fuse mechanism.
- `BodyActionGateSelfTest`: confirms health, speed, loot, milking,
  movement and explosion boundaries and exact reversal.

**Crucially these components do NOT YET constitute complete gameplay body/mind
separation.** Existing `GoalMindRuntime` still copies native Goal objects
and `NativeBrainSwap` still moves native Brain references; no adapter yet
translates every transferred Minecraft goal/Brain tick into portable
body-action intents. The new native BodyPort is present but not wired to
an AI tick bridge. Do not claim arbitrary mob swaps now work.

## Engine completion gates

1. Introduce a centralized, server-authoritative **MindDriver** per body.
   Disable the body's native decision-making while a foreign mind controls
   it; retain its native body code and physical ability methods.
2. Convert source AI goal/Brain output into **body actions**, without
   moving donor Java goal instances that contain hardcoded subtype casts.
   Preserve state, scheduling, sensor inputs and decision logic.
3. Build **dynamic action/capability discovery** for modded entities without
   an all-species whitelist. Native body providers advertise their
   capabilities and executable methods. Unknown action semantics cannot
   silently be treated as transferable.
4. Ensure all native body interactions still work correctly. A cow that is
   controlled by a zombie must still be milkable, use cow health/speed,
   and drop beef/leather. Similarly, a creeper with a cow mind keeps its
   explosive mechanism and own loot.
5. Test hostile/passive pairs, complex Brain-vs-Goal systems, reversal,
   complete lifecycle persistence, and multiplayer. Player↔player control
   requires real connection/input routing—not camera swapping.

## Test coverage and scope

Pure Java behavior tests check action gating and body-property invariance,
but cannot prove integrated Minecraft AI. Real NeoForge 26.2 and 26.3
compilation verifies signatures, not in-game functionality. A dedicated
runtime test is still required.
