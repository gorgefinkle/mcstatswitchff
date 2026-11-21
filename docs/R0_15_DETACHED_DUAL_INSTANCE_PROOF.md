# Freaky Friday R0.15 — Real dual-instance, detached-host feasibility gate

## Hypothesis

Use **two actual Minecraft entity objects** per actively swapped being:

1. **Physical body:** the existing, world-registered visible mob. All health,
   speed, armor, dimensions, loot, inventory, collision, drops, player
   interactions (milking/shearing), sounds and movement mechanisms remain on it.
2. **Mind host:** an instance of the source mind's *original registered
   EntityType*, not a copy of its goals into a foreign class. This retains
   its native Java subtype and can potentially execute its own Goal, Brain,
   and third-party AI logic without per-species pair translators.

The host must not become a separate real-world actor. The goal is to feed
read-only recipient observations to native mind code and intercept every
physical effect into the recipient-body capability bridge.

## This specific implementation

The new `DetachedMindHostProbe` creates an unspawned donor-type mob through
`EntityType.create(level, EntitySpawnReason.COMMAND)`. It deliberately **never**
calls `spawn`, `addFreshEntity`, `finalizeSpawn`, `Mob.tick`, `Goal.tick`,
or `Brain.tick`. A pair must be selected through the existing book first.

The probe verifies:
- Host is an object distinct from both existing mobs, with a separate UUID.
- Host is the exact donor EntityType and concrete Java class.
- Host UUID is absent from the server world's registered entity map.
- Host can be positioned at the recipient body's coordinates.
- Original recipient UUID, type, health and max-health remain unchanged.
- Actual native goal counts, target-goal counts, and Brain-active state of
  donor and host, so factory side effects / missing registration are visible.

**Important:** An unspawned entity can still reference a live `ServerLevel`.
Constructor, instance initialization, `registerGoals`, and even `setPos`
may run third-party Java methods with direct world effects. Therefore
even *construction itself* is not a verified sandbox. Test only in a
**disposable world**, and do not automatically manufacture a mind host
during ordinary book swaps.

After the one-off inspection, the candidate reference is not retained.
The real physical body continues to tick normally. No donor mind controls
the recipient in this version.

## In-game procedure

1. Install the built R0.15 experimental JAR in a *disposable* NeoForge
   26.2 or 26.3 world. Be able to read chat and `latest.log`.
2. With the book, right-click a cow (donor), then a zombie (recipient).
   Expect a message explicitly saying **NO swap performed**.
3. Run `/freakyfriday dualprobe`. This is the ONLY operation that creates
   the non-world-registered host. Inspect the reported class/type,
   registration, UUID, body invariants, and AI counts.
4. Run `/freakyfriday cancel`. Repeat using a villager (Brain-driven),
   cow↔creeper, and one optional third-party mob type.
5. Confirm no second entity appears, no extra loot/spawns occur, body
   interaction remains normal and server logs show no unexpected event.
6. Never interpret success from this one-off check as native AI isolation.

## Future gates — required for full mod compatibility

- Keep a detached host alive across observed ticks without registering it,
  including cleanup on logout/unload/death/restart. First **do not tick AI**:
  verify synchronized positions and body invariants.
- Build a safe observation world/transaction layer that presents recipient
  surroundings but traps all direct writes from source-class native AI,
  including modded callbacks and world API calls.
- Execute a single donor original AI tick in isolation and diff all
  attempted effects. Only dispatch approved intents through the existing
  dynamic recipient BodyCapabilityRegistry. Every unknown world write must
  reject the entire frame, never secretly run against the donor's original
  body.
- Prove zombie-native targeting/pursuit while cow body stays milkable and
  retains its health/speed/loot, with no second real-world actor.
- Swap back, then test save/load, server lifecycle, players, and modded mobs.

## Status label

R0.15 is a **real Minecraft factory/registration feasibility experiment**,
not an in-game mind swap or a certified safe virtual world.
