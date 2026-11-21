# Freaky Friday — Whole Mind Swap (R0.13 development branch)

**New gameplay direction:** One book, two living bodies, **exchange their complete minds/controllers**. No stat-category picker in the final experience.

- **Mob ↔ Mob:** exchange entire AI behavior while both physical mob bodies remain unchanged.
- **Player ↔ Player:** both authenticated users control each other's physical avatars. Account UUIDs, passwords, permissions and chat identities do not change; bodies retain appearance, equipment, inventory and health.
- **Player ↔ Mob:** a player controls the mob while its mind controls the vacated human body.
- Reverse the same exchange to restore both original minds.

The new source of truth for the redesign is [docs/WHOLE_MIND_SWAP_R0_13.md](docs/WHOLE_MIND_SWAP_R0_13.md).

### Important status distinction

The book now uses **two-click, category-free selection**, with a **dynamic runtime capturer** for goal-driven NPCs. `GoalMindRuntime` scans both native goal selectors, captures every actual goal and its nested state, and attempts to rebind donor-owned references to the recipient as one complete graph. There is no cow/pig profile table, no mob whitelist, and no fallback that quietly skips unsupported goals. A separate same-class native-`Brain` path remains available.

**Status: experimental.** This proves a dynamic capture strategy and compiling integration, **not** that every mob's AI can be executed in an arbitrary body. Source-specific cast/method expectations, `Brain` activities, hardwired AI outside selectors, player input, and world persistence remain major unsolved gates. Test only in a disposable world; a field-level rejection is intentional when a goal cannot be safely rebound.

See [dynamic AI capture architecture and acceptance gates](docs/R0_13_DYNAMIC_AI_CAPTURE.md). The prior [cow/pig hardcoded prototype](docs/R0_13_CROSS_SPECIES_GOAL_PROOF.md) is **superseded**, not the current implementation.

## Body vs Mind — New R0.13.3 implementation direction

**A mind decides; its recipient body performs only physically supported actions.**
The cow keeps cow health, native speed, dimensions, loot and the ability to be
milked. A creeper mind controlling that cow may intend to detonate, but the cow
body does not possess the explosion mechanism, so that action is unavailable
**without invalidating the mind swap**. A creeper body retains its fuse and
gunpowder drops even when a peaceful mind controls it.

The development branch adds a pure whole-mind `BodyActionGate` (dynamic
namespaced intents and three result states) and a native
`MinecraftBodyPort` with movement, looking, jumping and creeper fuse
capability dispatched **on the receiving body**. Body invariance and
reversal are covered by `BodyActionGateSelfTest`.

**Still incomplete:** The new body-action bridge has not yet been connected
to the native Minecraft AI decision-tick pipeline; the older goal/Brain
transfer remains experimental, and true arbitrary-species runtime behavior
and player control are not yet proven. Do not treat this as a completed
gameplay mind swap. See [R0.13.3 body/mind contract](docs/BODY_MIND_CONTRACT_R0_13_3.md).

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
