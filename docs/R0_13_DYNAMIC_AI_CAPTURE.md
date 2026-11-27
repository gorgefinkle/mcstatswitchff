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

The generic capturer is not a species-pair table: it attempts complete
runtime graph rebinding for unrecognized goals. A narrow semantic adapter
registry handles cases where a vanilla goal's implementation is inseparable
from its original species. These adapters translate a behavior intent to a
recipient-native implementation; they are not a claim that every goal or
species pair is portable. Unknown incompatible goals still reject the
complete exchange.

## Safety contract

- An owner field whose declared type cannot accept the new body rejects
  the **entire** swap with a field-level reason (e.g., a goal requiring
  `Creeper` cannot be rebound to a `Cow`).
- A lambda is shared only when its captured graph is limited to immutable
  values, recursively safe lambdas, or objects with no mutable instance
  fields. A static-final reference is not enough by itself. A closure that
  captures a mob, goal, navigation service, or mutable service object rejects
  the whole swap with the capture path. This is a narrow allowance for vanilla
  shared predicates and method references, not permission to reuse arbitrary
  captured callbacks.
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
- The semantic adapter registry currently covers a narrow hostile-mob path:
  - Any owner-bound `RemoveBlockGoal` subclass is reconstructed as a
    recipient-owned `RemoveBlockGoal`, retaining the target block, speed,
    vertical search range, accepted distance and control flags. The
    species-specific sound overrides are not retained by this port.
  - Species-bound melee goals, bow goals whose recipient lacks a bow/ranged
    capability, and Creeper `SwellGoal` are converted to a
    `RecipientAttackIntentGoal`. The physical recipient decides the action:
    Creeper bodies use their native `SwellGoal`; otherwise a body implementing
    `RangedAttackMob` and holding a bow uses `RangedBowAttackGoal`; remaining
    `PathfinderMob` bodies use `MeleeAttackGoal`. Attack cooldown/path state
    restarts because the underlying driver changes.
  - A translated goal is installed inactive, with its old selector lock
    released, so the newly constructed driver receives a normal
    `start()` lifecycle. Untranslated goals keep their captured scheduler
    state.

## Independent implementation audit — collection and scheduler state

A source-level audit identified two correctness boundaries that must remain explicit:

- Java collection interfaces do not guarantee interchangeable implementations. Replacing every List, Set, or Map with an ArrayList, LinkedHashSet, or LinkedHashMap can silently change immutability, ordering, comparator, or identity-key semantics. The graph copier now accepts only known standard implementations whose semantics it can retain; unknown collection implementations reject the complete capture. It preserves immutable JDK collections, unmodifiable List wrappers, and IdentityHashMap identity-key behavior. Opaque unmodifiable Set/Map wrappers reject because their hidden equality, ordering, or key semantics cannot be proven from the wrapper type. Rebinding collisions that would collapse distinct set/map entries reject the complete capture. Cyclic immutable collection graphs are rejected rather than leaking a mutable backing collection.
- GoalSelector does not store only Goal instances and priorities. Each entry is a WrappedGoal carrying scheduler state such as whether it is currently running. The runtime now snapshots each wrapper's running bit, the selector's disabled-control set, and the flag-to-running-wrapper lock map. It reinstalls new wrappers with their copied live Goal fields, restores running bits without invoking Goal.start() a second time, and reconstructs the selector lock map. The exact selector state is read back and compared before the exchange can continue.
- The three scheduler fields are exposed through the standard ModDevGradle access transformer, not reflective field-name guesses. If the access transformer does not apply or if the selector's running/lock invariants do not agree, capture or installation fails closed. The transaction also prepares independent same-body copies of both original selector graphs before mutation, because removeAllGoals calls stop() on running source goals; these copies are used for rollback if either install or ledger commit fails.

This closes the source-level scheduler bookkeeping gap, but it does not prove behavioral portability. Goal.start() is deliberately not replayed because it can reset copied runtime state; instead the copied goal instance and its WrappedGoal running bit are restored together. That still requires a dedicated-server test to confirm that the first post-swap selector tick behaves correctly for active and idle goals, including conflicting control flags, disabled controls, and reverse exchange. Exact navigation path progress and species-specific behavior outside GoalSelector remain separate limitations. Do not infer runtime correctness from compilation or the pure-Java graph harness.

## What is not yet proven or implemented

- **Arbitrary species behavior** does not automatically become portable.
  Species-specific methods such as `customServerAiStep`, hardwired
  attacks, movement physics, and custom mod systems run outside the
  goal selectors and are not yet dynamically captured or redirected.
  Swapping every goal does **not** necessarily swap all such behavior.
- Active Mojang `Brain` graphs (sensors, memories, schedule/activity
  handlers) are not yet rebound across different classes; the legacy
  native Brain exchange remains limited to compatible same-class mobs.
- The reflective clone and new semantic adapters have not been behaviorally
  verified in a running 26.2 server. Compilation cannot prove that a
  transferred goal's later `canUse`, `tick`, or `stop` methods work on
  the new body, nor that a semantic port reproduces all visible side effects.
  Runtime acceptance is essential.
- Player ↔ player, player ↔ mob control and save/restart persistence
  are still not implemented.
- GoalSelector running bits, disabled controls, and arbitration locks are now
  captured and restored as scheduler bookkeeping. Their behavioral correctness
  on the first post-swap server tick is still unverified. Exact navigation path
  progress and species-specific behavior remain outside this scheduler snapshot.

## Technical acceptance gates

1. **Pure Java:** `DynamicObjectGraphSelfTest` must prove real field
   capture, copied mutable flags, nested references, cross-root alias
   preservation, state retention, safe reuse of a method reference to a
   static shared predicate, rejection of a captured mutable singleton,
   subclass-incompatible recipient rejection, and rejection of a callback
   that captures the original body.
2. **Minecraft source/CI:** The real NeoForge 26.2 and 26.3 compilers
   must accept the capture/runtime hooks, and both artifacts must contain
   `GoalMindRuntime`, `DynamicObjectGraph`, `GoalTransferAdapters`, and
   `RecipientAttackIntentGoal`, plus the adapter's access transformers.
3. **In-game targeted pair:** Test Skeleton↔Zombie in both selection orders.
   Confirm that melee/ranged decisions execute through each physical body's
   available action, that the zombie egg-removal goal operates on the
   receiving body, and that both directions reverse cleanly. Also test with
   a Skeleton that does not hold a bow. This pair is not proven by CI or
   source-level tests; only an actual 26.2 server test can establish it.
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
