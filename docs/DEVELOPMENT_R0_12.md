# R0.12 — book UX and gameplay implementation ledger

R0.12 is **in development**. R0.11 remains the last source checkpoint with green compilation for both NeoForge targets. This document tracks concrete changes and explicit gates rather than promising unfinished features.

## R0.12.0 change — selection hint lifecycle

The R0.11 client displayed the book action-bar hint every 200 client ticks for as long as the book was held. This repeatedly overwrote other action-bar messages.

The new behavior shows the hint once when the book is equipped, and once if the user remaps the shortcut while still holding it. The existing client key mapping is unchanged and the self-selection command remains server-authoritative. Re-equipping and reconnecting refresh the hint.

### Regression coverage

The synthetic Minecraft client harness checks:
- the initial Ctrl+S hint;
- no hint repetition after 400 client ticks continuously holding the book;
- exactly one refreshed hint when the shortcut changes to Alt+R;
- the self-select command only while the book is held;
- a fresh hint on re-equip and reconnect.

These are client simulation tests; they **do not** validate the feature in a running Minecraft client.

## R0.12 remaining work in priority order

1. **Gate 1 / selection UX:** Replace the existing clickable-chat category list with a category-only picker. Preserve command fallback and avoid per-attribute choices. Validate both first-target and second-target Ctrl+S flows in a real client.
2. **Gate 0 / executable smoke:** Start a Minecraft 26.2 client and dedicated NeoForge server from a clean checkout; verify book registration, crafting, interaction cancellation and server-only loading. CI compiling a JAR is not a boot test.
3. **Numerical portability:** Investigate safe attribute attachment for asymmetric species without falsely promising unsupported modded attributes. Reject whole categories when any member cannot be transferred. Do not skip members.
4. **Whole-category abilities:** Implement registry-backed ownership, hooks for suppressing original species behaviors, recipient execution, durability, synchronization, and rollback. Creeper ↔ bat is the first hard acceptance scenario; never special-case a mob pair in the transaction API.
5. **Multiplayer and persistence:** Test unload, death, logout, reconnect, restart, and multiple observers for duplicate/phantom behavior.

## Build targets

Java 25, NeoForge 26.2 first, 26.3 second; use the single shared production source and versioned Gradle profiles. GitHub Actions builds and core checks are mandatory per commit. Successful compilation does not imply any in-game acceptance.

## R0.12.1 — dynamic source-grounded category offers (CI pending)

The category choice must reflect a **live server observation**, not a static
menu of imagined values. `AttributeCategorySwapper.categoryOffers(entity)`
reads the entity's actual attribute instances from the registered attributes,
classifies their registry IDs, and includes the two genuine live vitality
values. It returns immutable whole-category offers with exact member IDs.

`CategoryOffers.discover` offers only nonempty implemented numerical
categories in stable category order. In particular, **Signature Abilities
are excluded** until a working executable adapter and ownership lifecycle
exist. The offer is not proof of compatibility with an as-yet-unselected second
entity. The existing `CategoryExchange` transaction preflights both complete
snapshots and rolls back on an inexact write; asymmetric unportable category
schemas must fail rather than lose their surplus attributes.

The current clickable chat picker now describes live observed counts and
explicitly distinguishes **offered on the first target** from **safe to
transfer between both targets**. The final dedicated client GUI and dynamic
ability functionality are not yet implemented.

The added `CategoryOffersSelfTest` checks missing/empty categories,
unsupported abilities, immutable offers, modded member preservation,
asymmetric schema rejection, and matching schema compatibility.
`scripts/test-core.sh`, Gradle's `check`, and the synthetic Minecraft
production compile exercise this new model.

**Remaining subtleties:** The first-target menu cannot truthfully promise
transfer success before the second target is known. Real in-game GUI and
player-friendly two-target diagnostics need further implementation. Numerical
attribute presence does not substantiate the behavioral effects of a trait
such as flight; abilities remain unavailable until verified end-to-end.

## R0.12.2 — Broad categories and first native picker (build gate in progress)

The player-facing category catalog is intentionally compact: **Vitality,
Movement & Body, Combat, Defense, Other Traits**. **Signature Abilities**
remains unavailable until working, fully transferable behavior ownership exists.

Movement and physical-body attributes are combined in one atomic numerical
category; previously separate unclassified vanilla and modded categories use
Other Traits. No individual stat choices or bespoke mob pair menus were added.
The category ID and full member set are determined by registered attribute IDs
and actual AttributeInstances observed on the first target.

When the first target is selected, `CategoryPickerNetworking` sends the
server-generated offer snapshot to a small client-only
`CategoryPickerScreen`. Its buttons use the existing
`/freakyfriday category <id>` command, which always revalidates
the server session, living target and available category. Escape/Cancel
issues a server-side cancel; `/freakyfriday categories` reopens the picker,
and explicit category/cancel commands remain available.

This is a **server-backed UI snapshot**, not a claim of ability portability,
live multiplayer synchronization, or verified GUI runtime. The second target
is still preflighted by the transactional exchange engine at commit time.
An unportable member rejects the entire category, rather than being silently
excluded. The independent synthetic Minecraft harness does not fake native
26.x networking; the real NeoForge 26.2/26.3 CI builds must compile it.

The old chat message saying to choose a category still supplies stage
instructions, but category buttons now live in the GUI rather than a spammy
chat list.

**Pending acceptance:** Real client opens the UI; dedicated server loads
without client-side classes; category selection, Escape cancel, target death,
reconnect, stale sessions, modded attributes and actual reciprocal transfers
work in-game. A compiled client/server jar is not proof of these behaviors.
