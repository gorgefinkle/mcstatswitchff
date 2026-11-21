# Freaky Friday R0.6 — independent source review, corrections and evidence

**Baseline:** R0.5. **Target:** NeoForge 26.2 / Minecraft 26.2, Java 25. **Build artifact:** source-only checkpoint, version `0.6.0-alpha`. **Phase 1 status:** OPEN.

## Problem discovered

R0.5 exchanged a category's **base values** but left every permanent attribute modifier with its original owner. A cow could receive a skeleton's armor base without receiving a permanently applied armor bonus, so the actual numerical category was incomplete. Treating `getValue()` as the whole stat also would have been wrong: equipment and potion modifiers are transient, and cannot simply be reassigned without breaking ownership of their originating gear/effects.

## Correction implemented

1. Added the `PermanentModifier` core record and extended `CategoryBundle` to include deep-immutable maps of permanent modifiers grouped by attribute. Snapshots carry the modifier identifier, amount and operation; disallow duplicate IDs, non-finite values or modifiers without attributes.
2. The Minecraft adapter now snapshots each `AttributeInstance#getPermanentModifiers()` and swaps them **with all base values** of the selected category.
3. Replacements remove **only permanent modifiers**, install the incoming bases and permanent modifier set, then read back the complete category on **both entities**. Temporary modifiers are never removed and remain locally functional.
4. Preflight checks for an incoming permanent modifier ID colliding with any existing **transient** modifier ID, plus parses IDs and validates operations before mutations begin. Collisions reject the entire category.
5. Uses the existing two-entity transaction core and verified rollback. Failures on either recipient are reported; an incomplete rollback is **not** claimed to be atomic.
6. Added tests exercising multi-modifier exchange, all three components of effective armor, Vitality with permanent max-health, active temporary modifier preservation, recipient collisions, injected second-side modifier-write failures, and exchange reversal.
7. Reviewed the NeoForge attribute documentation and 26.2/26.3 Javadoc where available. Relevant public APIs: `getPermanentModifiers`, `getModifiers`, `removeModifier(Identifier)`, `addPermanentModifier`, and `Identifier.parse`. Real binary linking is still required to prove the 26.2 version signatures are all correct.

## Regression matrix (local harnesses — not actual Minecraft)

| Test | Status |
| --- | --- |
| Core transaction engine, deep-immutable bundle and 500 deterministic randomized reciprocal/reversal rounds | PASS — 1,025 assertions |
| Category definitions and dynamic registered attribute classification | PASS |
| Category schema diagnostic | PASS — 9 checks |
| Minecraft-facing swap, health, modifier collisions, rollback, selections | PASS — 50 checks, run twice |
| Entity attribute registration / conservative cross-species portable schema | PASS — 34 checks |
| Client keymap, Ctrl+S, remapping, actionbar, reconnect | PASS |
| All 13 production Java files against mocked API signatures | PASS |
| Resource JSON, mod TOML, source wiring, version agreement | PASS |
| JDK 25 / Gradle / actual NeoForge compiled JAR | **BLOCKED**: only Java 21 installed, no Gradle or cached NeoForge dependencies; DNS unavailable |
| Real integrated client, dedicated server, multiplayer and save/reload | **NOT RUN** |

The source ZIP is checked from a fresh extraction with `scripts/self_audit.py`. Test counts are individual test harness assertions, not counts of independent Minecraft playtests.

## Open issues and follow-up gates

- **Build blocker:** obtain Java 25, a supported Gradle distribution, and real NeoForge/ModDevGradle dependencies; compile and launch client/server. Correct verified API signature differences before declaring Phase 1 complete. A Java 21 synthetic compile cannot substitute for this.
- **Portability:** incompatible third-party attribute sets still reject a whole-category exchange. Adding every registered mod attribute to all entities would not ensure it functions; portability requires a separate, evidence-based strategy.
- **Stat semantics:** permanent modifiers swap; transient equipment/potion effects intentionally do not. Need real-game tests of active potions/equipment swapping, reload, and effective health. Third-party mods may create permanent modifiers whose own lifecycle needs special handling.
- **Atomicity limitations:** a mod callback can mutate attributes asynchronously or inject effects during swaps, so Java rollback cannot guarantee perfect all-or-nothing under every modded integration; the design detects inconsistencies and reports them, but does not claim a cross-mod atomic transaction.
- **Ability categories:** real AI/flight/explosion behavior transfer, donor suppression, player controls and modded executable ability discovery are **not implemented** in this checkpoint. String metadata in core unit tests does not constitute actual executable behavior.
- **Persistent custom state:** Phase 2 is open. No custom ability ownership persistence exists.

## Suggested next checkpoint (R0.7)

1. Resolve **real build/toolchain access** and correct errors from real NeoForge compilation. Do not close Phase 1 based on the current harness.
2. Add a **real runClient/runServer smoke test** with explicit first-target / Ctrl+S / whole-Defense exchange / reverse / cancel checks.
3. Characterize known-compatible modded registered attributes with real gameplay. Do not add speculative support for incompatible custom ability hooks.
4. Follow with a correction pass and dedicated multiplayer test before graduating Phase 1.