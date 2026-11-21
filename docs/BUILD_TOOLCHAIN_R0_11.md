# R0.11 — Build-toolchain handoff and verification

## Supplied dependencies accounted for

- Official MDK-26.2-ModDevGradle `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, and `gradle-wrapper.properties` are included in the source root.
- Official MDK Gradle plugin version: **net.neoforged.moddev 2.0.148**.
- Gradle version: **9.2.1**; separately supplied binary distribution is *not* copied into this source ZIP.
- Java: **JDK 25**, separately supplied Debian installer, not copied into this source ZIP.
- Minecraft 26.2 server bundler, separately supplied; used for actual Mojang-class tests.
- NeoForge 26.3.0.115 universal JAR, separately supplied; **not** a substitute for 26.2 development dependencies.

## Real build attempt on the assistant's offline workspace

The executable Gradle distribution and Java 25 were unpacked and launched successfully. `gradle --offline --no-daemon help -PtargetMinecraft=26.2` entered project configuration and failed locating plugin **net.neoforged.moddev:2.0.148** because the Gradle plugin metadata/artifacts were not present in local caches. DNS requests to Maven/Gradle repositories also failed. This is a dependency-resolution failure, not evidence of a successful compile and not proof that the source is free of NeoForge errors.

## Build on an internet-connected computer

1. Extract this source ZIP, preserving the project-root paths; install/configure **JDK 25** (`JAVA_HOME`).
2. Run **`scripts\BUILD_WINDOWS.cmd`** on Windows (or `scripts/BUILD_LINUX.sh` on Linux). These use the included official Gradle wrapper and should download missing Gradle/NeoForge dependencies on a connected machine.
3. Alternatively extract the supplied `gradle-9.2.1-bin.zip` and set `GRADLE_HOME` to its extracted `gradle-9.2.1` directory before running the script; this skips downloading the Gradle distribution, **not** the NeoForge artifacts.
4. Check `build/libs/` for `freakyfriday-mc26.2-neoforge-0.11.0-alpha.jar`, but treat it as a candidate until the integrated and dedicated-server smoke tests pass.
5. Send the **first actual Gradle compiler error/log** back for R0.12 correction if it fails. If it succeeds, send back the build log and JAR for further audit. No game content/ROMs are required.

To run the current local verifier if Gradle and Java 25 are installed: `python3 scripts/build_doctor.py --mc 26.2`, or pass `--offline` for the cached-dependency-only route. Set `GRADLE_BIN` to the installed Gradle executable or `GRADLE_HOME` to its parent installation directory when not using the wrapper.

## Known unfinished work

Real NeoForge 26.2 build, client startup, dedicated-server loading, book interaction, whole-category cross-species portability, and mob behavior swaps remain **unverified or incomplete**. Existing synthetic and Mojang tests must not be represented as a playable release. The 26.3 profile is an additional target, not yet an independently verified build.
