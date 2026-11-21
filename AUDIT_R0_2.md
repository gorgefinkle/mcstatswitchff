> Historical R0.2 audit only; see `AUDIT_R0_3.md` for the latest checkpoint.

# Freaky Friday R0.2 — self-audit and acceptance report

## Environment & build gate

- Target: **Minecraft 26.2 / NeoForge 26.2.0.95 / Java 25**.
- Observed local Java: **OpenJDK 21.0.12.1**.
- Local Gradle: **not installed**; no cached NeoForge / Minecraft development dependencies found.
- External distribution downloads: blocked by DNS/network policy.
- **Official NeoForge compilation: BLOCKED / NOT TESTED.** No JAR generated. No live server or in-game test claimed.

## Executed harnesses

| Harness | Result | Scope |
|---|---|---|
| Pure Java exchange tests | PASS | Reciprocity, unsupported target rejection, rollback on second write failure, self-swap rejection. |
| Whole-category exchange tests | PASS | Multiple values, different key counts, whole behavior bundles, reversal, preflight, partial-write rollback on either recipient, immutable snapshots, invalid values, 500 seeded fuzz rounds. |
| Dynamic group classifier | PASS | Vanilla category mapping, unknown modded ID discovery, opt-in reclassification, invalid ID/category rejection. |
| Synthetic Minecraft bridge | PASS | **Real R0.2 production adapters** compiled and exercised with mock Minecraft classes: numeric multi-value swaps, book first/second selection, reverse, self-target in both positions, vitality, modded attributes, unequal schema rejection, write-error rollback, session isolation, book prerequisite. |
| Synthetic NeoForge full-source compile | PASS (synthetic only) | All 11 production Java classes compiled against **generated test doubles**, not actual NeoForge jars. |
| Synthetic client key harness | PASS | Ctrl+S default binding, remappable KeyMapping registration path, hint on holding book, command dispatch with book and suppression without book. |
| JSON metadata & Java source tree | PASS | Item model, locale and recipe parse; one current book resource and version labels; no R0.1 AttributeSwapper source. |
| Real 26.2 Gradle build | BLOCKED | Java 25, Gradle and NeoForge dependency unavailable. |
| Dedicated server/client/game tests | NOT RUN | Dependent on real build. |

## Self-review findings and fixes in this checkpoint

1. **Found: R0.1 'categories' were individual attribute IDs.** Fixed: a fixed but extensible group catalog, whole-bundle transaction, grouped clickable choices.
2. **Found: previous numerical swap only tried to rollback the first entity.** Fixed: `CategoryExchange` attempts rollback of both entities even if a write failed partway through either one.
3. **Found: live health didn't accompany the Vitality category.** Fixed: vitality includes current health and absorption. Exact write-back is checked; non-lossless writes abort and roll back.
4. **Found: original ability API invited single-ability transfers.** Replaced with `AbilityCategoryAdapter`, explicitly whole-category. No gameplay implementation falsely advertised.
5. **Found: self-selection lacked guaranteed first-equip prompt.** Fixed: hint on equipping the book, repeated after a long interval, hidden otherwise; Ctrl+S still configurable.
6. **Found: source selection state remained after player logout.** Added cleanup hook and monotonic wall-clock expiration.
7. **Found: modded attrs absent on recipient could be silently treated as supported later.** Implemented explicit full-category compatibility preflight; mixed schemas reject whole trade until the separate portability milestone.

## Phase 1 gate status (strict live acceptance, not code existence)

| Step | Source status | Real acceptance |
|---|---|---|
| 1. Build/launch R0.1→R0.2 | Project configured | **BLOCKED** (no Java 25, Gradle/NeoForge deps) |
| 2. Book two-target selection | Implemented; synthetic pass | **NOT YET IN-GAME VERIFIED** |
| 3. Ctrl+S self-selection/hint | Implemented; synthetic pass | **NOT YET IN-GAME VERIFIED** |
| 4. Category registry | Implemented; unit pass | **NOT YET IN-GAME VERIFIED** |
| 5. Entire-category swapping | Core and compatible numeric attributes implemented; synthetic pass | **PARTIAL** (ability behavior and incompatible schemas not yet transferable) |
| 6. Atomic swaps with rollback | Implemented; unit + synthetic pass | **NOT YET IN-GAME VERIFIED** |

**Do not mark Phase 1 complete** until a real NeoForge 26.2 client/server build and the specified in-game acceptance tests pass. The ability-transfer milestones remain future phases.

## Explicit risks for next work pass

- Modern NeoForge/Minecraft method signatures (particularly chat click events, client key registration and item APIs) have **not been binary-verified**.
- Vanilla attribute maps have entity-specific supported sets; global registration of arbitrary modded attributes on all mobs would risk third-party interactions. Do not do this blindly.
- Category base values are not automatically all effects, behaviors, AI goals, equipment modifiers or custom mod ability code. These are future category-portability adapters and live behavior tests.
- Automatic discovery of modded registry attributes is real in source, but arbitrary modded ability transplantation cannot be guaranteed without its mod exposing an integration API or a compatible adapter.
- Saved-world persistence of the new category bundle and player survival on death/respawn is not yet implemented or tested.