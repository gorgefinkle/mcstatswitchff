# NPC ↔ Player Mind Exchange — Minecraft 26.2 Design

Status: partial 26.2 possession prototype is wired into the book transaction; not yet validated in a real client session  
Active target: Minecraft 26.2 / NeoForge 26.2.0.95 / Java 25  
Prerequisite status: NPC ↔ NPC controller exchange and Creeper fuse continuity pass a 26.2 dedicated-server smoke; full cross-species behavioral acceptance remains in progress

## Implementation status — 2026-11-28

The current branch has source-level NPC ↔ Player possession wiring:
- a server-owned session exchanges controller/body UUID bindings and rolls them back if setup fails;
- PlayerPossessionTickMixin runs the NPC's preserved native controller against the ServerPlayer physical body;
- the NPC body is switched to player-input-driven ticking, with the native goal scheduler suppressed for that body;
- ServerboundPlayerInputPacket captures the seven digital inputs, while ServerboundMovePlayerPacket contributes validated rotation only and its position coordinates are ignored;
- camera routing keeps the client viewing the NPC while the ServerPlayer's physical position is not teleported to or continuously snapped onto that NPC;
- attack, entity-interaction, player-action, item-use, player-command, and swing handlers are wrapped in an NPC physical-body context;
- PlayerPossessionState.validateOrEnd reverses the session when either endpoint is no longer valid; logout cleanup is wired;
- /freakyfriday end is the explicit player escape/reversal command.

**What has actually been verified:** on Minecraft 26.2 / NeoForge 26.2, all registered Mixins apply on a dedicated server; the existing Zombie ↔ Creeper controller test and native Creeper fuse-state continuity pass; the full source audit and build pass.

**What has not been verified:** there has not yet been a real client-driven NPC ↔ Player possession session. Movement feel, camera synchronization, attack reach/damage, right-click interactions, block breaking/placement, inventory/equipment ownership semantics, packet ordering, reverse command behavior, and death/unload recovery still require dedicated in-game tests. This prototype is not a test-ready release.

## Goal and exact meaning

An NPC-to-player swap is a mutual exchange of who controls which physical body:

- The NPC's native mind/controller takes over the player's physical player body.
- The player continues to act as the authenticated player account but controls the NPC's physical body.
- The camera follows the NPC body while the player is driving it.
- The player's UUID, login session, profile, chat identity, permissions, advancements, statistics, and account security stay attached to the player account. They are not transferred to the NPC.
- The two physical bodies retain their own health, dimensions, attributes, equipment, inventory, and species mechanics unless a separate gameplay rule is explicitly approved.

This is not a player object replacement, account swap, fake-player login, or a permission/identity transfer.

## Required architecture: separate controller hosts from physical bodies

A ServerPlayer is not a Mob. It does not provide a Mob GoalSelector, Mob Brain type, PathNavigation, MoveControl, LookControl, or JumpControl in the same form as an NPC. Do not attempt to assign a Mob's Brain or GoalSelector directly to ServerPlayer.

Keep the NPC's actual AI controller host intact. Its Goals, Brain, sensors, behaviors, owner-bound lambdas, task state, and modded fields remain attached to the original NPC host. The NPC mind controls the player body through a PlayerBodyBridge that translates native AI body requests into player-compatible movement, perception, orientation, and action operations.

The player's input controller remains associated with the authenticated ServerPlayer connection. During the swap, its output target changes from the player's physical body to the NPC body. The account identity does not move.

The ownership service therefore needs two different body bridges:
- MobBodyBridge: maps native NPC controller output and perception to a physical Mob.
- PlayerBodyBridge: maps native NPC controller output and perception to a physical ServerPlayer.
- PlayerInputBridge: maps authenticated client input to a physical NPC body.

The three bridges share a controller/body ownership service and generic intent/capability types. They are not per-species adapters.

## 26.2 player-side API surface

Exact 26.2 mappings identify the main server-side boundaries:

