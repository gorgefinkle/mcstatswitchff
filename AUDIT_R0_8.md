# Freaky Friday R0.8 — Minecraft 26.2 API and interaction audit

**Release status:** source-only correction checkpoint, version `0.8.0-alpha`. **Phase 1 remains OPEN.** No verified NeoForge JAR or in-game runtime test.

## Findings verified against a real Mojang 26.2 server JAR

The project was compiled using **Oracle JDK 25.0.5** and a **user-supplied Minecraft 26.2 server bundler**. We extracted the vanilla `server-26.2.jar` and bundled library dependencies; Java compilation linked to those *real Minecraft classes* while substituting **test stand-ins only for NeoForge APIs**. `ClientBindings` cannot be covered because the server jar has no Minecraft client classes.

This found two concrete compilation defects in R0.7 that synthetic mocks missed:

1. `ServerPlayer.serverLevel()` does not exist in the 26.2 Mojang-mapped API. The correct covariant accessor is `ServerPlayer.level()`, returning `ServerLevel`. Corrected resolution of the first target and server-tick retrieval.
2. `CreativeModeTabs.TOOLS_AND_UTILITIES` is `private` in Minecraft 26.2. Creative-tab membership now compares `BuildCreativeModeTabContentsEvent#getTabKey().identifier()` with `Identifier.withDefaultNamespace("tools_and_utilities")`, which links to the real Minecraft public API and matches the documented NeoForge event return type.

After corrections, **all 13 server-side production Java sources compiled against real Mojang 26.2 classes** with 12 deliberately limited NeoForge test shims. This is a stronger intermediate check, **not an official NeoForge compile**; event signatures, NeoForge registry behavior, and the physical client still require official dependencies.

## Duplicate right-click correction

R0.7 could call `SwapSessions.select` twice for one physical interaction (the NeoForge event, item callback fallback, or a second-hand callback), potentially selecting a first entity and immediately triggering another selection action. R0.8 routes both interaction entry points through `selectFromInteraction` and ignores only a repeated **same player + same target + same server tick**. The Ctrl+S self-selection command uses `select` directly, so it remains functional for both first and second targets. The last-click stamp is removed on logout.

The expanded `BookInteractionHarness` checks duplicate event/item callback handling, next-tick legitimate clicks, cancellation, offhand book selection, and ordinary unmodified interactions. Stamps deliberately do **not** suppress clicks on *different* targets, even within the same tick.

## Harness results

- `CategoryExchangeSelfTest`: **1,025 assertions**, including **500 seeded randomized exchange/reversal rounds** — passed.
- `CategoryDefinitionsSelfTest`: categorical and modded-attribute registry classification — passed.
- `CategorySchemaSelfTest`: **9 schema compatibility checks** — passed.
- `BridgeHarness`: **50** simulated Minecraft adapter/session checks — passed.
- `PortabilityHarness`: **34** simulated registration/portability checks — passed.
- `BookInteractionHarness`: **13** interaction checks (up from 10) — passed.
- `ClientKeyHarness`: Ctrl+S/remapping, book guard, reconnect hint — passed (synthetic client).
- All **14 production Java files** compile against synthetic Minecraft + NeoForge stubs — passed.
- **13 server-side production files compile against real Mojang 26.2** with NeoForge stand-ins and Java 25 — passed.
- JSON/TOML resources and version consistency — passed.

Commands:

```bash
python3 scripts/self_audit.py
python3 scripts/test-mojang-api.py /path/to/mojang_26.2.jar
python3 scripts/build_doctor.py
```

## Environment blockers / remaining acceptance gates

- Real NeoForge 26.2 `gradle build` **blocked**: no Gradle runtime/wrapper JAR or NeoForge/ModDevGradle dependency cache; outbound DNS failed when attempting to retrieve them. Recovered Java 25 is no longer a blocker.
- No integrated client launch, dedicated NeoForge server, controller/multiplayer test, or real registry/event execution.
- Some cross-species categories with unequal native attribute sets still **reject** the exchange rather than silently dropping contents. This is an open Phase 1 requirement.
- No executable mob-specific powers (creeper fuse/detonation, bat flight, etc.); no ability persistence yet. Future work; do not infer these are supported from the numerical category tests.
- The server-JAR-linked check covers only server-side Minecraft signatures. Client-specific methods, NeoForge event interfaces, and the full dependency linkage require the actual mod development runtime.

**Result:** R0.8 is a meaningful tested correction of the source and a new compile gate, **not** closure of Phase 1.
