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

The manual API now has a Java client backend, resource and addon providers,
language fallback, tabs and interactive Markdown rendering using the original
manual textures. It initializes on the physical client before addon setup
registration; dedicated servers keep `API.manual` null. This does not establish
parity for every manual item interaction or every optional addon.

Earlier Windows testing exposed texture/layout problems, fractional energy
accounting errors, host-thread deadlocks and native-library class-loader issues.
The source contains targeted corrections, including deferred server-thread dirty
updates and an FML game-library manifest around unchanged upstream JNLua classes.
These findings should not be presented as acceptance of all rendering, boot,
persistence or multiplayer behavior in the current tree.

Still required: completion and functional acceptance of robots/drones and their
upgrades; servers/racks, tablets/terminals and microcontrollers; the remaining
automation/power/production devices; holograms/printers/prints; remaining
item/block/component families; commands, manual item behavior, loot and
configuration parity; optional integrations; saved VM restoration; and full
client/server behavior. Existing menus, payloads, rendering, energy, persistence
and crafting also need functional acceptance.

A successful API build validates the SDK build. API compatibility checks cover
the specific contracts they exercise; they do not establish computer boot, saved
VM restoration, all host devices, automation, multiplayer synchronization,
graphics fidelity or a complete migration. The current API validation version is
`8.0.0-alpha.1`; a stable ABI has not been announced.

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
