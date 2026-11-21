# R0.10 source audit and verification record

## Change scope

Prepared **one shared mod source tree** for separate NeoForge 26.2 and 26.3 builds. Added explicit version profiles, common Gradle target selector, isolated creative-tab platform hooks, strict expanded version metadata, distinct output filenames, and compatibility test scripts. No claim is made that the physical mob-ability architecture or remaining category-portability problem has been finished.

## Executed tests

- `scripts/self_audit.py` (Java 25): PASS. Pure Java transaction tests: **1,025** exchange assertions including **500 seeded random trials**, category classification and schema tests; synthetic bridge **50** checks; portability **34**, book interaction **13**, keybinding scenario; synthetic compile of **16 production Java source files**, plus JSON/TOML/static wiring.
- `scripts/test-version-profiles.py`: PASS. Both selected target profiles, exact dependency ranges in expanded TOML, separate artifact naming, one platform class per target, shared core present only once.
- `scripts/test-mojang-api.py` with user-provided Mojang 26.2 bundler: PASS. **15** server-side production source files compiled against genuine 26.2 server classes; only NeoForge interfaces were synthetic. Client APIs were not compiled.
- `scripts/test-mojang-attributes.py` on the user-provided Mojang 26.2 bundler: PASS. **25** assertions on real Minecraft AttributeInstance/RangedAttribute, including permanent modifier transfers and temporary modifier retention.
- `scripts/inspect-neoforge-api.py` on user-provided NeoForge 26.3.0.115 universal JAR: PASS for **six class signatures**, including event, creative tab, interaction, keymap and registration declarations. This is an API-shape check, not a 26.3 compile or runtime test.
- Gradle/NeoForge real build: **BLOCKED** — no Gradle executable/complete wrapper, network DNS unavailable for ModDevGradle/NeoForge artifacts. Java 25 is present from user-provided JDK.

## Important limits

1. No JAR has been produced, so **no integrated or dedicated world run** has been verified.
2. Not all modded/mob attributes are portable. Unsupported unequal schemas still correctly abort; this remains a core design gap.
3. No real transferable flight, creeper detonation, ability donor suppression or category persistence is implemented yet.
4. The 26.3 profile and API declarations must still undergo full compilation with its version's actual Minecraft and NeoForge libraries.
5. The Gradle build script is structurally checked offline, but **has not been executed** because Gradle and dependencies are missing.
6. Do not treat source-only checks as proof of a releasable build; do not claim Phase 1 closed.

## Next acceptance gate

Obtain Gradle + ModDevGradle + NeoForge 26.2 dependency graph; run `python3 scripts/build_doctor.py --mc 26.2`, address real errors, and conduct the real-game checklists before attempting to certify the first release.
