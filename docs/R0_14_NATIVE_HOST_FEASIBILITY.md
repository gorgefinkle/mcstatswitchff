# Freaky Friday — R0.14 Native Mind Host feasibility checkpoint

## Outcome

The native-AI / body-proxy hybrid has a coherent execution contract, but it
is **NOT YET a sandbox or a live cross-species host**.

**Do not claim broad mod compatibility or native AI virtualization yet.**
This work establishes testable ownership and dispatch semantics, exposes
mod-extensible body capabilities, and adds a real in-game read-only survey of
modded AI execution surfaces. The donor's original Java AI MUST NOT be
ticked in a detached entity until world-effect isolation is verified.

## Evidence from Minecraft's lifecycle

Minecraft's Mob AI does not exclusively return abstract movement/attack
intentions. Its native server AI step interleaves GoalSelector,
targetSelector, navigation, look/move/jump control, sensing, and
subclass-specific customServerAiStep. Brain-based mobs may run Brain
activities through subclass tick methods. A custom goal can directly call
world-mutating APIs or spawn an entity.

Consequences:
- Copying goal objects onto another species may retain owner-specific
  casts, fields and methods.
- Moving only the Brain object misses goals, physical actions and native
  species-code.
- Creating an unspawned or hidden duplicate mob does **not** isolate
  calls to Level, inventory, world spawning or entity damage; its AI may
  still mutate the real world.
- Merely observing goal flags, or setting NoAI on a real body, is not
  equivalent to capturing every decision and safely redirecting it.
- Java reflection cannot generically replace Java method bodies or
  intercept arbitrary side effects from unknown modded code.

## Native AI host architecture

```
Donor's actual class / AI state [future isolated host]
           |
           | Observes recipient surroundings through read-only host view
           | Standard and modded AI logic executes behind audited interceptors
           v
Bounded frame of semantic INTENTS, no direct world writes
           |
           v
MindHostEngine (single-writer ownership / per-tick decision capture)
           |
           v
BodyCapabilityRegistry (per ACTION, not source↔target species)
           |
           v
MinecraftBodyPort: native body mechanics, unchanged stats and interactions
```

Swapping minds exchanges *mind identities/control* in the ledger; body
UUID, entity type, actual attributes, health, speed, collision, model,
equipment, loot and physical interactions remain untouched. If a cow
body lacks a creeper explosion capability, the action is unavailable
without making the whole mind swap invalid.

## R0.14 code committed

- `MindHostEngine`: pure decision-to-body kernel using full mind
  identities, per-tick single execution, bounded intent capture **before**
  physical dispatch, duplicate tick rejection, exception handling and
  truthful partial-action error reporting. It **does not** invoke
  `Mob.tick`, `Goal.tick` or `Brain.tick` in this checkpoint.
- `MindHostEngineSelfTest`: synthetic pairs with body traits preserved
  throughout swap and reversal, missing capabilities, multiple requests,
  retry/double tick prevention, overflow rejection and physical failure.
- `BodyCapabilityRegistry`: registered namespaced actions and multiple
  implementations by actual recipient capability. No whitelist of mob
  types or mob pairs. `BodyCapabilityRegistrySelfTest` proves a mock
  modded teleport action can be provided by an independent body without
  changing the donor's mind.
- `MinecraftBodyPort`: real native navigation, looking, jumping, and
  creeper-fuse actions registered through this capability registry. Other
  mods may call `registerCapability` to add their own physical actions.
- `NativeAiSurfaceAudit`: read-only runtime inventory of actual mob
  goal/target goal implementations, Brain status and subclass hooks which
  may run additional AI or world effects. This is NOT a proof of purity.
- `/freakyfriday inspect`: Hold the book, select one mob, issue command.
  It lists observable AI surfaces without executing, swapping, or
  spawning a second entity. Useful for auditing arbitrary modded mobs.

## Isolation completion gates

1. **Observation proxy:** donor native logic must observe the physical
   recipient's location, surroundings and target state. Do not move the
   donor's actual world entity or invent an additional visible entity.
2. **Intercept all standard effects:** navigation, movement/look/jump,
   attack/target, sounds, interactions, spawns, status effects, block
   changes, explosions, inventory updates and damage. Actions must be
   captured and either executed on the recipient physical body or refused.
   No direct effects on the donor, host or world while evaluating.
3. **Verify arbitrary code:** unknown Java methods may bypass standard
   hooks. Do not tick an untrusted mind until the isolation boundary
   can detect and reject these escape paths. Runtime / mixin
   instrumentation may be needed; the mod cannot promise 100%
   compatibility with code that bypasses every interception seam.
4. **Controlled proof:** demonstrate zombie-native target selection and
   pursuit in a milkable cow body with original cow speed/health and
   loot, and no second ghost entity or duplicate effects.
5. **Second proof:** cow/creeper swapped decisions; a creeper-minded cow
   cannot detonate because the *cow body* lacks that mechanism. The
   creeper body retains its actual explosion mechanism.
6. **Mod proof:** pair two third-party mobs using common hooks without a
   species pair adapter; if a private mod API is unobserved, report the
   exact unsupported effect rather than claiming success.
7. **Persistence:** chunk unload/reload, death, restart, reversal,
   multiplayer and account-safe player controllers.

## How to inspect real mobs (disposable world)

1. Launch NeoForge Minecraft 26.2 with this experimental JAR.
2. Hold the Freaky Friday book and right-click one vanilla or modded mob.
3. Type `/freakyfriday inspect`. Observe actual Goal classes,
   priorities, target goals, active Brain status and native overrides.
4. Type `/freakyfriday cancel` before selecting a second body.
5. Compare a cow, zombie, creeper, villager and a modded mob.
6. An unlisted method or goal may still cause physical side effects:
   this report is an *inventory*, never an execution sandbox.

The existing experimental R0.13 dynamic goal and native Brain swap
paths remain in this development branch for historical comparison.
They are NOT routed through the new host yet and may reject or fail on
unported species. Test only in disposable worlds.

Official reference: https://docs.neoforged.net/docs/misc/gametest/
ModDevGradle: https://docs.neoforged.net/toolchain/docs/plugins/mdg/
