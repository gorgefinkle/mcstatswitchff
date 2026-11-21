# Freaky Friday — End Goal & Completion Roadmap

**Project:** Freaky Friday / Stat Switcher  
**Target platform:** Minecraft 26.2, NeoForge, multiplayer-capable  
**Document version:** 1.1 — whole-category exchange correction, November 20, 2026  
**Source checkpoint reviewed:** R0.1 (`FreakyFriday_R0_1_Source.zip`)  
**Status:** Design and early source prototype; **not yet a verified playable mod**

## 1. End goal

Create a survival-compatible Minecraft mod centered on a single, intuitive item: the **Freaky Friday book**. Holding the book, a player selects a first living entity, chooses a category, and selects a second living entity. **The complete selected category**—every applicable value, associated function, and transferable behavior belonging to it—is reciprocally exchanged between the two entities. Both retain their original appearances and identities, but the entire received category must have real gameplay effects.

A player should be able to exchange an entire movement category with a horse, an entire vitality category with another mob, or all special abilities and their functional state between a creeper and a bat. The system must also support exchanges between **two mobs**, with no player required as either target. The same category mechanism should automatically recognize compatible modded attributes and provide an extension path for modded special abilities.

**Core promise:** *One book. Two targets. One selected category. Every value and function in that category trades places.*

### Completion means

- A **buildable, tested, reproducible NeoForge JAR** runs on the designated Minecraft version in both integrated and dedicated-server play.
- The book's complete interaction loop works, including the default remappable **Ctrl+S** shortcut for selecting yourself and a clear unobtrusive hint explaining it.
- Whole-category exchanges change actual gameplay and persist, including compatible registered modded attributes grouped into the chosen category.
- Whole-category exchanges transfer **all values and behaviors assigned to that category**, including multiple abilities and their operating parameters; neither side keeps a duplicate of what it gave away.
- Players and mobs can both donate and receive supported abilities. Transfers work for player <=> player, player <=> mob and mob <=> mob.
- World saves, reconnects, server restarts, deaths, dimension changes and multiplayer observation do not leave duplicated or phantom abilities.
- The mod discovers compatible modded traits dynamically; unsupported opaque modded abilities are described honestly instead of silently appearing to swap.
- The shipped feature set is documented, tested, and stable; no unverified promises of universal third-party AI compatibility.

## 2. Intended player experience

### Book workflow

1. Obtain the Freaky Friday book (R0.1 has a simple book + fermented spider eye recipe; balance remains adjustable).
2. Hold it and **right-click the first mob or player**. Alternatively, press **Ctrl+S (default, remappable)** while holding it to select yourself.
3. Choose **one category** from a small, readable, dynamic category picker. Do not select individual stats, powers, sub-abilities, or internal slots. One category click selects its complete contents.
4. Right-click a different living entity as the second target, or use the same self-shortcut if selecting yourself second.
5. The server validates **every component of the selected category for both targets**, then swaps the two complete category bundles atomically. Show a succinct success or failure explanation; no partial exchange is allowed.

No separate command entry should be necessary for ordinary play. Commands can remain as diagnostics and fallback. Canceling the selection must be simple. Stale targets and unavailable categories should fail safely without partially changing either entity.

### Non-negotiable gameplay rules

- **Reciprocal, not copied:** each entity receives the other entity's entire selected category and loses its own; no category member is duplicated.
- **Preserve identity:** a cow remains a cow, a player remains a player; this is not shapeshifting.
- **Meaningful results:** acquiring flight actually allows movement through the air; acquiring detonation actually runs an appropriate fuse and blast sequence.
- **Any target pairing:** no bespoke code for every possible pair of entity species.
- **Native and modded attributes:** discover registered attributes at runtime and assign them to exchangeable categories; do not hardcode a vanilla-only roster or require clicking each attribute.
- **All-or-nothing categories:** a category is swappable only when every included value and function can transfer safely to the other target. Otherwise refuse the whole exchange with a clear reason; never silently omit subcomponents.
- **Persistence and authority:** the dedicated server owns the exchange and saved trait state; clients render and request actions.
- **Single intuitive tool:** the book drives the entire user-facing feature, without requiring a complex machinery or progression tree.

## 3. Trait model and categories

The player-facing picker is the **actual exchange unit**: selecting a category swaps **every attribute value, functional parameter, transferable ability, and supporting behavior registered under that category**. It is not a browsing group leading to per-stat or per-power selections. Categories can contain different numbers of values on each entity; the full contents trade places without bespoke species-pair rules.

