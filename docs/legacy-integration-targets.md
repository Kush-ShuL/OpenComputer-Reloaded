# Legacy integration targets

On **2026-10-28**, the user explicitly instructed **“没有的就不管，绕过继续做”** in response to the six unavailable integrations: I02 Charset, I03 CoFH, I05 ExtraCells, I09 IC², I16 Thaumcraft and I19 Wireless Redstone CBE. These six original checklist IDs are now **SKIPPED by user request**, outside the active completion scope. They are not claimed as implemented or runtime-validated. The fixed 120-ID checklist currently records 111 completed, six skipped and three active reviews: C19, C20 and C24.

This inventory originally covered those six integrations plus I08 Forestry and I15 Railcraft, both since implemented and validated. OpenComputers behavior is taken exclusively from MightyPirates/OpenComputers commit `258943363ea8fc4ef14948e6bc6f67375061580c`. The source inventory and prior target research below are retained as deferred reference, not as a request for more integration work. Official project pages and public source repositories were checked on 2026-10-27; CoFH, IC² and WRCBE were rechecked on 2026-10-28 before the scope change.

- No official usable Minecraft 1.21.1 NeoForge target/API was found for six original projects: I02 Charset, I03 CoFH, I05 ExtraCells, I09 IC², I16 Thaumcraft and I19 Wireless Redstone CBE.
- The 2026-10-28 check found a third-party IC² Experimental port, IC2R 21.1.66, but its own notice documents unresolved underlying code/asset authorization. It was not introduced as a dependency. I09 is skipped along with the other five integrations rather than adopting that candidate or treating it as completed.
- I08 Forestry is closed against the official Forestry: Community Edition alpha8 runtime, including its changed public genetics API and optional butterfly content.
- I15 Railcraft is closed against official Railcraft Reborn 1.2.15, with explicit mappings for its fuel-free, redstone-independent Worldspikes and both real tool callbacks.

The six skipped entries remain visible on the fixed checklist; no further foreign-runtime research or portable implementation is scheduled for them. Forestry's and Railcraft's completed mappings and evidence remain part of the completed scope.

## I02: Charset — SKIPPED

Original sources: `integration/charset/ModCharset.scala`, `integration/charset/CapabilitiesCharset.java` and the Charset capability branches in `common/tileentity/traits/BundledRedstoneAware.scala` under the pinned `src/main/scala/li/cil/oc` and `src/main/java/li/cil/oc` trees.

- Registers a bundled-redstone provider. Ordinary input queries the neighboring block's opposite-face `IRedstoneEmitter` capability and caps its signal at 15; absent emitters return zero.
- Bundled input queries the corresponding `IBundledEmitter` and converts its byte channels to unsigned integer values; absent emitters return no bundled signal.
- OC bundled-redstone hosts expose `IBundledEmitter` and `IBundledReceiver` views of their per-face output. The receiver notification resamples that face's bundled input. This adds protocol interoperability rather than new Lua callbacks.

