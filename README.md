# Freaky Friday — Stat Switcher

**Project:** NeoForge Minecraft mod for reciprocal whole-category stat and ability exchanges between players and mobs.

## Core gameplay
1. Select entity A with the **Freaky Friday** book (or **Ctrl+S**, remappable, to select yourself).
2. Choose an entire stat/behavior **category**.
3. Select entity B.
4. Exchange **all** values, attached functions, and transferable behaviors in that category atomically and reciprocally. Neither entity changes identity.

A swap must not be reported as successful unless the relevant capabilities really transfer. Modded registered attributes should be discovered dynamically; custom abilities require validated adapters.

## Targets
- Primary: Minecraft **26.2**, NeoForge **26.2.0.95**, JDK 25.
- Secondary compatibility: Minecraft **26.3**, NeoForge **26.3.0.115**.
- Future versions: separate adapters/build profiles sharing the same core engine.

## Status (R0.11)
Local synthetic tests and compilation with Minecraft/NeoForge server-side classes have passed. **A fully packaged, runnable NeoForge JAR and live game tests have not yet been verified.** Executable behavior swapping, persistence, and mismatched modded-attribute portability remain open.

## Building
The repository's `gradlew` and `gradle/wrapper/` must be committed alongside the source. GitHub Actions supplies **JDK 25**, Gradle **9.2.1**, ModDevGradle, NeoForge and dependencies automatically; no manual downloads or giant Gradle caches should be required.

Local Windows build (after source import):
```powershell
.\gradlew.bat build -PtargetMinecraft=26.2 --no-daemon
```

Select **Actions → Build Freaky Friday → Run workflow** to build supported versions. Successful jobs attach **version-specific JAR artifacts**. The workflow requires the complete project files at the repository root.

## Repository policy
Commit source, resources, Gradle wrapper, build scripts, test harnesses, goals, progress and audits. **Do not commit** downloaded JDKs, Minecraft/NeoForge redistributable JARs, Gradle distributions, `.gradle/` caches or private assets. GitHub Actions fetches them as needed and caches dependencies outside Git.

The historical source handoff is `FreakyFriday_R0_11_Full_Source.zip`. Once imported and verified, GitHub becomes authoritative. See `docs/REPOSITORY_SETUP.md`.
