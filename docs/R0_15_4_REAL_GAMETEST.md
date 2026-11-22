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

## New one-command diagnostic workflow

Operators can now use the following directly on a **disposable** test world:

```text
/freakyfriday test pair ender_dragon chicken
/freakyfriday test pair chicken ender_dragon
/freakyfriday test suite
```

These commands require **no book, right-clicks, manual summoning or target
selection**. They construct the source, recipient and detached host using the
real registered EntityType factories, without adding any of the three to the
world. They verify the host class, type, unique ID, absent world registration,
position alignment and original body snapshots. The full suite includes eight
vanilla pairings including both Ender Dragon/Chicken directions; individual
`test pair` commands also accept namespaced third-party type IDs.

These commands do **not** execute either species' Goal, Brain, native AI, or
an actual mind swap. Constructors or positioning overrides can STILL write
to world state even if no entity is registered: there is no full sandbox.
Do not run them in worlds you care about.

The automated CI GameTests also dispatch commands through Minecraft's real
Brigadier tree using a mock server player (not handwaved return strings). A
separate full-command lifecycle GameTest covers the original book selection,
lock protection, inspect, hostprobe, hostprobe40, dualprobe, dualprobe40,
40 real recipient ticks, cancel and reselection. An explicit CI success
marker is required for each flow, and the test fails if a command parses but
its lifecycle state is incorrect. This remains a host/probe diagnostic,
not mind-control behavior validation.
