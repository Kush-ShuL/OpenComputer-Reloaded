# Jade integration

This Java migration follows the two Waila files in
MightyPirates/OpenComputers commit
[`258943363ea8fc4ef14948e6bc6f67375061580c`](https://github.com/MightyPirates/OpenComputers/tree/258943363ea8fc4ef14948e6bc6f67375061580c/src/main/scala/li/cil/oc/integration/waila)
(`BlockDataProvider.scala`, `ModWaila.scala`, MIT). The original `NotAnalyzable`
implementations are Cable, PowerConverter and PowerDistributor. The repository's
upstream MIT license and attribution remain applicable. No Scala runtime or
community OpenComputers port is used.

Modern API signatures come from
[Snownee/Jade's official 1.21 NeoForge branch](https://github.com/Snownee/Jade/tree/1.21-neoforge/src/main/java/snownee/jade/api)
and its [1.21 migration guide](https://jademc.readthedocs.io/en/1.x/plugins20/changes/121/).
The branch targets Minecraft 1.21.1. The author also uses the Modrinth Maven
coordinate below in
[Snownee/JadeAddons](https://github.com/Snownee/JadeAddons/blob/1.21-neoforge/build.gradle).

## Player behavior

Install Jade on the client and server for OC's server-provided network details.
Without server-side Jade the custom OC fields are absent; the client never
constructs substitute network values.

- Address, stored local energy and component name retain the three original
  independent, enabled-by-default switches. Their modern Jade keys are
  `opencomputers:address`, `opencomputers:energy` and
  `opencomputers:component_name`, respectively. They correspond to the old
  `oc.address`, `oc.energy` and `oc.componentName` options.
- Sided environments use the node on the actual world-facing side under the
  crosshair. Assembler, hologram, keyboard and screen information appears from
  every side, matching the original exceptions.
- Relay information includes its actual component address for that side and
  wireless strength, followed by the original generic plug-node information.
  Missing/unloaded component addresses remain empty slots in a six-side snapshot
  so other addresses cannot shift onto the wrong side.
- A busy assembler displays its actual progress, remaining minutes/seconds and
  the pending output's display name, including custom names. The server sends a
  serialized text component so translation remains in the player's language.
  The pending stack is copied; no inventory state is exposed to tooltip code.
- Chargers show their real charge speed. Disk drives replace their own address
  with the inserted filesystem's address and show no filesystem address when
  empty.
- Nodes with reachability `None`, plus the original three `NotAnalyzable` block
  types, do not expose node data. Local connector energy is truncated to integers
  as in the original provider; this does not substitute global energy or FE.
- Rack-specific Waila information was TODO in the pinned source. Its sided node
  information remains suppressed. No additional player, owner, inventory or
  machine fields are invented.

The original translated analyzer/progress keys remain in use. Only the original
hardcoded `Building: ` prefix is given a translation key in the Java migration.
The original localization helpers' numeric formatting is retained: wireless
strength and progress truncate to integers, charge speed becomes an integer
percentage, and remaining time uses minutes and two-digit seconds.

## Loading, data and configuration

`li.cil.oc.client.integration.jade.OpenComputersJadePlugin` is discovered by
Jade's `@WailaPlugin("jade")` annotation. It registers a common server data
provider and a separate client tooltip provider. **Do not reference this plugin
or either provider from OC common startup.** No entrypoint, IMC message,
`jade_plugins.json` or mixin is required.

`JadeIntegration` contains no Jade or client API types. Missing Jade short-circuits
the gate. Both the historical `waila` blacklist name and modern `jade` name in
`integration.modBlacklist` disable registration, data requests, data production
and tooltip callbacks, ignoring case and surrounding spaces. Restart after
changing the mod blacklist to register providers again.

The server provider lives in the historical client integration package but
imports no Minecraft client classes or client tooltip API. Jade requests data
only for OC blocks with block entities. The original field names are enclosed
in `opencomputers:waila` to avoid collisions with Jade and other providers.
Relay addresses and the assembler output use lightweight read-only snapshots;
the relay strength uses its scalar-only update tag. The provider does not save
the machine, traverse neighbors or call analyzer/player callbacks. Normal node
lookup uses the existing OC `Environment`/`SidedEnvironment` API as in the pinned
Waila source.

## Build and metadata wiring

The dependency is optional. The API is part of Jade's official runtime artifact;
do not embed it or add foreign API classes to the published OC API package.

```groovy
repositories {
    maven {
        url = 'https://api.modrinth.com/maven'
        content { includeModule 'maven.modrinth', 'jade' }
    }
}
dependencies {
    compileOnly 'maven.modrinth:jade:15.10.0+neoforge'
}
```

An optional development run may use that same artifact as `localRuntime`; it is
not a mandatory OC runtime dependency. The NeoForge metadata dependency is
`modId="jade"`, `type="optional"`, `versionRange="[15.10.0,16)"`,
`ordering="AFTER"`, `side="BOTH"`. Server data support requires the common
registration even though tooltip rendering is client-only.

Add these language entries to the normal OC language resources:

| Key | English | Simplified Chinese |
| --- | --- | --- |
| `config.jade.plugin_opencomputers.address` | Address | 地址 |
| `config.jade.plugin_opencomputers.energy` | Energy | 能量 |
| `config.jade.plugin_opencomputers.component_name` | Component Name | 组件名 |
| `oc:gui.Assembler.Building` | Building: %s | 正在组装：%s |

Existing display keys are `oc:gui.Analyzer.Address`,
`oc:gui.Analyzer.StoredEnergy`, `oc:gui.Analyzer.ComponentName`,
`oc:gui.Analyzer.WirelessStrength`, `oc:gui.Analyzer.ChargerSpeed` and
`oc:gui.Assembler.Progress`.

## Validation boundary

The Windows main build has compiled against the official Jade 15.10.0 artifact.
The separate Windows dedicated-server run `jade-20261027-101832` passed with
official Jade 15.10.6. It obtained OC's provider from Jade's actual automatically
registered list, rather than constructing or registering a substitute. Real
world devices confirmed all six Case node slots, the closed front, actual local
connector energy, six relay component addresses and wireless-card strength,
empty/inserted/ejected floppy filesystem addresses, and charger speed changing
from 0 to 1 after placing a real redstone block. Dedicated-side plugin discovery
and provider execution completed successfully without client tooltip rendering.
See the [external SDK fixture](../tools/api-compat-addon/README.md).

A separate fresh world, `jade-waila-disabled-20261027-102339`, passed with the
historical `waila` blacklist entry enabled: Jade loaded normally but its actual
provider registry contained no OC information provider. Earlier ProjectRed runs
also started OC successfully without installing Jade.

Client acceptance still needs the three toggles, crosshair-side selection and
rendered formatting. Busy assembler progress/output-name serialization and
remote multiplayer synchronization were not exercised by this scenario.
