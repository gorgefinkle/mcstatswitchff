# Freaky Friday — Whole-Mind Swap Pivot (R0.13 design)

## Player-facing contract

**One book. Two living bodies. Swap their entire controllers.** No category menus, stat sub-pickers, or one-off power slots.

- **Mob ↔ Mob:** the complete decision-making package (goals/targeting or Brain activities, memories, sensing, navigation/movement commands, and species-specific actions) is exchanged. Bodies remain the original species and keep their appearance, health, inventory, collision, and physical attributes. Original brains must no longer run on the donor bodies.
- **Player ↔ Player:** player A controls the *physical avatar* of player B and player B controls A's. Both see from and interact through their controlled bodies. Avatar look, equipment, inventory, health, position and injuries stay with the physical body; authenticated login UUID, chat identity, permissions and account credentials **never** transfer.
- **Player ↔ Mob:** the player controls the mob's body; the mob mind (its entire AI package) drives the abandoned player body. A player should not inherit an NPC's AI on top of active player inputs.
- **Reverse:** exchanging the same two bodies again gives each mind its original body; no duplicates or lost controllers.
- Two clicks or Ctrl+S for the player's own body at either stage. No mandatory category choice or dialogue. Server-authoritative and atomically validated.

## Critical engine facts and implications

Minecraft does **not** expose a universal, transferable `Brain` object for all mobs. Vanilla mobs use:
1. `GoalSelector` + `targetSelector`, where concrete `Goal` instances may capture their original mob in constructors;
2. `Brain` with type-specific memory/sensor/behavior schema and activities;
3. Navigation, MoveControl/LookControl, entity-specific AI tick and special action methods.

Moving references to the original Goals or Brain wholesale can make the receiving body control the *donor* instead; replacing `Mob.goalSelector` also cannot transplant subclass-only actions. Full behavior means a **complete, recipient-bound reconstitution** of the original mind, not a shallow field swap.

Minecraft player input, chat and inventories are tightly bound to `ServerPlayer`, `ServerGamePacketListenerImpl` and an authenticated connection. A camera/spectator switch alone changes the view, **not** full control, inventory, collision, interactions or damage. Never swap network connections, account identity or permissions. Build a server-authoritative control-routing layer and client camera / input adapter instead.

## Architecture: ONE controller per body

1. **Mind ledger (pure Java, new):** persistent identity of each mind and authoritative body → controller assignment. A whole-mind exchange swaps *only two controller assignments*, with preflight, verification and rollback. Shared primitive for all three pair types; it does not independently execute Minecraft AI.
2. **Runtime controller bridge:** a recipient-bound `NpcMindDriver` or `PlayerMindDriver` owns all commands for its body, with an explicit single-writer rule. When a swapped mind is active, the body's native AI must be prevented from also ticking.
3. **AI capture/restore adapters:** serialize complete logical AI state and reconstruct goal and brain *implementations against the recipient body*. Do not claim generic support until all donor actions can run and originals can be suppressed. Missing one action → reject whole exchange with a precise explanation. Mod integrations may provide full drivers.
4. **Player control redirect:** authentically logged-in player sends permitted movement/look/interact/use input; the server routes it to the assigned body, and camera/feedback/HUD follow that body. Inventory is body-owned. Account chat/permissions/advancements remain account-owned.
5. **Lifecycle:** save body↔mind mapping AND serialized logical AI/controller state, restore on chunk loads, join/leave, restart and dimension travel, with an explicit death policy and no duplicate mind assignment. No silent takeover of offline players.
6. **Book:** replace old category UI only once the runtime path can complete, or fail with a clear reason. Existing R0.12 numerical experiment stays on `main` for regression reference; develop the redesign on `feature/whole-mind-swap`.

## Phased acceptance

1. **Controller assignment core:** duplicate-free exchanges for NPC/NPC, player/player and player/NPC; reversal; preflight, rollback and no-op rejection.
2. **Simple goal-driven mob pair:** swap entire behavior between two genuinely different vanilla mobs; verify original AI disabled, controls rebound to recipients, and reversal. Reject unsupported goal classes instead of copying invalid goal references.
3. **Brain-based mob:** demonstrate a Brain-driven mob ↔ goal-driven mob, including memory, schedules and target semantics, or reject cleanly with an actionable reason until supported.
4. **Two-player control exchange:** distinct network clients actively control opposite avatars, including camera, movement, targeting, interaction, inventory, damage and reconnection. Account identity stays secure.
5. **Player↔mob:** human-controlled mob and AI-controlled human body simultaneously; inverse swap, crash/death/restart recovery.
6. **Generalization:** creeper detonation, bat flight, villager routines, third-party brains/AI where functional adapters can be substantiated, multiplayer and persistence tests.

**Status:** This document is an approved direction, not a claim the gameplay works yet. The first code checkpoint provides an isolated controller-assignment engine and tests; Minecraft engine integration must be separately implemented and tested in a real client and dedicated server.

## Safe compatibility rule

Do not make swaps "succeed" by transferring just a name, target, one goal, or a camera view. If all required behaviors and controls cannot be rebound and sustained, refuse the *entire* swap before mutating either body. Preserve the ability to reverse via original mind identity.
