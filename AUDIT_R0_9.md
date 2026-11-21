# Freaky Friday R0.9 — real-attribute runtime audit

**Version:** `0.9.0-alpha`  
**Result:** source checkpoint passed supported local harnesses. **Phase 1 OPEN; actual NeoForge build and in-game execution NOT VERIFIED.**

## Work completed

1. Started from the delivered full R0.8 source tree; verified its original limitation that the source compiled only with NeoForge test stand-ins and couldn't run an official Gradle build.
2. Identified duplicated inline permanent-modifier handling in `AttributeCategorySwapper` and extracted it to `AttributeModifierTransfer`. The new helper implements production snapshot, incoming-ID validation, and replacement using actual `net.minecraft.world.entity.ai.attributes.AttributeInstance` APIs.
3. Updated the transaction adapter to use that helper in snapshot, preflight validation, and replacement. The transaction engine still owns whole-category exchange, exact readback, and rollback. No feature was replaced with a mock in production.
4. Created a headless JVM integration test using the **user-provided vanilla Mojang 26.2 server bundler** and its embedded libraries. This JVM test initializes Minecraft bootstrap, constructs real attribute instances, and operates directly on production transfer/transaction code.
5. Added a real **two-attribute** category scenario, beyond one-attribute modifier swaps. Kept all real-game-only features explicitly pending.

## Verification matrix

| Test | R0.9 result | Coverage / limitation |
|---|---|---|
| Core transaction engine | **PASS: 1,025 assertions**, including 500 seeded randomized swap/reversal rounds | Pure Java engine, no Minecraft runtime |
| Group classification / schema | **PASS** | Pure Java tests; 9 schema checks |
| Minecraft adapter + player sessions | **PASS: 50 checks** | Synthetic Minecraft APIs |
| Cross-species registered attributes | **PASS: 34 checks** | Synthetic NeoForge event and attribute data |
| Book routing / client shortcut | **PASS: 13 book checks and shortcut harness** | Synthetic events, not real UI |
| Full-source Java compile | **PASS: 15 production files** | NeoForge + Minecraft stubs |
| Mojang 26.2 server API compile | **PASS: 14 server-side production files** | REAL vanilla classes; NeoForge interfaces stubbed; no client |
| Mojang 26.2 attribute-runtime test | **PASS: 25 checks** | REAL `AttributeInstance` / `RangedAttribute`, real production transfer + transaction classes |
| TOML / JSON / version audit | **PASS** | Resource syntax and source wiring |
| Complete package extraction + rerun | **PASS** | Verified separately after packaging |
| Actual NeoForge Gradle build | **BLOCKED** | Gradle and NeoForge/ModDevGradle dependency cache unavailable; DNS to download hosts fails |
| NeoForge game client/dedicated server | **NOT RUN** | No validated mod JAR or live NeoForge runtime |

### What the 25 real-class runtime checks demonstrate

- Permanent and temporary modifiers affect effective stats as the actual Minecraft 26.2 classes define them.
- Only permanent modifier IDs and values are included in the tradable category; temporary equipment/potion bonuses stay attached to their original recipient.
- An exchange transfers values, operations, and permanent modifiers reciprocally; reversing recovers both original category states.
- Conflicts between an incoming permanent modifier ID and an existing transient ID reject before either side mutates.
- Out-of-bounds attribute bases are rejected during preflight using real vanilla attribute sanitization.
- A forced exception after a real modifier write triggers transaction rollback, preserving the old state and transient effects.
- A separate two-attribute category transfers **both attributes together**, then restores both on reversal.

These checks **do not** instantiate server worlds, player connections, live mobs or NeoForge events. They do not establish full category transfer for entities lacking the incoming attribute type.

## Runtime dependencies / build status

- Oracle Java 25.0.5 is available in the working environment, supplied previously by the user. No Java installer is distributed in this ZIP.
- User's vanilla `mojang_26.2.jar` provides the real Minecraft classes for the two special harnesses; it is not packaged.
- No complete Gradle wrapper or installed Gradle distribution is available in the current runtime; Gradle/NeoForge download hostnames could not be resolved. No claim of an official NeoForge build is made and **no JAR** is included.

## Open requirements

1. True `gradle build` on NeoForge 26.2 using Java 25, and fixes for any actual NeoForge API errors.
2. Integrated client and dedicated-server smoke tests of the book, remapped Ctrl+S, first/second target selections, all numerical categories, existing vanilla entity behavior, and multiplayer state.
3. Dynamic complete category portability between different entity attribute schemas, with functional behavior rather than merely inserted/ignored numbers.
4. Later phases: persisted exchanged ability ownership, donor suppression, recipient behavior (flight, detonation, modded abilities), and broad multiplayer/regression tests.

**Acceptance decision:** R0.9 is a verified source-only improvement. Phase 1 is not closed.