# R0.3 independent audit & correction report

**Target:** Minecraft 26.2 · NeoForge 26.2.0.95 · Java 25 · project version 0.3.0-alpha

## Source reviewed

Inspected R0.2's entrypoint, item registration, right-click selection, server-side sessions, client key mapping, numeric category adapter, generic exchange engine, API interface and harnesses. Inspected the game-facing call graph rather than only the pure-Java tests.

## Findings corrected

| Finding | R0.3 correction | Evidence |
|---|---|---|
| Silent partial category write could be reported as a success | Added post-write read-back of both complete category bundles, requiring exact swap | New silent-skip test + 500 randomized rounds |
| Rollback could silently fail | Verify both snapshots after attempted restoration; attach failures and surface recovery warning | Injected restoration failure test |
| Range-clamped modded or vanilla attribute could start a failing write | Validate incoming values using real `Attribute.sanitizeValue` before mutation | Preflight clamp test, no changes |
| Health adjustments could be clamped by recipient-specific maximum-health modifiers | Read-back detects mismatch and triggers rollback | Synthetic vitality test with nonzero max-health penalty |
| Stale first target after death | Check `isAlive` both on selection and when resolving first target | Synthetic dead-target tests |
| Hint text remained Ctrl+S after remapping | Read active `KeyMapping.getTranslatedKeyMessage()` in popup | Synthetic key-rebind test |
| Reconnect could suppress first-hold hint | Reset client state while disconnected | Synthetic reconnect test |
| Obsolete second exchange engine had weaker rollback | Removed unused class and its tests | Source tree inspection |

## R0.3 acceptance tests

| Test | Result |
|---|---|
| Pure Java 1,015 assertions, 500 seeded randomized category swaps and reversals | PASS |
| Full-bundle snapshots with unequal attribute and behavior **metadata** entries | PASS (pure Java only) |
| Partial write exception / first and second recipient rollback | PASS (synthetic) |
| Silent write failure detection + rollback verification failure reporting | PASS (pure Java) |
| Minecraft-facing integration, numeric group selection and vitality | PASS (27 synthetic checks) |
| Registered modded numeric attributes, equal schema / unequal schema rejection | PASS (synthetic; portability NOT solved) |
| Out-of-range attribute preflight, clamped vitality recovery | PASS (synthetic) |
| Ctrl+S, rebinding hint, no-book guard, reconnect | PASS (synthetic) |
| Compile all 10 production Java files against generated mock APIs | PASS — **NOT NeoForge linkage** |
| JSON / TOML / metadata / version consistency | PASS |
| ZIP extraction without preserved Unix executable mode | PASS after `self_audit.py` was made to invoke shell with `sh` |
| Actual Java 25 + NeoForge 26.2 Gradle compile | **NOT RUN — toolchain absent** |
| NeoForge client GUI / key mapping and dedicated server launch | **NOT RUN** |
| Whole-category portability across differing entity attribute sets | **NOT COMPLETE** |
| Creeper ↔ Bat / player flight / detonation / modded behavior transfer | **NOT IMPLEMENTED** |
| Persistent in-game saved/reloaded swapped categories | **NOT RUN / NOT IMPLEMENTED as dedicated state** |

## Evidence-based API review

- Official 26.2 JavaDoc confirms `Attribute.sanitizeValue(double)`; incoming value range preflight uses that API. https://aldak.netlify.app/javadoc/26.2.x/net/minecraft/world/entity/ai/attributes/attribute
- 26.2 EventBusSubscriber infers mod/game bus by event type; client key-registration remains physically client-side. https://aldak.netlify.app/javadoc/26.2.x/net/neoforged/fml/common/eventbussubscriber
- 26.2 item interaction method signature was checked in JavaDoc class usages. https://aldak.netlify.app/javadoc/26.2.x/net/minecraft/world/class-use/interactionhand
- NeoForge 26.2 MDK uses ModDevGradle 2.0.148 and Java 25. https://github.com/NeoForgeMDKs/MDK-26.2-ModDevGradle

**API documentation review is NOT binary compilation.**

## Phase 1 gate evaluation (strict)

1. Real build: BLOCKED. Synthetic-only compilation cannot pass this gate.
2. Book two-target interaction: SOURCE IMPLEMENTED; synthetic tests pass; in-game unverified.
3. Self-selection: SOURCE IMPLEMENTED with new remappable hint fix; in-game unverified.
4. Dynamic group registry: SOURCE IMPLEMENTED; modded registration compatibility unverified.
5. Complete category swap: **PARTIAL**. Core engine supports full snapshots, but game adapter cannot transfer disjoint schemas or executable ability behavior.
6. Atomic exchange: **IMPROVED**. Preflight, post-write equality verification and rollback checking; actual Minecraft side effects require runtime scrutiny.

Phase 1 must **remain open**. This is an audit/correction checkpoint, not a full feature-completion claim.

## What to pursue next (R0.4)

1. Obtain JDK 25, Gradle 9.2.1, NeoForge 26.2.0.95 artifacts; compile, run dedicated server and client. Audit actual API method signatures and fix failures.
2. Test all book interactions in a real world (client-server command, key rebind, two players, target death/disconnect).
3. Design **functional** cross-schema attribute portability without blindly adding every modded attribute to every mob; verify transferred values actually influence gameplay.
4. Start persistent per-entity category state and normalization tests, then repeat all regression harnesses.
