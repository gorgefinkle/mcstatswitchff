# Freaky Friday — R0.11 GitHub migration acceptance report
Date: 2026-11-21

## Status: original archive complete; working branch builds verified

The user-provided `FreakyFriday_R0_11_Full_Source.zip` contains **70 files**.
GitHub's `archive/r0.11-original` branch is an exact file-for-file
snapshot, including its original DOCX, sources, reports and test programs.
All 70 Git blob SHA-1 hashes were compared with the original archive:
**70 matched, 0 missing, 0 extra**.

- Archive branch: `archive/r0.11-original`
- Archive commit: `5597fc267edbf4125a5351426c5eb279bd47f68c`
- Archive root tree: `d608d4e2460b9f140d538067f03b44cd3f28b622`
- Original ZIP: 198,096 compressed bytes, 362,866 uncompressed bytes

The **main** branch is authoritative for active R0.12 development, and does
not replace its working build/compatibility adjustments with older originals.
Historical source and resources are accessible directly by switching to the
archive branch. The main branch contains the original roadmap (Markdown and
DOCX), R0.2–R0.10 audits, original test programs (with needed compatibility
updates), Mojang API tests, and smoke-test procedures.

## Differences on main versus the immutable original

The original archive was compared against main. Before the two required
test-harness compatibility changes, the comparison found 58 identical files,
11 files intentionally updated by GitHub migration/build repairs, and
one original disposable `logs/latest.log` not kept on main.

Updated files include `.gitignore`, the current README/build records,
`build.gradle`, Gradle wrapper files, `ClientBindings.java`, and the
book recipe. The original versions of every such file remain in the archive
branch. The main-branch `scripts/test-minecraft-stubs.py` was subsequently
updated to provide the GUI/HUD API already used by production code, and
`scripts/self_audit.py` was updated to check `ClientKeyFactory` for the
Ctrl+S modifier rather than looking for it in `ClientBindings`. These
are compatibility changes to the test fixtures and static assertions, not
changes to production gameplay code.

The strict `scripts/audit-source-import.py` comparator correctly reports
the intentional main-branch differences; it is **not** expected to report
literal archive parity for main. For exact historical byte parity, compare
the user ZIP against `archive/r0.11-original`.

## Verified GitHub Actions acceptance

Successful run: https://github.com/gorgefinkle/mcstatswitchff/actions/runs/44887362310

- Pure-Java core regression suite: PASS
- Original synthetic Minecraft bridge/portability/book/client-key harness:
  PASS (17 production Java sources compiled against synthetic API)
- JSON/TOML and static-wiring audits: PASS
- Version profiles for 26.2 and 26.3: PASS
- Real NeoForge Gradle build for Minecraft 26.2: PASS
- Real NeoForge Gradle build for Minecraft 26.3: PASS
- JAR integrity, version metadata, required entries, SHA-256 reports: PASS
- Separate GitHub build artifacts uploaded for both targets

GitHub Actions automatically downloads Java 25, Gradle, ModDevGradle,
Minecraft and NeoForge components. Gradle dependency caches are managed by
Actions; **none of these large downloaded caches or installers need to be
committed or exchanged by chat**. The Gradle wrapper scripts/JAR and
source/resources stay under version control.

## Still not verified / R0.12 acceptance work

A successfully compiled mod is **not** an in-game acceptance pass. Live
client and dedicated-server startup, multiplayer selection/swaps, arbitrary
asymmetric category portability, persistence, and transferable executable
abilities/AI remain to be implemented or physically verified.

The historical Mojang 26.2 API test requires a vanilla server bundler as
an explicit local argument; this optional test is not claimed as executed
by the latest GitHub workflow. The runtime test and original source scripts
are now present in GitHub.

## Build commands

```bash
sh scripts/test-core.sh
python3 scripts/self_audit.py
bash ./gradlew build -PtargetMinecraft=26.2 --no-daemon
bash ./gradlew build -PtargetMinecraft=26.3 --no-daemon
```

The `main` branch is the buildable active project. The archive branch is the
literal original historical snapshot, not an alternative development fork.