- ServerPlayer.setCamera(Entity) and getCamera() are public. The server can direct the player's view to the NPC entity.
- ServerPlayNetworkHandler.handlePlayerInput(ServerboundPlayerInputPacket) receives a PlayerInput record.
- PlayerInput contains seven digital state values: forward, backward, left, right, jump, sneak/shift, and sprint.
- ServerPlayNetworkHandler.handleMovePlayer(ServerboundMovePlayerPacket) receives movement and rotation updates.
- The connection also exposes action handlers for attack, interact, player action, player command, use item, and use item on block.
- The camera setter is a view-routing mechanism. It does not automatically redirect movement, attacks, interactions, or item-use commands.

Reference mappings:
- ServerPlayNetworkHandler: https://mappings.relativitymc.org/26.2/net/minecraft/server/network/ServerPlayNetworkHandler.html
- ServerPlayer: https://mappings.relativitymc.org/26.2/net/minecraft/server/network/ServerPlayerEntity.html
- ServerGamePacketListener: https://aldak.netlify.app/javadoc/26.2.x/net/minecraft/network/protocol/game/servergamepacketlistener
- ServerboundPlayerInputPacket: https://mappings.relativitymc.org/26.2/net/minecraft/network/protocol/game/ServerboundPlayerInputPacket.html
- PlayerInput: https://mappings.relativitymc.org/26.2/net/minecraft/util/PlayerInput.html

## Direction A: NPC mind controls the player body

The NPC controller host remains a Mob. The runtime does not install the mob's Brain or Goals on ServerPlayer.

At each player-body AI phase, the NPC mind's current native controller is evaluated with the player's physical body as the target. The PlayerBodyBridge translates body-facing operations.

### Perception

- Position, rotation, bounding box, eye height, and world context must come from the player body.
- Line-of-sight and target reach must be evaluated from the player body's position, not from dormant/host NPC coordinates.
- The controller's target/attention and Brain memories remain with the NPC mind.
- Targets are stored/revalidated by UUID where possible; do not retain stale direct object references across unloads.

### Movement and navigation

- Player body collision and LivingEntity movement remain vanilla player mechanics.
- Native mob navigation requests must be converted into a path/waypoint or movement intent suitable for the player's dimensions and movement physics.
- Move/look/jump outputs are translated into player yaw/pitch, velocity, jump, and travel-compatible state. The player's normal physics and collision resolution still decide the resulting movement.
- Do not copy a Mob MoveControl or PathNavigation instance into ServerPlayer.
- Measure path-following fidelity and avoid double-driving the player from both the client and the NPC AI.

### Actions and capabilities

- Generic attack/interaction intents are routed through the player body's valid action APIs.
- Special mob actions remain with the NPC mind. If the player body cannot express the action, preserve it as pending/latent and execute the closest valid body behavior; never reject the mind exchange.
- The player's inventory/equipment remain with the player body. An NPC mind does not gain the player's items merely because it now controls the player body.
- Physical action validation must still use the player body's reach, world, permissions, and collision context.

## Direction B: player input controls the NPC body

When the swap begins, the player connection's input target changes to the selected NPC. The player's physical body receives the NPC mind's AI through Direction A.

### Input stream

1. handlePlayerInput updates a server-owned input state for the controlled NPC body.
2. Directional input is interpreted relative to the NPC body's current yaw.
3. handleMovePlayer contributes validated look rotation; client-supplied position is not applied to the NPC.
4. The NPC's own body physics and capabilities determine movement. Generic movement state is applied through the Mob's available controls; no per-mob code path is added.
5. The normal player body must not also receive the same movement command.

### Attack and interaction routing

The controlled entity must be the only action actor for redirected input. Intercept or replace the relevant 26.2 connection action boundaries:
- attack
- entity interact / interact-at
- player action (block-break start/stop, drop/swap actions as applicable)
- use item
- use item on block
- swing/animation

Every redirected action is revalidated against the NPC body:
- target entity ID must resolve in the server's current level;
- target distance is measured from the NPC body;
- line of sight / collision is measured from the NPC body;
- the NPC body must have the relevant capability;
- player permissions and account identity are not inherited by the NPC;
- the NPC's equipment/inventory is used, not the player's carried stack, unless a separately approved mechanic says otherwise.

Unsupported actions are safely ignored or translated to an available generic behavior, but they do not break the ownership transaction.

### Camera and synchronization

