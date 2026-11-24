# Freaky Friday — R0.15.4 Dual-Instance Native Host Feasibility


> **Gameplay safety notice (Nov 24, 2026):** Reciprocal Player↔Mob and Mob↔Mob
> exchanges are temporarily disabled on this branch. The previous experimental
> runtime launched one or two additional NeoForge server JVMs per exchange,
> disabled the real mobs' native AI, and replayed only a narrow action stream.
> This caused the extra Minecraft instances and the frozen/arrested-body
> behavior reported during gameplay. The runtime now fails closed before
> changing either body or launching a worker. Player-only possession remains
> a separate experimental feature and is not a complete two-way mind swap.
> Re-enable reciprocal exchange only after a single-server controller runtime
> passes simultaneous, independent two-direction gameplay acceptance.

**Development branch:** `feature/dual-instance-host-poc`  
**Minecraft targets:** 26.2 and 26.3 / NeoForge / Java 25

## New R0.15 architecture experiment

The candidate system now physically instantiates a **second NPC object of
the donor's exact EntityType** without adding it to the world. The existing
recipient NPC remains the only visible, registered, physically interactive
body. It keeps its own health, speed, model, milking/shearing behavior, loot,
inventory and collision.

**Test:** In a disposable world hold the book, select donor then recipient,
and issue `/freakyfriday dualprobe`. After that, run
`/freakyfriday dualprobe40` to retain the detached donor-class instance
across **40 real recipient entity ticks** while mirroring its position
without executing its AI. The report checks that the host stays absent
from the server world's entity registry and the original body is unchanged. The command constructs an unspawned
donor-type candidate and reports whether its Java class/EntityType matches,
its UUID remains absent from the world, its location can be aligned to the
recipient, and its native goals/Brain are initialized. It does **not** tick
the new host, change either real body, or perform a mind swap.

Entity constructors and position methods in unfamiliar mods can themselves
affect the live world. This experiment is not yet a safe sandbox and must
only be used in disposable worlds; do not treat a passing report as proof
that the original donor AI can safely execute.

See [the R0.15 dual-instance test and safety gate](docs/R0_15_DETACHED_DUAL_INSTANCE_PROOF.md).

## First real-world result and R0.15.3 selection correction

The initial user-validated R0.15.2 session passed the 40-tick
detached-host lifecycle test, but the final pair was **Cow -> Cow**:
additional book clicks had silently changed the original Cow -> Zombie
selection. This was fixed in R0.15.3: two clicks LOCK the donor/recipient
pair; later clicks cannot change it. Use `/freakyfriday cancel` to pick
a new pair. This still does not execute native donor AI or swap minds.

[Full test findings and retest plan](docs/R0_15_3_PAIR_LOCK_CROSS_SPECIES_GATE.md).

## R0.15.4 independent audit

A real source review found and corrected leftover global entity attribute
registration, checks that captured body integrity too late, abandoned
probe-session references when recipients stop ticking, same-tick
cancel/reselection loss, and stale selection state across server restart.

The detached host's original AI **still cannot safely run**. All donor
Goal/Brain tick paths remain disabled until there is an effective
world-side-effect isolation mechanism. `dualprobe` and `dualprobe40`
are diagnostics only, and their output is not proof that donor memories
or decisions have been transferred.

[Read the verified findings and remaining blockers](docs/R0_15_4_SELF_AUDIT.md).

## Intended gameplay

One book, two living bodies: exchange **entire mind/controller identity**,
not individual stats, goals, or attributes. The physical body retains
appearance, health, speed, size, inventory, loot and native interactions
such as milking or shearing. A mind can ask for an action; the recipient's
actual anatomy and abilities determine whether it can execute.

No hand-written source-species / recipient-species pairing table.
Modded bodies may register physical capabilities by action ID.

## What R0.14.2 implements

- `MindHostEngine`: isolated-decision **interface**, reciprocal controller
  ownership, bounded actions, one frame per body per tick, explicit failures
  and correct body-only property preservation. Verified with pure Java tests.
- `BodyCapabilityRegistry`: physical action registration by namespaced
  operation (not species pair), including independent third-party providers.
- `MinecraftBodyPort`: native Mob navigation, looking, jumping, and
  recipient-body creeper ignition through capability dispatch.
- **`/freakyfriday hostprobe`**: after selecting a real Mob with the book,
  sends synthetic look/path instructions to the *actual selected body*
  through the new host and body port, reports accepted/blocked actions,
  and verifies body UUID, type and health didn't change. This is a physical
  execution probe, **not a donor AI swap**, and native Mob AI can override it.
