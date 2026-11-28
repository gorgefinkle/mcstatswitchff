# Dynamic AI Capture Study — Universal Mind Exchange

Status: engineering study and architecture recommendation  
Target: NeoForge 26.2 only; later-version ports are deferred until 26.2 passes live acceptance tests  
Scope: any living mob body ↔ any other living mob body, including unknown modded entities

## Executive conclusion

Retire native AI object-graph copying as the transfer foundation. A Minecraft Goal, Brain, Sensor, Behavior, navigation object, control object, or captured callback is executable machinery built around a particular entity instance and often a concrete class. Copying its fields does not make it a body-independent mind.

The target is **complete native AI-runtime exchange through controller-host preservation and body virtualization**, supported by continuous server-thread observation and runtime body-capability mediation. A mind mirror is diagnostics and latency preparation; it is not a substitute for the donor's actual AI runtime:

1. Preserve each donor's actual native controller host in place: GoalSelector, Brain, Sensors, Behaviors, Goal instances, active task state, callbacks, and custom AI-owned state are not cloned or discarded.
2. Exchange the controller-to-body bindings at a safe server tick boundary, so original owner-bound AI continues to execute while its body-facing reads and outputs are mediated to the newly assigned physical body.
3. Instrument shared decision, perception, navigation, control, and action boundaries continuously so the runtime can diagnose incomplete mediation.
4. Discover the recipient body's available actions dynamically. Translate only at the physical body boundary; retain controller state and intent even when the current body cannot express a particular action.
5. Maintain a compact MindRecord as an audit/continuity mirror, not as a replacement for the real native controller.
6. Never reject a pair because of species, class, AI framework, missing capability, or unknown modded code.

This is the best balance of dynamic coverage, low swap latency, and safety. It does not magically make arbitrary private mod AI portable. No external observer can infer every hidden decision variable or reproduce arbitrary code that directly mutates private fields, casts its owner to a custom class, or performs side effects outside common APIs. That is a fundamental boundary. The product contract can still guarantee universal exchange acceptance: every pair exchanges; observed intent remains with the mind; a recipient body expresses what it can; unexpressible intent remains pending rather than causing rejection.

## Repository audit: why the current approach fails

The current branch has two separate native paths, not one universal mind:

- NativeBrainSwap moves native Brain references but applies same concrete class/type checks, rejects brain-dead mobs and any mob with GoalSelector entries, and rejects player participation.
- GoalMindRuntime snapshots native selectors and attempts to clone every Goal object graph using Unsafe allocation and reflective field replacement. It only handles pairs on the goal path and rejects active Brain AI.
- DynamicObjectGraph rejects captured lambdas, inaccessible fields, unsupported collection types, and unknown mutable objects. Its cloneability policy is not an arbitrary modded AI execution environment.
- GoalSelectorState captures scheduler bookkeeping, not the complete decision process. Running flags and control locks do not encode every task-local variable or external side effect.
- MindLedger records logical ownership but does not virtualize AI execution, body perception, or controls.

The architecture therefore has incompatible native implementation paths. Relaxing the current checks would make the code accept exchanges while leaving stale owner references or corrupted behavior. The synthetic graph tests cannot prove arbitrary modded AI portability.

## What Minecraft gives us to hook

The active development target is Minecraft 26.2 / NeoForge 26.2.0.95. The implementation plan is based on the exact 26.2 mappings and API surfaces below, not on a cross-version abstraction:

- Mob.serverAiStep is the shared server-side mob AI envelope and is protected final; inject at the head/tail rather than override it.
- Mob.customServerAiStep(ServerLevel) is the important extension boundary for Brain-driven and custom mob AI.
- Mob exposes getGoalSelector, getLookControl, getMoveControl, getJumpControl, getNavigation, and getSensing accessors. The target selector's visibility must be handled with a 26.2-specific accessor only if necessary.
- GoalSelector exposes add/remove/clear, tick, tickRunningGoals, getAvailableGoals, and control-flag operations. Observe lifecycle transitions rather than serialize its private locks.
- Brain is generic over the owning LivingEntity. Brain.State/pack captures its memory map, not the sensors and behavior/task machinery; it is not a complete brain export.
- Mob exposes target assignment and movement intent/control boundaries. Navigation and the movement/look/jump controls are useful common points to observe enacted commands and results.
- LivingEntity damage/attack and interaction boundaries provide outcomes, but cannot reconstruct decisions on their own.

Goal and Brain are materially different pipelines. GoalSelector arbitrates registered wrapped goals, priorities, running state, and control locks. Brain tracks memories, sensors, activities, and behavior controls. Some mobs also run custom logic outside either system. The observer must cover both native pipelines and the shared perception/action boundaries; it cannot assume every mob is Goal-only or Brain-only.

Keep live observation and transfer work on the logical server thread. Do not asynchronously inspect live entities. The first implementation and every hook descriptor will be verified against 26.2 only. Later-version source files are dormant references and are excluded from the build, CI, and acceptance testing until 26.2 works.

