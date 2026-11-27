# Later Minecraft versions — deferred until 26.2 is complete

This document is retained as a historical note for future ports. It is **not** the active build plan.

## Current rule

Freaky Friday development is pinned exclusively to **Minecraft 26.2 / NeoForge 26.2.0.95 / Java 25**. The Gradle build, CI workflow, artifact verifier, and current source audit compile and test only 26.2.

The build intentionally rejects a different `-PtargetMinecraft` value. Do not add a second CI matrix entry or maintain another active platform source tree while the universal mind exchange is still being built.

## When later-version work may begin

Start a port only after Minecraft 26.2 passes all real acceptance gates:

- Creeper ↔ Zombie in both directions.
- Skeleton ↔ Zombie, including avoidance and panic behavior.
- Ender Dragon ↔ Chicken in both directions.
- Repeated exchanges and reversals without losing mind state.
- At least one vanilla ↔ modded and one modded ↔ modded pairing without mob-specific Freaky Friday code.
- Player participation only after its separate identity and input-bridge tests pass.

At that point, re-check the target version's mappings and NeoForge API changes, create the new platform hooks, add a separate build verification path, and run the full same acceptance suite on the new target. Do not assume the 26.2 injection points or mixin descriptors are valid on another version.

Any retained later-version profiles or platform files are dormant reference material only. They are not promised to compile, are not packaged, and are not currently tested.
