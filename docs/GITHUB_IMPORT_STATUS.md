# Freaky Friday — R0.11 repository migration status

Repository: `gorgefinkle/mcstatswitchff` (private)

## Verified in repository

- Production Java source and the generic category-exchange engine
- Version-specific NeoForge 26.2 and 26.3 adapters
- Mod metadata, book resources, recipes, and English localization
- Java 25 / Gradle 9.2.1 wrapper and version profiles
- Core Java regression tests and GitHub Actions build matrix
- CI artifacts for separate Minecraft versions, with dependency caches managed by Actions
- JAR integrity/metadata checks and SHA-256 manifests in the current workflow

## Historical archive reconciliation: OPEN

The original `FreakyFriday_R0_11_Full_Source.zip` is the historical source of truth,
but its contents are not available in this repository. The previous offline
R0.11 audit documents additional scripts, including:

- `scripts/self_audit.py`
- `scripts/test-mojang-api.py`
- `scripts/test-mojang-attributes.py`
- `scripts/inspect-neoforge-api.py`

These historical scripts are **not currently tracked in this repository**.
Do not mark their historical assertions as run by GitHub CI.
Do not substitute invented replacements and claim original parity.

To perform a source-by-source hash reconciliation, obtain the original ZIP
once and run from the repository root:

```bash
python3 scripts/audit-source-import.py FreakyFriday_R0_11_Full_Source.zip --output build/reports/r011-import-audit.json
```

The audit returns nonzero if original noncache files are missing or differ.
It records GitHub-only files separately. Inspect differences, import originals
that remain relevant, and rerun before declaring historical archive parity.
Keep the original ZIP out of Git; Git should contain extracted project sources,
needed tools and test assets, not opaque redundant archives.

## Build verification versus runtime verification

A successful GitHub Actions build proves its Gradle tasks passed, not
that multiplayer swaps, portability across modded species, or ability/AI
transfer work in a running game. Physical client and dedicated-server
testing remains an independent R0.12 acceptance requirement.

## No repeated large dependency transfers

The Gradle wrapper and version profiles are committed. GitHub Actions obtains
Java, Minecraft, NeoForge, ModDevGradle and caches from their upstream sources.
Do not commit per-run caches, JDK installers or third-party binary distributions.
