# Freaky Friday R0.15.4 — Independent source/self-audit

## Audited baseline

R0.15.3 `feature/dual-instance-host-poc`. Source checked against the
real `MindSwapSessions`, actual NeoForge event registrations, registered
item interactions, `DetachedMindHostProbe`, `DetachedHostMirrorProbe`,
`LiveBodyProbe`, `ProbeBodySnapshot`, `MindHostEngine`, and legacy
registration paths. This is not a claim of real donor AI execution.

## Verified architecture limits

- A two-click book selection locks diagnostic donor and recipient identities.
  It **does not commit a mind swap**. Commands are permission gated.
- `dualprobe` constructs an actual instance of the donor's EntityType,
  with a fresh class-specific AI setup. It checks the host UUID is not in
  the world. It does not transplant existing donor goals/memories.
- `dualprobe40` retains an unregistered donor-class instance and copies the
  body's coordinates to it for 40 **recipient** ticks. The host's Goal,
  Brain, and Mob tick are never executed.
- `hostprobe40` executes a synthetic source of move/look intents through
  the native body port but does not run a native donor mind.
- The earlier cow/cow real-game run passed construction, 40-tick
  mirroring, zero alignment failures and zero host registration violations.
  The earlier attempted cow/zombie selection had been overwritten prior
  to that run. R0.15.3's lock fixes that overwrite; cross-species host
  construction still needs its own in-game proof.
- Neither a fresh AI Goal count nor a matching Java class demonstrates
  transferred memories, AI state, targeting, or behavior.
- **Critical blocker:** an unspawned native mob retains a reference to
  the real ServerLevel. Constructor code and especially arbitrary native
  Goal/Brain/subclass code can mutate real entities, blocks and inventory.
  The goal of automatic mod compatibility has not been achieved and must
  not be presented as complete.

## Genuine defects fixed in R0.15.4

1. **Preexisting global body modification:** `FreakyFriday` still
   registered the legacy `PortableAttributes::addToEntityTypes` listener,
   which added numeric attributes to all living entities. This is
   incompatible with a minimal body-unchanged experiment. The listener
   is now disconnected; legacy source remains archived for reference.
2. **Late baseline:** original donor/recipient health, position and AI
   selector counts were previously snapshotted only after creating or
   positioning the host. They could miss synchronous side effects. A
   `ProbeBodySnapshot` now captures both original bodies before the
   factory and before `setPos`; it rejects factory/preflight mutations
   and aborts if position-mirroring affects original body properties.
3. **Factory failure blind spot:** after a factory exception, the
   originals are rechecked against the pre-factory snapshot and
   deviations are logged as an explicit failed audit.
4. **Infinite live references:** both 40-tick probe maps previously
   remained populated if a recipient stopped ticking without triggering
   unload/leave callbacks. Independent `ServerTickEvent.Post` watchdogs
   now fail and close sessions after 120 active server ticks without
   recipient progress. This prevents indefinite retained entity/world
   references when a server remains alive.
5. **Same-tick reselection:** `/freakyfriday cancel` previously left
   the per-player interaction deduplication stamp. A same-tick click
   on the same target could disappear. Cancel now clears this stamp;
   the synthetic interaction regression explicitly tests it.
6. **Server lifecycle:** selections and interaction stamps now clear
   on server shutdown, preventing static state from leaking into a new
   integrated-server world.

7. **Misleading player interaction:** the early prototype allowed a player
   to be locked into a diagnostic pair although the detached host factory
   accepts only NPC `Mob` entities. The experimental runtime now explicitly
   rejects player-target selection and leaves the Ctrl+S shortcut available
   for future player controller work with an unsupported warning.
   The synthetic harness tests this failure mode.

## Verification tiers

- Source and pure Java unit tests: virtual host/ledger/body capability
  contracts, bounded intent capture, owner invariants, failure handling.
- Headless Minecraft stub harness: server-side book interactions,
  two-click lock, cancel+same-tick reselection, command routing, and
  explicit no-mind-swap failure.
- NeoForge 26.2/26.3 compile and JAR class verification: runtime API
  compatibility, artifact completeness.
- **Still unverified in this code audit:** actual in-game 120-tick
  stalled-entity cleanup, modded constructor side-effects beyond the
  observed mob snapshot, arbitrary world modifications (especially
  other mobs or blocks), and donor AI execution.

## Next engineering gate

Run the cow -> zombie diagnostic on the R0.15.4 branch, then test
bounded native mental-state capture to a same-class detached host.
Before its original Goal/Brain can tick, provide an execution boundary
capable of intercepting ALL world-changing operations or fail closed.
Merely setting a mob invisible or detaching it from `ServerLevel`
registration is not an isolation mechanism.

No species-pair hardcoding has been added to the control engine.
