# Freaky Friday R0.7 — source/API/interaction audit

**Baseline:** R0.6. **Target:** NeoForge 26.2, Java 25. **Phase:** 1, still **OPEN**. **Build:** source-only; no verified NeoForge JAR.

## Failure mode discovered and corrected

R0.6 relied on `Item#interactLivingEntity` for book selection. The item callback does not have guaranteed priority over a target's native interaction. For example, villagers can handle trading interactions and animals can handle breeding/feeding before the book's fallback runs. The result is that the book may be ineffective on precisely the living entities that the player wants to select.

`BookInteractions.onInteract(PlayerInteractEvent.EntityInteract)` now checks for the book in either hand, routes the target to the **existing** `SwapSessions.select` server-side entry point, cancels the normal interaction on both sides, and returns `InteractionResult.SUCCESS`. It leaves nonliving targets and all interactions without the book alone. The original `FreakyFridayBookItem` handler is retained as a fallback for integrations that invoke the item callback directly; normal NeoForge event cancellation should prevent that callback from firing twice.

This is **generic entity interaction routing**, not a species whitelist, extra stat category, or new per-pair implementation. The unchanged category transaction still exchanges all supported values/modifiers together.

## Toolchain discovery and actual tests

The user's previously uploaded Java 25 Debian installer was located in the connected Library and extracted in the working environment. Verified `java -version` reports **25.0.5.0.2**. It is **not** included in this package.

With that JDK selected, these local tests passed:

- `CategoryExchangeSelfTest`: 1,025 assertions, including 500 seeded randomized swap/reversal rounds.
- `CategoryDefinitionsSelfTest` and `CategorySchemaSelfTest`: passed (the schema harness contains nine reported checks).
- `BridgeHarness`: 50 Minecraft-adapter assertions.
- `PortabilityHarness`: 34 simulated entity registration/cross-species checks.
- **New** `BookInteractionHarness`: 10 assertions covering the event path, complete Defense exchange, book in offhand, no-book interaction preservation, nonliving targets and client input consumption.
- `ClientKeyHarness`: default Ctrl+S, remapped key label, no-book guard, reconnect hint.
- Full source compile: all **14 production Java source files** compile with the **actual Java 25 compiler against synthetic Minecraft/NeoForge test APIs**.
- JSON and TOML metadata validation passed.

These are **not game/runtime tests**. All mock API classes are clearly identified in `scripts/test-minecraft-stubs.py`.

`python3 scripts/build_doctor.py` with Java 25 reached the next blocker: **no Gradle executable or complete wrapper** in the working environment. Network name resolution is unavailable, and no NeoForge/ModDevGradle dependency cache is present. Consequently no genuine NeoForge compile, JAR, client start, dedicated server start or multiplayer test was possible.

## Relevant external API evidence

The official NeoForge 26.2 ModDevGradle template specifies Java 25, `net.neoforged.moddev` 2.0.148 and a Gradle build: https://github.com/NeoForgeMDKs/MDK-26.2-ModDevGradle/blob/main/build.gradle

NeoForge's documented interaction pipeline places `PlayerInteractEvent.EntityInteract` before target-specific `Entity#interact` and `Item#interactLivingEntity`; cancellation returns the chosen result: https://github.com/neoforged/Documentation/blob/main/docs/items/interactions.md

The version-specific `PlayerInteractEvent` method signatures remain **unverified against actual NeoForge 26.2 binaries**. Older and newer Javadocs support the intended event contract, but official binary compilation is the acceptance gate.

## Open Phase 1 gates

1. Build against **real** NeoForge 26.2/ModDevGradle artifacts, correcting verified API differences.
2. Launch both the integrated client and a dedicated server; test the book against villager, animal, player, and modded living entity; verify other interactions work without the book.
3. Verify category-wide exchanges and reverse trades with actual attribute instances, current health and permanent/temporary modifiers.
4. Implement and verify full unequal-schema numerical category portability where genuinely possible. The mod still rejects incompatible category contents rather than simulating a successful transfer.
5. Run two-player input, session, and reconnect regression tests with a live client/server.

**No claims:** no playable JAR; no executable flight, detonation, player special powers or arbitrary modded ability transfer; no persistent custom category ownership yet. This is deliberately a focused correction checkpoint.