- Set the ServerPlayer camera to the controlled NPC so the client sees the NPC's location and orientation.
- Restore the camera to the ServerPlayer when the exchange is reversed or cancelled.
- Camera routing is independent from input routing. Both must be implemented and tested.
- The player's real physical body must continue to receive the NPC mind's movement/AI, and its position must be synchronized to the owning client.
- Prevent vanilla player movement packets from moving the account body a second time while input is redirected to the NPC.

## Atomic swap sequence

All operations occur on the logical server thread:

1. Validate the player session and NPC are connected/alive/loaded and in the allowed world scope.
2. Ensure the NPC has a registered native controller host and the player has a player-input controller record.
3. Freeze redirected input for the transaction window.
4. Create a rollback snapshot of the controller/body ownership bindings, camera entity, body positions, rotations, and active input state.
5. Bind the NPC controller host to the player body.
6. Bind the player's input controller to the NPC body.
7. Set the player's camera to the NPC body.
8. Suppress vanilla movement/action handling that would also mutate the player's body, while preserving authentication, chat, connection, inventory, and permission ownership.
9. Publish the new input target and resume on the next safe tick.
10. Verify both control directions are active. If the transaction fails before commit, restore both bindings and camera.

After commit, a body capability mismatch cannot roll the swap back. It only changes how a particular intent/input is enacted.

## Reverse exchange

Reversing the same pairing must:
- restore the NPC controller to its original physical body;
- restore player input to the ServerPlayer body;
- return the camera to the player;
- preserve both minds' state accumulated during the exchange;
- preserve each physical body's health, equipment, inventory, attributes, and other body-owned state;
- clear stale control targets and pending input.

## Important implementation risks

1. **Player movement authority:** ServerPlayNetworkHandler validates incoming player position. Do not forward client coordinates as NPC coordinates or bypass movement validation. Use input booleans and validated rotation as controls, and let the NPC body physics resolve movement.
2. **Camera is not possession:** setCamera changes what is viewed; it does not make serverbound movement or action packets target the camera entity.
3. **Navigation mismatch:** NPC AI's pathfinder assumes a Mob-like owner. PlayerBodyBridge needs player-sized pathfinding and movement resolution without replacing the NPC controller host.
4. **Combat reach:** attacks must be validated from the controlled physical NPC, not from the ServerPlayer's original body coordinates.
5. **Identity and security:** chat, commands, permission checks, advancements, statistics, and inventory remain attached to the account. Do not impersonate the NPC or player to the server.
6. **Tick ordering:** the NPC AI must not also drive the NPC body after the player has taken control. The player's physical body must receive exactly one AI driver.
7. **Modded actions:** unknown special actions may remain opaque. Preserve the NPC controller and its state; never reject the swap for a capability mismatch.

## Acceptance tests

NPC-to-NPC must pass first. Then test NPC ↔ Player in both directions:

- NPC AI drives player body: pursue, evade, navigate obstacles, turn, jump where applicable, attack/interact where applicable.
- Player controls NPC body: forward/back/left/right, look, jump, sneak, sprint, attack, interact, and permitted block/item actions.
- Player physical body is simultaneously driven by NPC AI and stays in valid collision/health state.
- Camera follows NPC body while player input is redirected; camera restores on reverse.
- No double movement, duplicate attacks, packet desync, rubber-banding, or client crashes.
- Player UUID, login, chat, permissions, inventory, advancements, and statistics remain attached to account.
- NPC health, equipment, inventory (if any), dimensions, and native AI state remain attached to the NPC body/mind according to the ownership model.
- Repeated swap/reverse cycles preserve both controllers and body state.
- Disconnect, death, dimension transfer, NPC removal, and chunk unload have explicit rollback/cleanup behavior.

## Development order

1. Complete and prove universal NPC ↔ NPC AI exchange on Minecraft 26.2 using preserved native controller hosts and body virtualization.
2. Build PlayerInputBridge and NPC-body movement/action routing.
3. Build PlayerBodyBridge so the NPC mind can drive ServerPlayer.
4. Integrate camera routing and packet interception as one atomic possession transaction.
5. Run the full acceptance suite. Do not release a player-swap test JAR before both input directions and camera restoration work in-game.

This document describes both the required design and the currently wired but unvalidated prototype. Source wiring and dedicated-server Mixin loading must not be treated as proof of working player possession.
