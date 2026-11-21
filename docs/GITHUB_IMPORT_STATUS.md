# Freaky Friday — R0.11 source migration audit (November 21, 2026)

## Original archive recovered

Original user-provided `FreakyFriday_R0_11_Full_Source.zip` was inspected
directly: 70 files, 198,096 compressed bytes, 362,866 uncompressed bytes.

Its original `scripts/self_audit.py` was run against the complete extracted
archive and **passed** with local Java 21 for the synthetic tests:

- CategoryExchangeSelfTest: 1,025 assertions plus 500 seeded fuzz rounds
- BridgeHarness: 50 assertions
- PortabilityHarness: 34 checks
- BookInteractionHarness: 13 assertions
- ClientKeyHarness: passed
- Synthetic compilation of 16 production Java sources: passed
- Both offline Minecraft profile tests: passed
- JSON resources and R0.11 wiring assertions: passed

These checks are *not* a real NeoForge build or live game test. Existing GitHub
CI has separately produced successful builds for Minecraft 26.2 and 26.3.

## Direct GitHub imports verified byte-identical to the archive

- `scripts/self_audit.py` — original Git blob c9b2d8814be2cc943d53f458cd75df323313ee01
- `scripts/build_doctor.py` — original Git blob fd31b7f774db0d40bcbecc83507f8c9ff3819621
- `scripts/inspect-neoforge-api.py` — original Git blob 6b6c00d212d4603d327adf7249692873055df74b

## Remaining historical import

The original archive also includes historical R0.2–R0.10 audit reports,
roadmap markdown and DOCX, smoke-test procedures, Mojang API test scripts,
`scripts/java/MojangAttributeRuntimeTest.java`, and the full
`scripts/test-minecraft-stubs.py` synthetic harness. Not all have been
committed yet.

Do not claim full archive parity until this material is imported and audited.
Do not overwrite existing updated GitHub files blindly: the original
`ClientBindings.java`, book recipe, Gradle build, wrapper scripts and some
docs differ from the current CI-capable repository.

To import **only missing original files** from the archive without replacing
working GitHub files:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/IMPORT_MISSING_R0_11_WINDOWS.ps1 -SourceZip "C:\\path\\FreakyFriday_R0_11_Full_Source.zip"
```

Run the PowerShell launcher from the existing repository checkout; it
resolves that checkout automatically from its own location.

After import, the launcher runs the recovered `self_audit.py` and commits
and pushes only if it passes. Historical contents can be verified with:

```bash
python3 scripts/audit-source-import.py FreakyFriday_R0_11_Full_Source.zip
```

The strict comparison deliberately flags differences in already-updated
repository files: inspect and justify those, rather than forcing old versions
over the working CI code.

## Resource policy

GitHub tracks first-party source, runtime resources, version profiles,
audit scripts, roadmap and tests. Java 25, Gradle, NeoForge, Minecraft jars
and dependency caches are obtained through the wrapper/GitHub Actions.
Historical runtime logs and build output are excluded.

## R0.12 runtime gates remain open

Live client/server startup, multiplayer state, full asymmetric entity
attribute portability, and actual ability/AI ownership exchange remain
unverified until real game tests are performed.
