# R0.7 real-game acceptance script (NOT EXECUTED)

Prerequisites: Java 25, Gradle 9.2.1 or later, resolvable NeoForge 26.2/ModDevGradle dependencies, official client and dedicated-server launch setup.

1. Run `python3 scripts/build_doctor.py`. Save compiler diagnostics and the output JAR; do not count the smoke test as started unless it succeeds.
2. Launch `gradle runClient`, create a creative test world and obtain the Freaky Friday book from tools/utilities or a `/give` command. Check `Ctrl+S` hint and key rebinding.
3. **Villager:** right-click with book in main hand. Expected: first-target category choices appear; trading UI DOES NOT open. Without book, villager trade must still open.
4. **Animal:** with book in offhand and an ordinary item in main hand, right-click the animal. Expected: book selection takes priority. Without book, ordinary interaction must remain unchanged.
5. **Mob ↔ mob:** select first living entity, choose Defense, select second entity, inspect base attributes and permanent modifiers before and after. Reverse the same exchange. Verify every value in category restores exactly and temporary equipment/potion effects remain with their original owner.
6. **Player ↔ mob:** press remappable Ctrl+S with book held, select category and right-click a living mob. Repeat with player as second target. Ensure selecting the same target twice never mutates state.
7. **Invalid cases:** choose mismatched schema modded entities, dead/unloaded target, different dimensions, no book and invalid category; each must reject without mutating either entity.
8. Start a dedicated NeoForge 26.2 server with two modded clients. Repeat (5) and (6) from separate players, including simultaneous sessions and rejoining.
9. Restart the world, confirm vanilla attribute persistence behavior as applicable, and explicitly record unimplemented custom-category/ability persistence separately.

Collect client and dedicated server logs, reproduction steps and exact mod versions for any failure. Do not mark Phase 1 completed until these tests have passed on real software.
