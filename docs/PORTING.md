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

The scope is now locked to the [120-item migration checklist](MIGRATION-CHECKLIST.md),
backed by [its machine-readable state](MIGRATION-CHECKLIST.json). Run
`python tools/migration_status.py` for exact current completed and remaining counts.
The fixed units are 61 device/component families, 16 other player feature families,
19 original integration modules and 24 platform/delivery requirements. Existing
code awaiting fidelity review or acceptance still counts as open. A regression
reopens its existing ID; a bug, helper, tier or additional test does not add scope.
The fixed original commit determines behavior. Old integrations without a confirmed
modern target remain on the list until resolved; they are not silently discarded.

Shared rendering, menus and remote networking are tracked once under C20/C23/C19.
A runtime-only device or integration item may close while those shared items remain
open. Closure does not demand testing every behavior of an external mod or every
Cartesian combination of host and architecture. The checklist records each item's
bounded acceptance and evidence, and the whole goal requires all 120 items closed
including the final delivery item C24.

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
acceptance is outstanding. ProjectRed now uses the same state through its modern
optional public API, with a real framed-wire scenario described below. Other
bundled and wireless redstone bridges still need migration or acceptance for
supported modern runtimes.

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

ProjectRed Core / Transmission 4.23.0, CodeChickenLib 4.6.1.529 and CBMultipart
3.5.0.161 passed Windows dedicated-server scenarios with the integration enabled
and explicitly blacklisted. A real survival item placed one neutral framed
bundled cable between a native Lua 5.3 Case/card2 and external Redstone I/O.
All sixteen channels, unsigned byte boundaries, both directions, two Case
orientations, wrong sides and ordinary shutdown clearing agreed across native
Lua, SDK callbacks and the official transmission API. The real screwdriver
passed non-consuming detection then exactly one durability loss on use.
Blacklisting removed real cable connections and refused tool interactions.
Branched/face wire networks, covers, multiple sources, chunk reload and remote
multiplayer remain separate acceptance. See [ProjectRed integration](projectred-integration.md).

The original Waila machine information now has an optional Java Jade plugin.
The Windows build targets official Jade 15.10.0; a dedicated-server run with
15.10.6 passed real annotation discovery and provider calls against placed OC
devices. Case world-side addresses/local energy/component names, all six relay
addresses and real wireless strength, floppy filesystem insertion/ejection, and
redstone-driven charger speed matched actual device state. A separate `waila`
blacklist run loaded Jade without registering the OC provider. Client toggles,
rendered tooltips, busy assembler data and remote synchronization still need
acceptance. See [Jade integration](jade-integration.md).

The MinecraftForge platform bridge now registers standard NeoForge Energy item
charging, including simulation, OC/FE conversion, integer limits and unapplied
remainders. Both the integration and power blacklists gate OC's FE capabilities
and this generic item handler; OC's own chargeable items retain their internal
charging path. The existing four energy-device callbacks still use real sided
capabilities. This corresponds to fixed checklist item I13.

I13 passed four isolated Windows server runs on 2026-10-27: normal operation,
`power.modBlacklist=["NeoForgeEnergy"]`, `integration.modBlacklist=["forge"]`,
and independently disabling only `minecraft`. The normal charger changed an
actual item from 0 to 3 FE and its real OC buffer from 1000 to 999.7 with the
configured 0.1 OC/FE ratio. Actual SDK-selected energy-device callbacks matched
the charger's real capability; the public hook also passed simulation, receive
and capacity limits, fractional and integer-max remainders, and absent or
extract-only capability rejection. Both blacklist runs refused charging, hid
the OC FE capability/driver methods and retained internal energy. The four
`forge-energy*-20261027-110738` reports are linked from the fixed checklist.

Fluid control now restores the original incompatible-fluid tank exchange,
same-target/zero no-ops, omitted versus explicit-nil arguments, no-tank defaults
and matching-fluid world drains. A successful robot world transfer previously
called an unsupported action delay after changing resources; that extra call is
removed. Tank metadata again reports the original product and capacity. These
changes belong to D15/D36/D40/D56/D57, rather than new scope.