## Primary complete-exchange candidate: preserve native controller hosts

The most promising route to complete exchange is not cloning or reserializing native AI. It is to preserve the original controller host and change which physical body its body-facing operations address.

Each original Mob remains the owner of its own native runtime:
- its concrete Java class and subclass state;
- its GoalSelector and targetSelector;
- all native Goal and WrappedGoal instances and scheduler state;
- Brain, memory map, sensors, activities, behavior/task instances;
- captured lambdas, callbacks, and references to its original owner;
- mod-added fields and runtime objects that belong to that controller.

A swap exchanges the two controller-to-body bindings. It does not move Goal objects or Brain objects into another concrete class.

### Runtime shape

Conceptually maintain a pair of references:

- **controller host:** the original Mob instance whose concrete class and native AI machinery remain intact;
- **physical body:** the entity whose location, dimensions, perception, movement, equipment, and physical interactions are currently controlled by that mind.

At the normal body AI phase, the runtime identifies the mind host assigned to that body and runs the host's native AI phase under a scoped binding context. Shared body-facing APIs are mediated to the assigned physical body. The native GoalSelector/Brain stays on the host. The body still runs its own physical tick, collision, health, animation, and network synchronization.

A 26.2 implementation spike should investigate a Mixin at Mob.serverAiStep:
1. Intercept the body's normal AI phase.
2. Resolve the controller host assigned to that body.
3. Execute that host's original server AI phase under a re-entrancy-safe MindBodyContext.
4. Keep host-owned selectors, Brain, customServerAiStep, Goal instances, and private controller state attached to the host.
5. Mediate body-facing reads and action requests to the recipient body.
6. Ensure every physical body's navigation/control/physics phase runs exactly once.

This is a candidate to prove, not a claim that the repository already implements it. The critical spike is whether a host's native AI phase can be executed while shared Entity/Mob API calls are safely rebound without corrupting either host's physical tick.

### Why this is stronger than cloning

- Owner-typed Goal fields remain valid because the owner object never changes.
- Captured lambdas remain attached to the object and owner they originally captured.
- Brain generic type constraints remain valid because a Brain stays with its original host.
- Scheduler running state, task-local fields, custom mod fields, and callback state remain in place.
- A swap can be a binding-map exchange rather than a deep graph copy.

This is the first approach to prototype for complete AI exchange. A successful binding swap alone is not enough: the body-facing API mediation must be verified for position, dimensions, level, perception, targets, navigation, move/look/jump controls, equipment, attributes, interaction, and special actions.

### Critical split: mind state versus body state

Do not blindly redirect every method. Some state is mind-owned and must stay with the controller host (for example, active goal state, Brain memories, and controller attention state). Other state is body-owned (physical position, dimensions, collision, health, equipment, attributes, and control execution). A third set is an action boundary that must be translated from controller intent into a body capability.

The first 26.2 spike must classify and test these boundaries. In particular:
- target/attention state must follow the mind, but perception must query from the new body's position and senses;
- navigation decisions belong to the native controller, while movement and collision belong to the body;
- a subclass-specific action such as a Creeper fuse must remain part of the Creeper mind, while the recipient body executes the closest capability it has;
- missing recipient capabilities never cancel the exchange and never erase the donor's latent intent.

### Limit of generic host virtualization

A modded Goal that uses common Minecraft Mob/Entity APIs can benefit from the shared mediation hooks automatically. A modded Goal that reads its owner's private fields directly, casts to its concrete class, or produces world side effects without passing through a mediated boundary may bypass the bridge. Host preservation keeps its hidden state intact, but it cannot make arbitrary direct field access behave as though it referenced a different physical body.

We should instrument and mediate shared APIs first, then test real modded examples. Optional extension contracts may improve fidelity, but must never be required for an exchange. Do not claim perfect execution of arbitrary opaque third-party bytecode without proving it.

## Methodologies compared

### A. Clone native AI object graphs

Mechanism: reflectively walk Goals/Brains/behaviors, clone mutable objects, replace references to the original mob and controls, restore scheduler state.

Advantages:
- Can appear to preserve task-local state without reimplementing each task.
- Discovers runtime classes without an explicit species list.
- Useful as a diagnostic experiment for simple graphs.

Failures:
- Owner-typed fields cannot accept a different concrete mob.
- Lambdas and method references may capture the original owner or helper objects in hidden fields.
- A callback may capture an object that itself references the original mob several levels down.
- Goals may cast their owner or call subclass-only methods.
- Brain packages depend on registered memories, sensors, activities, providers, POI claims, and body-specific context.
- Unsafe allocation bypasses constructors and can violate invariants/final-field assumptions.
- The graph omits state in the entity, level, static helpers, registries, capabilities, and external managers.
- A reflective copy can be structurally valid and semantically wrong.

Verdict: do not extend this as the production transfer engine. Keep only as a diagnostic tool for understanding ownership.

