# Freaky Friday — Whole Mind Swap (R0.13 development branch)

**Gameplay direction:** one book, two living bodies, exchange their complete minds/controllers. The goal is not a stat-category picker.

- **Mob ↔ Mob:** exchange minds while the physical mob bodies remain unchanged.
- **Player ↔ Player:** each authenticated user controls the other's physical avatar. Account UUIDs, permissions, chat identity, and credentials remain attached to the account.
- **Player ↔ Mob:** a player controls the mob while its mind controls the vacated human body.
- Reverse the exchange to restore the original assignments.

The binding product requirements are in [docs/UNIVERSAL_MIND_EXCHANGE_ARCHITECTURE.md](docs/UNIVERSAL_MIND_EXCHANGE_ARCHITECTURE.md). The current technical investigation is [docs/DYNAMIC_AI_CAPTURE_STUDY.md](docs/DYNAMIC_AI_CAPTURE_STUDY.md).

## Current engineering status

The R0.13 native-Brain and cloned-Goal experiments are not a universal solution. They are being replaced by a body-independent mind record, passive observation of native AI decisions, and a generic bridge to the current body's capabilities.

**Do not treat the existing R0.13 JAR as a universal mind-swap test build.** It does not meet the cross-species or player-to-NPC acceptance criteria. A successful synthetic test or compilation is not proof of live AI exchange.

Every living mob pairing must be accepted, including vanilla-to-modded and modded-to-modded pairs. If a recipient body cannot express an intent, the exchange still succeeds and that intent remains with the mind for a body that can express it.

## Active development target — Minecraft 26.2 only

- Minecraft **26.2**
- NeoForge **26.2.0.95**
- Java **25**
- Gradle wrapper / ModDevGradle build

All implementation, mappings, runtime hooks, build verification, and live testing are focused on 26.2. The Gradle build is pinned to 26.2 and rejects any request for another Minecraft target. The dormant 26.3 source/profile files are not part of the active build or CI.

Later Minecraft versions will be ported only after the 26.2 mind-exchange implementation passes its real acceptance tests, including cross-species, repeated reverse exchanges, and modded mobs.

Run the pure Java regression checks:

```bash
sh scripts/test-core.sh
```

Build the active target:

```bash
./gradlew clean build --no-daemon
```

The completed 26.2 workflow uploads the version-specific JAR and build reports. A passing build still needs live in-game testing before it can be called playable or complete.