The Windows native run `agent-fluid-20261027-112607` closed D56 and D57 with two
real preset robots: one replaces an existing hover upgrade with a second tank;
the other removes the tank and retains its controller. Real Lua 5.3 exercised
all ten controller callback families, strict and default nils, registry-aware
fluid/component/node restoration, exact source-address/index/delta events,
incompatible-fluid exchange, zero/same-target operations, real bucket transfers
and a rejected two-bucket stack. Source-water drain/replacement/full-source
refusal retained exactly 2000mB across the actual tanks/world/item. The initially
incorrect fixture omitted the original signal's source address; only the fixture
was corrected, preserving the original machine signal format. The final report
passed after 79 world ticks. Remote networking and GUI remain shared checklist
items; additional host/mod-container coverage does not claim D15 or D36 complete.

Experience upgrades now fire the original robot-analysis event on the real
persistent robot host and display the original XP/level summary. Item tooltips
and Lua use the same fractional-level formula. These D47/T01 changes pass the
Windows build. D47 subsequently closed in the Windows native run
`experience-20261027-120333`: actual bottles and registry-enchanted tools were
consumed, maximum level refused consumption, a real driver restored XP/fractional
level/address and 1234.5 local energy, and the real analyzer entrypoint emitted
one persistent-host event and one correctly formatted XP message. Actual item
tooltip data matched native fractional level. This exposed and fixed null-address
slot lookup during initial placement of a valid saved-XP upgrade before its node
joined a network. Actual remote transport and screen rendering remain C19/C23.

D48 closed in `generator-20261027-115447`: a real installed generator completed
16 native inventory/resource stages, including container partial acceptance,
drop remainders, full refusal/rollback and partial fuel removal. The world ticker
consumed priming fuel and advanced its timer; an independent actual driver and
connector graph verified configured generation and continuing to burn at full
capacity through the public update lifecycle. Creative robots automatically
refill their physical graph, so that graph was not used as generation evidence.
Registry-aware driver save/load retained the queue/timer/address, and physical
equipment removal dropped exactly three queued fuels and cleared the removed
item's saved queue/timer without duplicating containers.

