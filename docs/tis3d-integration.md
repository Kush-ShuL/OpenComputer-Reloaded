# TIS-3D integration

This Java implementation follows only `integration/tis3d/ModTIS3D.scala` and
`SerialInterfaceProviderAdapter.scala` in MightyPirates/OpenComputers commit
[`258943363ea8fc4ef14948e6bc6f67375061580c`](https://github.com/MightyPirates/OpenComputers/tree/258943363ea8fc4ef14948e6bc6f67375061580c/src/main/scala/li/cil/oc/integration/tis3d).
The original MIT license and source attribution remain in LICENSE and NOTICE.
No community OpenComputers port or Scala runtime is used.

The modern serial API comes from the official MightyPirates/TIS-3D release
[`1.21.1/2.0.0`, commit `fff58fd3dbca7ba08a5ed0262274555c873641c9`](https://github.com/MightyPirates/TIS-3D/releases/tag/1.21.1/2.0.0).
The manual API comes from the same author's
[MarkdownManual 1.21.1 API](https://github.com/MightyPirates/MarkdownManual/tree/1.21.1/common/src/main/java/li/cil/manual/api).

## Player behavior

Place an OC adapter against a TIS-3D serial port module. The serial interface
creates an OC `serial_port` component. Its protocol is defined by the program
running on the attached OC computer, as in the pinned source.

- `setReading(boolean)` controls whether TIS-3D can send values to OC. It starts
  disabled. Disabling it makes `canWrite()` false so the TIS-3D module cancels
  its pending reads at its next serial step; already received values remain.
- `read()` removes the oldest incoming value, returning no values when empty.
- `write(number)` truncates to a signed 16-bit value and queues it for TIS-3D.
  The outgoing queue rejects further OC writes at 128 entries with
  `false, "buffer full"`.
- Incoming availability is offered while reading is enabled and fewer than
  128 incoming entries are queued. A value already accepted by TIS-3D's
  handshake is appended without an additional rejection, matching the original.
- TIS-3D's `peek()` is non-consuming. Only `skip()`, called after a completed
  port transfer, consumes the outgoing value. An empty outgoing queue makes
  `canRead()` false; TIS-3D then cancels its outstanding writes.
- `canRead()` lazily connects the serial node to the adapter's actual component
  network. It reconnects if the two nodes no longer share a network.
- Each interface remains valid only while the identical adapter block entity
  remains at its position. Shutdown/removal resets both queues and disconnects
  the serial node. The reading flag is retained by reset, as in the original.
- Node identity, both ordered queues and the reading flag persist under the
  original NBT fields: node fields at the root, `writeBuffer`, `readBuffer`,
  `isReading`. Values retain signed-short conversion when loaded.

There is no new polling task, custom serial timing, fixed packet format or
signal invented by this integration. TIS-3D's own serial module owns stepping,
transfer completion and cancellation.

## Optional loading and registration

Root startup must call this once in the OC constructor, after loading settings
and before registry events:

```java
li.cil.oc.common.integration.tis3d.Tis3dIntegration.register(modBus);
```

The entrypoint contains no foreign API types. It checks the `tis3d` mod and the
original `integration.modBlacklist` name `tis3d` before linking the binding
classes. Blacklist comparison ignores case and surrounding spaces. Change this
setting before startup; this is registration-time integration selection.

`Tis3dRegistration` registers the provider as `opencomputers:adapter` in the
public `tis3d:serial_interface_provider` registry. The old `SerialAPI.addProvider`
has been replaced by that registry. **Do not defer registration to common
setup**, because the registry has already frozen then.

Only the physical client loads `Tis3dClient`. It registers the document provider
as `opencomputers:tis3d_adapter` with MarkdownManual and explicitly matches only
the `tis3d:manual` model. It reuses the existing OC resources under
`assets/opencomputers/doc/tis3d/{en_us,zh_cn,ru_ru}/protocols/`.
The documentation link is now `opencomputersadapter.md`, relative to TIS-3D's
localized protocol index; retaining the old `protocols/` prefix would duplicate
that path segment in the modern manual.

The existing three protocol documents still contain the old image identifier
`block:OpenComputers:adapter`. Root should update that identifier to
`block:opencomputers:adapter` when modernizing the shared resources. This source
handoff does not modify those resource files.

## Dependencies and runtime versions

Compile against official artifacts without including them in the OC JAR or SDK:

```groovy
repositories {
    maven {
        url = 'https://api.modrinth.com/maven'
        content { includeGroup 'maven.modrinth' }
    }
    maven {
        url = 'https://fnuecke.github.io/maven'
        content { includeGroup 'li.cil.markdown_manual' }
    }
}
dependencies {
    compileOnly('maven.modrinth:tis3d:MC1.21.1-neoforge-2.0.0') { transitive = false }
    compileOnly('li.cil.markdown_manual:markdown_manual-1.21.1-api:1.2.7') { transitive = false }
}
```

The MarkdownManual API coordinate is the official dependency used by TIS-3D's
own 2.0.0 build. Its official release also supplies an `-api.jar`. The full
official runtime coordinate is
`maven.modrinth:markdownmanual:MC1.21.1-neoforge-1.2.7`.

This implementation uses NeoForge's deferred registries and Minecraft's public
registry lookup. It does not import Architectury types, so no Architectury
signature dependency is required by these source files. TIS-3D itself requires
the runtime `dev.architectury:architectury-neoforge:13.0.11`, published at
`https://maven.architectury.dev`.

OC metadata should declare `tis3d` optional, side `BOTH`, ordering `AFTER`,
version range `[2.0.0,3)`. Base OC's minimum NeoForge remains 21.1.224.

The downloaded official TIS-3D runtime's actual metadata requires:

| Dependency | Required range |
| --- | --- |
| Minecraft | `1.21.1` |
| NeoForge | `[21.1.248,)` |
| Architectury | `[13.0.11,)` |
| MarkdownManual | `[1.2.7,)` |

The two official published 1.21.1 NeoForge releases found are 1.7.7 and 2.0.0.
The older 1.7.7 release's exact commit `58cd1ea70931485cccd9f9e31609059a4af12d93`
also sets NeoForge 21.1.248 in its version catalog and substitutes that value
into its required metadata range. It is not a 21.1.224-compatible alternative.
The repository README's `1.2.6` TIS-3D dependency example is stale, and was not
used as a published target.

The runtime was downloaded into the existing `build/libraries` directory and
matched once against its official Modrinth SHA-512:

```text
tis3d-MC1.21.1-neoforge-2.0.0+fff58fd.jar
a54c0089453195bdb49d24fc0c18ff510a3fb01d8a9e411621f83c4f3ad496512d0fb34a251423621c8abf04051fe7fc6c491560d0e535a91065c038a96ac237
```

## Validation boundary

I17 has compiled in the Windows Java 21 build against the official TIS-3D and
MarkdownManual releases. Runtime acceptance must use a compatible
NeoForge version of at least 21.1.248 with the required Architectury and
MarkdownManual runtimes. No base project version was changed. Serial transfer,
queue pressure, reset/reconnect, persistence and the manual link still require
their finite runtime acceptance before I17 is marked complete.
