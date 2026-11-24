# Controller-proxy runtime pivot

Status: **SELECTED PRODUCTION DIRECTION — NOT YET IMPLEMENTED**

This design replaces the disposable second-Mob/worker-arena approach as the intended production runtime. It does not claim that a mind swap currently works.

## Decision

Use one authoritative physical entity per body and one persistent logical controller per mind. Exchange controller ownership between bodies. Do not create or tick a second Minecraft Mob as a proxy.

A visual proxy is optional and presentation-only. It may represent a controller for UI/debugging, but it must have no independent collision, navigation, AI, damage, inventory, or world authority.

The logical controller is not a visual stand-in. It owns the incoming mind's decision state and schedules decisions. A decision backend must still execute the incoming mind's behavior. Until that backend is proven safe and complete, the runtime must refuse to advertise or commit a full swap.

## Core rule: bodies stay alive; only mind authority moves

A swap is **not** possession, puppeteering one entity while parking the other, or mirroring the same input onto both bodies.

For every reciprocal exchange:

- Both original physical entities remain registered and receive their ordinary entity tick exactly once per server tick. Gravity, velocity, collision, fall damage, animation, damage, knockback, fluids, fire, item use, block interaction, and body-owned equipment/inventory behavior remain active.
- Each body has exactly one decision authority: the incoming mind. The body's former mind must stop issuing decisions to that body while assigned elsewhere.
- The incoming mind must continue its own decision lifecycle against the recipient body's current surroundings. It must not be replaced by a one-time movement sample, a copied appearance, or the requester's raw input.
- Both directions advance concurrently. Neither body may be frozen, anchored, held at a saved position, set to NoAI as a substitute for an incoming controller, or moved in lockstep with the other body.
- This does **not** mean leaving both original AI brains running. That would create two competing decision authorities on each body. Suppress only the body's displaced controller decision loop; never suppress the physical entity lifecycle.
- Do not use `setNoAi(true)` as the swap implementation. A backend may gate the outgoing native decision scheduler only when the replacement controller has been bound and is actively driving that exact body. Any inability to bind the replacement mind must abort before changing either body.

The player-driving-a-mob experience is a separate **possession/input-adapter exception**. It can validate camera, authenticated input, body collision, and player UX, but it is not the mind-swap mechanic and cannot count as reciprocal Player↔Mob acceptance. For Player↔Mob, the mob mind must independently pilot the vacated original player body at the same time the player's mind pilots the mob body.

## Runtime-discovered locomotion; no species movement rules

Locomotion is a property of the **physical body's live implementation**, not a species name table and not a behavior profile authored by Freaky Friday.

The runtime must inspect and bind the movement surfaces actually present on each physical entity at runtime. This includes, where implemented:

- Native entity travel/physics and movement overrides.
- The actual movement controller, navigation implementation, jump controller, look controller, and body-rotation controller attached to that entity.
- Brain memories/sensors/behaviors and goal/target-goal actions that request movement.
- Runtime movement attributes, dimensions, collision shape, fluid state, gravity/drag/buoyancy rules, and environmental movement hooks.
- Mod-provided movement controllers, capabilities, mixin hooks, and entity overrides.

The discovered components are captured as a generic runtime capability contract, with concrete implementation references bound to the physical body. The controller must issue decisions through that body's discovered native surfaces; it must not reduce every entity to a universal horizontal displacement command. Vanilla and modded entities must use the same discovery and dispatch path.

**Explicitly forbidden design:** species-ID branches or hardcoded movement profiles such as "if Bat then fly", "if Chicken then glide", or "if Turtle then swim". Those examples are useful as regression subjects, but they must never appear as implementation branches or special-case registries. Adding an unfamiliar vanilla or modded entity must not require Freaky Friday source changes to recognize its movement mechanics.

The implementation must also avoid mutating or transplanting the recipient body's movement controllers from the donor. The body retains its own locomotion stack and physical parameters; the incoming mind's decision state is rebound to that stack. Conversely, the donor's former body must no longer receive decisions from that donor mind.

If a required native movement surface cannot be discovered, rebound, and safely invoked under the runtime's side-effect boundary, the entire reciprocal transaction must fail before either body is changed. Do not fall back to generic movement or claim partial mind-swap success.

Regression coverage should select entities by discovered runtime locomotion surfaces rather than species conditionals. The test suite may include a flying body, a gliding body, an aquatic body, a ground-only body, and modded examples; the assertions are about discovered capabilities and preserved behavior, not hardcoded mob identities.

## Required simultaneous-independence proof

A valid test must observe both original entities concurrently, not just see one body move:

1. Exchange two unlike mobs (first gate: Chicken↔Zombie).
2. Show the Chicken mind independently causing Chicken-native decisions in the original Zombie body while the Zombie mind independently causes Zombie-native decisions in the original Chicken body.
3. Change targets, obstacles, nearby entities, and environmental conditions after the swap; both decision streams must react to the new recipient surroundings.
4. Verify both bodies keep normal gravity/fall speed, collision, knockback, animation, damage, attacks, and interaction behavior.
5. Verify neither body's position or rotation is a mirror of the other body's controller/input stream.
6. Reverse the exchange and prove both original controllers resume their original bodies without replacing either entity.

A movement-only probe, a single controlled body, a worker that ticks a duplicate mob, or a body that merely continues its own AI is a failure.

## Runtime objects

