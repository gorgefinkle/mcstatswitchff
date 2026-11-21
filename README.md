# Freaky Friday — Stat Switcher

A NeoForge mod for reciprocal **whole-category** exchanges between living entities.
The selected category is the unit of exchange: the player does not select individual attributes or mob-pair exceptions.

## Intended gameplay
1. Select entity A with the **Freaky Friday** book (or use the remappable **Ctrl+S** shortcut to select yourself).
2. Choose an **entire category**.
3. Select a different entity B.
4. Transfer all category values, permanent modifiers, and ultimately all functions/abilities/behaviors **in both directions**, exactly and atomically, without changing identity or appearance.

**Implemented so far:** Numerical attributes, permanent modifiers and current vitality with preflight, exact read-back and rollback. Arbitrary asymmetric attributes are deliberately rejected rather than silently skipped. **Not yet implemented:** functional bat flight ↔ creeper detonation, other executable abilities, persistent behavior ownership, complete modded-attribute portability, or verified multiplayer gameplay.

## Build targets
- **Minecraft 26.2 / NeoForge 26.2.0.95** — primary target.
- **Minecraft 26.3 / NeoForge 26.3.0.115** — secondary target.
- **Java 25**, **Gradle 9.2.1**, **ModDevGradle 2.0.148**.
- One shared source tree, thin platform adapters under `src/platform/minecraft-*/java`.

## Continuous integration
GitHub Actions [**Freaky Friday builds**](../../actions/workflows/build.yml) provisions Java 25/Gradle 9.2.1, resolves NeoForge and ModDevGradle itself, caches dependencies, runs the core exchange harness, and attempts **separate 26.2 and 26.3 builds**. Successful jobs upload their version-specific JARs and reports; a compiled JAR is not the same as a passed in-game acceptance test.

To compile locally (Java 25 installed):

```powershell
.\gradlew.bat build -PtargetMinecraft=26.2 --no-daemon
```

To check core logic without downloading NeoForge (Java 25 installed):

```bash
sh scripts/test-core.sh
python3 scripts/test-version-profiles.py
```

## Source of truth and asset policy
The `main` branch contains the **production source, game resources, version profiles, extended historical regression tests, original roadmap and audits, Java and Gradle build configuration, official Gradle wrapper, and CI scripts**. The separate [`archive/r0.11-original`](../../tree/archive/r0.11-original) branch is a **70/70 Git-blob-hash-verified exact snapshot** of the original `FreakyFriday_R0_11_Full_Source.zip`. The original archive is preserved without rolling back the build fixes on `main`. GitHub Actions successfully builds both Minecraft 26.2 and 26.3; in-game acceptance remains outstanding.

Large third-party JDK installers, vanilla Minecraft JARs, NeoForge universal/userdev artifacts, Gradle distribution ZIPs, and per-machine dependency caches **do not belong in Git**. The Gradle wrapper, upstream resolution, GitHub Actions caches and build artifacts eliminate transferring those archives during ordinary development.

See `docs/GOALS_AND_STATUS_R0_11.md`, `docs/BUILD_TOOLCHAIN_R0_11.md`, and `AUDIT_R0_11.md` for history and the remaining testing gates.
