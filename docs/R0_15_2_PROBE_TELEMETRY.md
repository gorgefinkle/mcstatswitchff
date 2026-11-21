# R0.15.2 — Dual-instance host diagnostic coverage

This checkpoint adds correlated **server latest.log** instrumentation to
R0.15.1's explicitly invoked experimental probes. Every line is prefixed
`[FF-PROBE]` and contains a stable `trace=<UUID>` identifier.

## What is logged

- **Book selections:** source and receiving body EntityTypes, class names,
  UUIDs and dimension, plus in-game diagnostic messages.
- **`/freakyfriday dualprobe`:** constructor begin/result, exact class
  and type parity, unique host UUID, world-registration status, original
  body health/identity, goal counts, target selector counts, Brain status,
  pre/post-position coordinates, squared alignment error, final summary.
  Constructor and positioning exceptions include stack traces.
- **`/freakyfriday dualprobe40`:** separate trace ID for each run, full
  source/host/body identities and initial values, one line for each of up
  to 40 recipient ticks, position before and after mirroring, game time,
  deviation, goal counts, health, world registration, and repositioning
  status. Final summary is emitted even if the requesting player has
  logged out (if the server event completes).
- **Registration anomaly:** a detected attempt for an ACTIVE detached host
  to join the real world is logged; the event is cancelled and the probe
  stops. A constructor that performs side effects *before* the host is
  tracked is not covered by that event guard.
- **Cleanup:** original mob unloading, server stop, mirror failure,
  and unexpected exceptions are recorded with the trace ID.
- **`/freakyfriday inspect`:** all inventoried native goals, targeting
  goals, state and priority, native override candidates, and the bounded
  list of potential world effects. Chat can remain abbreviated.
- **`/freakyfriday hostprobe40`:** every physical recipient probe tick
  reports command counts/status and actual body location, with a final
  uptake/displacement summary.
- **`/freakyfriday hostprobe`:** command result is logged alongside its
  chat message; it is a one-off synthetic action probe, not AI transfer.

## Evidence and limitations

These reports support the initial TWO-INSTANCE **constructor, positioning,
registration and retained lifecycle** investigation. They do **not** capture
or prove the absence of arbitrary world effects initiated by unknown modded
Java code, and they do **not** run the donor's AI in isolation. Source-class
static initialization and entity factories can mutate a real Level before
our monitor observes the returned host. All tests must use disposable worlds.

A passing 40-tick mirror indicates that the candidate host was *observed*
unregistered and aligned during the probe, not that a complete AI controller
has been swapped.

## Testing procedure

Use a disposable world, game-master permission, and the **new R0.15.2
JAR** (do not mistake R0.15.1 for this instrumentation update).

Select cow first with the book, then zombie; run
`/freakyfriday dualprobe` and then
`/freakyfriday dualprobe40`. Let at least 40 physical ticks elapse.

Afterward collect the Minecraft `logs/latest.log` from that run. Search
for `[FF-PROBE]` and correlate lines by their `trace=` ID. Chat should
also show the trace IDs. If something crashes, preserve the full
`latest.log` and crash report rather than copying only the chat result.

Only after vanilla-host construction and lifecycle is validated should we
test one modded-mob factory and move to AI interception and donor state.