| Group | Initial examples | Implementation principle |
|---|---|---|
| Vitality | Max health and all other vitality values/behaviors in the category | Exchange the entire health-related bundle, including valid supporting functions and state |
| Movement & body | Movement speed, scale, gravity, jump, knockback resistance | Exchange all assigned physical/movement values and their working implementations together |
| Combat | Attack damage, attack speed, armor and other assigned combat traits | Exchange all category-owned values and functions; retain external gear/effects on their wearers |
| Modded category extensions | Any compatible registered attributes or functions added by other mods | Place discovered traits in their declared or supported category; swap them as part of its complete bundle |
| Special abilities | Creeper detonation, bat flight, teleportation, climbing, projectiles and other supported powers | Exchange **all** abilities within this category, including their parameters, AI/player handlers and functional state |

**Fixed rule:** There are **no one-power slots in the book interface**. If Entity A has three powers in Special Abilities and Entity B has one, A receives that one and B receives all three, together with each power's triggers, AI/controls, parameters and other functioning components. An empty category is still a valid whole-category value when the transfer is supported. No selective picking or invisible exclusions.

### Whole-category transfer contract — fixed requirement

- Each category is a **bundle**. A click exchanges both entities' **entire bundles** for that category: every present member value, any category-owned functional code/behavior, and the state those behaviors require.
- The two sides may contain **different numbers of members**. This is not a pairwise stat matching operation; their whole sets trade places, including the case where one side is empty.
- The book only displays **categories**. It does not offer hidden secondary selection of a particular attribute, power, or one-off species-specific feature.
- Preflight **all** members for both recipients. If even one cannot be transferred safely, **no changes happen to either entity**; both bundles must roll back completely if application fails.
- Category membership is an explicit, extensible registry rule, not a limitation invented for a demonstration. Future modded values and behaviors join the appropriate category and are transferred with that category when supported.

### Numerical values within category exchanges (proposed defaults)

- Exchange **all intrinsic/base values in the selected category together**, plus any directly category-owned behavior and durable parameters; keep equipment, active potion effects and unrelated transient modifiers with their original entities.
- Preflight every included value and function on both entities for type, range, recipient support and safe behavior detachment/attachment **before** committing the complete exchange.
- For maximum-health changes, clamp current health to the new maximum (the current R0.1 behavior); consider whether proportional health preservation is preferable before final release.
- When a registered attribute exists on one entity but not another, do **not** silently reject every potential exchange forever. Investigate safe recipient attribute attachment. If a safe path does not exist, show the precise limitation.
- Every exchanged category component should remain exchanged after equipment/effects change, save/reload and reconnect.

## 4. Transferable special abilities

A special-abilities category exchange is **not** swapping a boolean label, one selected power, or a decorative effect. It transfers the **complete set of category-owned abilities** and each one's parameters, controls, triggers and functions. The transaction is **discover both full bundles → validate every component → suppress/detach both original bundles → attach/activate both received bundles → commit**, with rollback of both entities if any step fails.

### First proof: Creeper <=> Bat

After exchanging their entire **Special Abilities** category:

- The **bat** receives the creeper's complete supported special-ability bundle—including proximity detection, fuse/hiss, explosion settings and functioning behavior—and loses all of the bat abilities that were in its outgoing bundle.
- The **creeper** receives the bat's complete supported special-ability bundle—including flight, aerial movement and related navigation—and no longer runs the transferred creeper abilities; it keeps its appearance.
- Both states survive a world save/reload and a server restart; nearby multiplayer clients see the same outcome.
- Reversing the exchange restores **both original full bundles**, including all their values and functions, with no duplicated fuse, invisible flight, residual AI goal or phantom effect.

This is the first **hard architecture acceptance gate**. The example proves generic category transfer, **not** a one-off creeper/bat pairing. If either entity retains any transferred-away behavior, or a category member is skipped, the test fails.

### Player ability adapters

AI mobs and human-controlled players cannot always share input handling. Each ability inside a transferred category must activate the correct **player-control** or **mob-AI** implementation on the recipient. A player receiving a category with flight and detonation must receive **both functioning powers**, with appropriate controls and server-defined rules; the donor must lose all powers moved out of the category.

### Extensibility for modded mobs

Use a **tiered discovery system**:

1. **Automatic:** registered numerical entity attributes, including mod-added ones.
2. **Automatic when standardized:** behaviors exposed through a compatible built-in ability interface or registered capability.
3. **Adapter-supported:** arbitrary mod abilities via a documented `AbilityAdapter` extension point and lightweight integration modules.
4. **Unsupported:** opaque custom AI and special attacks that cannot be safely detached or reattached. These must not appear as working exchanges.