- **`/freakyfriday hostprobe40`**: after selecting a real Mob,
  executes a bounded synthetic action source over 40 real entity ticks
  and reports actual displacement, physical body invariants and native
  body-command outcomes. The mob's normal AI remains enabled, so observed
  displacement is not automatically caused by the probe.
- **`/freakyfriday inspect`**: after selecting a Mob, prints its actual
  goal/target goal classes, priorities and running status, Brain activity,
  subclass AI overrides, and **possible world-effect API references**
  discovered from classfile bytes. It executes no extra AI, and is
  a risk survey, **not an isolation certificate**.
- Core tests cover synthetic cross-body decisions, reversal, body invariants,
  partial physical failure, interrupted AI capture, duplicate tick prevention,
  bytecode effect candidates and mod-capability extension.

## Explicitly NOT supported yet

A safe **virtual native AI execution environment** that runs the donor's
original Goal/Brain/subclass code without physical world side effects is
**not implemented**. An ordinary hidden or unspawned Mob can still modify
a real Level through Java calls. That is the critical next engineering gate.

Until native effects are isolated, **selecting two book targets in R0.14.2
does not claim or perform a complete mind swap**. It provides a clear
diagnostic instead of invoking the earlier experimental reflective
Goal/Brain transplant. That original proof remains available on
`feature/whole-mind-swap` for comparison. R0.14.2 does not provide full
cross-species swaps, player control, world persistence, or multiplayer
runtime validation.

See [R0.14.2 timed real-body probe](docs/R0_14_2_TIMED_BODY_PROBE.md) and [R0.14.1 effect audit](docs/R0_14_1_LIVE_PROBE_AND_EFFECT_AUDIT.md)
and [native mind-host feasibility and acceptance gates](docs/R0_14_NATIVE_HOST_FEASIBILITY.md).
Neither CI compilation nor synthetic tests are proof of in-game AI behavior.

## Build and smoke test

```bash
sh scripts/test-core.sh
python3 scripts/self_audit.py
./gradlew build -PtargetMinecraft=26.2 --no-daemon
```

In a **disposable 26.2 world**, hold the Freaky Friday book, select one
Mob, run `/freakyfriday inspect` and `/freakyfriday hostprobe`, and
inspect the messages and `latest.log`. Then cancel the selection with
`/freakyfriday cancel`. Repeat with a third-party modded Mob.
For the full future player-facing concept, see
[whole-mind swap roadmap](docs/WHOLE_MIND_SWAP_R0_13.md).

Known source references: `main` contains R0.12 numerical category
experiments; `feature/whole-mind-swap` contains the previous R0.13
experimental AI transplant; the current branch is a **new hybrid host
architecture**.


## P05 immediate player/player physical-control candidate

When the Freaky Friday book selects your own body and another online player
(in either order), reciprocal server-owned physical control begins **immediately**.
No permission request, confirmation or `/accept` is required. Both real
player entities retain their body UUID and stats; each account's movement
samples drive the other player's collision-resolved body. Either player may
use `/freakyfriday cancel` to restore both. The existing player-command
context projection now binds with the real reciprocal-control session.

Minecraft 26.2 and 26.3 real GameTests must physically move two different
ServerPlayer bodies in opposite directions and verify their identities,
command routing and simultaneous cancellation. Mock GameTests have **no
connected graphical clients**; working camera, inventory/GUI, item use,
combat, all addon commands and player↔mob reciprocal native AI behavior
are **not** proven. The real player session is experimental, not an entire
transfer of accounts or mob minds. No fake players are created as gameplay
avatars, and no permanent player account properties change hands.


## P06 chicken fall, camera-input and death-return candidate

User gameplay reported that the original player's head rotated while the
possessed mob camera failed to turn. The P05 client sent the occupied entity's
stale network yaw and pitch. P06 copies the local mouse-look rotation into the
camera entity and sends those same angles as authenticated movement input.
**This client-camera behavior must still be confirmed by a live connected
client; headless GameTests do not render.**

P05's no-AI possession applied identical gravity to every body. P06 re-adds
the vanilla Chicken glide coefficient (0.6 on negative vertical motion).
Ocelots **must not** glide but naturally remain fall-damage-immune under the
vanilla entity-type tag. Normal zombies must descend at regular gravity and
take normal fall damage. Required `native_fall_profiles` runs real falls
for all three bodies in sequence; only the controlling mind moves.

On death of a possessed mob, return its player to their original body.
On death of either physical player in a player↔player exchange, immediately
eject BOTH connected minds and clear both command-body routes. The other
player always recovers their own body, with no consent prompt or fake avatar.
Required server GameTests check both death cleanup paths.

