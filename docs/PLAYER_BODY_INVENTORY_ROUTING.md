# Player physical-body inventory routing

Status: **REQUIRED PRODUCTION CONTRACT — NOT IMPLEMENTED**

## Ownership rule

Inventory belongs to the **physical body**, not to the login account, network connection, or travelling mind. The mind swap changes who controls a body; it does not move the body's possessions.

### Player ↔ Player

For a reciprocal exchange between physical player bodies A and B:

- Mind A inhabits physical body B and operates **B's existing inventory**.
- Mind B inhabits physical body A and operates **A's existing inventory**.
- A's items remain stored on A. B's items remain stored on B. No item stacks, armor, offhand contents, hotbar contents, experience, or inventory metadata are copied or exchanged by the mind swap.
- On reversal, each mind sees the inventory belonging to the body it returns to. Every item remains with its original physical body throughout.

### Player ↔ Mob (example: Player ↔ Chicken)

If the player mind moves into a Chicken body, it inhabits the Chicken's actual physical capabilities. It does **not** take the player's old inventory along with it. The Chicken body has no ordinary player inventory, so the player's occupied-body inventory UI must honestly show that no such inventory is available (unless that particular mob/modded body actually registers storage slots).

The Chicken mind simultaneously inhabits the player's original physical body. That original player body retains its original inventory, armor, offhand, hotbar, and other body-owned state. The Chicken mind is now the controller of that player body; the possessions do not move just because the mind changed.

This is a crucial distinction: **the body the Chicken mind now inhabits is still the original player body, and that body keeps the inventory.** The player mind inside the Chicken does not get to use the old player body's inventory remotely.

A body with native storage/equipment capabilities exposes those capabilities. Do not invent a generic player inventory for a Chicken or any other mob that does not have one.

## Client presentation

The client must present the currently occupied physical body's available inventory as its active inventory view. For a player body, this includes:

- Main inventory slots and hotbar, including selected slot.
- Armor slots, offhand, equipment durability, and currently held stack.
- Cursor/carried stack while a menu is open.
- Crafting input/output slots and recipe availability where supported.
- Inventory screen, creative inventory interactions where supported, and inventory-backed quick-move/drag/swap/drop actions.

When the occupied body is a Mob without player inventory, the player must not see or mutate the old Player body's inventory. Show no player inventory or a clear body-has-no-inventory state. Do not quietly fall back to the authenticated account's original inventory.

## Server authority and packet routing

Implement a server-authoritative `InventoryProjection`/`InventoryActionRouter` keyed by the authenticated connection and resolved through the committed mind-to-body assignment on every operation.

- Resolve the currently occupied physical body from the server-owned controller registry on each request. Never accept a client-supplied body UUID or slot owner.
- Read and mutate the occupied body's actual inventory/capability implementation, not the account's default inventory by assumption.
- Route slot clicks, quick-move, drag, hotbar selection, offhand swaps, equipment changes, cursor-stack updates, drops, crafting, and item use to that same body.
- Validate menu/container IDs, slot bounds, item limits, reach, permissions, transaction sequence, and stale/replayed actions against the actual occupied body.
- Keep open container sessions and world interactions spatially bound to the occupied body. A controller must not open/use a chest from the abandoned body's old location unless that is where its currently occupied body actually is.
- Synchronize authoritative body inventory updates to the controlling connection; reject client predictions that target the abandoned body.
- Prevent duplicate execution by the original account's ordinary player input/tick path. The original Player body is simultaneously controlled by its incoming mind, not frozen.
- On swap, reversal, death, logout, dimension change, disconnect, or menu invalidation, retire the projection and resynchronize the controller to the body they now inhabit. No stale carried stack, slot, or open menu may leak across bodies.

Vanilla player inventory packets and container menus normally associate input with the authenticated `ServerPlayer`. Therefore it is not enough to change the camera, replace hotbar labels, or send a one-time inventory snapshot. The implementation must route both display and every server-side mutation to the occupied body's actual inventory. Any vanilla packet path that cannot be safely rerouted must be intercepted and implemented through an authenticated custom action rather than silently mutating the account's old inventory.

## Account identity versus body inventory

The authenticated account and connection remain stable for profile, security, permission checks, advancement/account data, and network identity. Those account-level facts must not decide which body's inventory a gameplay action reads or mutates after a mind exchange.

Do not copy inventories between accounts to fake a body transfer. Do not swap player profiles, UUIDs, network connections, or account permissions. Keep physical-body inventory ownership separate from controller identity and authenticated connection identity.

## Non-player body capabilities

For Player↔Mob swaps, expose the body's actual registered equipment/inventory capabilities (for example, hands, armor, saddle, chest, storage, or modded slots) through its body adapter. If a body has no inventory capability, represent that honestly. Items on the player's vacated physical body remain there under the control of the incoming mob mind, subject to what that mind's behavior can actually do with the body's capabilities.

## Acceptance tests

A connected-client test with two real accounts and a second observer is mandatory:

1. Put unique named items in different hotbar, main inventory, armor, offhand, and cursor positions on A and B.
2. Exchange minds. A's client must immediately display B's exact inventory; B's client must display A's exact inventory.
3. Use, equip, move, quick-move, drop, and swap items from both clients. Verify server-side changes affect the correct physical body's real inventory.
4. Reverse the swap and verify every item remains with its original physical body.
5. For Player↔Chicken, place named items throughout the original player's inventory. After exchange, the player inhabiting Chicken must not see or manipulate those items; the Chicken mind now inhabiting the original player body must find those same items still stored there.
6. Reverse Player↔Chicken and verify the player returns to the original inventory unchanged.
7. Repeat with a modded mob that provides a real storage/equipment capability, and prove the adapter exposes that capability without species-pair special cases.

Headless GameTests that inspect item stacks are useful but insufficient. At least one real connected-client test must verify menu rendering, cursor stack, hotbar selection, interaction packets, and two-client isolation.
