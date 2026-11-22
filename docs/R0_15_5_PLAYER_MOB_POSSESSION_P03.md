# Freaky Friday 0.15.5-alpha — Player/Mob possession P03

## Actual functionality now connected to the book

An authenticated player can select a Mob with the Freaky Friday book
(right-click), then select themselves (Ctrl+S / `/freakyfriday self`). The
same input works if they select themselves first. The mob stays the exact
original Minecraft physical entity, with its UUID, collision, health, type,
loot and appearance. Its original native AI is paused during possession,
allowing the connected user to supply movement and orientation.

The server binds the authenticated user's UUID to the chosen loaded Mob and
ignores client-specified target IDs entirely. Bounded, sequenced WASD/jump and
look input is carried by NeoForge's serverbound payloads; server-owned ticks
drive the physical body via the vanilla collision resolver `Entity.move`.
The original player entity is anchored in place. `/freakyfriday cancel`
ends the session, restores the mob's preexisting NoAI/gravity settings,
clears momentum and returns the client camera to its original player.
Death/unload/dimension/logout also invalidate the session.

The client is intended to display the controlled Mob as the camera entity,
retain movement keys, and send only authenticated input samples. The
camera/input code **compiles** for Minecraft 26.2/26.3 but has **not** been
observed running in a connected graphical Minecraft client. Do not
mark the camera as verified. Singleplayer integrated-server performance,
first-person aiming, animation, item use, flight and GUI interactions are
also still unverified.

## Required real-server behavioral acceptance

GameTest `freakyfriday:player_controls_chicken` now exercises genuine
book-style selection followed by the real Brigadier `/freakyfriday self`
shortcut, rather than synthetic detached-host construction. It checks:

- the real Chicken body exists in the world with its original UUID/health
- a real possession session owns that Chicken (no target from client)
- its native autonomous AI is paused
- successive input samples cause **real Chicken movement** over stone blocks
  using the actual Minecraft collision resolver
- an invalid NaN movement sample is rejected
- `/freakyfriday cancel` uses the real command dispatcher and restores
  original AI, ending the session without replacing or damaging the Chicken

The first accepted physical-movement runs for both versions advanced the
Chicken **3.99 blocks** and passed all 13 real NeoForge GameTests. Future CI
must continue to require the two `FF-GAMEPLAY` success markers.

**Headless GameTests do not have a negotiated graphical client.** They
deliberately skip sending clientbound camera packets to their mock
`ServerPlayer`; real singleplayer/dedicated clients keep that packet path.
A passing GameTest is NOT evidence that a client has successfully switched
its camera.

## Not yet a complete Freaky Friday swap

This is **one-way possession**, not a complete two-mind swap. The NPC
chicken's own native AI is paused and does NOT take over the original
player's body. Zombie <-> Chicken native behavioral exchange is NOT
validated; `NativeBrainSwap.exchange` may explicitly reject incompatible
cross-species goals, rather than actually make a Chicken chase someone.
Two-account player/player transfer, modded native AI isolation, fly/ride/
combat/attack ability routing and generic third-party player command
identity remain separate acceptance goals.

No additional diagnostic commands or end-user auditing features were added
for this possession milestone. Legacy developer probe commands are still
present from earlier experimental checkpoints, not the gameplay product.

Use the version-specific **full source/JAR** ZIP for code inspection and CI,
but do not describe this as a fully playable reciprocal mind-swap release.


## P04 candidate (separate development branch; not accepted)

P03's original locomotion had a correctness defect: every possessed mob
received the same fixed 0.19-block horizontal displacement per tick,
regardless of the physical body's movement-speed attribute. The
`feature/p03-native-body-motion` branch instead derives grounded physical
displacement from the controlled mob's own `MOVEMENT_SPEED` and retains
vanilla `Entity.move` collision resolution.

Required `native_speed_collision` GameTest changes a single physical
Chicken's native movement speed during an active possession. It must
observe different travel rates, a genuine stone-wall collision, unchanged
entity UUID and health, and restoration of AI/ownership on cancel. CI
requires explicit pass markers separately on 26.2 and 26.3.

This does **not** establish a fully vanilla-equivalent physics controller:
the current direct-displacement factor is calibrated for the initial
grounded possession case, not flight, swimming, mounts, friction or
arbitrary modded movement controllers. Nor does it install a Chicken
mind into the original player body, repair Zombie/Chicken behavioral
exchange, or verify live client-camera integration. P04 remains a
candidate until both real-game CI runs pass.
