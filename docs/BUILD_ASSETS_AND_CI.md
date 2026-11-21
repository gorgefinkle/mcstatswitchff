# Reproducible builds — asset and tooling policy

**Canonical source checkpoint:** Freaky Friday R0.11 full-source ZIP (to be imported in full).
The repository is not considered fully bootstrapped until all original source project files are present.

## Commit these required project assets

- `src/main/`, `src/platform/`, `src/test/` (Java sources and game resource data/recipes)
- `versions/` (exact Minecraft/NeoForge target profiles)
- `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, and its properties
- `build.gradle`, `settings.gradle`, `gradle.properties`
- `scripts/` test and build harnesses; `docs/` roadmap/audit and handoff notes
- `.github/workflows/build.yml` — downloads a Java 25 toolchain and resolves NeoForge/ModDevGradle dependencies on GitHub Actions

## Do not commit third-party distributable binaries or transient caches

Do not add the downloaded Oracle Linux JDK installer, Gradle 9.2.1 distribution ZIP, Mojang server bundler, NeoForge universal/userdev binaries, or machine-specific Gradle dependency caches.
They are build *inputs resolved from their authoritative upstream repositories* rather than project's proprietary source assets. 
Committing them would increase repository size, risk redistribution/license issues, and still miss transitive dependencies.

GitHub Actions `actions/setup-java` provisions JDK 25; `gradle/actions/setup-gradle` caches Gradle dependencies between runs; Gradle wrapper selects its distribution; ModDevGradle resolves Minecraft and NeoForge.

Each workflow run uploads version-specific JAR artifacts and build reports when generated. Do not claim a successful or playable binary until CI actually passes and in-game smoke tests are performed.

## Bootstrapping

1. Import the R0.11 source ZIP contents at the repository root, preserving paths.
2. Commit/push the source; do not commit the ZIP itself or cached dependencies.
3. Open GitHub Actions > NeoForge build and verification and run the workflow.
4. If one target fails, read the compile logs and correct the adapter or build setup; do not mark that target validated.
