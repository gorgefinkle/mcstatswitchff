# Freaky Friday R0.15.3 — Locked dual-instance pair after live test

## Grounded finding from the user's 2026-11-21 R0.15.2 run

The first pair chosen was **Cow (donor) -> Zombie (recipient)**, but
subsequent right-clicks with the book kept replacing the second body.
By the time the probes ran, the pair was **Cow -> Cow**.

The real `dualprobe` and `dualprobe40` tests were successful for
Cow -> Cow:

- Detached donor-class Cow created, unique UUID, exact matching Java class
- World registered: false
- Position aligned: true
- Recipient body unchanged: true
- Goal selectors: 8 donor / 8 host, zero target goals
- 40/40 recipient physical ticks completed
- 19 position mirror operations, 0 alignment failures
- 0 world registration violations
- Cow recipient and donor both stayed at 10 health
- Final body displacement ~0.13194 blocks
- Host AI was never ticked; this is **not** an AI transfer

Therefore this validates a same-species dual-instance lifecycle and
does NOT establish that Cow -> Zombie construction/mirroring behaves
the same, despite a prior cow-zombie selection in the log.

## Fix

R0.15.3 locks the donor/recipient diagnostic pair at the SECOND book
click. Any third/fourth click with the book is ignored and warns
`Diagnostic pair is LOCKED`. Further interactions cannot silently
overwrite the recipient. `/freakyfriday cancel` releases the pair,
allowing a new two-click selection; the existing timeout and
dimension-change protection still apply.

The headless interaction harness now proves that third click attempts
do not alter the pair. This was an actual user-discovered bug, not a
cosmetic copy change.

## Retest request

With R0.15.3 in a disposable world:

1. `/freakyfriday cancel` if a selection exists.
2. Right-click a cow, then a zombie, using the book.
3. Confirm chat reports a **LOCKED Cow -> Zombie** pair.
4. Run `/freakyfriday dualprobe` and
   `/freakyfriday dualprobe40`. Observe their reports and
   send `logs/latest.log`.
5. Optional: click a third mob and verify the message says the pair
   is still locked instead of changing target.

Do not tick the hidden host's Goal/Brain or claim full mind transfer
until world-effect isolation is implemented.
