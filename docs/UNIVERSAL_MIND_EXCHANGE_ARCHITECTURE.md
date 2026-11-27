# Universal Mind Exchange Architecture Contract

Status: binding design requirement for the whole-mind implementation.

## Non-negotiable behavior

Every living mob body is a valid participant in a mind exchange, regardless of concrete class, entity type, vanilla/modded origin, or AI implementation. A swap must not be refused because the two mobs are different species, use different AI systems, have different goal classes, or expose different capabilities.

Examples that must work through the same runtime:
- Creeper <-> Zombie
- Skeleton <-> Zombie
- Ender Dragon <-> Chicken
- Any vanilla mob <-> any modded mob
- Any modded mob <-> any other modded mob

There are no species allowlists, compatibility matrices, per-mob profiles, per-pair adapters, or hand-authored rules for individual mobs. A new mob must participate automatically without Freaky Friday being updated for that mob.

## What is exchanged

The transferable mind is not a collection of native Minecraft Goal objects or a Brain instance. Those objects are implementation details of a particular body and can contain owner-bound references, species-specific methods, captured lambdas, navigation/control objects, and assumptions about body mechanics.

The mind is represented independently of its current body. It includes the complete transferable decision state: perception-derived context, current target/attention, priorities and intent, active task/behavior state, memory, and decision continuity. The body remains responsible for its physical traits, native movement and interaction mechanics, equipment, health, inventory, dimensions, and species-specific actions.

The runtime must continuously bridge the abstract mind to the current body's available controls. It must not transplant a donor Goal, Brain, lambda, navigation object, or controller reference into a different species.

## Capability mismatch is not incompatibility

A recipient body may not possess a literal action available to the donor body. That does not invalidate the exchange. The mind's intent remains attached to the mind; the recipient body executes the closest behavior its own controls can express, while preserving the intent so it can resume if a later body can express it.

For example, an explosive attack intent carried by a creeper mind in a zombie body must not cause rejection or attempt to install CreeperSwellGoal on the zombie. The zombie body expresses the available hostile approach/attack behavior. If that mind later returns to a creeper body, its explosive intent can again be expressed by the creeper's native capabilities.

This is a general capability translation mechanism, not a table of species-specific translations. It must discover and use capabilities dynamically, including capabilities supplied by mods.

## Runtime requirements

1. Exchange is server-authoritative and atomic. Validate both bodies and prepare the mind transfer before committing either side.
2. No compatibility check may reject a pair based on class, entity type, Brain-vs-Goal implementation, goal inventory, target selector contents, or unknown modded AI classes.
3. Native AI objects remain owned by their physical bodies. No cross-body cloning or direct transplantation of Goal/Brain graphs.
4. The abstract mind continues making decisions after transfer. It is not a one-time snapshot of a goal list.
5. Perception and body feedback are rebound to the current body at runtime; stale references to the former body must not survive.
6. Capability discovery is generic and extensible. It must not require a Freaky Friday update for each new mob.
7. A capability mismatch changes how an intent is enacted, never whether the mind exchange is allowed.
8. Reverse exchanges must restore the original mind-to-body assignments and their retained decision state.
9. Player participation must use the same mind/body separation; account identity, permissions, chat identity, and credentials remain attached to the authenticated player account.

## Consequences for the current prototype

The DynamicObjectGraph strategy of cloning native Goal graphs is not a foundation to extend. It will continue to fail on owner-typed fields, opaque lambdas, inaccessible implementation details, and species-specific behavior. Relaxing its checks would create stale references and corrupted AI rather than universal compatibility.

The current NativeBrainSwap same-class/same-type eligibility checks and GoalMindRuntime Brain/Goal split are also incompatible with this contract. They are prototype code to replace, not compatibility rules to expand.

The synthetic Minecraft stub harness must never report a successful AI exchange merely because a fake NativeBrainSwap returns null. Session/UI tests and actual AI-transfer tests must be clearly separated, and the former must not be described as proof of the latter.

## Acceptance criteria

A build is not considered ready for user testing until a live NeoForge test demonstrates, at minimum:
- Creeper <-> Zombie in both directions
- Skeleton <-> Zombie in both directions, including avoidance and panic behavior
- Ender Dragon <-> Chicken in both directions
- Repeated exchange and reverse exchange without losing either mind's retained state
- A modded mob pair without adding mob-specific Freaky Friday code

For every pair, the exchange action itself must succeed. Differences in physical capability may change the enacted behavior, but may not produce a species incompatibility rejection.

Compilation, synthetic stubs, object-graph unit tests, and static audits are not substitutes for these live acceptance tests.
