# Crafting and trading upgrades

The behavior source is MightyPirates/OpenComputers at
`258943363ea8fc4ef14948e6bc6f67375061580c`, specifically
`src/main/scala/li/cil/oc/server/component/UpgradeCrafting.scala`,
`UpgradeTrading.scala` and `Trade.scala`, their original item classes and
`integration/opencomputers/` drivers. Original attribution and licensing
remain in the repository's license and notices. Both upgrades expose their
components with Network visibility and create no energy connector.

## Crafting (D45)

The upgrade uses the robot inventory's top-left three-by-three area, with
parent indices `0, 1, 2, 4, 5, 6, 8, 9, 10`. Grid stacks refer to the real
material stacks, matching the original inventory bridge. The existing
small-inventory adaptation treats unavailable cells as empty, and output
insertion retains the previously repaired selected-slot-first order before
wrapping through the rest of the inventory.

`craft` defaults to 64 requested output items and clamps the requested count
to 0 through 64. It locks to the initial recipe instance and stops when that
recipe no longer matches, when it produces no result, or once the output count
has reached the requested amount. A single recipe can therefore take the final
count above the requested amount. Failure/no work returns `false, 0`; successful
work returns `true, actualCount`.

The normal `ResultSlot` path handles material consumption, crafting events and
container remainders. It removes one result-slot unit as in the original; the
result container supplies the full recipe output stack. The output count is
recorded before `onTake`, so item crafting hooks cannot retroactively change
the reported amount. The grid is written back before remaining output is added
to the robot inventory, and uninserted output follows the existing real-player
drop path. The callback has no added energy fee or pause. Its original Lua
documentation is restored verbatim.

## Trading (D59)

Discovery uses the center of the block containing the host, the original search
bounds and inclusive range filter, and UUID ordering. Merchant IDs are one-based
sort indices; recipe indices remain the merchant's actual offer indices.
Getters return copies of the live offer's inputs and output, including modern
calculated prices. The Trade userdata retains its real host and merchant
independently of whether the upgrade remains installed.

Executing a trade uses the host's actual coordinates and a strict distance
limit. Invalid, removed or out-of-range merchants return
`false, "trade has become invalid"`; a disabled offer returns
`false, "trade is disabled"`. A host without an Agent inventory returns
`false, "trading requires an inventory upgrade to be installed"`. A real
zero-sized inventory instead reaches the original room check and reports
`false, "not enough inventory space to trade"`.

Output space is checked before payment. The existing payment repair is retained:
both inputs spend from one working inventory copy, first with exact components,
then retrying item-type matching from an untouched copy. Insufficient payment
returns `false, "not enough items to trade"`. Snapshot insertion retains the
real inventory's stack limit and `canPlaceItem` rules and uses the normal
ascending slot order. Success commits input consumption and output insertion,
calls the real merchant's `notifyTrade` exactly once, and returns `true`.

The upgrade adds no busy-customer restriction, synthetic interaction-permission
event, temporary trading customer, manual offer-use increment or extra XP
override. These were not part of the original operation. The real modern
merchant owns use counts, XP/rewards and its NeoForge `TradeWithVillagerEvent`;
the upgrade does not post a replacement event. Existing merchant customer and
price state are left to that merchant. No energy cost or artificial pause is
added. All original Trade and upgrade callback documentation is restored.

## Trade persistence

The previously repaired modern host identity, deferred lookup and unloaded-chunk
handling remain in place. Resolving a saved value does not force a chunk load.
Node address identity is independent of an installed trading upgrade; the older
preview upgrade-address fallback remains confined to those preview values.

The value also reads and writes the original `hostIsEntity`, `dimensionID`,
host UUID most/least, host X/Y/Z, merchant UUID most/least, `recipeID` and
`merchantID` fields alongside its modern identity fields. Vanilla numeric
dimensions -1, 0 and 1 map to their modern resource names. Unknown legacy
custom dimension IDs are preserved without guessing their resource names.
A missing merchant sort ID retains the original -1 default.

The native userdata loader needs the one original class-name alias,
`li.cil.oc.server.component.Trade` to
`li.cil.oc.server.component.UpgradeTrading$Trade`, before reflective construction.
That alias and the value's NBT compatibility do not establish compatibility
with an entire old Minecraft or Lua VM save.

The original item classes use the common Delegate/ItemTier defaults. Existing
shared item behavior covers their tier-2 uncommon rarity, normal stack size
64 until a node address exists, original tooltip text/address and advanced
tier display, and pass-through right-click behavior. No dedicated item or
new menu is needed.

The full mod passed its Windows build. The Windows NeoForge 21.1.224 scenario
`agent-actions-20261027-b2` passed with an actual configured Robot and its stopped
Machine as the callback Context. The real crafting component consumed three
milk buckets, two sugar, one egg and three wheat for one vanilla cake, retained
three empty buckets and inserted the output at the selected slot.

The real trading component discovered an actual vanilla Villager's two-input
offer. Its Trade value consumed two emeralds and three bread for one diamond,
and incremented the actual offer's use count exactly once. With the second
input one item short, both matching passes failed without changing any cargo
stack, components or offer uses. The SDK fixture invokes the public Trade
callback through a small managed-environment bridge, retaining the real
Machine Context and Arguments; it imports no internal OC implementation.
The report is at
`build/api-compat-addon/devices/agent-actions-20261027-b2/device-results.txt`.
