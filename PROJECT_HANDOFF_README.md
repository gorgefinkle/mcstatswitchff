# R0.10 handoff

This is the latest Freaky Friday full-source checkpoint, with original goals, historical audits and updated R0.10 status.
- `README.md`: quick start, test steps and current limitations.
- `docs/GOALS_AND_STATUS_R0_10.md`: current design and completion gate.
- `docs/MULTIVERSION_BUILD.md`: 26.2/26.3 profiles; one source, independently built JARs.
- `AUDIT_R0_10.md`: local verification and remaining blockers.
- `versions/`: target-specific metadata.
- `src/main/`: shared source and assets.
- `src/platform/minecraft-26.2/`, `src/platform/minecraft-26.3/`: thin platform hooks.

**Not a certified playable build.** External JDK, Mojang 26.2 server JAR, NeoForge 26.3 universal JAR are in the separate R0.9 Development Assets ZIP. Gradle/NeoForge development graph and fully tested JARs are still missing. Never redistribute proprietary development assets inside a public release.


## New R0.11 status

Official MDK Gradle wrapper added, Java 25 and Gradle 9.2.1 validated, real Gradle plugin-resolution attempt recorded. NeoForge dependencies still unavailable for a full compile. See `docs/GOALS_AND_STATUS_R0_11.md` and `docs/BUILD_TOOLCHAIN_R0_11.md`.