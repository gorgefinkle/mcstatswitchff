# Freaky Friday — goals, progress, and next milestones

**Handoff status:** R0.9 (`0.9.0-alpha`) source checkpoint.  
**Initial target:** Minecraft 26.2 / NeoForge 26.2.0.95.  
**Broader release goal:** As many supportable Minecraft versions as practical, with 26.2 **first**, 26.3 **second**, and older/newer ports evaluated separately.  
**Acceptance:** Phase 1 is OPEN; **no certified installable NeoForge JAR exists yet**.

> This is an updated handoff/status document. The accompanying `ORIGINAL_Roadmap_v1.1.*` is the prior design reference written against R0.1; its historical progress statements are not current. For source-level evidence, see `AUDIT_R0_2.md` through `AUDIT_R0_9.md` in the project root.

## Non-negotiable end goal

A survival-compatible **Freaky Friday book** that allows a player to click a first living entity, select **one category**, and click a second living entity. The **entire category** must exchange **reciprocally and atomically**, transferring **all included values, permanent modifiers, abilities, triggers, parameters, state, and functioning behavior**, without cloning or retaining abilities on the donor. The two targets may be mobs, players, or one of each; their identities/appearances are not transformed.

- Default, **remappable Ctrl+S** selects the player holding the book as first or second target; a discreet contextual hint explains the shortcut.
- Categories are the unit of swapping. **Never** offer per-stat or per-ability selection hidden under the category, and never implement special mob-pair exceptions.
- Categories can differ in number of members, including an empty side; a complete exchange remains required. If even one component cannot transfer safely, reject everything and explain why.
- Numerical attributes added by other mods should be discovered dynamically. Custom executable mob powers require a portable standardized interface or adapter when they cannot otherwise be safely transferred; never claim arbitrary unknown AI automatically works.
- All exchanges must persist correctly, be server-authoritative and multiplayer-safe, and be validated on real clients/dedicated servers.
- First hard special-ability test: **Creeper ↔ Bat** swaps the complete Special Abilities category: bat gains genuine proximity fuse/detonation and loses flight; creeper gains genuinely functional flight and loses detonation. Reverse trade restores both original full bundles.
- For public releases, use **a shared core and version-specific compatibility layers/builds** as needed. One compiled JAR should not be assumed to work across versions.

## What is actually implemented in R0.9 source

| Area | Status at R0.9 | Evidence and boundary |
|---|---|---|
| Gradle / NeoForge scaffolding, metadata, book model, translation, recipe | **Source present** | `build.gradle`, `gradle.properties`, `src/main/...`; real NeoForge build unverified |
| Two-target book selection and category menu | **Implemented; synthetic tested** | Book and session classes; real in-game UI remains unverified |
| Default Ctrl+S self selection, remapping and shortcut hint | **Implemented; synthetic tested** | Client bindings; real in-game key UI remains unverified |
| Category grouping / dynamic registered attribute classification | **Implemented for numeric attributes** | `CategoryDefinitions`, `AttributeCategorySwapper`; some unknown modded attributes remain under generic Modded Stats |
| Entire supported numerical category swap | **Core engine and real-class tests passed** | Full base values + permanent modifiers; atomic exchange, exact readback and rollback in tested cases |
| Current health and absorption when trading Vitality | **Synthetic tested** | Subject to safe recipient limits; real-world mobs untested |
| Common cross-species registered attributes | **Partial** | Six generally shared numerical attributes added via event; missing/unsupported schemas still reject atomically |
| Permanent modifier transfer while retaining temporary equipment/potion modifiers | **Tested on real Minecraft 26.2 attribute classes** | 25 R0.9 runtime checks; no live world |
| Entity-interaction priority, offhand, duplicate callback protection | **Synthetic tested** | Real NeoForge callbacks require verification |
| Server persistence and client sync of exchanged special abilities | **Not implemented/verified** | Phase 2 onward |
| Full category transfer across mismatched entity schemas | **Incomplete** | Cannot safely insert or activate every arbitrary modded or species-specific attribute |
| Transferable flight, creeper fuse/detonation, ability ownership/donor suppression | **Not implemented** | Adapter interface present, no working behavior system |
| Broad modded-mob special ability transfer | **Not implemented** | Requires adapters when no standard transferable ability interface exists |
| Real NeoForge 26.2 Gradle build and actual game test | **Blocked / not verified** | Java 25 and Mojang classes present, but Gradle/ModDevGradle/NeoForge 26.2 dependency graph unavailable |
| Minecraft 26.3 release | **Not built** | User supplied 26.3 NeoForge universal JAR for inspection, not a full matching dev toolchain |