### B. Snapshot GoalSelector / Brain state

Mechanism: capture running goal wrappers, priorities, lock tables, disabled flags, Brain memories, active activities, and behavior status.

Advantages:
- Cheaper than copying full graphs.
- Useful for diagnostics, persistence, and continuity metadata.

Failures:
- GoalSelector bookkeeping does not contain a portable decision policy; Goal instances remain owner-bound.
- Brain memories are only one part of Brain; sensors and behavior packages may be body/provider-specific.
- Not all memory values are serializable or transferable.
- Custom AI may live outside either scheduler.
- Entity references, POI ownership, positions, and world handles require validation/rebinding.

Verdict: one telemetry source, not the mind itself.

### C. Instrument AI lifecycle and shared action boundaries

Mechanism: inject at common runtime boundaries to record goal/behavior transitions, target changes, navigation requests, look/move/jump commands, attacks, and relevant perception results. Translate these into body-independent intent records.

Advantages:
- No native Goal/Brain object transplantation.
- Covers vanilla and modded mobs that use common Minecraft APIs.
- Captures decisions as they happen rather than inferring them from an object graph.
- Can be maintained continuously with bounded per-tick work.
- Unknown classes are observed without registering them in a species table.

Limitations:
- A goal's canUse decision is not itself an intent; lifecycle-only instrumentation misses internal task state.
- Mod code can bypass common methods, mutate custom fields, call static helpers, or perform side effects directly.
- A command stream alone is not a complete simulation of the original decision process.
- Perception must be observed too, or the transferred mind retains stale targets/context.
- Hooks must cover Goal and Brain pipelines plus customServerAiStep and shared controls.

Verdict: core ingredient, combined with continuous state tracking and capability mediation.

### D. Continuous shadow mind / event-sourced decision state

Mechanism: update a compact mind record as native AI runs. Store current attention/target identity, recent perception context, active high-level intents, urgency/priority, task phase where observable, destination, look focus, action outcomes, and continuity metadata. Keep a bounded journal of transitions.

Advantages:
- Swap cost does not depend on native AI graph size.
- No full scan, reflection, or graph allocation during the interaction.
- The mirror is already warm when exchange is triggered.
- It can retain an intent the current body cannot express, then resume it when a later body can.
- Enables diagnostics: intended, enacted, blocked, approximated, pending.

Limitations:
- The mirror only knows what instrumentation observes.
- It must not pretend to preserve arbitrary hidden mod fields.
- Journal size and event rates need bounds.
- Represent entity references as UUID/logical references plus validation, not copied live pointers.
- Context-sensitive decisions should be re-evaluated after transfer rather than blindly replayed.

Verdict: best foundation for low-latency exchange. It mirrors transferable decision state; it is not a replacement AI implementation.

### E. Preserve the native AI host and virtualize its body binding

Mechanism: keep the original native AI host and all its native AI instances intact; at the server AI phase, run that controller host against the opposite physical body through a scoped mediation context.

Advantages:
- Could preserve opaque modded decision code and task-local fields because the original class and AI objects continue to execute.
- Avoids cloning captured lambdas or reconstructing native task classes.
- May preserve behavior outside a generic command vocabulary.

Problems:
- Minecraft AI is not designed around an IMobBody facade. Goals commonly hold a concrete Mob reference and invoke it directly.
- Correct virtualization must redirect reads as well as writes: position, bounding box, level, attributes, equipment, target, senses, navigation, controls, random source, and species-specific accessors.
- A hidden host remains an entity with tick, world, persistence, collision, event, and lifecycle behavior unless deeply isolated.
- Custom mod AI can access subclass fields directly or use casts/instanceof; a generic facade cannot rewrite those assumptions.
- Shells add ticking/lifecycle complexity and risk duplicate world side effects.
- A shell cannot impersonate a different concrete subclass while retaining donor subclass logic.

Verdict: primary complete-exchange candidate. Prototype it before falling back to a reduced intent-only design. It still needs proof that shared reads, commands, and tick ordering can be mediated safely.

### F. Bytecode-level instrumentation of arbitrary mod AI

Mechanism: transform third-party AI classes to intercept field reads/writes, method calls, and owner references.

Advantages:
- More visibility than event hooks.
- Could identify direct owner access and custom state mutations.

Limitations:
- Arbitrary third-party bytecode transformation is difficult and version-sensitive.
- Reflection, invokedynamic, method handles, static state, generated classes, and direct world access complicate coverage.
- Rewriting semantics safely is not equivalent to rewriting references.
- Transformer conflicts are likely.
- Complete semantic transparency for arbitrary code cannot be proven.

Verdict: use targeted instrumentation at shared Minecraft boundaries first. Do not promise complete interception of arbitrary bytecode.

## Expanded 26.2 tick-dispatch design: preserve subclass tick and AI phases

