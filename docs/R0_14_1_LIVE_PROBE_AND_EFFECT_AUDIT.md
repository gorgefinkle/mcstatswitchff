# R0.14.1 — Native mind host: live body probe and AI-effect survey

## Engineering outcome

**This is a working physical execution probe and dynamic audit, NOT a
complete native-AI swap or an isolated donor execution environment.**

No per-mob matching table. The native MindHostEngine and capability
registry remain designed around action IDs, while each physical body
retains its true species, health, speed, loot and physical interactions.

### Safe real-game host probe

1. Start a disposable NeoForge 26.2 world with R0.14.1.
2. Hold the Freaky Friday book and select one mob (first click).
3. Type `/freakyfriday hostprobe`.
4. The command creates a short-lived **synthetic isolated decision source**,
   captures two intents (look at and path toward the initiating player),
   runs them through `MindHostEngine` and `MinecraftBodyPort` on the
   **original selected body**, then reports EXECUTED / BLOCKED.
5. It checks body UUID, entity type and health before/after. No additional
   entity is spawned, no second AI ticks, no mind identity is changed. The
   mob's ordinary native AI may override navigation on the next tick.
6. An accepted moveTo request proves only navigation accepted a path
   command, not that the mob completed the path.
7. `/freakyfriday cancel` clears the book selection.

This path is intentionally instrumented rather than simulated in a stub.
The synthetic stub harness verifies command selection and no deceptive
native mind success. Real body-control dispatch is compiled against
NeoForge 26.2/26.3, and requires an actual in-game run for behavioral proof.

### Read-only native AI effect audit

After selecting one living mob, issue `/freakyfriday inspect`.

The report inventories the live GoalSelector and targetSelector classes,
priorities and running status, Brain presence and native class overrides.
It additionally scans accessible JVM classfiles of those goals,
their superclasses, the Brain and the mob class for candidate method
references that may mutate entities, inventory, world blocks or spawn
entities; reflective/invokedynamic sites are flagged.

**The bytecode audit is not a sandbox or a security proof.** Classfile
references can be unreachable, virtual calls may dispatch elsewhere,
native class transformations can differ from resources, and custom
Java methods can have unrecognized effects. The audit only helps us
identify which interceptions are required. There is no automatic claim
that a clean scan makes AI safe to execute.

### What has been explicitly disabled in this branch

The R0.13 native-Brain and dynamic goal-transplant proof-of-concepts still
exist as reference source, but **the R0.14 book no longer invokes them**.
On second selection it reports "NO swap performed" until the real
native-mind sandbox and action output capture work. This avoids
mixing an unisolated heuristic transplant with a new control architecture.

### Remaining production gates

1. Build a shadow/host world view that supplies donor-native AI with
   read-only observations from the actual recipient.
2. Intercept all write paths before the donor code runs. A hidden
   entity still has live references into the real world: never tick it
   just because its rendering is suppressed.
3. Capture standard AI operations as semantic commands and bind them
   to the actual body's controls. Preserve native per-mind state, goals
   and Brain memories without cloning species Java methods into alien
   body classes.
4. Detect uninstrumented effects and reject the entire native-host
   attempt instead of allowing side effects from escaped code.
5. In-game prove cow body + zombie original decision loop: target
   pursuit, no ghost damage, no duplicate spawns, native cow speed,
   health, milking, loot, then reverse and restart.
6. Test a third-party mod creature with no special species adapter.
7. Only then connect player controllers and saved-world persistence.

## Test and scope labeling

- Pure Java: action registry, mind ledger, bounded frame semantics,
  safe failure, classfile footprint tests.
- Synthetic Minecraft: book selection, inspection route, safe probe
  routing and no fake whole-mind success.
- Real NeoForge: 26.2/26.3 binary compatibility, JAR contents, archive
  checksum.
- **Not passed:** in-game path behavior, isolated native AI execution,
  arbitrary cross-species mind swapping, multiplayer, persistence.
