# Barcode, navigation and sign upgrades

The behavior source is MightyPirates/OpenComputers at
`258943363ea8fc4ef14948e6bc6f67375061580c`, specifically
`src/main/scala/li/cil/oc/server/component/UpgradeBarcodeReader.scala`,
`UpgradeNavigation.scala`, `UpgradeSign.scala`, `UpgradeSignInRotatable.scala`
and `UpgradeSignInAdapter.scala`; navigation item data comes from
`common/item/data/NavigationUpgradeData.scala`. The corresponding original
drivers are under `integration/opencomputers/`. Original attribution and
licenses remain in the repository's license and notices.

## Tablet scans and barcode reader (D42)

`TabletUseData` accepts the original eight-field `tablet.use` payload only
from a machine whose host implements the public `Tablet` interface. It
requires the result tag, item stack, player, block position, side and three
floating hit coordinates. Modern tablet hits use doubles; they are converted
to the floats consumed by the original `Analyzable` API. Invalid messages
are ignored by all three upgrades.

The barcode reader first uses `Analyzable.onAnalyze`, then falls back to a
`SidedEnvironment` node or an `Environment` node. A null returned node array
leaves the result untouched; a non-null array produces `analyzed`, including
an empty list when no non-null nodes remain. Each entry contains its component
`type` where applicable and its nonempty `address` where available. The
reader keeps Network component visibility and its normal persisted node.
The analyzer's barcode-reader driver accepts hosts through the common driver
blacklist; the scan message itself requires a Tablet source.

## Navigation (D52)

Navigation is visible to neighboring nodes. Its device information includes
the full map width as `capacity`. Position remains the host's actual
coordinates relative to the stored map center, with absolute Y; X and Z
must both lie within half the map width, including the boundary. Outside
that square it returns `nil, "out of range"`. Facing uses the original
global direction numbering, and range is half the map width.

Waypoint searches retain the original range clamp, energy cost and half-second
pause. Their query and distance filter use the center of the host's containing
block, including for moving hosts with fractional coordinates. Results contain
the waypoint's facing-adjacent block center relative to that origin, redstone
input, label and address. The existing waypoint index preserves the original
half-range bounding-box query before the spherical filter.

Map stacks persist under `oc:map` with registry-aware ItemStack encoding.
Both persistence overloads retain the node and map; the overload without
an explicit registry provider uses the host world's registry access. A
legacy unbound filled map starts at map ID zero. If its data is missing on
the server, a real scale-3 map is allocated at world spawn and its ID is
written back, reproducing old `ItemMap.getMapData` behavior. Existing maps
keep their center, scale and ID. Non-map or unreadable data reports
`"invalid map"`. Tablet scans add map-relative `posX`/`posZ` and absolute
`posY`, as before.

## Sign I/O (D54)

The two original callback interfaces are retained with their original Lua
documentation. `UpgradeSign.InRotatable` uses the host's global facing,
Neighbor component visibility, `getValue()` and `setValue(value)`.
`UpgradeSign.InAdapter` uses a required global side from 0 through 5,
Network visibility, `getValue(side)` and `setValue(side, value)`.
Both first check the host's own block for a sign, then the adjacent block.
Missing or invalid text arguments are checked even when no sign exists.

The original single sign surface maps consistently to the modern sign's
front text; selecting the sign's back does not change this mapping. Writes
preserve the modern back text, color and glowing state, and respect wax.
Reads and tablet scans join the four front lines as unformatted text. The
original `setValue` return uses the text components' string representations;
this behavior is intentionally retained rather than normalized to plain text.

Writes split lines with the original Scala line semantics (LF or form feed,
with CR stripped only before LF), pad to at least four lines, and truncate
each line to fifteen UTF-16 characters. Pre/Post events receive the full
resulting line array, including lines beyond the four actually written;
changes made by a Pre listener to its first four entries are applied.

Protection uses the robot's own player for a Robot host and the configured
fake-player profile otherwise, followed by world interaction permission,
`BlockEvent.BreakEvent` and `SignChangeEvent.Pre`. Modern cancellation denies
the write with `nil, "not allowed"`; the old Forge event result API is no
longer part of NeoForge's event base. Successful changes mark and synchronize
the sign through `SignBlockEntity.setText`, then emit `SignChangeEvent.Post`.
Missing signs return `nil, "no sign"`.

The shared driver routing is connected and the Windows Java 21/NeoForge build
passed. Navigation keeps the original uncommon rarity, node-address stack
limit and advanced tier/address tooltip, as do the shared upgrade item traits.
