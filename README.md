# Freaky Friday — Whole Mind Swap (R0.13 development branch)

**New gameplay direction:** One book, two living bodies, **exchange their complete minds/controllers**. No stat-category picker in the final experience.

- **Mob ↔ Mob:** exchange entire AI behavior while both physical mob bodies remain unchanged.
- **Player ↔ Player:** both authenticated users control each other's physical avatars. Account UUIDs, passwords, permissions and chat identities do not change; bodies retain appearance, equipment, inventory and health.
- **Player ↔ Mob:** a player controls the mob while its mind controls the vacated human body.
- Reverse the same exchange to restore both original minds.

The new source of truth for the redesign is [docs/WHOLE_MIND_SWAP_R0_13.md](docs/WHOLE_MIND_SWAP_R0_13.md).

### Important status distinction

This branch now has a **two-click, category-free book workflow** and two AI runtimes: a native `Brain` exchange for compatible same-species Brain-driven mobs, plus a **first cross-species cow ↔ pig whole-goal AI exchange**. The cow/pig route rebuilds every recognized donor goal against the other living body, including all temptation rules and priorities. It refuses unknown or mismatched goal sets rather than skipping them. Both runtime controllers are tracked through a server-side mind ledger. This is an **experimental in-game test candidate**, not a universal mind-swap release.

**Not yet supported:** arbitrary cross-species Goal/Brain rebinding, most goal-driven mobs, player↔player or player↔mob control, reliable save/reload/death lifecycle, multiplayer behavior validation. **Test only in a disposable world** until those gates pass. See [cow/pig cross-species test plan](docs/R0_13_CROSS_SPECIES_GOAL_PROOF.md) and [native Brain proof](docs/R0_13_NATIVE_BRAIN_PROOF.md).

The existing R0.12 numerical-category prototype remains on [main](../../tree/main) as a known buildable reference. The historical original is preserved on [archive/r0.11-original](../../tree/archive/r0.11-original).

## Build targets

- Minecraft 26.2 / NeoForge — primary target.
- Minecraft 26.3 / NeoForge — secondary target.
- Java 25, Gradle 9.2.1, one shared source with version-specific adapters.

Run the controller-assignment tests without downloading NeoForge:

```bash
sh scripts/test-core.sh
```

Build each target with GitHub Actions or:

```bash
./gradlew build -PtargetMinecraft=26.2 --no-daemon
```

GitHub Actions resolves third-party dependencies upstream and caches them. Do not commit large JDK installers, Minecraft/NeoForge downloads, or build caches.
