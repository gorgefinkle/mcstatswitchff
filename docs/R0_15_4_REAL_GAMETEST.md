# R0.15.4 real NeoForge GameTest gate

The test file `data/freakyfriday/test_instance/detached_cross_species.json`
runs on `runGameTestServer` on both supported Minecraft targets (26.2 and 26.3).
It uses REAL registered server-level vanilla entities, not stub world objects.

Checks: a Zombie donor and Cow recipient are real world-registered physical
mobs; a third actual Zombie-class host is created with the real EntityType
factory; host UUID is absent from the world entity map; host identity, type,
class, and location match expectations; original donor and Cow body remain
unchanged before and after host positioning. No ghost AI is ticked.

CI requires the explicit `FF-GAMETEST detached_cross_species PASS` marker so
a zero-tests or prematurely terminated game-test process cannot masquerade as
successful validation. The separate Python test covers injected faults using
the SAME production probe code. This GameTest only verifies vanilla runtime
construction, not arbitrary modded AI isolation or behavior transfer.

This first test has to pass actual server initialization on each target before
we can treat it as evidence. Do not declare it passed from compilation.
