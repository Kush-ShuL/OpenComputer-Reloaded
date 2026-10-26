# ComputerCraft compatibility

This optional integration follows MightyPirates/OpenComputers commit
`258943363ea8fc4ef14948e6bc6f67375061580c`, reimplemented in Java for Minecraft
1.21.1 NeoForge and CC: Tweaked 1.120.2. It uses only CC's public API. CC itself
and its API are not included in the OpenComputer-Reloaded JAR.

The integration is **disabled by default**, including when CC: Tweaked is
installed. To enable it, install CC: Tweaked and explicitly set this in
`config/opencomputers/settings.conf`:

```hocon
opencomputers {
  integration {
    computerCraft {
      enabled = true
    }
  }
}
```

The setting inside the `opencomputers` configuration is
`integration.computerCraft.enabled`. A missing setting also means disabled.
`integration.modBlacklist` takes priority: entries `computercraft` or
`ComputerCraft`, matched without case sensitivity, disable this integration
even when its flag is true. Restart the game or dedicated server after changing
these settings; registrations are applied during startup.

`-PocWithComputerCraft=true` only installs CC for a development run; it does
**not** enable compatibility. The isolated API fixture similarly uses
`-PocComputerCraftJar=<full-jar>` only to install CC. It writes the explicit
test configuration only when `-PocEnableComputerCraft=true` is supplied.
Without that enable property, its CC-present run verifies that the relay,
Adapter, media, converter and bundled-redstone bridges remain unregistered.

- The relay is a CC `modem` peripheral, including the original remote OC
  component discovery and `callRemote` methods. Each attached computer keeps
  its own channel set and receives `modem_message` events.
- Adapters expose directly registered CC peripherals, including computers,
  through their dynamic methods or public `@LuaFunction` methods. Calls use
  OC's synchronized callbacks; a `mainThread` method requires the server
  thread. Configured class blacklists, blacklist IMC and `BlacklistedPeripheral`
  remain effective.
- CC media and mounted filesystems use their real mount channels. Media labels
  and filesystem node addresses remain associated with the actual item.
- The additional modern bundled-redstone bridge maps CC's sixteen digital
  channels to OC channel levels 0/255; any positive OC output enables that CC
  wire. The original source did not contain this CC provider.

Two original relay protocol statements were inconsistent: `transmit` appended
the reply channel after its payload, while the receiver expected it first.
This port places the numeric reply channel first, retaining the payload order
and producing the expected CC event fields.

As in the original integration, CC coroutine yields and Lua-context-specific
tasks cannot run inside an OC Lua VM. They produce an explicit error. External
CC userdata restores without its original context and is unavailable after
loading a saved computer. Read/write mounts themselves persist open descriptor
positions through the OC filesystem wrapper.

CC's generic peripheral aggregation is not exposed by its public 1.21.1
lookup API; this integration reads directly registered peripheral capabilities
and does not call CC internal implementation classes. OC filesystems exposed
by a peripheral are still mounted by OpenOS as OC filesystem components; CC's
requested mount name is the attachment's logical name, not an OpenOS path.

Official dependency and API references:
[CC: Tweaked 1.21.1 Java API](https://tweaked.cc/mc-1.21.x/javadoc/),
[peripheral registration](https://tweaked.cc/mc-1.21.x/javadoc/dan200/computercraft/api/peripheral/package-summary.html),
[LuaFunction contract](https://tweaked.cc/mc-1.21.x/javadoc/dan200/computercraft/api/lua/LuaFunction.html),
[MethodResult](https://tweaked.cc/mc-1.21.x/javadoc/dan200/computercraft/api/lua/MethodResult.html).
