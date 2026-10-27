# Geolyzer, motion sensor and waypoint

Behavior reference: the local MightyPirates/OpenComputers source object
`258943363ea8fc4ef14948e6bc6f67375061580c`. Original license and attribution
remain in the repository's license and notices.

## Geolyzer (D17)

The reference includes `server/component/Geolyzer.scala`, its WorldControl,
WorldAware and SideRestricted traits, `util/DatabaseAccess.scala`,
`integration/minecraft/EventHandlerVanilla.scala`, the geolyzer block/tile
classes and `integration/opencomputers/DriverGeolyzer.scala`.

All six callbacks are present with their original documentation:
`canSeeSky`, `isSunVisible`, `scan`, `analyze`, `store` and inherited `detect`.
Scanning keeps the original optional dimensions, normalization, 64-cell volume
limit, range checks, boolean/table options, energy payment and cancellable
events. Option type checks precede bounds checks and payment as in the source;
`analyze` also validates its options before charging. Existing overflow guards
remain in the modern implementation.

The default vanilla scan fills all 64 output entries with the original signed
byte noise before replacing the requested cells with density or zero. In a
smaller-than-64 scan, unused tail entries consequently retain their original
noise. Modern RandomSource lacks `nextBytes`; the adapter uses the same
four-byte extraction from each random integer. Fluids/replaceable-block options,
unloaded-cell zeroing, hardness and distance scaling remain in place.

The vanilla analyze event retains a ResourceLocation `name`, numeric hardness,
harvest information, color, compatibility metadata, state properties, optional
numeric ID and growth. Registry conversion presents the resource name to Lua.
The former metadata model no longer exists in Minecraft 1.21.1, so its
compatibility field remains zero; harvest information uses modern mining/tool
tags. Modern attached stems and carved pumpkins retain the growth information
of their corresponding original blocks.

`analyze` and `store` translate all Rotatable hosts' local sides globally.
The inherited world-control `detect` instead retains its original special
Microcontroller `toLocal` conversion, Robot/Drone/Tablet `toGlobal` conversion
and global directions for other hosts. Drone world-control/sky position retains
the old entity-position rounding. Detection tests the nearest entity before
classifying the block and posts the original break-protection event for liquids
and replaceable blocks. Sky checks use the modern counterpart of the old
below-water sky query; sun visibility distinguishes rain precipitation.

Database storage requires a component in the same node network, reports the
original component/database/slot errors, and preserves the explicit original
comparison with `ItemStack.EMPTY` for the overwritten flag. Block item
representations follow modern block-to-item identities. Tablet analysis uses
the existing strict eight-field Tablet machine message decoder and only copies
supported numeric/nonempty String fields, retaining the original event types.

The previously missing item driver now installs the real geolyzer environment
in Robot, Tablet and other supported host contexts. It has Slot.Upgrade,
internal tier zero, the original common blacklist handling, an environment
provider and client-world creation guard. The physical block continues using
its persisted server environment; both forms retain their node and connector.

## Motion sensor (D25)

The reference is `server/component/MotionSensor.scala`, its block/tile classes
and the common SimpleBlock/Item wrappers. There is no extra installable motion
sensor driver in the original family.

The sensor samples every ten game ticks, detects living visible entities within
radius eight, and tracks positions between samples. An unknown/newly visible
entity signals immediately; tracked movement signals only above
`sensitivity² * 2`. The intentionally offset original bounds/range/signal origin
is retained, while rays start one block from the host toward the entity's feet
or eyes. Invisibility filters and the original ray-trace block outline behavior
apply. Optional usernames use the existing `inputUsername` setting.

Both sensitivity callbacks remain direct and carry their original docs.
Setting sensitivity returns the old value and applies the original
`max(0.2, value)` behavior, including accepted nonfinite doubles. Loading reads
`oc:sensitivity` without adding a clamp or replacing a missing field with the
constructor default; node persistence is retained. The existing SideTracker
provides the original logical-side fallback before a world is available.

## Waypoint (D60)

The reference includes `common/tileentity/Waypoint.scala`, its Rotatable,
RedstoneAware and lifecycle traits, `common/block/Waypoint.scala`,
`util/ExtendedEnumFacing.scala` and `server/network/Waypoints.scala`.

The two label callbacks retain their original docs, 32-character truncation
and half-second write pause. Placement uses the original rounded player pitch;
wrench rotation uses the existing signed-axis rotation matrix and pitch/yaw
transition. Global/local side conversion now follows all twelve pitch/yaw
orientations instead of inheriting identity conversion. Existing redstone
sampling, node identity, client portal particles and server/client label state
remain intact.

The loaded-world index retains original bounds and excludes detached/unloaded
waypoints. Its explicit chunk-unload cleanup complements the normal block-entity
detach path, matching the original safety cleanup without forcing chunks.
Shared registration also keeps the original no-creature-spawn property and
the existing compact, non-pausing label UI. An unchanged label does not commit
or close the original Enter operation.

The three block items retain their common rarity, stack size, standard use and
original tooltip keys. No new tier/item-use behavior is invented. Shared driver
registration and the vanilla event contract's ResourceLocation expectation
are connected, and the full Windows build passed.

The Windows NeoForge 21.1.224 scenario `device-batch-20261027-c3` passed actual
configured-Robot geolyzer installation, adjacent-stone analysis/scan, the original
64-value result and noise bounds, three configured costs, options validation
before payment and the real Analyze cancellation event. A live ArmorStand
produced the original new-entity and one-meter movement signals at the actual
ten-tick sample. Actual waypoint placement, label callbacks and forward/reverse
wrench interactions retained pitch rounding, 32-character labels, vertical
local/global side conversion and node identity. The report is at
`build/api-compat-addon/devices/device-batch-20261027-c3/device-results.txt`.
