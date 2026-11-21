# Freaky Friday — Whole Mind Swap (R0.13 development branch)

**New gameplay direction:** One book, two living bodies, **exchange their complete minds/controllers**. No stat-category picker in the final experience.

- **Mob ↔ Mob:** exchange entire AI behavior while both physical mob bodies remain unchanged.
- **Player ↔ Player:** both authenticated users control each other's physical avatars. Account UUIDs, passwords, permissions and chat identities do not change; bodies retain appearance, equipment, inventory and health.
- **Player ↔ Mob:** a player controls the mob while its mind controls the vacated human body.
- Reverse the same exchange to restore both original minds.

The new source of truth for the redesign is [docs/WHOLE_MIND_SWAP_R0_13.md](docs/WHOLE_MIND_SWAP_R0_13.md).

### Important status distinction

This branch currently contains a **tested, pure-Java body↔mind ownership ledger** and complete-mind exchange rules. It does **not** yet redirect actual Minecraft player input, rebind vanilla goal/Brain systems, suppress native AI, save the exchange across restarts, or implement the simplified two-click book flow. **Do not test this branch's JAR expecting functional mind swaps yet.**

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
