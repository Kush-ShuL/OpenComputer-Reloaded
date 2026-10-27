# Hover boots

T06 follows MightyPirates/OpenComputers commit
[`258943363ea8fc4ef14948e6bc6f67375061580c`](https://github.com/MightyPirates/OpenComputers/tree/258943363ea8fc4ef14948e6bc6f67375061580c):
`common/item/HoverBoots.scala`, `common/item/data/HoverBootsData.scala`,
`common/event/HoverBootsHandler.scala`, and the shared original
`common/item/traits/Chargeable.scala`. MIT attribution remains in LICENSE/NOTICE.

## Preserved behavior

- Diamond boots, uncommon rarity, one per stack, ordinary boots-slot armor
  equip interaction, no repair ingredients. The existing drone-textured armor
  model and dye-dependent light color are retained.
- Energy persists as the original root `oc:charge` field. Charge/discharge
  returns the unused amount, clamps the resulting store to capacity and honors
  simulation. Reading stored energy does not silently discard an old saved
  value merely because the configured maximum changed.
- The always-visible bar represents stored energy with the original vanilla
  red-to-green durability gradient. Reported maximum damage follows the current
  configured capacity. The item advertises `isDamageable=false` and
  `isDamaged=true`, as in the original; real damage never accumulates or breaks
  the boots. Damage consumes energy instead. Creative wearers do not pay armor
  wear, and vanilla durability enchantment processing is preserved before the
  energy deduction. Explicit `setDamage` clears the actual damage component to
  zero; a legacy positive damage component is normalized on inventory tick.
- Wearing empty boots without ignored power adds Slowness II for 20 ticks only
  when no slowness effect is already present. This is an item armor-tick effect,
  separate from movement events, and it does not deduct movement energy.
- On real-player movement ticks, non-sneaking powered boots activate step assist.
  The original step height is 1 while active and 0.5 after deactivation. A
  transient modifier expresses those values without changing the attribute's
  base. A missing active modifier is restored after a saved player reconnects.
- Ground movement consumes the configured movement cost at `tickFrequency` only
  when squared velocity exceeds `0.015f`, excluding creative mode. The original
  client inventory replica and logical server each update their own state;
  there is no extra server deduction from item tick.
- During the first five blocks of a fall, active boots multiply downward
  vertical velocity by `0.9f`. Sneaking disables this and the step assist.
- Jump boost requires the entire configured jump charge, unless creative or
  power is ignored. It adds 0.4 vertical velocity and, while sprinting, adds
  half the current horizontal velocity. Sneaking suppresses the jump boost.
- Falls exceeding three blocks pay the fixed absorb charge and use 30% of the
  fall distance. Sneaking does not disable fall absorption. Fake players remain
  excluded from movement, jump and fall handlers, matching the source.
- A dyed dropped pair in a nonempty water cauldron loses its color, uses one
  water level and returns true from the item-entity hook for that wash tick.
  Charge, other item data and the remaining pair are retained.

## Registration

`MiscDevices` registers this independent event subscriber once on the game bus:

```java
NeoForge.EVENT_BUS.register(li.cil.oc.common.event.HoverBootsHandler.class);
```

The old hover-only methods were removed from `MiscEvents`. Manual/new-player,
clone, crafting recovery and seasonal handlers remain there.

`MiscDevices.capabilities` delegates FE charging to the item implementation:

```java
event.registerItem(
    net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM,
    (stack, context) -> li.cil.oc.common.capabilities.ExternalPower.expose(
        () -> HOVER_BOOTS.get().energyStorage(stack)),
    HOVER_BOOTS.get());
```

`energyStorage` follows the original conversion: received FE is the requested
FE minus the integer-converted unused OC remainder. It accepts the final
fractional OC space rather than refusing it by rounding available space down
first. Extraction remains disabled. Existing external-power gating remains
around this implementation.

No new settings, items, dependencies or fixtures are introduced.

## Implementation check

The item, event subscriber, data storage and registration were compared with
the pinned source, and the complete Windows Java 21/NeoForge build passed.
