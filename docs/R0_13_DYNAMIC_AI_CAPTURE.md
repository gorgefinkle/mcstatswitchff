# R0.13.2 — Dynamic whole-AI capturer (experimental)

## Why the previous approach was rejected

R0.13.1 embedded cow/pig `Profile` enums, hardcoded goal constructors,
specific food predicates and priority tables. That only *imitated* AI
transfer for selected vanilla mobs. It was not the dynamic brain capturer
required for the full Freaky Friday book.

**All of that species-specific production code has been removed.** The
book remains two-click with no categories: first body, second body.

## New runtime — capture what the source actually has

The goal-driven path now discovers **all** goals from BOTH native
`Mob.goalSelector` and `Mob.targetSelector`. It captures:

- Every concrete goal object, including third-party goal subclasses;
- The actual live priority and scheduling flags;
- Current instance configuration and runtime state in instance fields;
- Nested AI objects, arrays, collections, maps, and cross-goal shared
  references in **one** graph traversal.

It then uses a receiver-bound graph rewriter to substitute the donor mob,
its navigation, movement/look/jump controls, sensing, and random service
with the **recipient's corresponding live instances**, and copies every
remaining accessible field. All goals and targeting goals are captured
**before either entity's native selectors are touched**.

The core does **not** contain a whitelist of mob species, predefined
goal recipes, or special cow/pig paths. Cross-species admissibility depends
on observed runtime object graph compatibility, not mob IDs.

## Safety contract

- An owner field whose declared type cannot accept the new body rejects
  the **entire** swap with a field-level reason (e.g., a goal requiring
  `Creeper` cannot be rebound to a `Cow`).
- Opaque stateful captured lambdas and unknown mutable owner-bound
  dependencies reject the whole swap; they are never silently reused.
- This prototype relies on JDK `sun.misc.Unsafe.allocateInstance` for
  reproducing actual goal instances without invoking constructors. Its
  availability is verified when planning the capture. Failure is an
  unsupported-goal result, not permission to skip a goal.
- Original goals are left in place during the complete preflight and
  rebind. After both recipient graphs are built, both selectors are
  replaced, read back by **object identity and priority**, and the server
  mind ledger is committed. If either install or commit fails, both
  selector packages are restored; incomplete rollback is explicitly
  reported and never called success.
- A full cross-species exchange may still be rejected because arbitrary
  Minecraft goals and mods are not required to be portable.

## What is not yet proven or implemented

- **Arbitrary species behavior** does not automatically become portable.
  Species-specific methods such as `customServerAiStep`, hardwired
  attacks, movement physics, and custom mod systems run outside the
  goal selectors and are not yet dynamically captured or redirected.
  Swapping every goal does **not** necessarily swap all such behavior.
- Active Mojang `Brain` graphs (sensors, memories, schedule/activity
  handlers) are not yet rebound across different classes; the legacy
  native Brain exchange remains limited to compatible same-class mobs.
- The reflective clone has not been behaviorally verified in a running
  26.2 server. Generic class/field compatibility cannot prove that a
  transferred goal's later `canUse`, `tick`, or `stop` methods work on
  the new body. Runtime acceptance is essential.
- Player ↔ player, player ↔ mob control and save/restart persistence
  are still not implemented.
- Exact restoration of currently-running goal scheduler state and
  navigation path progress is not guaranteed; a completed capture is
  not the same as a fully portable executing mind.

## Technical acceptance gates

1. **Pure Java:** `DynamicObjectGraphSelfTest` must prove real field
   capture, copied mutable flags, nested references, cross-root alias
   preservation, state retention, subclass-incompatible recipient
   rejection and rejection of opaque captured callbacks.
2. **Minecraft source/CI:** The real NeoForge 26.2 and 26.3 compilers
   must accept the capture/runtime hooks, and both artifacts must contain
   `GoalMindRuntime` and `DynamicObjectGraph` bytecode.
3. **In-game basic cross-species:** Try cow↔pig and cow↔sheep first,
   but **not** because those classes are whitelisted. Confirm behavior
   changed, both donors stopped their outgoing goals, and reversal
   restores their original goals. A refusal with a concrete field name is
   valid evidence of a portability boundary, not a successful swap.
4. **Hostile and modded AI:** Introduce a mob with targeting goals and
   a modded `Goal`; confirm every observed goal moves or the entire
   swap refuses—never silent partial success.
5. **Complete mind:** Incorporate and validate Brain activities,
   species-native actions, player input routing, synchronization,
   ownership persistence and restart-safe reversal before advertising
   *arbitrary* species support.

This is a dynamic AI-goal capturer **foundation**. It is not a certified
complete mind/executable AI swap for every pair. We must not conflate
CI compilation, graph rebind feasibility and proven in-world behavior.
