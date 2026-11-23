# Native mind runtime: implementation boundary

Status: **NOT IMPLEMENTED — architecture decision, not a release claim.**

This document records the boundary the production implementation must cross. The current detached worker and dynamic goal-graph experiments are research fixtures. Neither is the runtime that the book workflow may use to claim a complete exchange.

## Why the current worker is not the runtime

`NativeAiWorkerServer` currently:
- constructs a donor and stationary stimulus in a disposable stone arena;
- lets the donor's native AI tick in that arena;
- observes a navigation displacement and optionally one melee damage event;
- writes one signed frame, then stops.

It does not continuously receive the occupied body's surroundings, keep a persistent mind session, or capture the full action/effect stream. The recipient-world sense capture and sensory wire codec are currently standalone seams; they are not connected to this worker lifecycle. The static arena is therefore not the world the incoming mind inhabits.

A one-shot navigation frame cannot represent a mind. It cannot preserve ongoing target selection, memory, goal lifecycle, reactions to changing entities/blocks, inventory/equipment use, projectiles, interactions, or arbitrary modded abilities.

## Why copying goal objects is not a universal solution

`GoalMindRuntime` attempts to clone goal object graphs and rebind known owner services. It is a useful experiment, but it cannot certify arbitrary goal behavior:
- a goal can retain an owner through an opaque callback, lambda, helper, subclass field, static registry, or captured object not represented by the current rebinding map;
- a goal can call world-mutating methods directly, bypassing the action port;
- `customServerAiStep`, entity overrides, Brain behaviors, sensors, and modded tick hooks execute outside the two goal selectors;
- a goal's assumptions may be tied to the donor entity class and its physical capabilities.

Failing closed on inaccessible fields is necessary but does not prove that a successfully cloned graph has no hidden owner references or direct world side effects. Do not expand this into species allowlists or per-mob fixes.

## Required production architecture

The production runtime must keep a persistent controller identity separate from both physical entities.

1. **Authoritative ownership transaction**
   - Atomically exchange controller identities for both bodies.
   - Preserve body-owned health, dimensions, attributes, equipment, collision, gravity, and entity identity.
   - Roll back both sides if either controller cannot be started or rebound.

2. **Continuous native decision execution**
   - Run the incoming mind's actual native decision lifecycle continuously against a faithful, updated representation of the recipient body's surroundings.
   - Preserve Brain memories/sensors/behaviors and Goal/target selector lifecycle where present; include entity-specific native tick overrides and modded behavior surfaces.
   - Never run donor AI against its old body's world and translate a single observed movement afterward.
   - Never call AI synchronously from the player input path.

3. **Complete side-effect boundary**
   - Capture every decision's world-changing effects before they mutate the decision environment.
   - Map entity references to stable recipient-world identities.
   - Execute effects through the physical recipient's real capabilities, validation, cooldowns, and server authority.
   - Preserve order and timing; reject or explicitly report unsupported effects rather than silently dropping them.
   - Prevent the donor's old physical body from being mutated by the incoming mind.

4. **Live sensory mirror**
   - Feed continuously sequenced recipient-world snapshots into the mind runtime.
   - Include nearby entities and identity mapping, collision and block/fluid state, relevant world conditions, damage/attack stimuli, and changes since the previous frame.
   - Bound payloads and queues, handle missing chunks as unknown, and prevent stale/replayed frames.
   - Ensure the worker's internal simulation does not invent a different world state that causes decisions invalid for the real recipient.

5. **Player-body adapter**
   - A player body is not a Mob and has no Mob GoalSelector. The incoming mob mind must drive it through a body-native action adapter without pretending the player is a Mob.
   - The player's controller must simultaneously control the occupied mob body, with local camera/input prediction independent from AI scheduling.
   - Account/connection identity, inventory, commands, and permissions require explicit ownership rules; command-context projection alone is not a player identity swap.

6. **Lifecycle and multiplayer**
   - Handle death, unload/reload, disconnect, dimension changes, controller return, and an occupied original body without orphaning either mind.
   - Verify both bodies independently from a second connected observer.
   - Save enough controller state to recover consistently across server restart if persistent swaps are supported.

## Required acceptance before a user package

- Zombie ↔ Chicken in both directions, with continuous behavior decisions and changing surroundings.
- Both minds act simultaneously and independently after the exchange.
- Player ↔ Mob: mob mind drives the original player body while the player controls the occupied mob; no mirrored movement, look, attack, or interaction.
- Native movement and body-owned capabilities remain distinct from mind identity.
- Tests cover hostile, passive, ranged, interaction, navigation, environmental response, death, and reversal behavior.
- A modded entity test proves the generic interception/adapter path, not a special-case integration.
- Connected-client latency and second-observer acceptance passes on both 26.2 and 26.3.

Compilation, graph-copy tests, worker smoke tests, or a successful one-shot action frame are not substitutes for these gates.