A Mob.serverAiStep-only redirect is insufficient for complete mind continuity. Exact 26.2 source shows:
- ServerLevel.tickNonPassenger updates old position/tick count and calls Entity.tick().
- Entity.tick() delegates to baseTick().
- LivingEntity.tick() performs body lifecycle updates and invokes virtual aiStep().
- Mob.tick() delegates to LivingEntity.tick() and then updates GoalSelector control flags.
- LivingEntity.aiStep() performs movement/physics-adjacent AI work and calls serverAiStep() when effective server AI is active.
- Mob.aiStep() adds mob-specific work before/after the common living-entity implementation.
- Concrete subclasses can add mind-relevant logic in tick() outside serverAiStep. Creeper.tick(), for example, advances its fuse and can call explodeCreeper() before super.tick().

This means a serverAiStep-only dispatch would leave a Creeper's fuse logic running on the physical Creeper object rather than following the Creeper controller. This is a real functional hole, not a theoretical edge case.

### Candidate layered dispatch

For a physical Mob body B controlled by original controller host A:

1. Intercept the Entity.tick() call in ServerLevel.tickNonPassenger for B.
2. If A == B, preserve the vanilla path exactly.
3. If A != B, establish MindBodyContext(A, B) and invoke A.tick() so the concrete donor subclass's tick overrides execute with their original owner and fields.
4. At the Mob.tick() superclass boundary, redirect the LivingEntity.tick() base call to B's exact LivingEntity implementation using a Mixin invoker. This performs body B's base tick, health/effect/equipment aging, and common physical lifecycle once.
5. In B's LivingEntity.tick(), redirect the virtual aiStep() call to A.aiStep(), preserving the donor subclass's aiStep overrides and the normal Mob/Brain/Goal AI path.
6. In A.serverAiStep(), retain A.goalSelector and A.targetSelector; redirect the direct sensing, navigation, moveControl, lookControl, jumpControl, and level accesses to B's physical services.
7. Return through the controller's Mob.tick() tail so controller-owned GoalSelector control flags update based on the body context.
8. Leave entity tracking, collision, physical movement resolution, passenger handling, and network replication attached to B.

This uses the original native controller object as an executable host. It does not transplant Goals, Brain instances, captured lambdas, custom fields, or task-local state. The original class-specific tick and aiStep methods run on the controller host, while the common LivingEntity base tick and native control objects run against the physical body.

### Two concrete tick phases: controller first, body second

The dispatcher now separates the native tick chains rather than replacing the recipient's whole subclass tick with the donor's:

1. Invoke the original controller host's concrete `tick()` chain under `MindBodyContext`. This preserves its concrete subclass tick state (for example, Creeper fuse state).
2. At the donor's `Mob.tick() → LivingEntity.tick()` boundary, suppress only that donor superclass physical tick. Its controller-owned concrete tick logic has already run.
3. Invoke the recipient physical body's concrete `tick()` chain under the same context. It performs the recipient subclass's body-owned tick behavior and then reaches its normal Mob/LivingEntity base lifecycle once.
4. Redirect that physical body's `LivingEntity.tick() → aiStep()` call to the donor controller's `aiStep()`, preserving the donor's native AI decision phase.
5. Body navigation, sensing and movement controls are still resolved to the physical recipient.

The point of the split is that neither side loses its concrete subclass tick override: the donor's private AI/timer state is advanced, and the recipient's physical species tick remains active. It must still be tested for concrete examples that store behavior in `aiStep()`, not only `tick()`; the present redirect routes the LivingEntity aiStep call to the donor and therefore does not yet prove every recipient-specific aiStep side effect is retained.

### Donor AI step versus recipient Mob upkeep

Minecraft 26.2's shared `Mob.serverAiStep()` is the native AI phase: it advances sensing, target/goal selectors, navigation, custom server AI, and move/look/jump controls. The base `Mob.aiStep()` also performs physical Mob upkeep after its `LivingEntity.aiStep()` call (including tag-based daylight burning and loot pickup).

During a mind-controller phase, `MobAiStepBodyOwnershipMixin` preserves the donor's concrete subclass overrides but makes the base `Mob.aiStep()` call only its inherited `LivingEntity.aiStep()`. That still runs the donor's native `serverAiStep()` and movement pipeline, while skipping the base Mob's post-super physical upkeep. The recipient then runs its own concrete `aiStep()` body phase, with its native selector/navigation phase suppressed. This avoids the obvious capability leak of applying a Zombie's base daylight-burning upkeep to a Creeper recipient. It does not prove that every arbitrary third-party subclass separates its private AI from physical side effects; those remain a required modded-mob test boundary.

### Why this is only a candidate until proven

The controller's concrete tick/aiStep override can still write its own private subclass fields directly. That is usually correct for mind-owned task state (such as an active fuse timer), but a subclass method can also mutate fields that represent physical state, set a dead flag, discard itself, or invoke world actions directly. A generic body facade must distinguish those effects.

