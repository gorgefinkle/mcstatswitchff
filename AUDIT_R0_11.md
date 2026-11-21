# R0.11 audit — official MDK wrapper and genuine Gradle dependency check

## New inputs (November 20, 2026)

- User-supplied official `MDK-26.2-ModDevGradle-main.zip`.
- User-supplied official `gradle-9.2.1-bin.zip`.
- Previously supplied Java 25 JDK, Minecraft 26.2 server bundler and NeoForge 26.3 universal JAR.

## Changes

1. Copied `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` and `gradle-wrapper.properties` from the official 26.2 MDK. Preserved Gradle 9.2.1 and ModDevGradle 2.0.148.
2. Raised mod version to `0.11.0-alpha` without changing gameplay semantics.
3. Updated the build doctor and helper scripts; added versioned handoff documentation and SHA-256 manifests.

## Original R0.11 tests

- `scripts/self_audit.py`: **PASS** locally, including 1,025 core assertions, 500 seeded randomized tests, 50 synthetic Minecraft integration checks, 34 portability checks, 13 book event checks, client input simulation and synthetic source compilation.
- `scripts/test-mojang-api.py`: **PASS**, 15 production Java files compiled against real Minecraft 26.2 server classes with simulated NeoForge interfaces.
- `scripts/test-mojang-attributes.py`: **PASS**, 25 actual Minecraft 26.2 attribute-instance assertions.
- `scripts/inspect-neoforge-api.py`: **PASS**, six NeoForge 26.3 universal declarations checked (not equivalent to a development build).
- `scripts/test-version-profiles.py`: **PASS**.
- Full NeoForge build/gameplay: **BLOCKED** in the offline environment; no playable JAR claimed.

## Current GitHub migration

The GitHub `build.yml` workflow executes the pure-Java and profile checks, then attempts real NeoForge 26.2 and 26.3 compilation on internet-connected GitHub runners. This historic R0.11 audit remains evidence of earlier local checks; GitHub CI results must be inspected separately.

## Unfinished

Full asymmetric category portability, actual executable abilities/AI ownership transfer, persistence and multiplayer in-game tests. Phase 1 remains open.