### Proven test evidence (from supplied R0.9 audit)

- Pure Java engine: **1,025 assertions**, including **500 seeded random swap/reversal rounds**.
- Synthetic Minecraft adapter/session checks: **50**; cross-species registration checks: **34**; book interaction checks: **13**.
- Real Minecraft 26.2 `AttributeInstance` harness: **25 checks**, including two-attribute category trade, permanent vs transient modifiers, reversal and rollback.
- Server-side source: compiled against real Mojang 26.2 classes with **NeoForge interfaces stubbed**; **client and real mod not compiled**.
- Source ZIP re-extraction and regression rerun previously reported passing in `AUDIT_R0_9.md`.

**Important:** These are historical checkpoint audit results. This handoff repackages the same R0.9 source; it does not claim a new official NeoForge compile or playable JAR.

## Next ordered work

1. **R0.10 — Real NeoForge 26.2 build:** Obtain the matching Gradle + ModDevGradle + NeoForge dependency graph; compile under Java 25 and repair source/API defects. Do not silently switch to NeoForge 26.3.
2. **R0.11 — In-game acceptance:** Start integrated and dedicated servers; verify book click priority, Ctrl+S and remapping, category selection, duplicate-event handling, player/mob combinations and server/client consistency.
3. **R0.12+ — Close Phase 1:** Fix verified issues, implement safe and functional cross-species category portability (not fake attributes), and repeat independent audit/regression. Close phase only after acceptance passes.
4. **Persistence phase:** Serialize complete category ownership/state and sync correctly across restarts, despawns, travel, reconnects and player death policy.
5. **Portable ability phase:** Build full ability-bundle lifecycle, acquire/release, donor suppression, AI/player controls, rollback and persistence.
6. **Creeper/Bat proof:** Test one complete category exchange including mismatched numbers of abilities, real actions and reverse restoration on a dedicated server.
7. **Player abilities and vanilla catalog:** Controlled flight/detonation and additional broadly portable powers with no species-pair special cases.
8. **Modded compatibility:** Automatic compatible attribute detection, public ability adapters, sample third-party add-on and documented exclusions.
9. **Multi-version project structure:** Isolate version-specific calls; build/test separate 26.2 and 26.3 outputs, then assess other versions.
10. **Release gate:** Full multiplayer/edge-case/security/performance checks; package reproducible source, matched JARs, logs and checksums for each supported version.

### Development cadence

Each phase normally takes multiple passes: **implementation → independent source/runtime audit → correction/regression → repeat as needed**. A phase closes on acceptance evidence, not because a target checkpoint number was reached.

## Files to start from

- `README.md`, `AUDIT_R0_9.md`: authoritative current-source and audit notes.
- `src/main/java/`: production sources; `src/test/java/` and `scripts/`: regression harnesses.
- `scripts/REAL_GAME_SMOKE_TEST_R0_9.md`: first actual Minecraft run checklist.
- `docs/ORIGINAL_Roadmap_v1.1.md` and `.docx`: full earlier end-goal/acceptance specification, **historically scoped to R0.1**.
- **Separate archive** `FreakyFriday_R0_9_External_Assets.zip`: user-provided JDK 25 installer, vanilla 26.2 server jar, and NeoForge 26.3 universal jar. Binary game/development files are **not** included in the source handoff and should not be included in a public mod distribution.