The first test cases must specifically cover:
- Creeper fuse intent advances with the Creeper mind while in another body, without silently exploding the original physical Creeper body.
- Reverse exchange restores that same fuse/task state to the Creeper body.
- Zombie and Skeleton attack/avoid/panic custom AI phases follow their controllers.
- Chicken/Dragon tick and aiStep overrides do not double-run or mutate the wrong physical body.
- Every physical body receives exactly one LivingEntity base tick and one physical movement/collision pass.
- Every controller executes exactly one subclass tick, aiStep, Goal/Brain, and customServerAiStep phase per server tick.

The likely first Mixin sites are ServerLevel.tickNonPassenger, Mob.tick, LivingEntity.tick, and Mob.serverAiStep. Use invokers only for the exact 26.2 superclass method calls. Every redirect must be scoped through MindBodyContext and use try/finally; global entity fields must never be swapped temporarily.

A generic tick() host invocation alone is not a complete solution if the controller's tick method performs body-specific direct field writes. If the live Creeper test exposes such a write, instrument the common underlying side-effect boundary (for example entity removal/death/explosion dispatch) and the exact inherited physical-state fields first. Do not add a Creeper-specific pairing rule or reject the pair. Preserve controller state and translate or retain its action at the body boundary.

## First Minecraft 26.2 implementation probe

The first 26.2 Mixin is intentionally passive: it injects at the head of Mob.serverAiStep and registers the original Mob as a native controller host. This proves the common entry hook can be compiled and is configured for the 26.2 target; the dedicated-server smoke test is being added to verify that the configuration actually applies at runtime.

The 26.2 controller-to-body binding ledger has also been added. It maps physical body UUID to controller-host UUID and has executable tests for arbitrary pairings, reverse exchange, bijection, idempotent registration, and repeated exchanges. This is only ownership bookkeeping; it does not yet make live AI execute on another body and is not exposed as a successful gameplay swap.

The next dispatch spike must treat the fields differently:

| Mob AI state/service | Ownership during exchange | First redirection rule |
|---|---|---|
| goalSelector / targetSelector | Native controller host | Keep on original host; this is the actual decision scheduler and its live task state |
| Brain, memories, sensors, activities, Behaviors | Native controller host | Keep on original host; never install it on an incompatible body |
| sensing | Physical body | Run perception against the new body's location and senses |
| navigation | Physical body | Use the recipient body's PathNavigation instance, whose owner is already the physical body |
| moveControl / lookControl / jumpControl | Physical body | Apply AI output through the recipient body's controls |
| level/world context | Physical body for world queries | A controller tick must observe the recipient body's current level |
| Goal/Brain task-local fields and captured callbacks | Native controller host | Keep untouched and preserve their owner references |
| target/attention memory | Native controller host | The target belongs to the mind, but revalidate it against the new body's world/perception |
| entity identity/type and subclass-specific AI state | Native controller host | Keep the original controller class; missing recipient actions are body limitations, never a pairing rejection |
| position, bounds, eye position, rotation, movement state | Physical body when read/written by AI | Mediate common Entity/Mob accessors while MindBodyContext is active |

The server AI envelope touches some controller state through direct field accesses rather than public getters. A getter-only adapter is therefore insufficient. The Mixin must redirect the exact field reads for body-bound services while leaving the two selectors with the host. It must also redirect the level lookup used when the host calls customServerAiStep. The public Mob getters then need a context-aware path for Goals and Brain tasks that call them outside serverAiStep itself.

MindBodyContext must be re-entrant and scoped with try/finally; a nested entity AI call must not accidentally inherit the wrong body. It must map both directions for a pair, not just host → body. Avoid changing global Mob fields outside the active AI call. Each physical body's collision, health, entity tick, animation, and network replication must continue once; each mind's native decision scheduler must execute once. A paired-tick test must catch double navigation/control ticks, selector double-running, and body movement driven by the wrong mind.

Do not wire the book to the new ownership ledger until the dispatch bridge passes in-game. A mapping swap without AI dispatch is not an AI exchange and must never produce a success message.

## Recommended architecture

### 1. MindRecord: body-independent audit mirror

One logical record per controller host, created when a living entity enters the server. The actual native controller remains authoritative; this record is a cheap mirror used for transaction ownership, diagnostics, persistence metadata, and latency measurement. Suggested fields:

- Stable mind UUID and schema version.
- Current body UUID; ownership is explicit and one-to-one.
- Attention target as entity UUID, last-known position, and validity timestamp.
- Generic intent slots/stack: pursue, evade, flee, investigate, guard, follow, wander, rest, interact, attack, use-special-action, etc. This is extensible and not species-keyed.
- Intent source/confidence: native lifecycle, action boundary, repeated-behavior inference, or unknown.
- Priority/urgency and start tick.
- Destination and look focus as values, not live controller references.
- Active phase and continuity counters where observable.
- Recent perception facts with expiry, not stale entity pointers.
- Last enacted action and outcome: completed, blocked, approximated, pending, invalidated.
- Opaque extension payloads only when a mod supplies a safe transfer/serialization contract. Unknown opaque state is not cloned.

