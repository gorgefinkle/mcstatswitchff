# Freaky Friday end goal and status — R0.10

**Official initial release target:** Minecraft 26.2 / NeoForge 26.2.0.95.
**Second planned target:** Minecraft 26.3 / NeoForge 26.3.0.115.
**Longer-term policy:** Support as many practically maintainable versions as possible, producing independently built and tested JARs from one shared codebase.
**Current certification:** Neither target is playable-certified. Phase 1 remains open.

## End goal

- The book selects entity A, a **whole category**, and entity B; category swaps include **all numerical values, modifiers, behaviors, triggers, parameters, functions and state** as one reciprocal atomic exchange. No sub-selection, special mob-pair mapping or cloned donor abilities.
- Entire category bundles with different numbers of properties must eventually be supported. Reject unsupported trades atomically rather than silently dropping properties or fabricating their effects.
- The selectable entities can be player/player, player/mob or mob/mob, with unchanged appearances.
- Default remappable **Ctrl+S** selects the player as one target, shown in a subtle contextual hint.
- Automatically expose supported registered vanilla/modded numeric attributes, but only advertise portable executable abilities where actual transfer and donor suppression are implemented.
- Save/sync all ownership and state; test on actual multiplayer servers, client sessions and multiple worlds.
- **Creeper ↔ Bat** is the first full ability proof: the bat gains proximity detonation and loses flight, while the creeper gains genuine flight and loses detonation; reversal restores both complete categories.

## Source versus verified behavior

| Feature | R0.10 status |
|---|---|
| Generic immutable category bundle and atomic exchange/rollback engine | Implemented; core tests passed |
| Numerical registered attribute grouping/discovery and permanent modifier transfer | Implemented, local and Minecraft 26.2 attribute-library tests passed |
| Book two-target, category menu, event click priority, Ctrl+S | Implemented; synthetic only |
| Cross-species portable defensive numeric attributes | Partly implemented; synthetic tests only |
| True portable attributes absent from recipients | Incomplete: rejects unsupported schema |
| Whole-category executable ability ownership, flight, detonation | Unimplemented |
| Persistence of ability category ownership and client sync | Unimplemented |
| Minecraft 26.2 full NeoForge Gradle build / integrated / dedicated testing | Unverified, unavailable Gradle+deps |
| Minecraft 26.3 full NeoForge Gradle build / integrated / dedicated testing | Unverified, unavailable Gradle+deps |
| 26.3 relevant NeoForge class method signatures | Present in uploaded universal JAR; does **not** verify compilation |
| Multi-version Gradle profile selection and exact output metadata | Implemented; offline profile checks passed |

## R0.10 modifications and next steps

1. **Done in R0.10:** Explicit 26.2 and 26.3 build profiles, build-time metadata expansion, versioned JAR names, small separated version-specific UI integration hooks and offline validation. Kept the common numerical category engine untouched.
2. **Audit in R0.10:** Core + synthetic regressions; compile 26.2 server-side code against actual Minecraft classes with NeoForge test shims; run real 26.2 attribute instances; inspect supplied NeoForge 26.3 universal event and registration declarations.
3. **Next:** Obtain a real Gradle runtime / ModDevGradle / matching NeoForge 26.2 dependency graph. Build 26.2 and correct all genuine compile errors.
4. **After first true build:** Run integrated client and dedicated server acceptance, including book click priority, Ctrl+S rebinding, invalid/unequal categories and multiplayer.
5. **Phase 1 correction passes:** Solve all portability failures by actual functional adapters; audit and retest until acceptance closes. Do not skip hard cases by inventing values.
6. **Further phases:** Persistence; transferrable ability lifecycle; Creeper/Bat proof; broad vanilla/third-party coverage; separate 26.3 compile and acceptance; earlier versions where maintainable; final packaging.

See `AUDIT_R0_10.md` for test evidence and `docs/MULTIVERSION_BUILD.md` for reproducible commands. The historical v1.1 roadmap remains included and should not be mistaken for current implementation status.
