# Minecraft version profiles — R0.10

Build the same shared source for Minecraft 26.2 (first release target) or 26.3 (future target).
Version matching is strict. Never install a 26.3 binary into a 26.2 server or vice versa.

- `gradle clean build -PtargetMinecraft=26.2` selects NeoForge 26.2.0.95; output has `mc26.2` in the JAR name.
- `gradle clean build -PtargetMinecraft=26.3` selects NeoForge 26.3.0.115; output has `mc26.3` in the JAR name.
- `gradle showTarget -PtargetMinecraft=26.2` previews resolution.
- `gradle showTarget -PtargetMinecraft=26.3` previews resolution.

The single `src/main/java` tree contains all generic category transaction and Minecraft-facing logic. Version-specific source is limited to `src/platform/minecraft-26.2/java` and `src/platform/minecraft-26.3/java` and must be expanded only when a real version-specific API difference is verified. We do not duplicate or fork the category engine. Matching version metadata is expanded at build time into `neoforge.mods.toml`.

**Verification status at R0.11:** Minecraft 26.2 passed real Mojang server-side library checks with NeoForge API stubs. The full 26.2 NeoForge build and game testing were blocked in the assistant's offline environment. Neither target is certified playable by this historical audit. GitHub Actions is configured to attempt both profiles from an internet-connected runner.

Do not bundle the user-provided vanilla Mojang JAR, NeoForge universal JAR or the Java 25 installer into release source/JAR distributions.
