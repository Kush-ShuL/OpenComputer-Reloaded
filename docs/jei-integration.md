# JEI integration

This Java implementation follows the seven files under `integration/jei` in
MightyPirates/OpenComputers commit
[`258943363ea8fc4ef14948e6bc6f67375061580c`](https://github.com/MightyPirates/OpenComputers/tree/258943363ea8fc4ef14948e6bc6f67375061580c/src/main/scala/li/cil/oc/integration/jei)
(MIT). Modern JEI interfaces are taken from its official public API. No community
OpenComputers port or Scala runtime is used.

## Behavior

- Manual usage pages retain the 160 × 125 layout and Open Manual button. Each
  ingredient uses the actual OC Manual path provider, including addon paths.
  Clicking closes the container and opens that manual page.
- API usage pages resolve all registered OC EnvironmentProviders, discover real
  valid `@Callback` methods through their class hierarchy, honor callback aliases,
  parse both original documentation signature formats, wrap text and paginate at
  twelve lines. Manual is registered before API, as in the original integration.
- OC's wrapped shaped/shapeless recipes display the actual vanilla base layout
  and OC display result in JEI's crafting category. The loot-disk cycling
  extension uses the actual registered cycling disks and wrench, and obeys
  `misc.lootRecrafting`. Vanilla JEI enumerates the actual synchronized recipes;
  this plugin does not add a second copy of normal crafting recipes.
- Preconfigured microcontrollers, robots, drones and tablets use all modified
  item components as subtype data, the modern equivalent of full item NBT.
  Floppies use only `oc:lootFactory`; filesystem UUIDs and other mutable disk data
  do not create extra JEI subtypes.
- Invalid base stacks excluded by OC's creative catalog are removed from JEI at
  runtime: present, robot, drone, tablet, microcontroller, print and
  floppycontainer. Configured device subtypes remain available.
- The relay's 23 × 26 right upgrade tab is registered as extra GUI space.
- Hovering a JEI ingredient highlights compatible OC hardware sockets using the
  real slot's `mayPlace`. It excludes player inventory, robot cargo/tool slots,
  database ghost slots, outputs, disassembler inputs and generic any/none/tool
  sockets. It supports cases, servers, relay, adapter, rack, RAID, disk drives,
  microcontroller EEPROM, typed tablet/robot bays and typed assembler sockets.
  This preserves ItemSearch's original socket highlighting purpose.
- World-provided program disks are added when JEI runtime starts and when the
  client receives its disk snapshot. Replacing/removing that snapshot removes
  stale world disk variants and restores any bundled factory they overrode.
  No polling task or unconditional JEI client event subscriber is installed.

The original integration has no assembler/disassembler recipe categories,
custom transfer handlers or catalysts; none are invented here.

## Optional dependency and lifecycle

JEI discovers `OpenComputersJeiPlugin` through `@JeiPlugin`. Common startup must
not reference that plugin class. Every registration is gated by the original
`integration.modBlacklist` entry `jei`, case-insensitively.

`JeiIntegration.refreshLoot()` contains no JEI types. The client disk lifecycle
can call it immediately after updating or clearing its snapshot; it does
nothing when JEI has not attached an enabled runtime. JEI's runtime-unavailable
callback clears both the refresh action and hover runtime.

Official API coordinates used during source implementation:

```groovy
repositories {
    maven {
        url = 'https://maven.blamejared.com'
        content { includeGroup 'mezz.jei' }
    }
}
dependencies {
    compileOnly 'mezz.jei:jei-1.21.1-common-api:19.21.0.247'
    compileOnly 'mezz.jei:jei-1.21.1-neoforge-api:19.21.0.247'
}
```

Do not include JEI APIs or runtime in the OC JAR. A development runtime is a
separate optional `localRuntime` dependency. The mod metadata dependency must
also be optional and client-side.

## Validation boundary

Windows client `client-20261027-h4` passed with actual JEI 19.21.0.247 and
NeoForge 21.1.224. It rendered thirteen real GPU callback pages, navigated using
JEI's actual next-page buttons, and clicked the manual usage button for a real
configured tablet subtype to open its Chinese manual page. The runtime retained
configured devices, hid invalid empty base stacks and kept floppy subtype
identity independent of filesystem UUIDs.

The actual filtered JEI CPU ingredient was hovered through Minecraft's normal
cursor callback. Framebuffer pixels confirmed the original half-white overlay
on the accepting Case socket and unchanged pixels in player inventory. The
fixture only observes JEI's existing layout/slot bounds to locate controls; it
registers no replacement categories, recipes, hover results or GUI. A GLFW
cursor warp alone did not deliver game cursor events on this Windows driver.

A real owned world's `opencomputers/loot/loot.properties` supplied a named
OpenOS-factory override. It appeared in actual JEI ingredients after normal
login synchronization. Delivering an empty production `DiskSync` through the
normal integrated-server connection removed that variant and restored the
bundled factory's ingredient. This checks snapshot receipt, not hot reloading
the server's loot catalog or remote TCP multiplayer.

Report: `build/api-compat-addon/integration-client/client-20261027-h4/client-results.txt`.
Actual `jei-api-first.png`, `jei-api-last.png`, `jei-manual-button.png`,
`jei-tablet-manual.png` and `jei-sockets-highlight.png` live beside the report;
the manual and highlighted Case frames were visually inspected. The runtime
remains optional and is not included in the OC JAR. Shared C19/C20/C21/C23
multiplayer, full model, screen and menu acceptance remain separate.

JEI 19.21.0.247 declares NeoForge `[21.0.118-beta,)` and Minecraft
`[1.21,1.21.1)`. FML 4.0.42 explicitly supplies Minecraft `1.21` and NeoForge
`21.0.166` compatibility aliases for an actual Minecraft 1.21.1 runtime, and
`ModSorter` uses `VersionSupportMatrix` when checking dependencies. This is the
loader's normal supported compatibility path. The declared range alone is not
evidence that this JEI release cannot load on Minecraft 1.21.1. Later JEI builds
requiring NeoForge 21.1.238 need a newer NeoForge runtime than this project's
current 21.1.224 baseline.
