# Possession control responsiveness gate

Status: **NOT PASSED — no player test package is authorized by this document.**

This is a release gate for the player-input control path. It is separate from, and must coexist with, the reciprocal native-mind/body ownership gates in `P09_UNIVERSAL_NATIVE_AI_SWAP_ACCEPTANCE.md`.

## Required player experience

While possessing a mob, ordinary mouse and keyboard use must feel like controlling a native Minecraft player character:

- Camera look responds locally in the same rendered frame; it must not wait for a server packet.
- Movement, turning, jumping and applicable movement controls receive immediate local feedback.
- Attack and interaction presses are not lost, duplicated, or delayed by a stale input queue.
- The actual occupied entity remains the physical body. Its dimensions, collision, gravity, movement profile, health, fall behavior and capabilities remain body-owned.
- The original player body receives no player input, camera rotation, attack or interaction while its incoming mind owns it.
- Mob AI processing and sensory transport must never block the player's input path.
- Server authority remains final for collision, damage, inventory, block changes and entity creation. Prediction must reconcile without persistent snapping, rubber-banding or wall penetration.

## Architecture contract

1. **Client input sampler:** sample continuous axes/look state every client tick and edge-triggered actions exactly once. Keep a monotonic per-session sequence and bounded unacknowledged input history.
2. **Immediate local presentation:** apply mouse look to the occupied body's camera immediately. Predict only movement that can be simulated consistently with the body's real movement rules. Do not make the player wait for a server round trip for camera response or ordinary movement feedback.
3. **Authenticated input transport:** the connection identifies the controller; clients never nominate the body they control. Input frames carry sequence, axes, look, held controls and distinct press/release edges for actions.
4. **Server input scheduler:** consume the newest valid frame on the normal entity tick path, reject replay/out-of-order frames, bound stale input, and fail safely on timeout. Never replay an arbitrarily old movement backlog.
5. **Body-native movement execution:** feed controls into the occupied body's ordinary movement/physics lifecycle. Do not replace it with a species switch, hand-authored movement imitation, hardcoded jump impulse, or hardcoded gravity/fall adjustment. If prediction cannot faithfully reproduce a body or modded movement implementation, use a conservative visual prediction mode while the server still executes the native body behavior.
6. **Reconciliation:** acknowledgements identify the last processed input. Authoritative corrections include the state needed to reconcile position, velocity and orientation. Rewind/replay only still-unacknowledged frames, with bounded history. Smooth visual correction must not override collision truth.
7. **Independent mind path:** mob AI for the original player body runs independently. Its scheduler, worker, sensory snapshots and action transport cannot hold a lock or wait condition needed by player input.

## Audit of the current P09 possession prototype

The existing prototype is useful scaffolding but fails this gate:

- `ClientPossession` locally changes the camera target and samples input, but predicts movement by directly calling `PossessionMotion.step` on the client-tracked entity.
- `PlayerMobPossession.tick` calls the same helper from `ServerTickEvent.Post`, after the normal entity tick. This is not yet integrated into the occupied entity's native movement lifecycle.
- `PossessionMotion` implements a separate simplified movement model, including a hardcoded jump impulse and a Chicken-specific fall multiplier. These are not acceptable substitutes for body-owned vanilla/modded movement and contradict universal body physics.
- The correction packet currently carries position and vertical velocity, while the client does not use its authoritative yaw/pitch fields to reconcile look state.
- Attack intent is a separate server round trip. Right-click/use/pick are currently cancelled rather than routed through a complete occupied-body action interface.
- The current player-to-mob possession explicitly disables the mob's native AI; it does not yet make the original player body run the incoming mob mind.
- Existing GameTests use mock players and prove server-side movement, not real connected-client camera, input feel, packet jitter, or perceived latency.

These are known blockers, not minor polish items. Do not describe the prototype as latency-free or as full mind possession.

## Required automated and real-client tests

### Pure protocol tests
- Monotonic sequence, duplicate/replay rejection, malformed/non-finite axes, pitch bounds and session identity.
- Press/release edge delivery exactly once; held state survives packet batching.
- Bounded history, acknowledgement pruning, packet loss, jitter, reordering and timeout behavior.
- Reconciliation replays only unacknowledged inputs and never crosses session/body identity.

### Real NeoForge server tests
- Input is applied in the correct body tick phase, not by a second post-tick movement pass.
- Server body collision, native speed, gravity, jump, fall damage, fluids and special movement remain body-native.
- No player input mutates the original player body's transform or combat state.
- AI work cannot block or starve input processing.

### Connected-client acceptance
Run a real client against a dedicated server with controlled latency and jitter (including localhost and a remote/LAN case):
- Camera response occurs on the local render frame.
- Forward/reverse/strafe/diagonal movement, rapid direction changes and jump feel responsive.
- Repeated short attack/use presses are neither lost nor duplicated.
- No persistent camera snap, rubber-banding, wall clipping or desync.
- A second observer sees the occupied body move and look correctly while the original player body behaves exclusively under its incoming mob mind.
- Repeat with Chicken, baby Zombie and Ocelot to ensure body-specific physics do not leak.

Suggested measurements: local camera response in the same rendered frame; input consumed by the next available server tick; 20 TPS under normal load; no sustained visible correction; no AI-worker wait on the input thread. Network RTT itself cannot be eliminated and must be reported separately from local response and server processing delay.

## Package gate

Do not ask the user to test a new JAR until the integrated book workflow passes the reciprocal mind/body ownership gate and this connected-client responsiveness gate. Compilation, headless GameTests, one-shot worker tests, and synthetic packet tests are necessary but individually insufficient.
