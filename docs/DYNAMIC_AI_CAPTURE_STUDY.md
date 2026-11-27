# Dynamic AI Capture Study — Universal Mind Exchange

Status: engineering study and architecture recommendation  
Target: NeoForge 26.2 first; shared engine with version-isolated hooks  
Scope: any living mob body ↔ any other living mob body, including unknown modded entities

## Executive conclusion

Retire native AI object-graph copying as the transfer foundation. A Minecraft Goal, Brain, Sensor, Behavior, navigation object, control object, or captured callback is executable machinery built around a particular entity instance and often a concrete class. Copying its fields does not make it a body-independent mind.

The strongest practical direction is a **continuous server-thread mind mirror, generic decision-boundary instrumentation, and runtime body-capability mediation**:

1. Maintain a body-independent mind record for every participating entity and update it continuously during ordinary AI ticks.
2. Observe AI lifecycle and intent at shared runtime boundaries rather than cloning native AI graphs.
3. Separate durable intent, attention, priority, and observable task continuity from physical body controls.
4. Discover the recipient body's available actions dynamically and route intent through a generic bridge.
5. Commit ownership changes atomically at a server tick boundary.
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

The current 26.3 source anatomy (a map, not a substitute for checking exact 26.2 signatures) shows a common server-side mob AI tick with distinct subsystems:

- Mob.serverAiStep: common effective server AI entry point.
- Mob.goalSelector and Mob.targetSelector: goal arbitration and running-task lifecycle.
- Mob.getBrain() and Brain.tick: behavior/memory AI used by a subset of mobs.
- Mob.customServerAiStep: extension point for Brain-driven and custom mob logic.
- PathNavigation.tick: path progression and repathing.
- MoveControl, LookControl, JumpControl: physical command execution.
- Sensing: cached perception and line-of-sight.
- Mob target assignment/getTarget: target state boundary.
- LivingEntity damage/attack and interaction boundaries: action outcomes, but not sufficient alone to reconstruct decisions.

Goal and Brain are materially different. GoalSelector arbitrates registered wrapped goals, priorities, running state, and control locks. Brain tracks memories, sensors, activities, and behavior controls. A mob can also run custom logic outside either system. Universal observation must cover both pipelines and shared perception/action boundaries; it cannot assume every mob is Goal-only or Brain-only.

The documented AI pipeline runs on the logical server thread. Keep live observation and transfer work on that same thread. Do not asynchronously inspect live entities.

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

### E. Virtualize the native AI owner / keep an original-species mind shell

Mechanism: keep the original native AI host alive as a hidden logical controller and redirect its perception and actions to the visible recipient body.

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

Verdict: research prototype only. Potential optional bridge for selected opaque behaviors, not the universal foundation.

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

## Recommended architecture

### 1. MindRecord: body-independent and continuously updated

One logical mind record per body identity, created when a living entity enters the server. Suggested fields:

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

1. Stop extending DynamicObjectGraph as the transfer engine.
2. Keep MindLedger only after it becomes the authoritative owner map for a real MindRecord; it must not certify a native AI swap by itself.
3. Replace NativeBrainSwap and GoalMindRuntime with observer + mirror + bridge.
4. Add hooks in stages and prove they are passive before enabling exchange.
5. Make the transaction universally accepting; report behavioral fidelity separately.
6. Do not ship a test JAR until live vanilla-pair and modded-pair tests pass.

## Source basis

- Minecraft 26.3 AI anatomy reference, used as a current architecture map: https://minecraftdocs.dev/systems/entities/ai-goals-and-brains
- NeoForge 26.2 → 26.3 migration primer, used to flag version-specific changes: https://docs.neoforged.net/primer/docs/26.3/
- Repository branch feature/whole-mind-swap, audited files: NativeBrainSwap, GoalMindRuntime, GoalSelectorState, DynamicObjectGraph, and MindLedger.

This document is a study and architecture decision, not a claim that the recommended runtime has already been implemented or tested.
