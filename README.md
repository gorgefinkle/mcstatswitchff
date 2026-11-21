# Freaky Friday R0.11 — shared-source, multi-version preparation

**Current status: Phase 1 remains OPEN. No installable, tested NeoForge JAR is available.**

## End goal (unchanged)

A survival-compatible **Freaky Friday book**. Right-click a first living entity, choose **one category**, then right-click a second living entity. All the chosen category's values, permanent modifiers, behavior parameters, functions, abilities and state must exchange together, reciprocally and atomically. Both entities retain their original identity. Default configurable **Ctrl+S** selects yourself as first or second entity, with a contextual hint. Modded attributes should be discovered automatically; foreign executable AI needs supported behavior adapters, not fictional compatibility.

Present source implements **numerical category exchanges** and book workflow only. Full unequal-schema portability, true transferable flight/detonation, persistence and actual in-game confirmation remain incomplete.

## R0.11 changes

- A **single shared source tree** can now be compiled through explicit `26.2` or `26.3` Gradle build profiles. The default remains 26.2; no silent automatic migration.
- Separate version-specific `PlatformHooks` implementations now isolate the creative-tab integration. Future verified API differences belong there, not in the shared transaction logic.
- Build outputs are version-stamped (`freakyfriday-mc26.2-neoforge-0.11.0-alpha.jar`, `freakyfriday-mc26.3-neoforge-0.11.0-alpha.jar` *if those builds succeed*).
- `META-INF/neoforge.mods.toml` is templated at **build time** so each output declares an exact matching Minecraft version and selected NeoForge dependency, not stale `26.2` metadata in a `26.3` build.
- Added offline profile + source-isolation verification, and `scripts/inspect-neoforge-api.py` for the uploaded NeoForge 26.3 universal JAR.
- `scripts/build_doctor.py --mc 26.2` (or `--mc 26.3`) now selects the intended build profile.
- Existing numerical exchange engine and game-flow rules were preserved.

## Run local tests

Java 25 is needed for the actual Minecraft 26.2 API tests. The **JDK and game JARs are supplied in the separate development-assets ZIP**, not in this source package.

```sh
export JAVA_HOME=/path/to/java25-jdk
export PATH="$JAVA_HOME/bin:$PATH"
python3 scripts/self_audit.py
python3 scripts/test-mojang-api.py /path/to/mojang_26.2.jar
python3 scripts/test-mojang-attributes.py /path/to/mojang_26.2.jar
python3 scripts/inspect-neoforge-api.py /path/to/neoforge-26.3.0.115-universal.jar
python3 scripts/build_doctor.py --mc 26.2
```

## Build profiles (once full ModDevGradle and NeoForge artifacts are available)

```sh
gradle clean build -PtargetMinecraft=26.2
gradle clean build -PtargetMinecraft=26.3
```

See `docs/MULTIVERSION_BUILD.md` and `docs/GOALS_AND_STATUS_R0_10.md` for distinctions between checked API signatures, real-class tests, build verification and gameplay acceptance. Both build profiles remain **unverified** with the real NeoForge dependency graph.

## Next gate

Get the actual NeoForge 26.2 development Gradle toolchain, perform a real build and integrated/dedicated test, then fix API and gameplay issues. Don't mark Phase 1 complete merely because local mocks pass. 26.3 is a second target and needs its own matching client/server verification. No special-ability functionality should be claimed until the transfer system is implemented.


## R0.11 build-toolchain update

The source ZIP includes the official NeoForge 26.2 MDK Gradle wrapper. See `docs/BUILD_TOOLCHAIN_R0_11.md` for Windows and Linux build instructions, the actual offline Gradle failure, and required next checks. Use `scripts/build_doctor.py --mc 26.2 --offline` to check cached dependencies. The real NeoForge build has not yet passed.
