# R0.15.4 — Native AI boundary probes on real NeoForge servers

Run through `./gradlew runGameTestServer -PtargetMinecraft=26.2`
and `-PtargetMinecraft=26.3`. CI runs both and checks explicit
`FF-NATIVE-PROBE` log markers. Only GitHub Actions' disposable worlds
may run the adversarial world-write test.

## Required GameTests

- `native_ai_inventory` constructs real vanilla Chicken, Cow, Zombie,
  Villager, and Ender Dragon types, inventories available Goal/target Goal
  implementations and native method overrides, then surveys reachable
  *classfile references* for potential effects. This is a conservative
  static inventory, **not** a proof of which code executed or is safe.
  `NativeAiSurfaceAudit` truncates after 256 findings per inspected mob,
  so the displayed 257 includes a truncation marker and is not an exhaustive
  or species-comparable effect count.
- `host_world_effect_escape` builds a REAL detached Chicken-class host in a
  temporary NeoForge GameTest world, adds a deliberately adversarial Goal,
  and directly invokes that test-only Goal's `tick` method. The Goal uses
  the detached host's ordinary live-level reference to write a real block.
  The test **expects** and detects that block mutation, confirms that the
  host was never world-registered and that the original mobs were untouched,
  and restores the old block in `finally`. Passing this *negative* test
  means **we have proven a real isolation breach**, not an AI success.
  It does not tick Chicken's built-in donor AI.
- `native_goal_scheduler` attaches the same harmless counting Goal type
  to a real world-registered Chicken and an unregistered Chicken-class host.
  After 65 real server ticks it requires multiple Goal ticks on the registered
  Chicken and **zero** ticks on the detached host. This distinguishes actual
  Minecraft AI scheduling from a host object merely existing in memory.
  It does not transfer native minds or execute arbitrary donor behaviors.

Existing real-server `physical_world_mirror` also exercises 40 recipient
physical ticks, collisions, world block integrity, unregistered host
position mirroring, and lifecycle cleanup.

## Safety and next engineering gate

**Do not enable native donor AI on an unregistered host in production.**
An unregistered Mob still holds a real `ServerLevel`; a Java Goal,
Brain task, mob override, static reference, or third-party callback can
modify the world or shared state directly. Snapshotting the donor and
recipient is only a detector for selected fields, not an enforcement
boundary.

The passing boundary probes show that world registration is not isolation.
An actual safe generic swapping engine needs a trusted effect-interception
boundary or an explicitly supported isolated execution model with a
complete action capture/replay interface. Modded behaviors that bypass that
interface must fail closed. The current branch deliberately does **not**
execute native donor AI or implement a real mind swap.

Only count a test as verified after its actual GameTest log and CI run
are green for both Minecraft version targets. Compiling these classes is
not an adequate substitute.
