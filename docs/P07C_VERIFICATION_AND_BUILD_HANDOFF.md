# P07C verification / build handoff

Date: 2026-11-22
Branch: `feature/p07-occupied-body-health-ui`
Release version in source: `0.15.10-alpha` (Minecraft 26.2 and 26.3)

## What is genuinely verified

- P07B (`0.15.9-alpha`) passed all 9 CI jobs on BOTH Minecraft versions:
  https://github.com/gorgefinkle/mcstatswitchff/actions/runs/45016190885
- Real GameTests observed occupied Ocelot HP decrease from 10 to 8
  after a vanilla attack, then lethal damage caused return to the
  unchanged living original player body.
- Real fall profiles proved Chicken glide, Ocelot's native fall-damage
  immunity without glide, and ordinary Zombie fall damage on a passing
  prior test run. Player/player death ejects both controllers.
- P07C contains NO changes to production control, physics, camera,
  health, inventory, or AI Java classes versus successful P07B.
  The only changed `src/main/java` file is the registered GameTest
  `PlayerMobGameTests.java`, correcting staged fall-test timing.
  Other changes are test JSON, release metadata, docs and build tools.
- The Git-less source-package integrity test uses an explicitly
  **dummy** ZIP-format JAR. This proves packaging only, never gameplay.

## Why the P07C JAR is not yet released

GitHub Actions started failing jobs BEFORE assigning a runner:
`runner_id=0`, `steps=[]`, `BlobNotFound` for job logs, and jobs
ending in about two seconds. Example:
https://github.com/gorgefinkle/mcstatswitchff/actions/runs/45016797909

Two retries yielded the same pre-runner result. This is not a compiler
error, and cannot be fixed by changing Java. Common causes include
Actions usage/billing budgets or GitHub runner-provisioning problems.
The exact cause is recorded in the run's top-level annotation when
available; inspect GitHub Settings -> Billing and licensing -> Usage
and Budgets rather than guessing. Do not claim P07C passed a real
Minecraft GameTest or that P07B is a P07C JAR.

The local packaging environment has Java 21 only and no reachable
Gradle/NeoForge dependencies. Java 25 is required.

## Rebuild from this source ZIP (no Git checkout needed)

1. Extract the full source ZIP into a fresh folder.
2. Install Java 25 and make it available via `JAVA_HOME`.
3. Make Python 3 and Gradle's downloaded dependencies available. The
   bundled Gradle wrapper can download Gradle on a machine with Internet.
4. Windows: `scripts\\BUILD_WINDOWS.cmd 26.2` (or `26.3`).
   Linux: `bash scripts/BUILD_LINUX.sh 26.2` (or `26.3`).
5. The real Java 25/NeoForge build must finish successfully. The
   launchers then produce one versioned JAR and one complete nested
   source ZIP inside `build/packages/` with SHA-256 checksums.
6. Separately run real dedicated-server/GameTests and real connected
   client camera / inventory-portrait visual checks before approval.

When GitHub runners execute again, the existing dual-version CI
will perform the Java 25 build, dedicated smoke, GameTests, native
worker checks and artifact integrity gates.

## Do not overclaim

Headless GameTests cannot prove camera rotation, inventory model
rendering, or HUD placement on a connected Minecraft client.
Player->Mob possession still does not transfer the mob's native AI
into the vacated player body. P07C remains a development checkpoint,
not a complete mind-swap release.