A third-party dragon's custom attack is not automatically transplantable just because it exists in Java code. We should avoid brittle reflection hacks or copying opaque mob AI objects into incompatible entity classes. The ambition is **broad automatic compatibility wherever the underlying API makes it safe**, not an impossible guarantee of every ability from every mod.

## 5. Architecture target

| Component | Responsibility | Proof of correctness |
|---|---|---|
| Book & client UX | Selection hints, remappable self key, readable category picker | Correct first/second/self targets; no chat spam or accidental actions |
| Server session manager | Target UUIDs, selected category ID, TTL, permissions, dimensions | Stale/disconnected/despawned targets fail cleanly |
| Category catalog | Registry-based category discovery, membership, labels and support checks | All registered members of a category transfer together, including mod-added members |
| Atomic exchange engine | Snapshot both complete bundles, preflight all members, apply, rollback both | No subcomponent missing, duplicated or partially applied after induced failure |
| Ability registry | Portable ability IDs, category membership, full bundles, ownership and snapshots | Every ability and parameter in selected category moves and functions on the new owner |
| Behavior adapters | Actual mob AI or player-input behavior | Flight, explosion, later powers function on new species |
| Persistence & sync | Save/load, respawn policy, reload, tracking packets | Clients and dedicated server agree across lifecycle events |
| Third-party API | Adapter registration and compatibility diagnostics | Test mod can add a trait without modifying Freaky Friday core |
| Automated QA harness | Integration tests and multiplayer matrix | Regression tests exercise swaps on real entities |

**Key engineering constraint:** vanilla species-specific behavior may require a targeted hook or adaptation. A generic registry solves dispatch and pairing, **not** the problem of suppressing hardwired creeper/bat logic. We must validate those hooks against the actual NeoForge/Minecraft version rather than assume API names compile.

## 6. Audited R0.1 baseline — what is and is not done

**Direct inspection of `FreakyFriday_R0_1_Source.zip` and its source, November 20, 2026.** This is the truth source for the current checkpoint.

| Area | Status | Evidence / limitation |
|---|---|---|
| Project scaffolding | Source present | Gradle, mod metadata, item assets and recipe exist; full NeoForge build unverified |
| Book and two-target interaction | Source present, unverified in-game | Right-click handler, server-side selection, category command and cancel exist |
| Self-selection shortcut | Source present, unverified in-game | Ctrl+S configurable key mapping and periodic action-bar hint are coded |
| Attribute discovery | Source present, unverified in-game | Iterates registered attributes, including other mods' registrations |
| Numerical attribute swap | Source present, **does not implement new category rule** | R0.1 swaps one matching attribute at a time; must be refactored to whole-category bundles and tested |
| Pure Java exchange core | **Tested** | Re-ran tests: reciprocity, unsupported-target rejection, rollback and self-swap rejection pass |
| Ability extensions | Interface only | `AbilityAdapter` exists but is not connected to working ability transfers |
| Creeper <=> Bat | **Not implemented** | No working ownership/behavior exchange yet |
| Dedicated GUI | Not implemented | Current interaction uses clickable chat categories |
| World persistence and lifecycle | Not verified / incomplete | No complete special-ability storage, reconnect/respawn semantics or runtime validation |
| Buildable mod JAR | **Not verified** | Local environment has Java 21; 26.2 build targets Java 25 and NeoForge/Gradle dependencies |

**Do not call R0.1 a playable build.** Passing independent exchange-core unit tests does not prove that the Minecraft-facing code compiles or runs.

## 7. Ordered completion roadmap

Each gate produces a concrete checkpoint, automated/manual evidence, and a decision to proceed. No gate should be marked complete just because its code was written.

### Gate 0 — Reproducible NeoForge baseline [NEXT]

- Establish JDK 25 and the appropriate Gradle wrapper, resolve official NeoForge 26.2 dependencies.
- Compile the **unaltered R0.1** source; fix version/API mismatches without changing gameplay semantics.
- Start an integrated client and a dedicated server; verify the book can be crafted, held and selected.
- Deliver complete source, a versioned compiled JAR, build instructions and a test log.

**Exit:** A clean checkout builds and boots; book registers on both server types; no client-only code loads on the dedicated server.

### Gate 1 — Finish the book interaction loop

- Make Ctrl+S self-selection work at both stages; make the default key remappable.
- Show a compact first-use or held-book hint; avoid repeating hints every few seconds indefinitely.
- Replace verbose clickable chat lines with a **category-only** picker (no per-stat or per-ability drilldown) while preserving command fallback.
- Add clear target/category/result feedback and intuitive cancel/reselect behavior.
- Validate selections server-side; handle changing dimensions, logout, death, unloaded targets and timeout.