The [official repository](https://github.com/CharsetMC/Charset) defaults to `1.12-stable` and states that the mod is not actively maintained. The [author's Charset Lib release page](https://www.curseforge.com/minecraft/mc-mods/charset-lib) lists version 0.5.6.6 for Forge 1.12.2. Implementing the original bridge on 1.21.1 requires an actual modern Charset capability contract and runtime; compiling against its old Forge capability types would not provide that bridge.

## I05: ExtraCells (`ec`) — SKIPPED

The original module is ExtraCells, mod ID `extracells`, with a minimum version of 2.5.2; it is unrelated to ExtendedCrafting. Original sources: `integration/ec/{ModExtraCells,DriverController,DriverBlockInterface,NetworkControl,ECUtil}.scala`.

- Registers drivers and item environment providers for AE ME controllers and block interfaces, exposing `me_controller` and `me_interface` environments at the internal AE location.
- Adds `getGasesInNetwork()`: obtains the real grid's EC gas storage channel, excludes null entries and returns each stored gas stack. It returns an empty table when the EC gas system is disabled.
- `ECUtil.canSeeFluidInNetwork` is defined but has no caller in the pinned source; it does not establish an additional observable fluid-filtering behavior.

The [official ExtraCells2 release page](https://www.curseforge.com/minecraft/mc-mods/extracells2) is marked abandoned and lists 2.6.5 for Forge 1.12.2. Its [official build configuration](https://github.com/ExtraCells/ExtraCells2/blob/rv6-1.12/build.properties) targets Minecraft 1.12.2 and AE2 rv6. A usable modern ExtraCells gas-stack/storage API is needed before these environments can be connected to real storage. A different AE2 add-on or ExtendedCrafting does not implement this target.

## I16: Thaumcraft — SKIPPED

Original sources: `integration/thaumcraft/{ModThaumcraft,ConverterThaumcraftItems}.scala`. Initialization registers only the item converter; it does not add research, wand or block drivers.

- Recognizes exactly `thaumcraft:jar_normal`, `thaumcraft:jar_void`, `thaumcraft:phial` and `thaumcraft:crystal_essence`.
- Converts each compound in the item's `Aspects` list into an `aspects` entry containing `aspect` from `key` and integer `amount`.
- Exposes the string `AspectFilter` as `aspectFilter` when present.

The [author's release page](https://www.curseforge.com/minecraft/mc-mods/thaumcraft) lists Thaumcraft 6.1.BETA26 for Minecraft 1.12.2; the linked [official API repository](https://github.com/Azanor/thaumcraft-api) does not provide a 1.21.1 NeoForge target. The converter has no foreign API types, so its original data-reading logic can be migrated independently. The actual official modern item data representation and runtime interoperability still require a corresponding release; legacy custom data alone is insufficient evidence of that interoperability.

The original converter was migrated to Java in `common/integration/thaumcraft` before the scope change: it reads the same four item IDs and legacy fields from modern `CUSTOM_DATA`, and registers only when the real `thaumcraft` mod is loaded and not blacklisted. That existing source is retained, without substitute items or guessed modern data components. It has not been validated against a matching runtime and does not turn the skipped I16 entry into a completed integration.

## I19: Wireless Redstone CBE — SKIPPED

Original sources: `integration/wrcbe/{ModWRCBE,WirelessRedstoneCBE}.scala`, with the receiving/transmitting-device implementation in `server/component/RedstoneWireless.scala`.

- Adds the WRCBE backend to the wireless-redstone systems. It registers/removes receiving and transmitting devices through `RedstoneEther.server`; transmitters require output enabled and a positive frequency. A positive-frequency receiver samples the Ether's current frequency state when registered.
- Preserves the wireless card's input/output/frequency getters and setters. Output changes update transmitter registration, return the old value and apply the configured redstone delay. Frequency changes remove old registrations, clear input/output, register the receiver at the new integer frequency, return the old frequency and pause for 0.5 seconds.
- Matching-frequency input changes update the cached boolean and emit the existing wireless `redstone_changed` event. Device position and dimension come from the real host.
- Connection schedules registration; disconnection removes both device registrations and clears output/frequency. Frequency, input and output are persisted.

The [official release page](https://www.curseforge.com/minecraft/mc-mods/wireless-redstone-cbe) lists 1.12.2-2.3.2.33. The [official repository's build configuration](https://github.com/TheCBProject/WirelessRedstone/blob/master/build.properties) targets Minecraft 1.12.2. A modern WRCBE Ether and device API is required for the real transport bridge. Other wireless-redstone projects do not substitute for this protocol.

On 2026-10-28 the [official branch list](https://api.github.com/repos/TheCBProject/WirelessRedstone/branches?per_page=100) still contained only `1.10.2`, `1.11.2`, `1.12.x` and `master`; the release page still identified the 2019 1.12.2 file. No renamed modern WRCBE transport was identified in those primary sources.

## I03: CoFH — SKIPPED

Original sources: `integration/cofh/item/{ModCoFHItem,EventHandlerCoFH}.scala` and `integration/cofh/tileentity/{ModCoFHTileEntity,DriverEnergyInfo,DriverRedstoneControl,DriverSecureTile,DriverSteamInfo}`.

- The hammer bridge checks `IToolHammer`, calls `isUsable` without consuming durability, and calls `toolUsed` when durability should change.
- Four block drivers expose stored energy and energy rates, redstone control reads/writes, owner/access checks and steam rates. These are separate from generic FE energy storage.

The official [CoFH Core build configuration](https://github.com/CoFH/CoFHCore/blob/1.20.x/gradle.properties) currently declares version 11.0.2 for Minecraft 1.20.4 and NeoForge 20.4.237; the [download page](https://teamcofh.com/downloads/) lists the released 1.20 generation. No official 1.21.1 target was found. Modern control interfaces have also moved to `cofh.lib.api.control`; an actual 1.21.1 runtime and its hammer/information contracts are needed before the original bridge can be implemented and exercised.

The 2026-10-28 [official branch check](https://api.github.com/repos/CoFH/CoFHCore/branches?per_page=100) found `1.16.5`, `1.18.2`, `1.19.x`, `1.20.x` and `1.20.1`, with no 1.21 branch. The unchanged `1.20.x` build properties and official download page do not substantiate third-party catalogue claims of a 1.21.1 build. Modern CoFH also uses mod ID `cofh_core`, unlike the original `cofhcore`; a future bridge must use the actual selected runtime's identity and interfaces.

## I08: Forestry — completed

Original sources: `integration/forestry/{ModForestry,DriverAnalyzer,ConverterItemStack}.scala` and `{DriverBeeHouse,ConverterIAlleles,ConverterIIndividual}.java`.

- The analyzer exposes working state, progress and the displayed individual. Bee housing exposes breeding availability, drone/queen, mutation data, mutation-result species and parents.
- Three converters expose mutation/species information; analyzed active/inactive bee, tree and butterfly genes; living-individual information; and individual/circuit data attached to actual items.

[Forestry: CE](https://www.curseforge.com/minecraft/mc-mods/forestry-community-edition) publishes `1.21.1-3.0.0-alpha8` for NeoForge. The actual release JAR declares NeoForge `[21.1,)` and Patchouli `[1.21.1-92,)`. Its [build configuration](https://github.com/thedarkcolour/ForestryCE/blob/1.21.1/gradle.properties) uses NeoForge 21.1.230 for development; that is not the release's minimum runtime. The official alpha8 core, optional butterfly module and Patchouli 92 loaded successfully on this project's NeoForge 21.1.224. This is the Community Edition project, distinct from the original ForestryMC repository.

The [public entry point](https://github.com/thedarkcolour/ForestryCE/blob/1.21.1/src/main/java/forestry/api/IForestryApi.java) supplies genetics and circuit managers. `forestry.api.core.genetics` replaces the old genetics package; `IIndividualHandlerItem` reads actual item individuals, and the integration converts the typed genome/allele pairs back to the original Lua table fields. `IBeeHousing` remains public. The analyzer is now [`forestry.core.content.analyzer.TileAnalyzer`](https://github.com/thedarkcolour/ForestryCE/blob/1.21.1/src/main/java/forestry/core/content/analyzer/TileAnalyzer.java), retaining `hasWork`, `getIndividualOnDisplay` and inherited progress access. The butterfly content module remains optional.

The individual `forestry.native` result in [completion-20261027-h1](../build/api-compat-addon/devices/completion-20261027-h1/device-results.txt) passed all nine real analyzer/apiary callbacks through a native Lua 5.3 Case and Adapter. The registered converters passed real bee, tree and butterfly individuals, analyzed/unanalysed and active/inactive genes, numeric and territory shapes, mutation/species information, item individuals and official-factory circuit IDs. That report's overall result was FAIL from a separate recipe-fixture timeout; the Forestry result passed. The recipe focus subsequently passed in completion-20261027-h4. See [Forestry integration](forestry-integration.md) for the implementation and bounded acceptance.

## I09: IndustrialCraft² — SKIPPED

Original sources: all ten files in `integration/ic2/`, including the electric-item manager/converter, event handler and six block drivers.

- Registers EU item charging, electric durability and wrench callbacks. Robot tool-use events adjust actual electric-item charge loss using the configured damage rate; the item converter exposes charge, capacity, tier, transfer limit and energy-output ability.
- Six drivers expose block energy/capacity/tiers, conductor properties, mass-fabricator progress and reactor/chamber/redstone-port heat and output. The OC electric-item manager also allows compatible external charging of OC chargeable items.

The [original project's official page](https://wiki.industrial-craft.net/?title=Main_Page) does not confirm an original-team 1.21.1 NeoForge target. [IC2 Classic's publisher page](https://www.curseforge.com/minecraft/mc-mods/ic2-classic), rechecked on 2026-10-28, lists `IC2Classic-1.19.2-2.1.3.4` for Forge as its current main file; it is not a 1.21.1 target.

The [original IC² forum discussion](https://forum.industrial-craft.net/thread/17202-industrial-craft-2-port-to-1-20/?postID=220822) links HalfCooler's IC2R, now maintained at `neo-industrial-mc/IC2R`. Its own [21.1.66 release](https://github.com/neo-industrial-mc/IC2R/releases/tag/21.1.66) provides `ic2-neoforge-21.1.66.jar` (7,952,291 bytes; published SHA-256 `78f5fa96e165c468662456f9d4500a63176e4a65944568d208a4a6b251db9223`). The [tagged build properties](https://github.com/neo-industrial-mc/IC2R/blob/21.1.66/gradle.properties) target Minecraft 1.21.1 and NeoForge 21.1.234 for development, with mod ID `ic2`. This is a real modern foreign-mod candidate, not an OC implementation source.

The same tag retains public [`ElectricItem`](https://github.com/neo-industrial-mc/IC2R/blob/21.1.66/src/main/java/ic2/api/item/ElectricItem.java), `IElectricItem/IElectricItemManager`, [`IEnergyStorage`](https://github.com/neo-industrial-mc/IC2R/blob/21.1.66/src/main/java/ic2/api/tile/IEnergyStorage.java), [`IEnergyConductor`](https://github.com/neo-industrial-mc/IC2R/blob/21.1.66/src/main/java/ic2/api/energy/tile/IEnergyConductor.java), [`IReactor`](https://github.com/neo-industrial-mc/IC2R/blob/21.1.66/src/main/java/ic2/api/reactor/IReactor.java), `IReactorChamber` and `IWrenchAble`. The actual reactor redstone-port block entity is present, as is [`TileEntityMatter`](https://github.com/neo-industrial-mc/IC2R/blob/21.1.66/src/main/java/ic2/core/block/machine/tileentity/TileEntityMatter.java), the exact concrete class used by original `DriverMassFab`. The README's removed item-UU-Matter/legacy Mass-Fabricator content must not be confused with removal of this class. These are concrete contracts for the original EU item charge/durability/conversion, storage, cable, mass-fabricator and reactor callbacks, rather than substituting FE; actual registered blocks and behavior still require runtime validation.

IC2R's [tagged README copyright notice](https://github.com/neo-industrial-mc/IC2R/blob/21.1.66/README.md#copyright-notice) states that it decompiled original IC² builds, is not an official successor, and applies AGPL-3.0 to its own additions while acknowledging it cannot grant rights to the underlying code/assets. No IC2R binary, implementation source or dependency was introduced. This authorization question is retained as research context; I09 is now skipped by the user's explicit instruction, with no further adoption work scheduled.

## Deferred implementation notes

The following possibilities were recorded before the user skipped these six integrations. They are historical planning notes, not active tasks or completion evidence.

| Open item | Independently portable original behavior | What still needs a real foreign contract |
| --- | --- | --- |
| I02 Charset | Existing OC side/channel resampling can be reused; unsigned-byte channel conversion and 0–15 redstone limiting are small internal operations. | Actual modern emitter/receiver capabilities, their notifications and registration. A locally invented capability with a similar name would not connect Charset. |
| I03 CoFH | No original converter reads standalone item NBT. Callback names, argument parsing and result shapes can be translated when bound to real interfaces. | Hammer `isUsable/toolUsed`, energy/steam information, owner access and actual redstone-control enums all come from CoFH objects. Adding disconnected wrappers now would not implement those operations. |
| I05 ExtraCells | Existing AE2 environment setup can be reused. | `getGasesInNetwork` needs the actual EC gas storage channel, enabled-state and gas stack representation. Empty tables from a guessed channel would hide the missing integration. |
| I09 IC² | Original chargeable-item accounting can use the existing `Chargeable` SDK plus `Settings.ratioIndustrialCraft2`: simulated charge reads, EU conversion and the original tablet transfer cap do not themselves need foreign classes. | Registration as an electric-item manager, electric-item properties, wrench behavior and all six block drivers need the selected actual API. The original unsupported discharge/use paths must not be expanded into invented power extraction. |
| I16 Thaumcraft | The original four-ID `CUSTOM_DATA` item converter is already migrated. | Actual matching items and their authoritative modern data representation remain unverified. |
| I19 WRCBE | Original wireless frequency/input/output persistence, old-value returns, output-change delay, frequency-change reset/pause and `redstone_changed` emission can be separated into internal Java state. | Device registration/removal, initial frequency sampling and cross-host delivery require real WRCBE Ether receiving/transmitting interfaces. Internal state alone must not be presented as working wireless transport. |

These six entries remain **SKIPPED**, preserving their original IDs and the distinction between source possibilities and actual runtime interoperability. Active work continues on C19, C20 and C24.

## I15: Railcraft

Original sources: `integration/railcraft/{ModRailcraft,EventHandlerRailcraft,DriverWorldspike}.scala`.

- The crowbar bridge checks `IToolCrowbar`, calls `canWhack` without consuming durability and calls `onWhack` when durability should change.
- The `worldspike` driver has five callbacks: fuel amount, owner name, anchor type (`world`, `admin`, `personal`, `passive`), actual fuel-slot contents and redstone-disabled state.

Railcraft Reborn's official [1.2.15 tag configuration](https://github.com/railcraft-reborn/railcraft/blob/1.2.15/gradle.properties) targets Minecraft 1.21.1, uses NeoForge 21.1.228 for development, and declares a runtime minimum of 21.1.50. Its public [`Crowbar` API](https://github.com/railcraft-reborn/railcraft/blob/1.2.15/src/api/java/mods/railcraft/api/item/Crowbar.java) retains `canWhack` and `onWhack`; the consuming callback now takes `ServerPlayer`. The Java bridge invokes those real callbacks and reads the owner's profile through the inherited public `Ownable` contract.

Modern [`WorldSpikeBlock`](https://github.com/railcraft-reborn/railcraft/blob/1.2.15/src/main/java/mods/railcraft/world/level/block/worldspike/WorldSpikeBlock.java) forces surrounding chunks on placement, while [`WorldSpikeBlockEntity`](https://github.com/railcraft-reborn/railcraft/blob/1.2.15/src/main/java/mods/railcraft/world/level/block/entity/worldspike/WorldSpikeBlockEntity.java) supplies particles and ticket validation. They provide no fuel inventory or redstone-disable mechanism and have only world/personal variants. All five original OC queries remain: fuel returns zero, fuel-slot contents return no values, owner/type use the real entity, and disabled returns false because actual redstone does not revoke these tickets. Old admin/passive types have no modern blocks; the bridge does not invent them.

Windows dedicated-server reports `railcraft-20261027-j1` and `railcraft-disabled-20261027-j1` both passed in six world ticks, under `build/api-compat-addon/devices/<run-id>/device-results.txt`. They exercise the actual Case/Adapter callbacks, official crowbar and durability, real BlockItem owner assignment, each real spike owner's nine persistent tickets through redstone changes, physical removal, and the integration blacklist. The blacklist suppresses OC drivers/tools while leaving Railcraft's own tickets intact. I15 is closed for this original integration scope; details and remaining client/multiplayer boundaries are in [Railcraft integration](railcraft-integration.md).
