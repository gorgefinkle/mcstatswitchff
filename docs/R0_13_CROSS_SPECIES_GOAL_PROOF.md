> **SUPERSEDED — NOT THE PRODUCT REQUIREMENT.** This document describes an abandoned prototype approach. Its supported-pair model, per-species goal assumptions, goal-class matching, and species/goal-based rejection behavior are explicitly incompatible with the universal mind-exchange contract in [UNIVERSAL_MIND_EXCHANGE_ARCHITECTURE.md](UNIVERSAL_MIND_EXCHANGE_ARCHITECTURE.md). Do not extend this implementation strategy or use its acceptance list as the target. It is retained only as historical evidence of the R0.13 prototype and its limitations.

# R0.13.1 — Cow ↔ Pig cross-species AI-goal exchange

This is the **first supported cross-species implementation** in the `feature/whole-mind-swap` branch. It is not a universal all-mob brain transfer.

## What is exchanged

The book swaps the **entire active goal-driven AI recipe**, not individual user-selected components.

- Cow mind: flotation, panic at speed 2.0, breeding, cow-food temptation, following parent, wandering, looking at players, random looking.
- Pig mind: flotation, panic at speed 1.25, breeding, *both* carrot-on-a-stick and pig-food temptation goals (same priority 4), following parent, wandering, looking at players, random looking.
- Each goal is re-instantiated with the **recipient** animal as its owner. This is important because moving the original `Goal` objects would keep controlling the original species/body.
- Every goal's priority and class is compared before committing. Unexpected modded, extra, or missing goal classes cause whole-exchange rejection; the engine does not silently discard unknown goals.
- The native target selector must be empty on both mobs. Two recipients must be alive and on the same level.
- Both full goal selectors are replaced in one protected transaction and read back. On any error, the previous goal references are reinstalled on both bodies. A separate `MindLedger` also tracks which named mind controls which body.
- Repeating a swap with the same bodies returns cow and pig AI recipes to the original bodies.

The cow's **body** still has its own health, size, milk interaction, breeding offspring, and physical properties. The pig **body** keeps saddling, rider handling and lightning behavior. Those body mechanics are **not** standalone mob AI goals and are not transferred by this initial driver.

This implementation **reconstructs functional goal behavior, not exact in-progress Goal object runtime timers, targets, or memory state**. It does not yet persist swapped AI over a server restart or chunk unload/reload. Therefore it is an *experimental gameplay proof*, not the complete all-species Freaky Friday feature.

## Why this is safe to extend

Adapters describe a **whole source species' complete AI profile**, not a source→recipient pair. The current profiles target two vanilla types with the same `Animal` base class and movement/navigation semantics.

The goal profile snapshot is validated against the **actual runtime source**, so changes in vanilla versions or mods that add unmatched goal classes fail safely rather than produce a partial swap. Because goal signatures do not inspect the internal private parameters of a known Goal subclass, nonvanilla changes to parameters without a change of class/priority may not be detectable: this is another test and compatibility limit.

## First hands-on acceptance test

Use a **new disposable Minecraft 26.2 NeoForge world**:

1. Summon or find one **cow** and one **pig**. Hold the Freaky Friday book.
2. Right-click cow, then right-click pig. There is no category UI. The chat should confirm a complete supported AI-mind swap.
3. Hold carrot/potato/beetroot and carrot-on-a-stick **near the cow**. A pig-mind cow should now react to pig temptations rather than its original cow-only wheat food temptation.
4. Hold wheat **near the pig**. A cow-mind pig should react to wheat (as cow-food temptation).
5. Observe movement, response to damage/panic, nearby parents and other goals.
6. Right-click the **same two bodies** again. Their original temptation patterns should return.
7. Try cow ↔ sheep, cow ↔ zombie and cow ↔ creeper. Unsupported species should receive an explicit rejection rather than false success or partial attribute exchange.
8. Test only a disposable world. Avoid chunks unloading and restarting between swap and reversal; persistence is unfinished.

If the cow/pig exchange reports an **extra/missing/modified AI goals** diagnostic, the 26.2 runtime differs from the audited expected profile. Capture the **exact message and latest.log** rather than modifying the profile blindly.

## Next implementation gates

- Build reciprocal runtime **goal-state capture/restore** and durable ownership persistence.
- Add more whole-species AI adapters, then demonstrate a goal-driven hostile ↔ peaceful mob without dropping targeting, navigation or special behaviors.
- Build a cross-species **Brain-based** adapter; native `Brain` references cannot simply be transplanted into a mob with a different subclass.
- Add genuine player↔player and player↔NPC server-authoritative control (not camera-only swaps).

## Verification claims

CI for both NeoForge 26.2 and 26.3 validates source compilation, regressions and packaged class presence. CI does **not** demonstrate in-world behavior. Only a real Minecraft run can validate the temptation change and reverse swap.
