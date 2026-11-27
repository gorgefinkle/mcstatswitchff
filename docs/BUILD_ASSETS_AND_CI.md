# Reproducible builds — asset and tooling policy

**Active target:** Minecraft 26.2 / NeoForge 26.2.0.95 / Java 25.

## Commit these required project assets

- `src/main/`, `src/platform/minecraft-26.2/`, and `src/test/` (Java sources and game resource data/recipes)
- `versions/minecraft-26.2.properties` (the active Minecraft/NeoForge profile)
- `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, and its properties
- `build.gradle`, `settings.gradle`, and `gradle.properties`
- `scripts/` test/build harnesses and `docs/` roadmap/audit/handoff notes
- `.github/workflows/build.yml` — the single-target Minecraft 26.2 build and verification workflow

Later-version source/profile files may remain as dormant references, but must not be included in the active build, CI matrix, or artifact verification until 26.2 is complete.

## Do not commit third-party distributable binaries or transient caches

Do not add downloaded JDK installers, Gradle distribution ZIPs, Mojang server bundles, NeoForge universal/userdev binaries, or machine-specific Gradle dependency caches. They are build inputs resolved from authoritative upstream repositories, not project source assets.

GitHub Actions provisions JDK 25 and resolves Gradle/NeoForge dependencies upstream. The Gradle wrapper selects the Gradle distribution; ModDevGradle resolves Minecraft and NeoForge inputs.

The active workflow uploads only the Minecraft 26.2 JAR and build reports. Do not claim a successful or playable binary until CI actually passes and live in-game smoke tests are performed.

## Build procedure

```bash
./gradlew clean build --no-daemon
```

The build is pinned to 26.2. Passing `-PtargetMinecraft=26.3` (or any value other than 26.2) is deliberately rejected.

Do not add a later-version build job until the 26.2 implementation has passed its live universal mind-exchange acceptance suite.