This is not a completed general-purpose native-movement or cross-species
mind-swapping engine. In particular, a chicken's AI still does not drive a
vacated player body, and full player item, combat and GUI forwarding need
connected-client verification.


## P07 occupied-body health and inventory portrait candidate

The vanilla HUD/inventory continue to reference the authenticated player's
own body after P05/P06 camera possession. P07 renders a separate occupied-body
health indicator using the actual client-tracked LivingEntity health and
maximum health, and draws the currently occupied entity's model over the
vanilla player portrait in the survival inventory and the Creative\nmode survival-equipment inventory tab. It does NOT transfer player
identity, inventory, or health onto an arbitrary mob.

An Ocelot is vanilla immune to *fall damage*, but can take damage and die
from ordinary attacks. The required real-world `ocelot_damage_and_death`
GameTest first inflicts nonlethal generic Minecraft damage, verifies its
actual health decreases while possession remains, then inflicts lethal
damage and verifies the player returns intact.

GUI and client rendering cannot be visually verified by headless server
GameTests. Both target versions must compile against the real NeoForge GUI
API. Inventory preview support covers the normal survival InventoryScreen;
different creative/modded screen layouts may require separate adapters.


### P07C validation correction

The P07B Minecraft 26.3 required fall GameTest sampled a newly spawned
Zombie before it had entered the world's normal entity tick flow. It falsely
reported chicken glide after 0.08 blocks. The P07C test registers Zombie,
stages it in no-gravity, then starts its real fall on a later tick after
registration is confirmed. The pass criteria require a normal-speed drop
**and genuine reduction in the Zombie's health**, while Chicken still glides
and Ocelot preserves its native fall-damage immunity. No permanent
no-gravity setting is installed on any gameplay entity.


## P08 predicted-body-control development checkpoint (NOT CI-certified)

The P07B remote-entity camera input loop had severe latency and sent original
player actions while possessing a mob. P08 is a source-level **candidate**:
- Locally predict occupied entity locomotion immediately, then replay only
  unacknowledged movement samples after receiving the authoritative server
  position and input sequence.
- Keep the original player's orientation anchored; do not let mouse look
  animate its head while a remote body is being controlled.
- Cancel vanilla player-origin attack, item-use and pick-block actions while
  possessing. Send an authenticated attack **intent**, validate target/range/
  facing/line of sight/cooldown against the occupied body's real position, and
  let the occupied body perform its native melee attack. Native ranged/unique
  mob abilities are NOT implemented.
- Expire pending book selections after 8 seconds and clear them on return.
- GameTests require a REAL Zombie-body attack against the original player
  and require an explicit fresh second body selection. These are not evidence
  of connected-client latency; a live client must be tested for that.

**Known missing critical feature:** Zombie <-> Chicken *native AI* exchange
is NOT a working feature. The current portable Goal/Brain transfer rejects
it because these controllers contain owner-bound species-specific code. A
manual-only GameTest now records this as an explicit unmet acceptance gate.
Do not confuse a successful selection/UI message or numeric exchange with a
genuine reciprocal behavior swap. A properly isolated native-mind host with
full behavior outputs is needed; do not use species-by-species fake goal rules.

This release source is NOT a verified JAR: GitHub Actions has a billing gate
that prevents all runner allocation and the local environment lacks Java 25
and NeoForge dependencies. Never rebrand older P07B JARs as P08.


### P08.2 first-body selection HUD (development source)

After right-clicking the first Mob with the Freaky Friday book (or selecting
your own body first), a compact `First: Chicken  8.0s` countdown is shown
**above the vanilla health hearts**. The selected body name comes from the
server, not a client-chosen entity ID. The timer decreases over the same
160-game-tick (eight-second) selection window already enforced by the server.
It includes a shrinking progress bar and safely truncates very long names.

The server sends a definitive clear when the pairing completes, the selection
is cancelled, the first body is lost/dies, the player dies, their dimension
changes, or the selection expires. The client also locally expires the display
and clears it when the camera transfers, on death, or on logout. Expiration
does not execute a swap; a fresh two-body pairing is still required.

The two existing physical GameTests now assert that selection begins with a
160-tick lifetime, that completion/cancel removes pending state, and that an
expired first selection cannot silently pair with a later Mob. These prove
server-state lifecycle only. The HUD placement and rendering have **not**
been visually tested with a connected Minecraft client because GitHub Actions
billing currently blocks NeoForge compilation. The P08.2 package is therefore
source-only, NOT a new playable JAR.
