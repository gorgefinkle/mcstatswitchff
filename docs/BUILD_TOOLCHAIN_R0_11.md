# R0.11 — Build-toolchain handoff and verification

## Supplied dependencies accounted for

- Official MDK-26.2-ModDevGradle `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, and `gradle-wrapper.properties` are included in the original R0.11 source ZIP. GitHub generates equivalent Gradle 9.2.1 wrapper binaries via the bootstrap-wrapper workflow.
- ModDevGradle **2.0.148**, Gradle **9.2.1**, Java **25**.
- The supplied binary Gradle distribution and Java Debian installer are *not* copied into the Git repository.
- Minecraft 26.2 server bundler and NeoForge 26.3 universal JAR, supplied separately, were used for earlier API tests.

## Earlier offline build check

Gradle reached project configuration and failed locating `net.neoforged.moddev:2.0.148` because artifacts were absent locally and the assistant's container could not reach Maven repositories. That is not a successful build and not proof the mod compiles against NeoForge.

## GitHub builds

Github Actions provisions JDK 25 and Gradle 9.2.1, downloads ModDevGradle, NeoForge, and Minecraft dependencies automatically and caches them between runs. See `.github/workflows/build.yml`. The CI first runs `scripts/test-core.sh` and `scripts/test-version-profiles.py`, then attempts both versions. A successful CI package is still only a build candidate until client and dedicated-server smoke tests pass.

## Known unfinished work

Real integrated/dedicated server testing, complete asymmetrical attributes across species, persistent transferable behaviors, bat flight ↔ creeper detonation and general ability adapters remain unfinished.
