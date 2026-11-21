# Freaky Friday R0.5 — self-audit and correction record

**Starting point:** R0.4 full source. **Target:** Minecraft 26.2, NeoForge 26.2.0.95, JDK 25. **Version:** 0.5.0-alpha. **Phase 1: OPEN.**

## What was actually changed

1. Added `PortableAttributes.java`: on NeoForge's official `EntityAttributeModificationEvent` (mod bus, after attribute creation, before common setup), iterate all registered living entity types (including modded entity types) and fill *missing* generally applicable numerical attributes, without overriding existing supplier defaults.
2. Initially includes `minecraft:max_absorption`, `minecraft:oxygen_bonus`, `minecraft:armor`, `minecraft:armor_toughness`, `minecraft:knockback_resistance`, and `minecraft:explosion_knockback_resistance`. This is not a new per-mob whitelist. Support is intentionally limited to shared vanilla numeric effects, not all registered/modded attributes.
3. Wired the new handler into `FreakyFriday(IEventBus)` on the mod bus, preserving the existing book, category, and shortcut workflows. Updated source package version to `0.5.0-alpha`.
4. Extended the production-code Java synthetic compile to include the new handler and an event simulator with two different entity type suppliers. The test spawns synthetic cow and skeleton entities from those augmented suppliers, swaps entire Defense and Vitality categories, checks values on both, and reverses them.

## Test matrix (all synthetic)

| Harness | Result |
| --- | --- |
| Pure Java category transaction core | PASS — 1,020 assertions, including 500 two-way randomized rounds |
| Category definitions and dynamic modded numeric classification | PASS |
| Category schema checks | PASS — 9 assertions |
| Minecraft-facing source bridge | PASS — 29 assertions (twice, including full-source stub compile) |
| New registration-time portability simulator | PASS — 34 assertions; 16 checks beyond the attribute install guards |
| Client Ctrl+S shortcut + remapping/reconnect | PASS |
| Production Java compilation against mocked client/server/NeoForge APIs | PASS — all 12 production Java sources |
| Resource JSON/TOML/metadata checks | PASS |
| Real Gradle / NeoForge 26.2 binary build | BLOCKED — JDK 25, Gradle and reachable dependencies unavailable here |
| Real player, mob, dedicated server and restart tests | NOT EXECUTED |

## Findings and unsatisfied gates

- **Progress, not general solution:** NeoForge's `EntityAttributeModificationEvent` can add supported attributes to all relevant types *at registration time*. This removes one source of incompatible numerical schemas. It does not dynamically install every third-party attribute, ensure a mob's unique AI consumes transferred attributes, or disable intrinsic species behaviors. Unsupported whole-category trades remain rejected.
- **No ability portability yet:** A creeper's explosive behavior, bat flight, spider climbing, and player flight controls still require behavior ownership/dispatch and donor suppression. An attribute alone does not make those abilities work.
- **Base values only:** The current numerical bridge swaps permanent base values, not all equipment/potion/permanent attribute modifiers. Phase 1 category semantics are therefore incomplete for modifiers, and Phase 3 behavior categories are not implemented. The book must not claim it swapped full functional abilities yet.
- **No explicit persistence layer yet:** Current native base values may be serialized by Minecraft, but respawn, world restart, and transfer of custom category ownership are unverified. Phase 2 is open.
- **Potential mod compatibility:** Adding six neutral attributes to modded living entity types needs runtime validation, particularly for existing custom attribute definitions and spawn behavior. The test creates types from simulated suppliers, not real loaded mods.
- **Actual build remains blocked:** `java -version` is Java 21; no Gradle executable or official wrapper or dependencies available; HTTPS DNS is unavailable in the execution container. Do not call this a real NeoForge build or provide an unverified JAR.

## Next R0.6 audit/repair gate

1. Compile with the official NeoForge 26.2 toolchain and verify client/server startup. Correct any real API signature differences.
2. In-game exchange a full defensive category between a cow and skeleton, then reverse it; inspect max health, health, armor, absorption, and disconnect/reload behavior.
3. Audit more registered attributes individually for safe functional portability before expanding the shared set. Keep unsupported entries visible and reject category transfers if atomicity cannot be achieved.
4. Establish how entire categories should carry attribute *modifiers* and custom modded ability behavior, not just base values.
5. Keep independent audit-and-correction cycles running before closing Phase 1.

## Evidence limits

Relevant API reference: NeoForge 26.2 `EntityAttributeModificationEvent` exposes `getTypes()`, `has(type, holder)`, and `add(type, holder)`; official NeoForge attribute documentation warns that specific attributes are not functional on all species. The synthetic harness is not proof of real mod behavior.