# Migration to Minecraft 1.21.1 NeoForge

## Source of truth

- Only [MightyPirates/OpenComputers](https://github.com/MightyPirates/OpenComputers) is used as the implementation source.
- Branch: `master-MC1.12`.
- Pinned commit: [`258943363ea8fc4ef14948e6bc6f67375061580c`](https://github.com/MightyPirates/OpenComputers/tree/258943363ea8fc4ef14948e6bc6f67375061580c).
- No community port code is used.

The public repository starts with an independent root commit, `de9a637`, rather
than the original project's Git history. Upstream history is used only through
local development references and the external source link above; it is not
pushed into the new repository. Starting a new Git history does not remove the
original authorship or license obligations.

Lua programs, manuals, assets, original copyright notices, MIT LICENSE and the
third-party notices in NOTICE are retained. Runtime code is being rewritten in
Java 21; there is no Scala compiler or Scala runtime dependency. Original Scala
implementations are consulted as source references, not shipped as the runtime.
A rewritten module is not automatically validated in game.

## Delivery requirements

Completion requires the whole mod, not just registration or an API JAR: computers and persistent Lua VM state; native Lua and LuaJ; OpenOS and loot disks; driver discovery and component networks; storage and EEPROM; graphics and input; robots and upgrades; drones; tablets and terminals; servers and racks; redstone, transposers and fluid/inventory automation; printers, holograms, geolyzers and motion sensors; nanomachines; recipes/configuration; multiplayer menus and payloads; rendering; and compatible optional integrations.

Systems awaiting Java migration remain incomplete, even if the current Java
modules compile. Empty components are not a substitute for their original
behavior.

## Current state

Work in progress. This is a partial development implementation, not a complete
playable release. Earlier Windows development clients loaded Minecraft 1.21.1,
NeoForge 21.1.224 and this mod. That startup evidence does not validate later
changes or establish functional completion.

The Java source includes the API types, core network/filesystem/machine/driver
logic, LuaJ and native architecture code, CPU/APU/memory/storage/GPU/network-card
items, cases, screens, keyboards, cables, floppy drives, power devices,
configuration/recipes and nanomachines. Adapter and automation devices, including
transposers, geolyzers and motion sensors, are connected to mod registration;
adapter drivers are also connected to setup initialization. These modules are
included in the current full build and still require functional validation.
Module presence is distinct from complete upstream behavior and player acceptance.

Analyzer and wrench items now have their original diagnostic, clipboard and
rotation paths. Redstone I/O and both internal redstone-card tiers, and the two
hologram tiers with real voxel VBOs, shaders and revisioned synchronization,
have been connected to registration. These additions require interactive
acceptance; compilation does not establish their rendering or world behavior.
The bundled-redstone core retains all sixteen channels, provider aggregation,
per-color events and NBT. The opt-in CC:Tweaked bridge registers both input and
output providers against that same state; its full sixteen-channel gameplay
acceptance is outstanding. Other optional bundled and wireless redstone bridges
still need migration or acceptance for supported modern runtimes.

The manual API now has a Java client backend, resource and addon providers,
language fallback, tabs and interactive Markdown rendering using the original
manual textures. It initializes on the physical client before addon setup
registration; dedicated servers keep `API.manual` null. This does not establish
parity for every manual item interaction or every optional addon.

The robot/drone control platform, portable data, physical hosts, inventory and
screen/keyboard drivers, movement, interaction, client models and input are now
registered. Robot movement retains its controller and VM. Racks, server blades,
component buses and paired wireless terminals are also registered. The production
chain includes original robot/drone assembly layouts, template-driven powered
assembly and disassembly, the 3D printer and dynamic printed blocks, and the
redstone-controlled charger. These modules compile and the Windows development
client joins the existing world; their full gameplay acceptance is outstanding.
The current Java batch connects tablet and microcontroller hosts, their menus,
screen/input synchronization, charge and storage, and original assembly and
disassembly layouts. It also adds functional agent upgrades, including battery,
generator, solar, crafting, database, inventory/fluid controllers, navigation,
experience, trading and world interactions. Data, Internet and linked cards,
the queued wired/wireless relay, the sided network splitter and loaded waypoints
are connected as well. These additions require gameplay acceptance, including
world-save restoration, protection interactions and multiplayer use; their
presence is not a claim that every upstream behavior has been validated.

RAID storage, the debug card/debugger/texture picker, hover boots, seasonal
presents and artificial end stone are also registered. RAID retains the original
three-disk concatenation and destructive member-removal behavior. Missing vanilla
Adapter drivers now include beacons, brewing stands, command blocks, comparators,
spawners, note blocks and jukeboxes. The command-block driver remains opt-in.

Rack disk-drive mountables now expose their real floppy filesystem on the selected
primary bus, including insertion/ejection, labels, media addresses, whole-drive
removal and world serialization. The original configured creative robot, drone,
microcontroller and tablet presets are available; robots and tablets use the
ordinary disk-drive item as their floppy container. A Windows real-device run
passed robot/tablet floppy hot-swap, latest robot disk label and address retention,
rack bus isolation and energy-charged filesystem I/O, media/whole-drive swaps,
same-process rack save/remove/load with its wiring, and the configured robot's
native Lua 5.3 BIOS execution. This run does not establish cold-process rack
restoration, remote multiplayer or rendering acceptance.

Original client behavior now includes robot labels, supporter robot pets and the
own-pet visibility setting, optional GL/font diagnostics and periodic light
updates. The remaining original diagnostic commands and integrated-server wireless
tree visualization are connected. These client additions compile; their visual
and multiplayer acceptance remains outstanding.

The original JEI manual/API categories, crafting extensions, configured-device
subtypes, slot hover hints and client program-disk refresh are now implemented in
Java against the official optional API. The Windows build passed; the JEI client
screens and live world transitions still need acceptance.

The optional AE2 integration passed a Windows dedicated-server scenario with
official AE2 19.2.14 and GuideME 21.1.1. Native Lua 5.3 reached real Adapter
components, queried actual ME stock/CPU/energy/cell state, configured database
filters, exported eight iron and imported eleven copper without changing item
totals, and completed a real crafting request using a CPU, pattern and Molecular
Assembler. SDK crafting values passed registry-aware save/load round-trips;
pending-job cold recovery, fuzzy export, spatial movement and remote multiplayer
remain separate acceptance work. See [AE2 integration](appeng-integration.md).

The optional Mekanism bridge passed Windows dedicated-server scenarios with the
official 10.7.19.85 runtime: actual Configurator modes and NetSplitter interactions,
native Lua 5.3 ChemicalStack conversion including 64-bit integer boundaries,
debug IDs on/off, and both original blacklist entries enabled together. This does
not establish its client hints or remote multiplayer acceptance.

The optional EnderStorage bridge passed an actual Windows dedicated-server run
with official EnderStorage 2.13.0.191 and CodeChickenLib 4.6.1.529. A real Case's
native Lua 5.3 VM used a real Adapter to call all five callbacks on an Ender Chest
and Ender Tank, including frequency bounds and all sixteen colors. Actual item
and fluid capabilities retained separate public-frequency and private-owner
contents, and OC frequency changes preserved owner UUID and display name.
Owner setup used the foreign mod's public API; player diamond interaction,
remote synchronization and cold-process recovery are not established by this run.
Separate blacklist runs passed with the generic inventory/tank drivers both off
and explicitly enabled: no dedicated API leaked, existing generic wrappers
rejected all five methods, and actual contents/frequency/ownership remained intact.

Program-disk chest generation preserves the original vanilla pool objects and
enchantment holder ownership. A Windows real-world run generated all four target
chests with fixed seeds (256 rolls each), retained vanilla loot, produced complete
program disks without the internal marker or zero-weight programs, and mounted
one generated disk in the real rack to read its bundled bytes. This covers the
generation/mount path, not a statistical probability proof or every external-world
program-pack lifecycle. Update checks are connected but still need login and
network acceptance. See [loot and updates](loot-and-updates.md).

Configuration loading now includes the original version patches, array and value
validation, one-time NeoForge schema marking, legacy filtering/effect/class/energy
mapping, migration backups and atomic writes. Existing port configurations retain
their custom values across repeated loads. Invalid existing files are retained.
The actual inventory/fluid-driver toggles, clipboard cap, thread priority, robot
delays/exhaustion/cobweb behavior, rack switch tier, packet ranges and external FE
blacklist are connected to existing modules. The isolated configuration behavior
tool passed on Windows; this does not establish every optional integration.

ComputerCraft compatibility requires explicit
`integration.computerCraft.enabled=true`, an installed supported CC:Tweaked, and
no matching integration blacklist entry. It is disabled by default. Windows
dedicated-server contracts passed with CC installed but compatibility disabled,
and with compatibility enabled: real relay packets and modem events, Adapter
computer calls, and two-way disk mount I/O, labels and remount identity. See
[the integration details](computercraft-integration.md) for public-API limitations.

Earlier Windows testing exposed texture/layout problems, fractional energy
accounting errors, host-thread deadlocks and native-library class-loader issues.
The source contains targeted corrections, including deferred server-thread dirty
updates and an FML game-library manifest around unchanged upstream JNLua classes.
These findings should not be presented as acceptance of all rendering, boot,
persistence or multiplayer behavior in the current tree.

A live Windows robot-GUI profile then found repeated inventory-capacity driver
scans in 940 of 1118 render-thread samples. Capacity now uses the existing direct
calculation, and component slot offsets are computed once per component list.
After a method-only hot update, the player confirmed the GUI recovered from
about 15 FPS to about 60 FPS. The missing carpeted-capacitor manual path now
falls back to the original capacitor page only when its exact page is absent,
preserving language fallback and resource-pack overrides.

HTTP redirects now retain the original HttpURLConnection boundaries: host/port
changes discard destination credentials and protocol changes return the actual
redirect response. LuaJ byte-string headers are decoded before applying the
configured header gate. These source corrections pass the Windows build;
end-to-end HTTP acceptance remains separate work.

A later live client freeze exposed a concrete external-drive lock cycle: a Lua
read held its filesystem lock while `BlockEntity.setChanged` waited for a server
chunk task, and a server-side keyboard message waited for that same filesystem.
External drive save notifications now use an atomic flag drained by the server
tick; irrelevant filesystem messages return before locking. Activity packet and
sound recipient lookups are also dispatched to the server thread without waiting
inside the callback. The captured stacks remain development evidence, not a claim
that every host/thread combination has passed acceptance.

The Windows external-drive regression then passed the actual native Lua 5.3
worker path: a direct read completed while the main thread deliberately did not
pump chunk tasks, keyboard down/up returned in 0 ms during a bounded held-volume
lock, and the VM completed 80 reads and 10 writes with 181 received key-up signals.
The filled-to-empty drive update also passed NeoForge's real `onDataPacket`
receiver path and cleared the inventory state used by the disk renderer.

Still required: functional acceptance of advanced robot/drone upgrades, tablets,
microcontrollers, network devices, robot/drone and rack/server/terminal
platforms, holograms, printers and prints; remaining automation/power devices,
item/block/component families; commands, manual item behavior, loot and
configuration parity; remaining optional integrations and configuration
consumers; restoration on remaining host types; and full
client/server behavior. Existing menus, payloads, rendering, energy, persistence
and crafting also need functional acceptance.

Printed blocks now persist the original six redstone input levels used for edge
transitions, with independent NBT snapshots and no server input history in client
updates. Existing saves without that field retain the original initial-sample
behavior. Printers now process the current piece even when a compatible output
stack is full, then retain it as busy until there is delivery space. Waiting
does not spend additional energy, consume another piece's material or convert
more input items. The Windows build covers these source changes; actual chunk
reload and output-release gameplay acceptance remain outstanding.

A successful API build validates the SDK build. API compatibility checks cover
the specific contracts they exercise; they do not establish computer boot, saved
VM restoration, all host devices, automation, multiplayer synchronization,
graphics fidelity or a complete migration. The current API validation version is
`8.0.0-alpha.1`; a stable ABI has not been announced.

Native Lua 5.2 and 5.3 have passed replacement-host checkpoints retaining the
coroutine, synchronized-return stack, addon Value with registry-aware item data,
open tmpfs handle and queued signal. LuaJ retains the upstream reboot-on-load
behavior. This exposed and fixed a LuaJ direct-callback pause/start monitor
deadlock. A separate Windows world fixture then passed a real case's native Lua
5.3 cold-server restart and chunk unload/reload, retaining the coroutine, open
tmpfs position, userdata/iterator state, signal and component identities. A
separate LuaJ world passed its expected reboot and disk-data restoration. A
separate native Lua 5.2 world then passed prepare and resume in different JVMs,
including real chunk unload/reload, one BIOS boot and two userdata restorations.
Cold-world restoration on the remaining physical host types still needs acceptance.
No public SDK signatures changed for these runtime fixes.

A real configured robot also passed native Lua 5.3 cold-process restoration after
its Lua program moved the physical proxy upward. The second JVM retained the same
machine/component identities, native locals and nested coroutine, userdata item
data and iterator cursor, open tmpfs/HDD offsets, EEPROM and queued signal, plus
the robot's name, owner, selected slot and inventory. The BIOS boot count remained
one. The robot's LuaJ reboot path still needs its own acceptance.

A configured drone then passed native Lua 5.3 prepare/resume in separate Windows
JVMs. The actual entity loaded from world storage with its original UUID, saved
position/velocity/flight target, owner and inventory. Its native stack, nested
coroutine, userdata, tmpfs descriptor offset, EEPROM, queued signal and all node
identities resumed with one BIOS boot. The fixture keeps the original drone's
hardware limits: it adds no unsupported hard drive.

A configured tablet also passed separate-JVM native Lua 5.3 restoration. The
same item was saved in a real chest's world NBT, returned to a new holder and
recreated through the item's normal inventory tick. Its VM and userdata, tmpfs,
HDD and menu-inserted bay-floppy offsets, EEPROM, queued signal and all node
identities survived. This establishes chest/world storage restoration, not
online player-inventory persistence or remote multiplayer use.

The assembled microcontroller passed native Lua 5.3 prepare/resume in two Windows
JVMs through its real placement, EEPROM menu and power interaction. The second
process retained the native stack and nested coroutine, registry-aware userdata,
open tmpfs offset, EEPROM, queued signal, owner, orientation, hardware metadata,
all node identities and six saved output flags. Real packets entered through
every non-front side and left only through the configured open outputs; the five
external graphs remained separate from one another and from the private VM.
Its original hardware does not permit an HDD, and the fixture adds none.

A real rack/server blade also passed native Lua 5.3 prepare/resume in separate
Windows JVMs. Legal hardware was installed through the held blade menu and the
blade through the rack menu; real payload codecs/handlers set wiring and power.
The loaded blade retained its native locals, nested coroutine, registry-aware
userdata, tmpfs/HDD offsets, EEPROM, queued signal, every component identity,
machine users and actual redstone output. Primary graphs stayed isolated and
secondary packets used the saved selected bus in both directions. A second
stopped blade witnessed bus isolation; no second running VM proof is claimed.
The registered test player used a local mock transport, not a remote client.

The trading upgrade now preserves the original two-pass payment matching:
prefer exact item/component matches, then retry both inputs from an untouched
inventory copy with item-type matching. Trade userdata retains the real host
and merchant independently of the installed upgrade, persists their identities
and resolves unloaded hosts later without forcing chunks. Older preview values
can still migrate through their saved upgrade address. These source changes pass
the Windows build; real villager interaction and Trade cold recovery still need
their own acceptance.

The external-addon fixture also passed an end-to-end IMC production contract:
the real assembler validates and consumes addon inputs, applies energy over
multiple work ticks and produces the requested item; the real disassembler
uses the addon callback and inserts its guaranteed drops into an adjacent chest.
This confirms those extension flows, not every built-in device recipe or model.

The standalone SDK has successfully compiled and published to Maven Local.
A separately compiled addon has passed on a Windows dedicated server: legacy
Converter binaries, registry-aware persistence, network and prefab lifecycles,
placed SimpleComponent discovery, and native Lua 5.3 / LuaJ callback conversion
and IMC program mappings. The new Windows client also starts and joins the
development world. Manual rendering and newly registered devices still need
interactive acceptance. The remote JitPack tag build has succeeded and its Maven
POM is available. The player manual item is registered, reuses the original
book + tier-one chip recipes, and opens the client manual with page history or
contextual block navigation; its interactive acceptance remains pending.

## API and release packaging

The standalone SDK project lives in `api/`. The root `jitpack.yml` selects Java
21 and runs `./gradlew -p api publishToMavenLocal --console=plain`, which compiles
only `li.cil.oc.api` and produces API, sources and Javadoc artifacts with the
license files and NOTICE. It does not require the unfinished runtime to compile.
`./gradlew build` builds the full mod; compilation alone is insufficient for a
playable release.

The API is distributed through JitPack as
`com.github.Kush-ShuL:OpenComputer-Reloaded:<tag>`. For an API release, create a tag
and a GitHub Release with **no attached assets**. JARs and Maven archives are not
uploaded to that Release; GitHub Packages is not used. Local SDK publishing uses
the separate `io.github.kush-shul:opencomputer-reloaded-api:<api_version>`
coordinate. The `api-8.0.0-alpha.1` preview release has been created without
attached assets; its remote JitPack tag build succeeded with Java 21 and produced
the SDK, sources, Javadoc, POM and Gradle metadata.

Integrations use `compileOnly`, must not shade the API, and must install the
corresponding full mod separately for development and gameplay. Old Forge addons
must be adapted and recompiled. `API.ID_OWNER` is `opencomputers`; callers built
against the earlier incorrect constant must be recompiled because Java inlines
the value. Registry-aware persistence and converters use an explicit
`HolderLookup.Provider`; the new three-argument Converter overload is a default
method preserving the existing two-argument functional interface. See
[API.md](API.md) for dependency and migration details.

CI definitions under `docs/ci/` are inactive templates. They validate builds and
do not upload release assets or publish GitHub packages. A runtime release must
not call an incomplete migration a complete port.
