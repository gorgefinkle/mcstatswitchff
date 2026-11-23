# P09 universal native mind-exchange acceptance contract

**Status: NOT IMPLEMENTED. This is a required acceptance contract, not a pass.**

## Non-negotiable gameplay requirement

For any two alive, loaded Mob entities—including mixed vanilla species, Brain-driven
mobs, Goal-driven mobs, hybrids, and mobs added by third-party mods—a Freaky Friday
book pairing must cause a **reciprocal native AI exchange**. It may not reject a
pair because the two Java classes, Brain implementations, goals, navigation types,
abilities, or mods differ. No species-by-species hand-authored imitation, hidden
whitelist, named exception or silent partial swap is acceptable.

The two physical entities keep the original UUID, type, hitbox, health, speed,
equipment, damage, fall rules, rendering, loot, and body-owned capabilities.
The *mind* retains its own native decision logic and state; both minds receive
the senses, position, surroundings and affordances of their **new** bodies.
The swap is simultaneous and reversible, with one mind per live body.

An AI may request a physical action that the occupied body cannot perform.
For example, a creeper mind in a cow may still pursue its target but cannot
detonate if the cow has no native explosion mechanism. The action is unavailable;
this does **not** invalidate or silently revert the entire mind transfer.

## Why the current code fails

- `NativeBrainSwap.eligibility` rejects different concrete mob types.
- `GoalMindRuntime.captureFor` must clone owner-bound Java Goal objects and
  can reject opaque subclass state; that cannot guarantee a complete exchange.
- A modded goal may directly call damage/spawn/sound/world/item APIs. Running
  it in an unspawned host *in the live JVM* is not isolated.
- `MindHostEngine` and `CapturedIntentBridge` already define safe ownership
  and authenticated, data-only dispatch contracts but are not wired to a
  continuous universal native decision source.
- `NativeAiWorkerServer` proves native target/navigation capture in a separate
  disposable NeoForge world for one CI fixture. It does NOT stream arbitrary
  modded native AI actions, attacks, Brain activities or full-world context.

## Required replacement architecture, not a new fake species AI

1. Use a **donor-class native host** for each mind. Native Brain/Goal/mob code
   executes in an isolated runtime, never as cloned owner-bound Goal objects
   in a recipient species.
2. Feed that host a read-only mirrored observation of the recipient body and
   its environment. Keep source-mind memory separate from physical-body state.
   The mind must see the recipient position/targets, not the old donor position.
3. Confine **all** native side effects in the disposable host runtime. Intercept
   or observe movement/navigation, attack, targeting, damage, blocks, inventory,
   projectiles, sounds, effects, interactions and modded behavior. No worker
   side effect may leak into the live world.
4. Produce a bounded authenticated action frame (with observation tick,
   mind identity and assigned body identity). No arbitrary code or object
   graph deserialization is accepted over the worker-to-gameplay boundary.
5. Apply an action to the occupied *real* body through its own physics and
   capabilities, never through the donor's body or a visually disguised
   replacement. Missing physical capabilities are allowed per-action, not
   per-mind swap rejection.
6. Commit/rollback two controllers atomically, and preserve uniqueness through
   death, reversal, despawn, unload/reload, dimension changes and restart.
   Keep behavior state persistent or explicitly stop gameplay until recoverable;
   never claim full success for a missing mind runtime.
7. Do not hardcode any donor or recipient species or mod ID into the runtime
   switch logic. Generalized event/interception hooks may be version-specific,
   but never entity-pair-specific.

## Required real-world acceptance tests

- Zombie ↔ Chicken: Chicken with Zombie mind actually **detects and pursues**
  a Survival player, navigates and attempts the recipient body's physical
  attack; Zombie with Chicken mind demonstrates actual Chicken decision
  behavior (e.g. nonhostile native wandering), not idle NoAI.
- Creeper ↔ Cow: goal state transfers both directions; the cow does not acquire
  supernatural anatomy while the creeper retains its body-native power.
- Villager ↔ Skeleton (mixed AI): Brain, Goals, native hooks and targeting
  all follow the minds; original body UUIDs/health/physics remain unchanged.
- Ender Dragon ↔ Chicken: no special type bypass, loss of unique bosses' body
  properties, invisible second live actor or donor world side effects.
- Two third-party modded mobs **with no adapters provided to Freaky Friday**:
  exercise native decisions and physical effects in both directions. Run
  fault injection and record any unsupported native side-effect interception.
- Multi-hop and reversal (A↔B, B↔C, undo), multiplayer interactions, chunk
  unload/load, death/respawn, persistence and 20 TPS under a realistic budget.
- Prove that the same physical entity IDs, hitboxes, movement/fall behavior,
  attributes, inventories and loot remain attached to their **bodies**.
- A headless probe that only copies names, swaps ledger entries, changes
  attributes, moves once or watches a donor in a separate world **cannot
  pass** a full native AI acceptance test.

## Release gate

`zombie_chicken_behavior_acceptance` is currently a known failing manual
GameTest. Do not reclassify it as a pass by removing its assertion or changing
what it means. The true gameplay requirement is **universal swapability**,
rather than "swaps for the mobs we patched". If arbitrary third-party code
escapes the observation/isolation boundary, report that as an unimplemented
engine capability and improve the generic runtime; never label the whole
system complete. No software can honestly guarantee compatibility with every
unknown future mod's arbitrary Java behavior without appropriate interception
and isolation, so claims must be based on passing real-world tests.


## Continuous sensory transport groundwork (not yet an integrated runtime)

The current P09 branch now contains:

- `NativeMindSenseFrame`: bounded immutable data describing the recipient
  body's real physical state, nearby entity UUIDs/types/relative positions,
  velocities, health/visibility, and a sparse block/fluid neighborhood.
- `MinecraftMindSenseCapture`: captures those facts from the occupied
  body's actual server level. It skips unloaded block positions rather than
  force-loading chunks just to feed a remote mind.
- `NativeMindSenseWireCodec`: HMAC-SHA256 authenticated binary transport,
  bounded frame sizes/counts, strict UTF-8, exact body/mind/tick binding,
  tamper detection and stale/replay rejection.
- Pure Java regression tests for the immutable frame and authenticated codec.

This is an input contract and capture/transport foundation only. It is not
yet connected to a persistent worker process, a per-tick server scheduler,
a mirrored donor-class world, native Brain/Goal state continuity, or a
complete side-effect interception layer. In particular, a snapshot of nearby
entities and blocks is not yet a full Minecraft world replica: pathfinding,
senses, memory modules, POIs, chunk state, mod capabilities and arbitrary
world queries still need a generic, bounded mirror strategy.

Do not remove the known-failing Zombie/Chicken behavior acceptance test,
and do not promote the branch to a playable release until the live book flow
uses continuous observations and passes the reciprocal physical behavior
tests above. The first synthetic-stub CI attempt after adding the capture
class failed because the legacy stub harness tried compiling a Minecraft API
class without Minecraft stubs; the class has now been excluded from that
legacy harness and remains subject to the real NeoForge 26.2/26.3 builds.