Keep live Minecraft objects out of the portable core.

### 2. MindObservationBus: generic instrumentation

Use a small typed, server-thread-only internal event API:

- GoalLifecycleEvent: goal class identifier for diagnostics only, start/stop, priority, flags, running transition. Never use class identity as compatibility.
- BrainBehaviorEvent: behavior lifecycle, activity, status, priority where exposed.
- TargetEvent: target assigned, cleared, invalidated.
- NavigationIntentEvent: destination, speed modifier, path accepted/rejected/completed/stuck.
- LookIntentEvent: look target/position and limits.
- MovementIntentEvent: strafe/forward/jump request and control result.
- CombatIntentEvent: attempted attack and actual hit/result.
- PerceptionEvent: sight/targeting outcomes and relevant sensor observations.
- InteractionEvent: item/block/entity interaction and result.
- LifecycleEvent: spawn, unload, death, dimension change, leash/riding/sleep state.

Hooks should be narrow and stable. Prefer method entry/return and argument/result capture over scanning private fields each tick. No reflection in hot paths.

### 3. MindBodyBridge: generic capability discovery

This is not a species adapter registry. Ask the recipient body at runtime what it can currently do.

Capability groups:
- movement/navigation
- look/orientation
- jump/flight/swim/climb
- target acquisition/combat
- item/equipment interaction
- block/entity interaction
- flee/avoid/panic
- special actions exposed through common or mod-provided capability interfaces

Discovery:
1. Common vanilla methods/controllers.
2. Loader/mod capability or component APIs, where present.
3. Generic read-only capability metadata where safe; never clone hidden AI graphs.
4. Fallback: no direct expression; preserve the intent as pending.

Do not create Creeper-to-Zombie translations. If a donor mind carries a detonation intent and the recipient has no detonation action, the bridge may enact a generic hostile approach/attack only if that is a valid generic fallback. It must retain the detonation intent. If the mind later returns to a body with that capability, the intent becomes expressible again.

### 4. MindOwnershipService: atomic transfer

Run only on the logical server thread at a safe tick boundary.

Preflight:
- both entities are alive/loaded and in the permitted world scope;
- neither is in another transaction;
- both mind records exist or can be created;
- target references are values and can be revalidated;
- no species, class, goal, Brain, mod ID, or capability compatibility gate.

Commit:
- suspend bridge command emission for both bodies during the transaction;
- exchange MindRecord ownership bindings;
- rebind each record's current body UUID;
- invalidate per-body cached control intents and stale perception references;
- revalidate targets in the new body/world context;
- publish the new bridge bindings;
- resume action mediation on the next AI phase.

Rollback:
- if mandatory ownership bookkeeping fails before commit, restore the prior mapping exactly;
- after commit, failure to enact an individual intent does not roll back exchange. Record it as a body limitation, not exchange incompatibility.

No expensive graph copy or class scan occurs in the interaction callback.

### 5. Persistence

Persist body-independent mind records through entity persistent data or server-owned saved data keyed by mind UUID, depending on lifecycle semantics. Use stable entity UUID mappings and explicit versioned codecs. Never persist Goal instances, Brain graphs, lambdas, navigation controllers, or live references.

Test chunk unload/reload, dimension changes, death/removal, restart, and different entity load orders. Define what happens to a mind when its body dies; do not silently orphan it.

## Minimal-delay strategy

Do expensive work continuously, not when the player clicks:

- Create a MindRecord at spawn/first tick.
- Update only fields whose observations changed.
- Use compact events/reusable per-entity state; no reflection scan in hot paths.
- Cache body capabilities per body instance; invalidate only when relevant equipment, attributes, state, or providers change.
- Keep live observation and transfer on the logical server thread.
- At exchange, do a bounded ownership-map swap plus a small number of target/control rebinds.
- Do not asynchronously inspect live entities; AI state is not thread-safe.
- Profile p50/p95/p99 transfer duration, event count per mob tick, allocations, and overhead at hundreds/thousands of mobs.
- Use a ring buffer for diagnostic history.

Expected exchange complexity: O(1) with respect to native goal/behavior graph size, plus O(k) for a small bounded number of intent slots and target revalidation. It must not be O(number of AI graph objects).

## Universal compatibility semantics

Separate two promises:

1. **Exchange acceptance: universal.** Every valid living mob pair can exchange mind ownership. No species or capability mismatch rejects the transaction.
2. **Behavioral fidelity: best effort for arbitrary third-party AI.** Common Minecraft API behavior can be observed and mediated generically. Unknown private state cannot be perfectly reconstructed without cooperation from that mod or preserving its original execution context.

The runtime must preserve every abstract intent it has observed. It may mark an intent pending, approximated, or not currently expressible. It must never erase an intent merely because the current body lacks a matching action.

A mod using standard Goal/Brain/control APIs benefits automatically from generic instrumentation. A mod bypassing those APIs may have partially opaque behavior. It still cannot block exchange.

