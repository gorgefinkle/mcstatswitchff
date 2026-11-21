# Freaky Friday R0.13.0-alpha — Native Brain execution gate

## What changed from R0.12

The **book interaction is now two-click and category-free** on this development branch.
Right-click the first living body, then the second. Ctrl+S still selects self,
but player-control routing is *not* implemented and therefore player-target
swaps must fail clearly. `/freakyfriday cancel` cancels a pending first body.

The previous numerical attribute engine remains in source solely to retain
historical regression coverage. The live book and command dispatch no longer
use the stat/category picker.

## First executable mob runtime

`NativeBrainSwap` moves the **whole native Brain reference** between two
compatible `Mob` entities in one two-sided transaction (via
`CategoryExchange`) while `MindLedger` tracks entire logical mind ownership.
Nothing is selected at the individual goal, memory, or ability level.

This is a **narrow working-path candidate**, not universal AI transplantation:

- Both bodies must be the **same concrete Java mob class and entity type**.
- Both must have live `Brain` implementations (not `isBrainDead()`).
- Both native `goalSelector` and `targetSelector` collections must be empty.
- Both mobs must be alive, loaded, and in the same level.
- `LivingEntity.brain` is made accessible via a NeoForge access transformer.
- Preflight occurs before either reference changes. Both references are read
  back; failure triggers two-sided rollback. Mind identities commit afterward
  or the native swap is restored.
- No species-specific cloning, remapping of goals, or hidden per-ability swaps.

The intended first real-game proof is **two villagers of the same type** with
distinct saved memories or POI attachments. Their native Brain instances are
genuinely exchanged; unlike copying a single memory field, this includes the
entire memory/sensor/activity/behavior runtime bundle. However, native
body-owned methods and profession-dependent brain refresh remain potential
limits, and only the in-game acceptance run can establish observable behavior.

## NOT supported or claimed

- **Cow ↔ zombie, creeper ↔ villager, or other cross-species brain rebinds.**
  Their `Goal` objects capture owner entities or their subclasses, and
  moving references could mutate the original donor or crash the recipient.
- **Goal-driven pairs**, even when their classes are identical; their
  `Brain` is often inactive and the actual AI lives in goal selectors.
- **Player ↔ player or player ↔ NPC.** A player camera change is not a
  functional authenticated control swap.
- **Durable swaps across unload/reload, server restart, death, or villager
  profession changes.** Saving and reinitializing a Brain may reconstruct
  different runtime behaviors while the ledger is presently in memory only.
- Multiplayer acceptance and gameplay verification. Successful JAR builds
  prove compilation and packaging only.

## First runtime test (disposable world only)

1. Start client and, separately, dedicated server with NeoForge 26.2.
2. Place two villagers with different known job-site/bed memories; right-click
   first and then second with the book. Verify no category GUI appears.
3. Observe their movement and schedules before/after. A chat success message
   alone does **not** prove their AI transferred; compare behavioral traces.
4. Reverse by selecting the same pair again; compare their behavior/state.
5. Try a villager ↔ cow and cow ↔ zombie. Each must reject **before** any AI
   or attribute changes, and the second body should be reselectable.
6. Try Ctrl+S; the server must reject a player-control swap, never claim it
   succeeded. Check selection cancellation, timeout and disconnect.
7. **Do not use a long-lived world yet.** Lifecycle persistence is a later gate.

## Next engineering gates

1. Instrument and test native Brain assignment, tick execution, refresh and
   memory/POI behavior in a real 26.2 server.
2. Implement complete goal-driven mind capture and **recipient-bound**
   reconstruction, including donor AI suppression, with verified cross-species
   compatibility. Reject any unsupported part of the whole mind.
3. Persist mind ownership and runtime logical state across unload/reload and
   restart before recommending survival worlds.
4. Implement server-authoritative player input routing and full body-facing
   camera/HUD/inventory handling (not identity or permissions swapping).
5. Prove disparate-mob pairings, player/player, and player/mob reversibility.

## Verification

- `sh scripts/test-core.sh`: pure ownership ledger suite.
- `python3 scripts/self_audit.py`: synthetic selection/transaction
  integration and static source tests. Its stand-in native swap is not an
  actual Brain implementation.
- GitHub Actions builds for Minecraft 26.2 and 26.3; packaging checks must
  include `NativeBrainSwap` and `MindSwapSessions` classes.
- Game acceptance remains required before calling R0.13 playable.