I12 now includes world/provider and per-tank conversions plus the original
`minecraft` blacklist gates for converters, vanilla geolyzer events and vanilla
redstone input. The world's `id`/`name` table retains the original double-MD5
identity for vanilla dimension IDs 0/-1/1. Modern custom dimensions use stable
namespaced keys in place of removed numeric IDs. The remote-level seed-zero
fallback follows the original [Forge join-game construction](https://github.com/MinecraftForge/MinecraftForge/blob/1.12.x/patches/minecraft/net/minecraft/client/network/NetHandlerPlayClient.java.patch).
The Windows `vanilla-20261027-115447` and `vanilla-disabled-20261027-115447` runs
passed actual callbacks with all three real server dimensions, original UUID/name
fields, tank/NBT/item conversions, published geolyzer events and real redstone
input, including explicit blacklist suppression. Provider snapshots additionally
checked custom key/seed identity without manufacturing test worlds. I12 stays
open for remaining driver and recipe-platform acceptance.

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

World-fluid transfers now reject already-full source blocks and unsuccessful
placements instead of reporting fictitious capacity. The original zero-based
source-tank fallback and strict explicit item-slot validation are restored.
Agent item insertion starts at the selected slot before wrapping, keeping batch
crafting outputs out of the recipe grid when a later slot is selected. Crafting
also accepts smaller addon inventories, treating unavailable grid cells as empty.
GPU bitblt errors distinguish the particular missing buffer from a missing
screen. These corrections pass the Windows build; their representative fluid,
small-inventory and batch-crafting gameplay checks remain outstanding.

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
The fixed physical-host persistence requirement is C12. Additional architecture/host
combinations are further regression coverage; actual remote player and network
lifecycle remains open under C19.
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
can still migrate through their saved upgrade address. Original merchant search,
strict execution distance, native merchant completion and legacy Trade userdata
fields/class-name restoration now match the pinned source. The Windows scenario
`agent-actions-20261027-b2` passed real two-input villager payment/output/use-count
and insufficient-second-input atomicity, alongside a real vanilla cake recipe
and its three bucket remainders. See [crafting and trading](crafting-and-trading.md).

The same Windows scenario passed five real Robot air/solid-reference placement
paths, including Angel `use` drawing from backpack slot one without changing the
selected slot, and actual NeoForge nine-chunk tickets with disable, insufficient
power and node-removal cleanup. Hover height rules and Data/Linked card behavior
were matched against the pinned source; card tooltips and optional saved fields
were completed. These close D09, D22, D41, D44, D45, D59 and T07 in the fixed
120-item checklist. See [flight upgrades](flight-upgrades.md),
[chunkloader](chunkloader-upgrade.md) and [data/linked cards](data-and-linked-cards.md).

The device batch restores the missing installable Geolyzer and Transposer item
drivers. Inventory-controller database writes copy the real cargo stack before
the database normalizes its count; sucking now follows the original selected
slot through the end before wrapping. Transposer callbacks retain the original
slot errors, per-operation costs and real item/fluid transfer paths.

Network devices now retain cable color/item state and survival dyeing, original
non-tickable splitter input timing, complete saved port/queue restoration and
wireless movement indexing. Relay hardware creates and preserves its private
driver environments without adding them to its six forwarding graphs, and
hardware changes retain the relay's configured strength. Power-device changes
restore modern cats to the original ocelot group, charger tablet disconnect and
remount behavior, non-analyzable converters/distributors and double-valued timing.
MFU distance uses target centers, and remote environments disconnect before
their state is saved into a removed or dropped upgrade. Its missing client
target overlay is restored; visual acceptance stays under C20.

Windows NeoForge 21.1.224 run `device-batch-20261027-c3` passed after twenty world
ticks using actual Case/Robot/Adapter contexts and real inventory, fluid, energy
and network state. It covers both wireless-card tiers, splitter redstone graph
isolation, relay hardware state, capacitor adjacency/removal, independent power
balancing, charger tablet storage remount, live motion signals, waypoint
rotation/labels, mounted geolyzer costs/events, cargo/database copying and MFU
connection/removal/remount at the configured three-block boundary. The report
closes D05, D06, D08, D17, D25, D26, D27, D28, D29, D35, D40, D49, D51, D60
and D61 together with the fixed-source comparisons in
[network devices](network-devices.md), [power devices](power-devices.md),
[world sensors](world-sensors.md), [inventory control](inventory-control.md)
and [remote adapter](remote-adapter.md).

The external-addon fixture also passed an end-to-end IMC production contract:
the real assembler validates and consumes addon inputs, applies energy over
multiple work ticks and produces the requested item; the real disassembler
uses the addon callback and inserts its guaranteed drops into an adjacent chest.
This confirms those extension flows, not every built-in device recipe or model.

The host/storage/tool batch closes C11, D02, D03, D07, D11, D14, D32, D33,
D38, T01, T02, T03, T04, T05 and T16, bringing the fixed checklist to 82/120
complete and 38 remaining. Windows NeoForge 21.1.224 run
`host-storage-20261027-d3` passed in 67 actual world ticks. It covers real
filesystem quota/ownership/descriptor semantics, raw media, hot hardware and
blade removal, closed-before-save item drops, buffered HDD immediate remount,
network-buffered Case FE, production comparators, stored navigation-map recovery,
RAID transport/member changes, wrench/debugger and material interactions.
`storage-cold-20261027-d1` also passed native Lua 5.3 prepare/resume in separate
Windows JVMs, including a real chunk unload. See
[machine hosts](machine-hosts.md), [storage](storage-filesystems.md),
[production](production-devices.md) and [player tools](player-tools.md).

Windows client `manual-20261027-d2` passed real manual provider registration,
image/link clicks, original pages, scroll/history, Case context and item reset.
Three actual rendered screenshots were inspected. Legacy shared-item images and
lowercase packaged diagram paths resolve, while inline code uses the OC font.
See [player manual](player-manual.md). These runs do not change the shared
multiplayer, visual, translation or final release checklist items.

The standalone SDK has successfully compiled and published to Maven Local.
A separately compiled addon has passed on a Windows dedicated server: legacy
Converter binaries, registry-aware persistence, network and prefab lifecycles,
placed SimpleComponent discovery, and native Lua 5.3 / LuaJ callback conversion
and IMC program mappings. The new Windows client also starts and joins the
development world. Manual rendering and newly registered devices still need
interactive acceptance. The remote JitPack tag build has succeeded and its Maven
POM is available. The player manual retains the original book + tier-one chip
recipes; its page history, contextual navigation and interactions passed the
Windows client scenario above.

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
