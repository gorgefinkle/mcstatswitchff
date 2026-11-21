# Freaky Friday — R0.4 independent self-audit / correction pass

**Base:** R0.3 full-source package. **Target:** Minecraft 26.2 · NeoForge 26.2.0.95 · Java 25.
**Version:** 0.4.0-alpha. **Phase:** Phase 1 still OPEN.

## Changes backed by tests

| Risk discovered / refinement | Code change | Proof possible in this workspace |
|---|---|---|
| Validator accidentally mutates entity state before the real transaction | Core exchange now inspects and restores categories even when validation changes data or throws | Injected preflight corruption and preflight exceptions, followed by exact readback |
| Failure recovery unnecessarily rewrites untouched entities | Only changed snapshots are restored; all resulting snapshots checked | Assert no restore calls on clean preflight rejection |
| Invalid per-species schema could be summarized ambiguously | `CategorySchema` compares both original sets, lists first-only and second-only keys, and explicitly rejects unsupported whole transfers | Nine focused tests and 29 Minecraft-facing bridge checks |
| Modded registry entries always grouped as one giant “Modded Stats” category | Conservative semantic ID classification; explicit override is retained, no mob whitelist | Classification tests for health, flight, attack_speed, armor, weight, unknown IDs, duplicate terms |
| Real binary build cannot currently be distinguished from synthetic API compile | `scripts/build_doctor.py` checks Java 25 and Gradle before real `build` invocation | Doctor exits 2 here with Java 21 / no Gradle; synthetic compile clearly labeled |

## Tests actually executed

- **PASS:** 1,020 pure Java exchange assertions, including 500 seeded randomized two-way swaps and reversals.
- **PASS:** CategoryDefinitions self-test including modded ID inference and explicit overrides.
- **PASS:** CategorySchema self-test: 9 assertions for compatible, incompatible, asymmetric and immutable schemas.
- **PASS:** Synthetic Minecraft bridge: 29 assertions, run twice including once against the full actual mod class. Covers book interaction, multiple numerical values, self selection, vitality, rejected disjoint schemas, rollback, and simultaneous player sessions.
- **PASS:** Remappable Ctrl+S synthetic client harness, first-hold/reconnect hint and no-book guard.
- **PASS:** 11 production Java sources compile against **synthetic** client/server NeoForge API stubs.
- **PASS:** JSON/TOML resource parsing and alpha-version consistency.
- **BLOCKED:** Real Java 25 / NeoForge Gradle build, dedicated server and client launch. The workspace has Java 21, no Gradle wrapper/dependencies, and cannot reach external download endpoints. `build_doctor.py` confirms Java version block.

## Explicitly unfinished / not credited

1. **Phase 1 step 1:** Actual NeoForge 26.2 binary build and in-game book/keybinding tests. Synthetic stubs cannot certify the real APIs.
2. **Phase 1 step 5:** Complete disjoint-schema portability. Only compatible native numerical sets work. The pure-Java mock's ability to exchange different maps does not demonstrate a game-effective transfer of missing attributes.
3. **Phase 1 step 6:** Strong transaction checks exist, but Minecraft or third-party side effects may not roll back. Real runtime checks needed.
4. **Abilities:** No transferable Creeper fuse, Bat flight, generalized AI or player ability controls. `SIGNATURE` deliberately remains hidden.
5. **Persistence:** No dedicated persistent category/ability state, player death/respawn synchronization or saved-world tests yet.
6. **Modded attrs:** Registry ID classification is heuristic. Registered values can only exchange when both recipients support every key. No claim of automatic modded ability transfer.
7. **Mob/player consent, external plugin interference, simultaneous users:** multiplayer state guards have synthetic tests but no live integrated server testing.

## Next correction gate (R0.5)

1. Obtain official NeoForge 26.2 build dependencies and JDK 25, compile against real APIs, fix actual link errors, run client and dedicated server.
2. Validate two-target book interactions and remappable Ctrl+S in-world, including real player targets and cross-dimension expiry.
3. Design the portability adapter around concrete, functioning attribute/behavior capabilities. Do not re-label incompatible partial swaps as successes.
4. Test save/load, world restart and player respawn before marking persistence done.
5. Repeat all tests after every correction; Phase 1 closes only once its acceptance criteria are genuinely met.

No official tested JAR has been produced in this checkpoint.