**Exit:** Two players and two mobs can each be selected correctly through every pairing with no chat commands required.

### Gate 2 — Complete numeric stat swaps

- Define authoritative category membership and dynamically include all compatible registered values and behavior components in each category.
- Validate **all** member values, functions, modifier semantics, health clamps and absent members before attempting one category exchange.
- Test all appropriate vanilla living entities and representative registered mod-added attributes.
- Make simultaneous attempts, interrupted swaps, and incompatible combinations safe; if any category member fails, roll back **both complete bundles**.

**Exit:** A multi-value numeric category trades every member in one action; swapping that same category twice restores both complete original bundles; atomic rejection/rollback tests pass.

### Gate 3 — Persist and synchronize swaps

- Store the full contents and ownership of exchanged categories in server-owned entity data, not only one attribute key.
- Load on chunk/entity reload and world/server restart without resetting or duplicating values.
- Define and test death/respawn policy for players; dimension travel and unload/despawn handling.
- Synchronize observable changes to tracking clients, including clients joining later.

**Exit:** A swapped mob and player retain correct state across lifecycle tests and dedicated-server reconnects.

### Gate 4 — Build the portable ability layer

- Introduce typed category bundles, member descriptors, category-wide snapshots and persistent ownership of every included ability.
- Provide acquire, release, apply, suppress-original, tick, controls, cleanup and rollback hooks.
- Separate player control from mob-AI control, and maintain server authority.
- Define empty-category bundles and category-wide rejection when any included ability cannot safely transfer.

**Exit:** A category containing multiple dummy abilities and parameters transfers atomically between unrelated entity classes and restores completely.

### Gate 5 — Prove Creeper <=> Bat works end-to-end

- Extract/adapt creeper proximity fuse/detonation as transferable behavior.
- Extract/adapt bat flight and navigation as transferable behavior.
- Suppress vanilla donor behavior reliably; preserve mob identities and appropriate animations.
- Exercise the complete special-ability bundles of bat <=> creeper, including unequal bundle sizes, reversal, save/load, multiplayer observation and explosion rules.

**Exit:** The specific bat/creeper exchange behaves correctly in an actual dedicated server, including after restarting.

### Gate 6 — Support player recipients and donors

- Implement usable player flight, player-triggered/automatic detonation semantics and input/network handling.
- Make player <=> mob and player <=> player **whole-category** swaps reciprocal, including multiple recipient abilities and correct lifecycle/ownership handling.
- Confirm game mode, anti-cheat, movement and damage interactions are handled intentionally.

**Exit:** Players can use and relinquish supported powers; other players observe the same effects; no ghost abilities survive.

### Gate 7 — Expand the supported vanilla ability catalog

- Inventory vanilla living entities and their meaningful transferable behaviors.
- Prioritize portable, legible abilities (e.g., teleportation, wall climbing, ranged attack, aquatic traits).
- Register each ability **once** in a reusable category-capable adapter; never add per-species pair rules or per-ability user selections.
- Publish a support matrix for fully portable, partially portable and deliberately unsupported behaviors.

**Exit:** The documented vanilla abilities are demonstrably transferable across diverse compatible species; unsupported abilities are visibly excluded.

### Gate 8 — Dynamic modded-mob compatibility

- Verify automatic discovery of attributes from at least two independently sourced third-party mods.
- Document the public ability-adapter interface, versioning and registration lifecycle.
- Write a standalone sample addon registering a custom portable ability.
- Add diagnostics explaining category membership and precisely which member prevents a safe full-category exchange.
- Consider optional data-driven adapters for simple capabilities, without pretending to transplant arbitrary unknown AI.

**Exit:** Sample modded attributes work without hardcoded mob IDs, and a custom third-party ability works through the public extension API.

### Gate 9 — Rules, safety and interoperability

- Add server configuration for permissions, player-consent policy, boss eligibility, cooldowns and high-impact explosions.
- Ensure exchanges respect world protections and intentional gameplay rules where possible.
- Audit death, teleport, duplication, despawn, cross-dimension boundaries, overlapping swaps and data upgrades.
- Profile entity tick costs and network traffic, especially many simultaneously altered mobs.

**Exit:** A multiplayer stress/regression session demonstrates no obvious abuse, stale state, or severe performance regression.

### Gate 10 — Final QA, documentation and v1.0 release