- **PhysicalBody** — the real player or mob already registered in the target world. Owns UUID, entity type, health, attributes, dimensions, equipment, inventory, collision, physics, damage, death, and native interactions.
- **MindController** — stable identity for the mind, independent of any body UUID. Owns decision state, memories, target references, behavior lifecycle, and its execution backend.
- **BodyPerception** — bounded, sequenced, read-only observations captured from the physical body's actual world and coordinate frame. Unknown/unloaded data remains unknown; capture never force-loads chunks.
- **DecisionBackend** — runs one controller's decision lifecycle against the current body's perception and emits a complete ordered intent/effect batch. It must not mutate Minecraft world state while deciding.
- **BodyActionAdapter** — validates and performs emitted intents through the physical body's native server-side capabilities. The adapter never invokes the donor body's methods.
- **VisualProxy** (optional) — rendering/UI only. It is not a LivingEntity and cannot become a second source of physics or AI.

## Tick pipeline

1. On the server thread, capture a body-local perception frame with body UUID, controller UUID, monotonic sequence, server tick, dimension, and bounded observations.
2. Submit the immutable frame to that body's assigned controller backend. Never block player input or the server tick waiting synchronously for an AI worker.
3. The backend advances controller-owned decision state and returns an authenticated response for the exact outstanding frame.
4. Validate session, controller/body identity, sequence, deadline, action count, payload size, referenced entity identities, and capability arguments.
5. Dispatch the ordered batch through the physical body's adapter on the server thread.
6. Record per-action outcomes. If capture/decision validation fails, dispatch none of that frame. If a physical action fails after earlier actions ran, report the partial result; never pretend world effects can be rolled back.
7. Retire or rebind the controller cleanly on death, unload, disconnect, dimension change, swap reversal, or backend failure.

At most one decision frame is outstanding per body. Stale, duplicate, replayed, or late responses are rejected. Backpressure drops no state silently: the controller is paused/faulted with an explicit diagnostic rather than allowing an unbounded queue.

## Reciprocal exchange

A swap is a transaction over two body-to-controller assignments:

- Snapshot both current assignments and session states.
- Prepare both incoming controller bindings and backend sessions without changing either body's physical state.
- Verify both sessions are ready and each controller is unique.
- Atomically commit both assignments.
- If either preparation fails, close prepared sessions and leave both original assignments unchanged.
- After commit, stop both old body/controller associations before either controller can issue another action.

The physical entities are never replaced, cloned, teleported to one another, or re-created. A player's network connection remains attached to that player account/body; the player controller's input stream is routed to the occupied body adapter under explicit permission and ownership rules.

## Why this is the cleaner performance model

It avoids ticking duplicate Minecraft entities and avoids running a full second world. The perception mirror is a bounded data structure, not a second Level. Only the assigned controller backend advances decision state. Visual proxies add no simulation.

This is not a promise that CPU use will automatically fall: a faithful generic decision backend may still be expensive. Profile decision time, perception capture, serialization, queue depth, and action dispatch separately. Do not optimize by reducing behavior fidelity or by silently skipping decisions.

## Non-negotiable isolation boundary

A hidden/unspawned Mob, copied Goal graph, detached entity, or second Level is not automatically safe. Native goals, Brain behaviors, entity overrides, callbacks, lambdas, modded hooks, and helper objects may call world-mutating APIs directly.

The production backend must either:
- execute native behavior code under a verified interception boundary that captures every world-changing effect before mutation; or
- use a different generic decision representation that faithfully preserves native behavior semantics without invoking unsafe world APIs.

A list of known effects, a successful class clone, or an allowlist of vanilla species is not proof. Unknown effects must fail closed and be visible in diagnostics. Do not solve this with hand-written species-pair behavior tables.

## Player and mob symmetry

The architecture is identity-neutral: a controller can be assigned to a player body or a mob body. Body adapters remain different because players have input/network/permission semantics while mobs have native movement and AI capability surfaces.

Acceptance requires simultaneous reciprocal operation:
- Mob A's controller drives Mob B's real body while Mob B's controller drives Mob A's real body.
- A mob controller drives a real player body while the player's controller drives the occupied mob body.
- Two player controllers can control the opposite real player bodies without swapping accounts, connections, inventories, permissions, or physical identities.

No mirrored input, look, movement, or attack is accepted as a mind swap.

## Implementation sequence and gates

1. **Controller kernel:** persistent controller identity, body assignment registry, immutable perception contract, ordered action batches, lifecycle states, and atomic two-body assignment transaction. Pure Java tests cover rollback, uniqueness, stale frames, and simultaneous independent controllers.
2. **Body adapters:** mob and player adapters with explicit capability discovery, authoritative server-thread dispatch, and body-invariant tests.
3. **Decision isolation:** prove one generic native decision surface can be advanced without mutating donor or live world; instrument all effect boundaries and fail closed on unknown paths. This is the principal blocker.
4. **Continuous perception/session:** connect live body perception to persistent controller sessions with strict sequencing, backpressure, timeout, and recovery.
5. **Real gameplay acceptance:** Zombie↔Chicken both ways, ranged/hostile/passive behaviors, environmental response, player↔mob both ways, player↔player, modded entity, reversal, death, disconnect, dimension changes, and second-observer multiplayer tests on 26.2 and 26.3.

A test JAR is not ready until the gameplay gates pass. Compilation, controller bookkeeping, synthetic action probes, or a visual proxy alone do not qualify.
