# Freaky Friday R0.14.2 — Timed real-body action probe

## What changed

The R0.14.1 one-off `hostprobe` action can report an accepted
`moveTo` command even if the mob does not physically move. This is
not enough evidence for the native mind/body control bridge.

R0.14.2 adds `LiveBodyProbe` and `/freakyfriday hostprobe40`:
- The selected real NPC body remains the original entity.
- No second donor mob, player input impersonation, Brain tick or
  foreign `Goal.tick()` is invoked.
- One synthetic `MindHostEngine` mind sends a LOOK_AT request each
  body tick, and bounded navigation requests every fifth tick.
- The NeoForge `EntityTickEvent.Post` path executes these requests on
  the actual recipient through `MinecraftBodyPort`.
- Forty real entity ticks are measured. The report records command
  execution, blocking, unavailability, decision failures, duplicate
  frames, final displacement in blocks, and body UUID/type/max-health
  preservation. Initial/final health are reported separately because
  ordinary world events can change health.
- The session is removed on completion, entity unload or server stop,
  with an eight-body concurrency limit.

## How to reproduce in-game

On a new disposable world with the correct NeoForge version:

1. Hold the Freaky Friday book and select a cow on reasonably clear ground.
2. Run `/freakyfriday inspect` and save the potential world-effect calls
   for the cow's actual AI.
3. Run `/freakyfriday hostprobe40`; do not select a second body.
4. Check the summary after 40 of that entity's server ticks. Note
   the physical displacement and command outcomes.
5. Repeat using a pig, a zombie, and one modded Mob. No species-specific
   pairing rules exist in the timed host probe.
6. Inspect native logs if outcomes show CAPTURE_FAILED or ACTION_FAILED.

**Interpretation:** EXECUTED means the body's native control API
accepted a request, not that the entity completed a path. Nonzero
displacement demonstrates in-game motion but is not automatically
caused by the probe, because ordinary AI is still running. A 0-block
result may be a blocked path, native goal override, terrain, or movement
limits. We must eventually disable only the recipient's original
decision-maker while preserving its physical tick and controllers.

## What this does NOT prove

This is not a complete Freaky Friday swap. It does not execute native
donor Goal/Brain Java code in a safe host. It does not suppress the
recipient's native decision-making, guarantee a trajectory, capture
subclass-specific effects, or persist a swapped controller. Those
are the next real-engine gates.

In R0.14.2 the book's second-target selection STILL does not invoke
the old unsafe reflective transplant. It returns a clear no-swap
message rather than faking completion.

## Evidence levels

- Core / stub regression suite: validates source interactions, action
  dispatch and selection, not physical Minecraft world behavior.
- NeoForge 26.2 and 26.3 build: validates compilation and binary packaging.
- Only a running Minecraft world can prove genuine motion or reveal
  command interference with the recipient's ordinary AI.

Next acceptance gate: a dedicated, controlled in-game test arena
that isolates the *recipient AI scheduler* without stopping physics,
and verifies the body's physical response to a portable decision.
After that, native donor AI write interception.