- Run unit, GameTest and dedicated-server acceptance matrix on the selected Minecraft/NeoForge version.
- Provide a full source archive, exactly matched versioned JAR, checksums, installation instructions, changelog and compatibility notes.
- Verify reproducible build from delivered source and a fresh test world.
- Mark every included feature proven, unsupported, or intentionally postponed.

**Exit:** All v1.0 acceptance checks pass and the delivered package rebuilds the delivered JAR from source.

## 8. Test matrix and release acceptance

| Test scenario | Expected result |
|---|---|
| Mob A <=> Mob B multi-value category | **Every** value and relevant function in that category exchanges; repeat restores all originals |
| Unequal category sizes (three vs one vs empty) | Each entity receives the other's full set; no selection or member is lost or duplicated |
| Player <=> mob, player <=> player | Correct target ownership; no hidden admin permission needed for normal survival use |
| Ctrl+S first and second target | Self is selected only while holding book; key can be rebound |
| Creeper <=> Bat Special Abilities category | **All** supported abilities, triggers and settings exchange; creeper flies without native detonation, bat detonates without native flight |
| Reversing category exchange | Entire original bundles restored, no residual goals/fuses or dropped members |
| Player receives special power | Correct controllable behavior and network-visible result |
| Mod-added numeric values | Automatically join a compatible category and trade together with its other members |
| Custom ability adapter | Third-party module registers transferable ability without core changes |
| One unsupported member in a category | Entire category exchange refused (no partial transfer), and neither target changes |
| Simultaneous swap attempts | No race-induced duplication, partial transfer or stale selection |
| Target disappears / world change | Server rejects or cancels cleanly; no orphaned swap |
| Save, quit, reboot, reconnect | Altered state persists correctly and displays consistently |
| Death and respawn | Behavior follows explicit documented policy, no duplicate powers |
| Integrated vs dedicated server | Identical behavior, no client classes loaded on server |
| Source rebuild | Clean source tree builds the shipped versioned JAR |

### Minimum playable milestone versus end goal

- **First playable alpha:** Gates 0–3. Usable book, dynamic **whole-category** numerical exchanges, basic persistence, and real server validation. No special-ability claims.
- **Mechanics-proof alpha:** Gates 4–6. Bat/Creeper exchange plus player control works. This is the first convincing demonstration of the core differentiator.
- **Public-ready v1.0:** Gates 7–10. Documented broad vanilla ability catalog, modded-attribute discovery, extension API, comprehensive tests and packaged release.

## 9. Decisions to lock before release

The user-defined core concept is settled. The following details are **proposed defaults, not yet approved design decisions**:

1. **Swapping health:** clamp current HP to new max (R0.1 behavior) versus preserving relative health percentage.
2. **Missing category members:** whether safe attachment of absent registered attributes is possible; unsupported members must cause category-wide rejection, never silent omission.
3. **Category membership for new traits:** how a newly discovered modded value/function declares or inherits a category. **Whole-category swapping is already fixed**, not an open decision.
4. **Player detonation:** proximity trigger conditions, targeting allies, visual fuse warning, survival implications and explosion-griefing rules.
5. **Consent and protections:** whether players must consent to exchanges or servers can configure direct PvP-style swapping.
6. **Duration:** permanent until reversed by another exchange (working assumption) versus temporary or configurable.
7. **Death and respawn:** whether swapped player abilities persist across death and how inventories/books behave.
8. **Bosses and compatibility:** any blacklist, whitelist, dimension or protected-entity constraints.
9. **Release scope:** supported special-ability catalog and honest exclusions, especially for third-party custom AI.

These decisions should not prevent progressing through the foundational gates, but must be resolved before final release acceptance.

## 10. Immediate next engineering sequence

1. Rebuild R0.1 against **actual Java 25 + NeoForge 26.2** dependencies; capture compiler errors and fix them.
2. Launch client and dedicated server, register the book, and verify first-target and self-target actions.
3. Establish repeatable GameTests for multi-value category exchanges, full rollback, and target lifecycle.
4. Add durable server-owned per-entity state and validate saves/reloads.
5. Design the complete Special Abilities category-bundle contract and test multiple transferable behaviors together.
6. Implement and prove **Creeper <=> Bat**, then test the same powers on players.
7. Only after that proof, expand vanilla ability coverage and third-party adapter support.

**Definition of progress:** Each checkpoint includes complete updated source, a buildable artifact when toolchain permits, a changelog, and passing tests relevant to the claimed features. The existing R0.1 one-attribute swapping behavior must be explicitly replaced; a category picker that still swaps only one value is not compliant. Don't equate source written with functionality verified.
