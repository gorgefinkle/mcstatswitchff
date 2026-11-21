# R0.11 audit — official MDK wrapper and genuine Gradle dependency check

## New inputs (November 20, 2026)

- User-supplied official `MDK-26.2-ModDevGradle-main.zip`.
- User-supplied official `gradle-9.2.1-bin.zip`.
- Previously supplied Java 25 JDK, Minecraft 26.2 server bundler and NeoForge 26.3 universal JAR.

## Changes

1. Copied `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` and `gradle-wrapper.properties` directly from the official 26.2 MDK. Preserved version `9.2.1` and ModDevGradle `2.0.148` from that official template.
2. Raised mod source version to `0.11.0-alpha` without changing existing gameplay semantics.
3. Updated the build doctor to use `GRADLE_BIN`, `GRADLE_HOME`, official wrapper or an installed Gradle; added an `--offline` flag and accurate BLOCKED vs FAIL diagnostic for missing plugins.
4. Added `scripts/BUILD_WINDOWS.cmd`, `scripts/BUILD_LINUX.sh`, build-toolchain instructions, current status and a clean SHA-256 manifest.

## Actually attempted build

The downloaded **Java 25** compiler and **Gradle 9.2.1** binary both launch. This command was actually executed:

`JAVA_HOME=<Java 25> GRADLE_HOME=<Gradle 9.2.1> python3 scripts/build_doctor.py --mc 26.2 --offline`

Gradle reached **project configuration**, then reported:

`Plugin [id: 'net.neoforged.moddev', version: '2.0.148'] was not found`.

The missing plugin's metadata/artifacts and its transitive dependencies are not in the available Gradle cache. The container cannot DNS-resolve `plugins.gradle.org` or `maven.neoforged.net`. **This is a real attempt that was blocked before Java compilation, not a compile success.** An internet-connected build can populate the dependencies.

## Tests run against R0.11

- `scripts/self_audit.py`: **PASS**, including 1,025 core assertions, 500 seeded randomized tests, 50 synthetic Minecraft integration checks, 34 cross-species portability checks, 13 book event checks, client input simulation, and synthetic source compile.
- `scripts/test-mojang-api.py <user Minecraft 26.2 bundler>`: **PASS** for 15 production Java files compiled against actual server classes, with NeoForge interfaces stubbed.
- `scripts/test-mojang-attributes.py <user Minecraft 26.2 bundler>`: **PASS**, 25 live attribute-instance assertions with the real Minecraft library.
- `scripts/inspect-neoforge-api.py <user NeoForge 26.3 universal jar>`: **PASS**, six declarations inspected. This does not prove 26.2 API compatibility or either version's real mod build.
- `scripts/test-version-profiles.py`: **PASS**, still yields distinct 26.2 and 26.3 metadata/filenames.
- Windows build helper: **not executable here**; batch script manually audited, and fixed errorlevel expansion using delayed expansion.
- Real NeoForge build/client/dedicated server: **BLOCKED / NOT TESTED**.

## Open requirements

- Resolve full ModDevGradle/NeoForge 26.2 dependencies via internet-connected Gradle and run a real build.
- Fix any compiler errors revealed by the real API, launch integrated and dedicated-server smoke tests, and verify book and keybinding behavior.
- Full asymmetric category portability, abilities, persistence and version-26.3 support remain incomplete/unverified.

This checkpoint is **source and toolchain only**. No JAR is included or claimed playable.
