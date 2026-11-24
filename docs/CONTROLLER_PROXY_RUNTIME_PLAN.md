# Controller-proxy runtime pivot

Status: **SELECTED PRODUCTION DIRECTION — NOT YET IMPLEMENTED**

This design replaces the disposable second-Mob/worker-arena approach as the intended production runtime. It does not claim that a mind swap currently works.

## Decision

Use one authoritative physical entity per body and one persistent logical controller per mind. Exchange controller ownership between bodies. Do not create or tick a second Minecraft Mob as a proxy.

A visual proxy is optional and presentation-only. It may represent a controller for UI/debugging, but it must have no independent collision, navigation, AI, damage, inventory, or world authority.

The logical controller is not a visual stand-in. It owns the incoming mind's decision state and schedules decisions. A decision backend must still execute the incoming mind's behavior. Until that backend is proven safe and complete, the runtime must refuse to advertise or commit a full swap.

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