If the requirement literally means exact execution of every arbitrary private AI field and every hidden variable as if the donor remained in its original subclass, while controlling a different concrete subclass with no cooperation from either mod, a normal NeoForge mod cannot guarantee that. Java object identity, concrete type checks, direct field access, and owner-capturing closures make that promise impossible to uphold generically.

The achievable robust contract is:
- exchange succeeds for every pair;
- logical identity, attention, intent, priority, and observable task continuity move with the mind;
- the new body controls physical execution;
- capabilities are discovered dynamically;
- unsupported intent remains retained and can resume later;
- common native decision/action pathways are observed generically;
- opaque mod logic degrades in fidelity, never in exchange availability.

## Exact Minecraft 26.2 findings

The version-specific mapping pages confirm several important details for the first target:

- GoalSelector owns an available-goals set, a control-lock map, and disabled controls. Its public lifecycle surface includes add/remove/clear, tick, tickRunningGoals, and getAvailableGoals. This is scheduler state, not a portable mind.
- Brain is generic as Brain<E extends LivingEntity>. Its sensor map is typed around Sensor<? super E>; its behavior/task lists are typed around Task<? super E>; Brain.tick receives both ServerLevel and the typed body E. That generic owner binding is direct evidence that swapping a Brain between unrelated concrete bodies does not make its behavior package portable.
- Brain.State is a record containing only a MemoryMap, with a codec. Brain.pack() creates that state. It is useful for memory persistence/inspection, but it is not a snapshot of sensors, task lists, running task state, activity providers, or custom AI. Do not mistake pack() for a whole-brain export.
- MoveControl, FlightMoveControl, and SmoothSwimmingMoveControl are generic over Mob in the 26.2 migration notes. This is another sign that the control pipeline is typed around a particular Mob owner; transplanting a controller is not equivalent to moving intent.
- In 26.2, Mob#getGoalSelector is the public accessor (the migration primer notes the Bee accessor move). Prefer supported public access where possible and reserve access transformers/mixins for genuinely private boundaries.

These 26.2 details strengthen the recommendation: use Brain.State only as an optional memory snapshot source; observe GoalSelector lifecycle; observe Brain behavior lifecycle; and instrument common controls. Do not exchange Brain task graphs or control objects.

References for the exact 26.2 API shape:
- GoalSelector mapping: https://mappings.relativitymc.org/26.2/net/minecraft/entity/ai/goal/GoalSelector.html
- Brain mapping: https://mappings.relativitymc.org/26.2/net/minecraft/entity/ai/brain/Brain.html
- Brain.State mapping: https://mappings.relativitymc.org/26.2/net/minecraft/entity/ai/brain/Brain$State.html
- Control classes: https://mappings.relativitymc.org/26.2/net/minecraft/entity/ai/control/index.html
- NeoForge 26.1.x → 26.2 migration notes: https://github.com/neoforged/.github/blob/main/primers/26.2/index.md

### 26.2 Mob hook map — confirmed class and method surfaces

The 26.2 MobEntity mapping (official alias net.minecraft.world.entity.Mob) gives a concrete hook plan:

- Mob.serverAiStep is protected final. Inject at its head/tail; do not try to override it. This is the shared server AI envelope.
- Mob.customServerAiStep(ServerLevel) is protected and overridable. It is the key observation boundary for custom/Brain AI extensions. Instrument the common method call and separately verify that subclass overrides still pass through the Mob envelope.
- Mob.goalSelector and Mob.targetSelector are protected final fields. Their runtime type is GoalSelector. Use public Mob#getGoalSelector where available, but target selector may need a mixin/accessor depending on exact visibility in the target mappings.
- Mob.getLookControl, getMoveControl, getJumpControl, getNavigation, and getSensing are public. These are suitable observation points and avoid cloning those controller instances.
- Mob.getTarget, getTargetUnchecked, and setTarget are public; getTargetFromBrain is protected final. Hook target assignment and target validity boundaries, not just the selector's goal classes.
- Mob.setZza, setYya, setXxa, setSpeed, and stopInPlace expose movement intent/drive state. These are useful additional observation points but must be correlated with control requests to avoid treating every velocity update as a new intent.
- Mob.tick and Mob.aiStep are visible lifecycle/tick surfaces. The Mob AI envelope should be instrumented once; avoid double-counting if aiStep and serverAiStep hooks both see the same action.
- Mob.updateControlFlags is protected and affects selector controls. Capture its control enable/disable transitions as environment constraints, not as the mind choosing a new intent.

This confirms a practical first hook set for 26.2: serverAiStep envelope; GoalSelector.tick/tickRunningGoals and WrappedGoal lifecycle; Brain.tick and memory writes; customServerAiStep; setTarget; navigation request methods; MoveControl/LookControl/JumpControl command methods; and attack/action outcome boundaries. Add startup hook-health diagnostics for each family.

