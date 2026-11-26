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

## Current P09 implementation and why it is not yet safe

- `GoalMindRuntime` currently creates an unregistered proxy of each donor's
  own EntityType, captures/rebinds its Goal and target-selector graph, copies
  available Brain state, and advances its native AI hooks on the live server.
  Each proxy's decisions are projected onto the opposite original physical
  body. This avoids transplanting a Zombie GoalSelector directly into a
  Chicken instance, but it is still an experimental same-server proxy runtime.
- The proxy shares the live `ServerLevel`. It is not a sandbox. A native
  Goal, Brain behavior, entity override, callback, or modded hook can still
  mutate live blocks/entities, spawn effects, change inventories, or perform
  other world actions directly. The bytecode effect survey is diagnostic,
  not an interception boundary.
- `DynamicObjectGraph` rebinds discovered owner/control references and rejects
  some incompatible graphs. A successful graph copy is not proof that every
  opaque callback, captured helper, static reference, or modded side effect is
  safely isolated.
- Movement intent is extracted from the native proxy and applied through the
  physical body's own native travel path. A current regression showed that a
  non-`Mob.getTarget()` goal such as Chicken `TemptGoal` can be running while
  its MoveControl request is consumed before it is projected. The runtime is
  being corrected to capture requested MoveControl intent before that tick.
- `MindHostEngine` and `CapturedIntentBridge` define bounded ownership and
  data-only action contracts, but they are not yet connected to a complete,
  continuously executing, isolated native decision backend.
- `NativeAiWorkerServer` runs in a separate disposable NeoForge JVM and can
  capture a limited navigation/melee stream from a mirrored worker world.
  That worker currently lacks the live book-session supervisor, full sensory
  world, reciprocal per-mind scheduling, and complete side-effect capture.

This experimental in-process path is for disposable test worlds only. It is
not the selected final runtime and must not be represented as universal or
safe for a normal survival world.

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

## Current acceptance evidence and release gate

The required GameTest suite now includes `zombie_chicken_behavior_acceptance`,
`villager_zombie_brain_swap`, `partial_native_swap_fails_closed`, and the
recipient sensory snapshot/mirror test. The latest fully verified commit
(`d74b5cc`) passed all 25 required tests on both Minecraft 26.2 and 26.3.
The cross-species fixtures now use individually sized GameTest structures so
their live stimuli are not outside the test runner's reserved bounds.

The passing Zombie↔Chicken test proves two simultaneous, specific behaviors:
the Zombie mind acquires and pursues an Iron Golem while hosted by the original
Chicken, and the Chicken mind's native TemptGoal moves the original Zombie
toward a registered seed-holding player. It also confirms that the Chicken
body does not gain a melee capability it does not physically possess. The
Villager↔Zombie test exercises a Brain-driven mind against a Goal-driven mind,
with independent stimuli, original body identities, and restored controller
state. The reciprocal combat tests confirm that mapped attacks reach the
opposite real physical health pools.

These tests validate the current experimental same-server implementation for
the covered cases. They do **not** establish arbitrary vanilla/modded mob
compatibility, live-client camera/input/UI correctness, or native side-effect
isolation. The active book route still uses proxies that share the live
`ServerLevel`; it is not safe for a normal survival world or a release.

Do not reclassify this acceptance as universal by weakening assertions or
adding species-specific workarounds. If arbitrary third-party code escapes the
observation/isolation boundary, report that as an unimplemented engine
capability and improve the generic runtime. No software can honestly
guarantee compatibility with every unknown future mod's arbitrary Java
behavior without appropriate interception and isolation.


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

Do not remove or weaken the required Zombie/Chicken behavior acceptance
test. The current P09 book-selection route now invokes the experimental
same-server proxy runtime described above; unlike earlier fail-closed
checkpoints, it is not merely a diagnostic selection flow. Because those
proxies execute against the live ServerLevel without complete side-effect
interception, the current route must remain test-world-only and is not a
playable release. The final runtime must use a persistent controller and a
verified decision/effect boundary, not simply make the current proxy test pass.

## Current implementation evidence (P09 worker-stream increment)

The branch has progressed beyond the earlier one-shot worker proof. The following
specific bounded path is now implemented and CI-tested:

- NativeMindSenseFrame and its authenticated codec carry body-local physical
  geometry/orientation, nearby living-entity identity/type/relative pose/velocity,
  and bounded block/fluid state properties. Unloaded block positions are explicit
  unknowns, not air.
- NativeMindMirrorState computes and atomically applies adjacent-tick entity
  and block deltas. NativeMindMirrorWorld materializes those observations in
  the separate worker ServerLevel, including no-AI mob target copies and
  UUID-stable FakePlayers for observed player targets. It restores managed
  temporary entities and overwritten blocks on shutdown or reconciliation fault.
- NativeAiWorkerServer can remain alive across adjacent authenticated sensory
  frames. It claims a single inbox update atomically, rejects invalid/out-of-order
  deltas, reconciles the mirror, and emits another bounded native navigation/
  optional attack frame for the newly observed recipient tick. A silence guard
  now retires an abandoned worker after 10 worker ticks without fresh senses.
- The two-JVM CI harness now proves two successive recipient observations
  (1700, 1701) in one worker session, verifies the second worker action frame,
  authenticates and dispatches both frames to the same real receiver entity, and
  checks that the receiver physically moves after the updated observation. It
  runs for Minecraft 26.2 and 26.3.

This is an important persistent mirror/transport milestone, but it is still a
**limited integration probe**. The CI harness builds the sensory inputs using
NativeMindSenseFixtureWriter; it does not yet drive the worker from live
MinecraftMindSenseCapture output in a normal server session. The worker is
started by the dedicated Gradle runAiWorker test run, and the receiver is a
second dedicated GameTest server. No in-game server supervisor currently
launches, monitors, restarts, or schedules these workers for book-selected
minds. The ordinary Freaky Friday book route intentionally remains fail-closed.

Still unimplemented for the actual product:
- a live per-mind worker process supervisor and tick scheduler in the server;
- actual live MinecraftMindSenseCapture output fed to that worker and
  authenticated replies dispatched by the normal server tick loop;
- complete donor-side effect interception and data actions for projectiles,
  status effects, sounds, inventory, block/entity interactions, building,
  abilities, mounts, and mod-specific hooks;
- sufficient world context for pathfinding/Brain POIs, memory modules, fluids,
  collision edge cases, time/weather, dimensions and modded capabilities;
- reciprocal multi-worker scheduling for both minds in a two-body exchange;
- player-to-mob and mob-to-player reciprocal control, including player input,
  camera/view ownership, packets, and multiplayer observers;
- persistence/recovery across process restart, unload/reload, death, reversal,
  and dimension change.

A second native movement frame is not proof of complete Brain/Goal or modded
behavior transfer. The current book route is an experimental same-server
proxy path and remains unsafe for normal play. The two-JVM worker evidence is
deliberately labelled `completeNativeMindTransfer=false`; it is not wired to
the live book workflow.
