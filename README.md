# Freaky Friday — P09 Native Mind Exchange

**Current source version:** `0.15.12-alpha`  
**Active development branch:** `feature/p09-universal-native-ai-host`  
**Targets:** Minecraft 26.2 and 26.3 / NeoForge / Java 25

## Current status: experimental, not a release build

The project goal is still one book, two living bodies: exchange complete mind/controller behavior while each physical body keeps its own identity, species, health, attributes, equipment, inventory, collision, movement physics, damage and native interactions. No entity-pair whitelist or hand-authored species behavior table is intended.

The current P09 gameplay path is a same-server experimental implementation:

- Mob↔Mob creates two unregistered, same-EntityType native Mob controller proxies in the current server process. Each proxy runs its native goals/target selector, navigation, controls and subclass AI hook; its decisions are projected onto the opposite original physical body. The physical bodies remain the registered entities.
- Player↔Mob combines the Mob native controller proxy with authenticated player-input routing to the occupied Mob body. Player↔Player has a separate reciprocal physical-control path.
- Ordinary book swaps do **not** launch a second Minecraft server JVM per session. The external worker server and mirrored-world code remain an isolated research/CI harness, not the normal book route.
- Original body selectors and controller ownership are retained for cancellation and restoration. Reciprocal attack routing and body damage are covered by real NeoForge GameTests.

## Critical limitation — native AI is not yet isolated

The Mob controller proxies are unregistered, but they still reference the **real ServerLevel**. Native vanilla or modded Goal, Brain, entity-hook or callback code can call world-mutating APIs directly. The bytecode effect survey is diagnostic only; it is not an interception boundary or sandbox. Unknown native behavior can therefore affect the live world instead of producing a clean action frame.

The current P09 proxy runtime is not the selected final controller-proxy architecture described in `docs/CONTROLLER_PROXY_RUNTIME_PLAN.md`. The complete, safe decision/effect boundary and a faithful continuous sensory model are still engineering blockers. This branch must not be advertised as universally compatible with arbitrary vanilla or third-party mobs, and it is **not ready for a normal gameplay world or release**. Use disposable test worlds only.

Do not confuse:
- successful compilation with successful mind exchange;
- a hidden proxy or copied Goal graph with isolated native AI;
- movement or one attack with complete mind transfer;
- a passing test fixture with verified live-client camera, inventory, GUI and multiplayer behavior.

## Current gameplay acceptance

The active acceptance contract is `docs/P09_UNIVERSAL_NATIVE_AI_SWAP_ACCEPTANCE.md`. It requires simultaneous two-way behavior, not just successful selection or entity creation.

The latest verified CI commit, `d74b5cc`, passed all 25 required NeoForge GameTests on both Minecraft 26.2 and 26.3. Coverage now includes simultaneous Zombie↔Chicken behavior, Villager Brain↔Zombie Goal behavior, reciprocal Mob↔Mob and Player↔Mob attacks, physical-body capability checks, and a recipient sensory snapshot/wire/mirror/cleanup test.

The GameTest structures are now validated by `scripts/test-structure-templates.py` before the Minecraft matrix starts. This catches malformed GZIP/NBT templates, invalid structure list tags, missing structure references, out-of-bounds block entries, and undersized arenas.

Those results validate the covered experimental cases only. Connected graphical clients are still needed to verify camera, mouse input, item/GUI forwarding and multiplayer observation. The native-worker backend is not yet wired into the book flow, and the current same-server proxy route remains unsafe for normal survival-world use.

## Build and verification

Both supported profiles use the same shared source and thin platform adapters:

```bash
sh scripts/test-core.sh
python3 scripts/test-structure-templates.py
python3 scripts/self_audit.py
./gradlew clean build -PtargetMinecraft=26.2 --no-daemon
./gradlew clean build -PtargetMinecraft=26.3 --no-daemon
```

CI also runs real NeoForge GameTests and normal dedicated-server startup checks for both targets. A green compile or server boot is not a release gate by itself; all required reciprocal behavior tests and the isolation work above must pass.

## Architecture and test references

- `docs/CONTROLLER_PROXY_RUNTIME_PLAN.md` — selected final architecture and tick/ownership contract.
- `docs/P09_UNIVERSAL_NATIVE_AI_SWAP_ACCEPTANCE.md` — non-negotiable cross-species, player and lifecycle acceptance.
- `docs/NATIVE_MIND_RUNTIME_ARCHITECTURE.md` — production runtime boundary and why current experiments are insufficient.
- `docs/R0_13_DYNAMIC_AI_CAPTURE.md` and `docs/R0_14_NATIVE_HOST_FEASIBILITY.md` — superseded Goal-copy and native-host experiments.
- `docs/R0_15_4_SELF_AUDIT.md` and `docs/R0_15_DETACHED_DUAL_INSTANCE_PROOF.md` — historical detached-host findings; diagnostic probes are not mind swaps.

Historical category-picker prototype: `main` (R0.12). Historical reflective whole-mind experiment: `feature/whole-mind-swap`. Neither is the active P09 source line.