Important 26.2 implementation note: the GoalSelector mapping exposes addGoal, removeGoal, removeAllGoals(predicate), tick, tickRunningGoals(boolean), getAvailableGoals, disableControlFlag, enableControlFlag, and setControlFlag. Prefer observing its actual lifecycle rather than trying to serialize its private lock map.

## Implementation sequence

### Phase 0 — exact version source map
- Inspect exact NeoForge 26.2 mapped classes and injection points for Mob.serverAiStep, GoalSelector, Brain, PathNavigation, controls, target assignment, sensing, and action boundaries.
- Keep 26.2-specific descriptors in the 26.2 platform source set; keep core event/record types version-independent.
- Add startup diagnostics proving which hooks applied. Missing hooks must be visible.

### Phase 1 — observer only
- Add per-mob mind records and event bus.
- Instrument Goal and Brain lifecycle and shared controls.
- Add a debug command/report showing a mob's mind record and recent transitions.
- Observe Creeper, Zombie, Skeleton, Chicken, Dragon, Villager, and at least one modded mob.
- Confirm observer mode does not change native AI behavior.

### Phase 2 — body bridge without ownership swap
- Route observed intents back to the same body in shadow/validation mode.
- Compare native requested actions with bridge-expressed actions.
- Detect duplicates, feedback loops, and ordering problems.
- Do not run two controllers against one body; shadow mode compares only.

### Phase 3 — exchange with generic intent layer
- Enable exchange for all living Mob pairs with no species checks.
- Prevent native AI from double-driving the body for intent categories controlled by the bridge.
- Preserve unobserved native AI behavior without claiming it transferred; expose fidelity diagnostics.
- Test both directions and repeated reversals.

### Phase 4 — mixed AI and modded behavior
- Add generic hooks for customServerAiStep and shared action/perception APIs.
- Validate modded mobs without Freaky Friday-specific code.
- Optional mod capability contracts may provide deeper state transfer, but are never required for exchange.

### Phase 5 — player bridge
- Player mind/body is a separate identity/security problem. Authenticated account identity, permissions, inventory ownership policy, chat identity, and network control must remain explicit.
- Use the same abstract mind model but a separate player body bridge.
- Player-to-NPC is not solved by treating ServerPlayer as another Mob. Do not claim it works until input/control and account identity semantics pass end-to-end tests.

## Acceptance and performance tests

Pair acceptance matrix, both directions and repeated at least 20 times:
- Creeper ↔ Zombie
- Skeleton ↔ Zombie, including avoidance and panic
- Ender Dragon ↔ Chicken
- Villager ↔ hostile mob
- Vanilla ↔ modded mob
- Modded ↔ a different modded mob

Acceptance:
- Exchange always commits for two valid living bodies.
- No Goal/Brain class or species compatibility rejection.
- Mind UUID and intent state follow the mind, not physical body.
- Targets are rebound by UUID and revalidated, never carried as stale entity pointers.
- Missing capabilities mark intent pending/approximated; they do not reject or erase it.
- Reverse exchange restores the prior mind with retained intent.
- No duplicate goal execution, duplicate attacks, or double movement controller.
- No crashes when a modded goal has owner-typed fields or captured lambdas; those objects are not transplanted.
- Chunk unload/reload and restart preserve the defined ownership policy.

Performance:
- Benchmark 1, 100, 500, and 1000 active mobs.
- Measure observer overhead per AI tick, allocation rate, and event volume.
- Measure transfer p50/p95/p99 and worst case at varying native goal/behavior counts.
- Verify transfer duration is independent of native AI graph size.
- Profile a real modded server, not only unit tests.

## Immediate recommendation

1. Stop extending DynamicObjectGraph as the transfer engine; keep the reflective copier only as a diagnostic experiment.
2. Prototype native controller-host preservation with a 26.2 Mob.serverAiStep binding-context spike. Preserve original Goal/Brain instances rather than transplanting them.
3. Prove that body-facing accessors, perception, navigation, control outputs, and action boundaries are mediated while each body's physical tick runs exactly once.
4. Keep MindLedger as the ownership map and add a compact audit mirror; neither may report complete exchange until the native runtime actually runs through the recipient body.
5. After universal NPC-to-NPC exchange works, extend the same host/body separation to NPC-to-player control.
6. Make capability mismatch affect enactment only, never exchange acceptance.
7. Do not ship a test JAR until live cross-species and modded-pair tests pass.

## Source basis

- Minecraft 26.3 AI anatomy reference, used as a current architecture map: https://minecraftdocs.dev/systems/entities/ai-goals-and-brains
- NeoForge 26.2 → 26.3 migration primer, used to flag version-specific changes: https://docs.neoforged.net/primer/docs/26.3/
- Repository branch feature/whole-mind-swap, audited files: NativeBrainSwap, GoalMindRuntime, GoalSelectorState, DynamicObjectGraph, and MindLedger.

This document is a study and architecture decision, not a claim that the recommended runtime has already been implemented or tested.
