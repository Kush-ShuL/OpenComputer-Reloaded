# Legacy integration targets

This inventory originally covered eight unfinished integrations: I02, I03, I05, I08, I09, I15, I16 and I19. I08 Forestry and I15 Railcraft have since been implemented and validated, leaving **six open integrations**: I02, I03, I05, I09, I16 and I19. OpenComputers behavior is taken exclusively from MightyPirates/OpenComputers commit `258943363ea8fc4ef14948e6bc6f67375061580c`. Official project pages and public source repositories were checked on 2026-10-27.

- No official usable Minecraft 1.21.1 NeoForge target/API was found for six original projects: I02 Charset, I03 CoFH, I05 ExtraCells, I09 IC², I16 Thaumcraft and I19 Wireless Redstone CBE.
- I08 Forestry is closed against the official Forestry: Community Edition alpha8 runtime, including its changed public genetics API and optional butterfly content.
- I15 Railcraft is closed against official Railcraft Reborn 1.2.15, with explicit mappings for its fuel-free, redstone-independent Worldspikes and both real tool callbacks.

The six remaining entries stay on the fixed checklist even though usable official modern targets were not found. Forestry's and Railcraft's completed mappings and evidence are retained below as the resolution of two former gaps.

## I02: Charset

Original sources: `integration/charset/ModCharset.scala`, `integration/charset/CapabilitiesCharset.java` and the Charset capability branches in `common/tileentity/traits/BundledRedstoneAware.scala` under the pinned `src/main/scala/li/cil/oc` and `src/main/java/li/cil/oc` trees.

- Registers a bundled-redstone provider. Ordinary input queries the neighboring block's opposite-face `IRedstoneEmitter` capability and caps its signal at 15; absent emitters return zero.
- Bundled input queries the corresponding `IBundledEmitter` and converts its byte channels to unsigned integer values; absent emitters return no bundled signal.
- OC bundled-redstone hosts expose `IBundledEmitter` and `IBundledReceiver` views of their per-face output. The receiver notification resamples that face's bundled input. This adds protocol interoperability rather than new Lua callbacks.

