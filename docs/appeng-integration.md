# Applied Energistics 2 integration

The OC behavior is reimplemented in Java from the twelve files under
[`integration/appeng`](https://github.com/MightyPirates/OpenComputers/tree/258943363ea8fc4ef14948e6bc6f67375061580c/src/main/scala/li/cil/oc/integration/appeng)
in MightyPirates/OpenComputers commit
`258943363ea8fc4ef14948e6bc6f67375061580c` (MIT). Modern AE behavior and method
signatures are taken only from the official AE2 19.2.14 source artifact. No
community OC port or Scala runtime is used.

## Implemented behavior

`AE2Integration.initializeDrivers()` is the safe optional common-setup entry
point, called after OC initializes its Driver API. It resolves AE classes only
after checking modern mod ID `ae2` and the original blacklist name
`appliedenergistics2`; blacklisting either name disables the integration.

The five block/part drivers preserve the original component names and priorities:

| Device | Component | Priority |
| --- | --- | --- |
| ME Controller | `me_controller` | 5 |
| ME Interface block | `me_interface` | 5 |
| ME Interface part | `me_interface` | 0 |
| ME Export Bus | `me_exportbus` | 2 |
| ME Import Bus | `me_importbus` | 1 |

Each driver also registers an EnvironmentProvider for JEI's real callback
documentation. Parts are matched on their cable host. Part configuration calls
take the original Minecraft side number 0–5, while the part interface's default
network connection uses the face opposite the adapter direction, as in the old
implementation.

Controllers and both interfaces expose all original NetworkControl callbacks:
CPU enumeration, craftables, stored items, filtered database storage, stored
fluids, average injection/usage, idle usage, maximum/current stored energy,
powered state and demand. Unified `AEKey`/`KeyCounter` services replace the old
separate item/fluid storage channels. Item lists include zero-stock craftable
entries, retain OC's existing item conversion schema and add `isCraftable` and
the actual long `size`. Filters support nested maps, numeric values and Lua
sequence tables. Database writes use a visible component on the same OC network,
retain occupied entries and honor one-based slots.

Block and part interfaces, import buses and export buses retain their respective
configuration getters/setters and the original short and explicit database
argument forms. They update the actual modern ConfigInventory, clear entries
when the database form is omitted and retain the half-second OC pause. This is
the existing item-descriptor contract; this port does not invent a database
fluid-descriptor format for modern AE fluid configurations.

`exportIntoSlot` uses the real selected export bus, adjacent sided item capability,
configuration, installed acceleration/fuzzy/capacity cards, AE power and machine
action source. It preserves the original speed sequence 1/8/32/64/96 and optional
one-based destination slot. Extraction happens before actual insertion. Any
remainder after a changing destination refuses insertion is returned to ME
storage; if that also refuses it, the remaining real item is dropped beside the
destination rather than duplicated or deleted. The returned count includes only
items accepted by the target inventory. Empty work returns `nil, "no items moved"`.

## Crafting values and persistence

`Craftable` retains `getItemStack`, `requesting` and
`request(amount, prioritizePower, cpuName)`. Requests start AE's real asynchronous
crafting calculation, wait outside the server thread and submit the resulting
plan on the server thread. Missing resources, submission errors and a replaced
grid produce a failed CraftingStatus; no synthetic success or placeholder job is
returned. CPU names and the original priority flag are passed to AE's submit API.

Modern AE2 discovers requesters through a grid-node service. Each active
Craftable therefore owns a non-world, zero-idle-power managed node advertising
`ICraftingRequester`, connected through the public GridHelper API to the actual
device node. Its owner ID is inherited from that device. MachineSource identifies
the real physical AE machine and has no player identity. Returning zero from
`insertCraftedItems` preserves the original requester's refusal to intercept the
final output; AE continues handling the remaining output in network storage.

Request links and their requester node data are persisted together with registry-
aware item data, dimension resource key, position and part face. Restored values
retry unloaded levels/chunks with the original exponential backoff, capped at
100 ticks, and rejoin their links through AE's public storage helper. They do not
force chunks to load. Idle requester nodes are destroyed after all their jobs
finish; active nodes are detached while their device is unavailable and cleaned
up on server stop.

CraftingStatus exposes the original `isCanceled`, `isDone` and `cancel` return
forms, persists the real link UUID and pairs it with the restored Craftable's
link in either load order. As in the original design, persistent link recovery
requires the Craftable and status userdata to survive with the VM. A plan that
was still computing when saved cannot serialize AE's Future; after reload the
status explicitly reports that interrupted calculation instead of staying in
`computing` forever. Neither a missing link nor an interrupted calculation is
reported as a completed job.

## Cells, tools and spatial movement

The cell converter uses the real StorageCells registry on a copy of an inspected
item. BasicCellInventory supplies all original field names: stored/remaining
types and counts, `getTotalItemTypes`, `getAvailableItems`, total/free/used bytes,
unused capacity, `canHoldNewItem`, fuzzy mode and name. Modern item and fluid keys
are converted through OC's registry-aware converters. Addon StorageCell objects
that expose only generic storage provide their actual stored content/count/name;
unsupported byte-capacity or fuzzy facts are omitted rather than fabricated.
The modern public generic StorageCell interface has no equivalent for those
extra metrics.

AE's old IAEWrench interface is replaced by its actual modern wrench/tag checks.
The OC usage hook respects player interaction restrictions and does not consume
or rotate an AE wrench by itself. The OC printed block is registered with AE's
public default block-entity move strategy, replacing the original spatial
movement whitelist entry and preserving its serialized data.

## Dependency and validation boundary

Use Maven Central and a separate compile-only dependency:

```groovy
compileOnly('org.appliedenergistics:appliedenergistics2:19.2.14') {
    transitive = false
}
```

The integration accesses public AE API types plus public concrete device/config/
cell classes where the generic API does not expose configuration or byte metrics.
It uses no private reflection or access transformer. Consequently compile against
the full official AE artifact without embedding it, its API or its dependencies
in the OC JAR. Optional development runtime should use a separate `localRuntime`
dependency with normal runtime transitives. OC metadata must declare `ae2` as an
optional dependency with both sides supported.

The selected official 19.2.14 runtime metadata requires Minecraft `[1.21.1]`,
NeoForge `[21.1.169,)` and GuideME `[21.1.1,)`; it is compatible with the project's
NeoForge 21.1.224. The official Maven POM supplies GuideME 21.1.1 at runtime.

The full Windows Java build passed. An isolated Windows dedicated-server run,
`appeng-20261027-084828`, then passed using official AE2 19.2.14 and GuideME 21.1.1.
A real Case's native Lua 5.3 VM discovered the actual Controller, block Interface
and multipart cable-host components through real Adapters. It queried filtered
stock, live CPU/energy and cell metrics; configured filters through a real OC
database; exported exactly eight iron with a speed card and imported eleven
copper while preserving item totals. The real crafting CPU, encoded oak-plank
pattern and Molecular Assembler completed a native Craftable request. The SDK
Craftable/CraftingStatus values also survived two registry-aware save/load
round-trips, and a completed job rejected cancellation.

The run exposed and fixed a modern type-only configuration conversion: AE stores
a valid bus filter with amount zero, so its returned descriptor must represent
that type with one item instead of converting it to air. Interface configurations
with positive amounts retain those amounts. Multipart adapters keep the original
highest-priority name and aggregate all part callbacks on one visible component;
their hidden child nodes are not additional public devices.

Remote multiplayer, AE client screens and spatial moves remain outside these
server scenarios.
OC-only server scenarios also started successfully without AE installed.

## Focused completion results

The existing appeng-20261027-084828 results are retained for the five driver
surfaces, ordinary import/export, database configuration, cells, power queries
and normal native crafting. AppliedEnergisticsPersistenceContract passed the
remaining three paths in the separate Windows game JVMs of ae2-20261027-h2:

- An undamaged pickaxe filter rejected a real damaged pickaxe. Installing the
  actual fuzzy card with IGNORE_ALL exported exactly that stack into the
  requested chest slot, retaining its damage and conserving the ME contents.
- A request acquired a real link on a busy crafting CPU. Calling the original
  status cancel changed that actual link to canceled.
- A second request sent real ore from a Pattern Provider into a vanilla
  furnace. Native Lua retained the actual Craftable and CraftingStatus in local
  variables while its VM was paused and Minecraft saved the world. A different
  server JVM reloaded that same world and resumed the native VM. The saved
  furnace and bottom-side Import Bus produced and returned the actual ingot,
  completing the original request UUID without another BIOS boot.

The observation component is installed through a public item driver in a real
Adapter and persists with that Adapter. It does not replace the AE requester,
crafting link, machine Context, furnace, output items or native userdata.
Preparation and resumption produce separate reports, and the preparation report
is finalized only after normal server shutdown. This contract reuses the
existing driver/cell/configuration evidence rather than repeating its matrix.

The [prepare report](../build/api-compat-addon/appeng-persistence/ae2-20261027-h2/prepare-results.txt)
passed after 251 ticks, and the
[resume report](../build/api-compat-addon/appeng-persistence/ae2-20261027-h2/resume-results.txt)
passed after 215 ticks. Both retained request UUID
982267ce-2c0c-46ee-9cb9-85953eb7dd1f and one native BIOS boot. The restored
Craftable requesting/getItemStack methods and completed status/cancel forms
also passed.

The only correction required by this completion run was the fixture's furnace
layout: vanilla furnaces accept ore from above, fuel from horizontal sides and
output extraction below. The Pattern Provider now feeds the furnace's top and
the Import Bus extracts from its bottom. The original production integration
already implemented fuzzy export, active cancellation and persistent requester
links correctly; no AE production change or repeat of the earlier interface
matrix was needed.
