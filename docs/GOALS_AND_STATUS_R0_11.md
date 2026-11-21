# Freaky Friday — R0.11 Goals and Progress

**End goal:** The Freaky Friday book selects entity A, presents a **whole-category** choice, then swaps that complete category (all values, supporting functions and behaviors) with entity B. Default remappable **Ctrl+S** selects the player. Transfers must be reciprocal, durable, and work on players, vanilla mobs, and discoverable modded attributes/abilities where truly supported. First release Minecraft/NeoForge **26.2**, then 26.3 and other practical versions from one shared codebase.

## Verified locally

- Shared category exchange engine including preflight, rollback, and permanent modifiers (synthetic tests and actual Minecraft 26.2 attribute-library tests).
- Book selection state, categories, Ctrl+S path and interactions against synthetic integration harnesses; not yet in-game validated.
- Version profile separation for 26.2 and 26.3; real 26.2 Minecraft server API compilation against NeoForge stand-ins.
- **R0.11:** MDK-supplied Gradle wrapper is included in source; Java 25 and Gradle 9.2.1 run in the workspace. The actual Gradle build reaches plugin resolution, where it fails due to missing/unreachable ModDevGradle artifacts.

## Not done / not established

- Genuine compile with NeoForge development dependencies, playable JAR, dedicated server and client gameplay tests.
- Support for arbitrary unequal attributes across species, preserving behavior when no compatible attribute exists.
- Executable mob abilities such as flight, proximity detonation, AI ownership/removal, full-category functions, persistence of custom swapped behaviors.
- Independent successful compilation and runtime validation of the Minecraft 26.3 profile.

## Next gate

Run the included Gradle wrapper with internet access and JDK 25 to resolve the ModDevGradle / NeoForge graph, then correct actual compiler errors before moving to the in-game smoke test. Do not close Phase 1 until verified against real NeoForge.