The [official repository](https://github.com/CharsetMC/Charset) defaults to `1.12-stable` and states that the mod is not actively maintained. The [author's Charset Lib release page](https://www.curseforge.com/minecraft/mc-mods/charset-lib) lists version 0.5.6.6 for Forge 1.12.2. Implementing the original bridge on 1.21.1 requires an actual modern Charset capability contract and runtime; compiling against its old Forge capability types would not provide that bridge.

## I05: ExtraCells (`ec`)

The original module is ExtraCells, mod ID `extracells`, with a minimum version of 2.5.2; it is unrelated to ExtendedCrafting. Original sources: `integration/ec/{ModExtraCells,DriverController,DriverBlockInterface,NetworkControl,ECUtil}.scala`.

- Registers drivers and item environment providers for AE ME controllers and block interfaces, exposing `me_controller` and `me_interface` environments at the internal AE location.
- Adds `getGasesInNetwork()`: obtains the real grid's EC gas storage channel, excludes null entries and returns each stored gas stack. It returns an empty table when the EC gas system is disabled.
- `ECUtil.canSeeFluidInNetwork` is defined but has no caller in the pinned source; it does not establish an additional observable fluid-filtering behavior.

The [official ExtraCells2 release page](https://www.curseforge.com/minecraft/mc-mods/extracells2) is marked abandoned and lists 2.6.5 for Forge 1.12.2. Its [official build configuration](https://github.com/ExtraCells/ExtraCells2/blob/rv6-1.12/build.properties) targets Minecraft 1.12.2 and AE2 rv6. A usable modern ExtraCells gas-stack/storage API is needed before these environments can be connected to real storage. A different AE2 add-on or ExtendedCrafting does not implement this target.

## I16: Thaumcraft

Original sources: `integration/thaumcraft/{ModThaumcraft,ConverterThaumcraftItems}.scala`. Initialization registers only the item converter; it does not add research, wand or block drivers.

- Recognizes exactly `thaumcraft:jar_normal`, `thaumcraft:jar_void`, `thaumcraft:phial` and `thaumcraft:crystal_essence`.
- Converts each compound in the item's `Aspects` list into an `aspects` entry containing `aspect` from `key` and integer `amount`.
- Exposes the string `AspectFilter` as `aspectFilter` when present.

The [author's release page](https://www.curseforge.com/minecraft/mc-mods/thaumcraft) lists Thaumcraft 6.1.BETA26 for Minecraft 1.12.2; the linked [official API repository](https://github.com/Azanor/thaumcraft-api) does not provide a 1.21.1 NeoForge target. The converter has no foreign API types, so its original data-reading logic can be migrated independently. The actual official modern item data representation and runtime interoperability still require a corresponding release; legacy custom data alone is insufficient evidence of that interoperability.

The original converter is now migrated to Java in `common/integration/thaumcraft`: it reads the same four item IDs and legacy fields from modern `CUSTOM_DATA`, and registers only when the real `thaumcraft` mod is loaded and not blacklisted. No substitute items or guessed modern data components are added. I16 remains open until an official matching runtime can verify interoperability.

## I19: Wireless Redstone CBE

Original sources: `integration/wrcbe/{ModWRCBE,WirelessRedstoneCBE}.scala`, with the receiving/transmitting-device implementation in `server/component/RedstoneWireless.scala`.

- Adds the WRCBE backend to the wireless-redstone systems. It registers/removes receiving and transmitting devices through `RedstoneEther.server`; transmitters require output enabled and a positive frequency. A positive-frequency receiver samples the Ether's current frequency state when registered.
- Preserves the wireless card's input/output/frequency getters and setters. Output changes update transmitter registration, return the old value and apply the configured redstone delay. Frequency changes remove old registrations, clear input/output, register the receiver at the new integer frequency, return the old frequency and pause for 0.5 seconds.
- Matching-frequency input changes update the cached boolean and emit the existing wireless `redstone_changed` event. Device position and dimension come from the real host.
- Connection schedules registration; disconnection removes both device registrations and clears output/frequency. Frequency, input and output are persisted.

The [official release page](https://www.curseforge.com/minecraft/mc-mods/wireless-redstone-cbe) lists 1.12.2-2.3.2.33. The [official repository's build configuration](https://github.com/TheCBProject/WirelessRedstone/blob/master/build.properties) targets Minecraft 1.12.2. A modern WRCBE Ether and device API is required for the real transport bridge. Other wireless-redstone projects do not substitute for this protocol.

## I03: CoFH

Original sources: `integration/cofh/item/{ModCoFHItem,EventHandlerCoFH}.scala` and `integration/cofh/tileentity/{ModCoFHTileEntity,DriverEnergyInfo,DriverRedstoneControl,DriverSecureTile,DriverSteamInfo}`.

- The hammer bridge checks `IToolHammer`, calls `isUsable` without consuming durability, and calls `toolUsed` when durability should change.
- Four block drivers expose stored energy and energy rates, redstone control reads/writes, owner/access checks and steam rates. These are separate from generic FE energy storage.

The official [CoFH Core build configuration](https://github.com/CoFH/CoFHCore/blob/1.20.x/gradle.properties) currently declares version 11.0.2 for Minecraft 1.20.4 and NeoForge 20.4.237; the [download page](https://teamcofh.com/downloads/) lists the released 1.20 generation. No official 1.21.1 target was found. Modern control interfaces have also moved to `cofh.lib.api.control`; an actual 1.21.1 runtime and its hammer/information contracts are needed before the original bridge can be implemented and exercised.

## I08: Forestry — completed

Original sources: `integration/forestry/{ModForestry,DriverAnalyzer,ConverterItemStack}.scala` and `{DriverBeeHouse,ConverterIAlleles,ConverterIIndividual}.java`.

- The analyzer exposes working state, progress and the displayed individual. Bee housing exposes breeding availability, drone/queen, mutation data, mutation-result species and parents.
- Three converters expose mutation/species information; analyzed active/inactive bee, tree and butterfly genes; living-individual information; and individual/circuit data attached to actual items.

[Forestry: CE](https://www.curseforge.com/minecraft/mc-mods/forestry-community-edition) publishes `1.21.1-3.0.0-alpha8` for NeoForge. The actual release JAR declares NeoForge `[21.1,)` and Patchouli `[1.21.1-92,)`. Its [build configuration](https://github.com/thedarkcolour/ForestryCE/blob/1.21.1/gradle.properties) uses NeoForge 21.1.230 for development; that is not the release's minimum runtime. The official alpha8 core, optional butterfly module and Patchouli 92 loaded successfully on this project's NeoForge 21.1.224. This is the Community Edition project, distinct from the original ForestryMC repository.

The [public entry point](https://github.com/thedarkcolour/ForestryCE/blob/1.21.1/src/main/java/forestry/api/IForestryApi.java) supplies genetics and circuit managers. `forestry.api.core.genetics` replaces the old genetics package; `IIndividualHandlerItem` reads actual item individuals, and the integration converts the typed genome/allele pairs back to the original Lua table fields. `IBeeHousing` remains public. The analyzer is now [`forestry.core.content.analyzer.TileAnalyzer`](https://github.com/thedarkcolour/ForestryCE/blob/1.21.1/src/main/java/forestry/core/content/analyzer/TileAnalyzer.java), retaining `hasWork`, `getIndividualOnDisplay` and inherited progress access. The butterfly content module remains optional.

The individual `forestry.native` result in [completion-20261027-h1](../build/api-compat-addon/devices/completion-20261027-h1/device-results.txt) passed all nine real analyzer/apiary callbacks through a native Lua 5.3 Case and Adapter. The registered converters passed real bee, tree and butterfly individuals, analyzed/unanalysed and active/inactive genes, numeric and territory shapes, mutation/species information, item individuals and official-factory circuit IDs. That report's overall result was FAIL from a separate recipe-fixture timeout; the Forestry result passed. The recipe focus subsequently passed in completion-20261027-h4. See [Forestry integration](forestry-integration.md) for the implementation and bounded acceptance.

## I09: IndustrialCraft²

Original sources: all ten files in `integration/ic2/`, including the electric-item manager/converter, event handler and six block drivers.

- Registers EU item charging, electric durability and wrench callbacks. Robot tool-use events adjust actual electric-item charge loss using the configured damage rate; the item converter exposes charge, capacity, tier, transfer limit and energy-output ability.
- Six drivers expose block energy/capacity/tiers, conductor properties, mass-fabricator progress and reactor/chamber/redstone-port heat and output. The OC electric-item manager also allows compatible external charging of OC chargeable items.

The [original project's official page](https://wiki.industrial-craft.net/?title=Main_Page) and [official forum download links](https://forum.industrial-craft.net/thread/17202-industrial-craft-2-port-to-1-20/) still identify the 2.8.x line for Minecraft 1.12. No official 1.21.1 NeoForge target/API was confirmed. The forum discusses independent remakes, and one linked remake author explicitly identifies its target as 1.20.1 Forge; that does not supply the requested runtime. A selected real modern target must provide corresponding EU item management, conductor and reactor contracts before this full family can be mapped. Generic FE compatibility alone does not cover it.

## I15: Railcraft

Original sources: `integration/railcraft/{ModRailcraft,EventHandlerRailcraft,DriverWorldspike}.scala`.

- The crowbar bridge checks `IToolCrowbar`, calls `canWhack` without consuming durability and calls `onWhack` when durability should change.
- The `worldspike` driver has five callbacks: fuel amount, owner name, anchor type (`world`, `admin`, `personal`, `passive`), actual fuel-slot contents and redstone-disabled state.

Railcraft Reborn's official [1.2.15 tag configuration](https://github.com/railcraft-reborn/railcraft/blob/1.2.15/gradle.properties) targets Minecraft 1.21.1, uses NeoForge 21.1.228 for development, and declares a runtime minimum of 21.1.50. Its public [`Crowbar` API](https://github.com/railcraft-reborn/railcraft/blob/1.2.15/src/api/java/mods/railcraft/api/item/Crowbar.java) retains `canWhack` and `onWhack`; the consuming callback now takes `ServerPlayer`. The Java bridge invokes those real callbacks and reads the owner's profile through the inherited public `Ownable` contract.

Modern [`WorldSpikeBlock`](https://github.com/railcraft-reborn/railcraft/blob/1.2.15/src/main/java/mods/railcraft/world/level/block/worldspike/WorldSpikeBlock.java) forces surrounding chunks on placement, while [`WorldSpikeBlockEntity`](https://github.com/railcraft-reborn/railcraft/blob/1.2.15/src/main/java/mods/railcraft/world/level/block/entity/worldspike/WorldSpikeBlockEntity.java) supplies particles and ticket validation. They provide no fuel inventory or redstone-disable mechanism and have only world/personal variants. All five original OC queries remain: fuel returns zero, fuel-slot contents return no values, owner/type use the real entity, and disabled returns false because actual redstone does not revoke these tickets. Old admin/passive types have no modern blocks; the bridge does not invent them.

Windows dedicated-server reports `railcraft-20261027-j1` and `railcraft-disabled-20261027-j1` both passed in six world ticks, under `build/api-compat-addon/devices/<run-id>/device-results.txt`. They exercise the actual Case/Adapter callbacks, official crowbar and durability, real BlockItem owner assignment, each real spike owner's nine persistent tickets through redstone changes, physical removal, and the integration blacklist. The blacklist suppresses OC drivers/tools while leaving Railcraft's own tickets intact. I15 is closed for this original integration scope; details and remaining client/multiplayer boundaries are in [Railcraft integration](railcraft-integration.md).
