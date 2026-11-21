# R0.8 — required real-game smoke checks (NOT yet run)

1. Compile with real NeoForge 26.2/ModDevGradle using Java 25. Verify clean JAR artifact and mod loading.
2. Launch integrated client. Book appears in tools & utilities and in `/give` command; texture and recipe load.
3. Hold book in main hand. Right-click villager: selection menu shows, trading does not open.
4. Hold book in offhand. Right-click villager: one selection only, no doubled callbacks or trading UI.
5. With no book, villagers trade and animal feeding works normally.
6. Choose Defense category; right-click two vanilla mobs with different original supported attributes; swap complete category values and all permanent modifiers, leaving temporary equipment/potion effects unchanged.
7. Swap same pair back and compare full category snapshots.
8. Verify invalid/mismatched category rejection changes neither entity, with no false-success message.
9. Verify health + absorption and max-health permanent bonuses in Vitality category; no silent health clamping.
10. Hold book, use Ctrl+S as first target and as second target; remap shortcut, verify visible hint reflects new mapping.
11. Verify per-player sessions do not cross on a dedicated server with two clients.
12. Verify cancel, logout, dimension change, unload, death, and expiration cleanup; no duplicate post-login hint state.
13. Verify server save/restart for supported numerical changes; treat special ability persistence as a future gate.
14. Test modded living entity with registered numerical attributes; verify category classification and refusal of unsupported abilities.
15. Test on both dedicated NeoForge server and local integrated server; inspect logs for API, registry, event, desync or double-trigger errors.

All steps above are **pending real NeoForge runtime**, and are not counted as passing in R0.8.
