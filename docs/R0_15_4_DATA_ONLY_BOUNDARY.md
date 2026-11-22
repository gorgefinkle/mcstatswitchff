# Freaky Friday R0.15.4 — Data-only intent boundary milestone

## What was implemented

`CapturedIntentBridge` is the **receiver** of a future isolated decision
backend. It has no `Goal::tick`, `Mob::tick`, `Brain::tick`, or arbitrary
`IsolatedMindSource.decide` callback. It accepts already-serialized VALUE
objects (`Frame`: recipient UUID, controlling mind UUID, tick, bounded list
of `BodyActionGate.Intent`) and dispatches only via the assigned physical
recipient's own `BodyActionGate.BodyPort`.

It enforces:
- One unique registered mind per physical recipient; atomic two-way exchange
  through the existing `MindLedger`, with no species-pair branching.
- The supplied mind UUID must be the **currently assigned** controller of the
  recipient. Frames from the previous controller after a swap fail closed.
- Each physical recipient accepts no more than one frame per tick (even after
  mid-tick exchanges or a partly failed action dispatch).
- At most 48 immutable actions and 32,768 characters of arguments per frame.
  Oversized frames are rejected on construction, before physical dispatch.
- Unsupported requests report `UNAVAILABLE` on the original body's actual
  capability registry. This does not grant donor anatomy, stats, collision,
  attack powers, or other physical mechanisms.
- Partial physical dispatch errors are recorded as partial, never disguised as
  a rollback. Already-completed native body actions may have real effects.

`CapturedIntentBridgeSelfTest` covers duplicate and stale frames, wrong mind,
unknown body, atomic exchange and reversal, mutable-input resistance,
oversized frames, unavailable abilities, exceptions and partial dispatch.
It runs through `scripts/test-core.sh` and Gradle `check`.

`data_only_intent_boundary` is a **real NeoForge GameTest**: it creates
physical Cow and Zombie mobs, turns off their own native AI for the test,
exchanges the logical mind assignments, receives a data-only LOOK_AT intent
for each opposite species, and verifies it passes to the correct original
Minecraft body. A DETONATE request on a Cow must return UNAVAILABLE. Entity
UUIDs, types and health remain unchanged, old-owner frames and same-tick
replays are rejected, and reversing the assignments preserves identities.
It prints `FF-ISOLATION data_only_exchange PASS` and is mandatory in CI
for Minecraft 26.2 and 26.3.

## What is NOT implemented or certified

This is the **first enforceable data receiving boundary**, not a safe general
native-AI executor. Arbitrary donor Goals/Brains are NEVER run through it.
The producer of a Frame must be trusted: a UUID is not authentication. The
interface does not protect against malicious Java code already running in the
live Minecraft JVM, an untrusted mod's `BodyPort.perform`, or arbitrary
world writes made upstream before a Frame was created.

To execute arbitrary/modded donor AI with safety against world effects, the
next backend needs a truly separate process/world context with a verified
IPC protocol or comprehensive interception of every relevant operation.
In-process unregistered mobs hold the real ServerLevel and can mutate it:
the negative `host_world_effect_escape` GameTest proves this explicitly.

**Do not describe this feature as actual transferred native AI gameplay.**
It is a tested species-agnostic control/data boundary, ready for future
isolated producers. There are no per-mob rules or paid assets.

## Bounded authenticated worker-to-game wire format

`CapturedIntentWireCodec` has now been added as a **pure-data** HMAC-SHA256
binary protocol. It uses 256-bit randomly generated session secrets,
explicit magic/version, a session UUID, receiving physical body UUID, owning
mind UUID, server tick, bounded action IDs and UTF-8 arguments.

Before any frame may be dispatched by the bridge's public method,
`deliverAuthenticated`, it validates a fixed-size HMAC in constant time,
rejects wrong sessions and stale/future server ticks, and rejects unknown
versions, malformed lengths/UTF-8, oversized messages (64 KiB maximum),
duplicate action arguments, and trailing bytes. The existing controller and
per-body tick checks are then enforced. No arbitrary Goal/Brain/Java object
is deserialized or invoked by decoding.

`CapturedIntentWireCodecSelfTest` injects tampering, wrong keys,
cross-session messages, mismatched ticks, truncated or oversized packets,
wrong mind identity and replay. The real-server data-only GameTest also
sends an HMAC-authenticated frame, corrupts it deliberately, requires the
corruption to be rejected, then validates a genuine accepted frame.

**Update — Native Worker P01:** The separate-process producer and the first
signed-frame handoff to a different real NeoForge server are now implemented
and verified on Minecraft 26.2 and 26.3. A vanilla Zombie runs actual native
target/navigation AI in a disposable worker world and the receiver executes
the resulting supported body actions. See
`docs/R0_15_4_NATIVE_WORKER_P01.md` for exact evidence and restrictions.
This remains one-shot offline observational capture, NOT a full streaming
mind swap or a safe sandbox for untrusted arbitrary modded Java code.
