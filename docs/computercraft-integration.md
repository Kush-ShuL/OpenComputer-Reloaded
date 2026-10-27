# ComputerCraft compatibility

This optional integration follows MightyPirates/OpenComputers commit
`258943363ea8fc4ef14948e6bc6f67375061580c`, reimplemented in Java for Minecraft
1.21.1 NeoForge and CC: Tweaked 1.120.2. CC itself
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
- Adapters expose registered and generic CC peripherals, including computers and inventories,
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

As in the original integration, CC coroutine yields and asynchronous Lua-context-specific
tasks cannot run inside an OC Lua VM. They produce an explicit error. A synchronous
main-thread task executes immediately when the OC callback is already on the server
thread, allowing modern generic inventory methods to use CC's normal implementation. External
CC userdata restores without its original context and is unavailable after
loading a saved computer. Read/write mounts themselves persist open descriptor
positions through the OC filesystem wrapper.

The original driver called CC's implementation lookup, `ComputerCraft.getPeripheralAt`.
Modern generic discovery similarly uses the public `Peripherals.getGenericPeripheral`
method in CC's implementation package, behind an optional compatibility shim targeted
at 1.120.2. Ordinary registered peripherals use the NeoForge capability first.
OC filesystems exposed
by a peripheral are still mounted by OpenOS as OC filesystem components; CC's
requested mount name is the attachment's logical name, not an OpenOS path.

Windows NeoForge 21.1.224 scenario `platform-20261027-g5` passed in 31 ticks with
CC 1.120.2 explicitly enabled. An actual Adapter read seven diamonds from a real
chest through CC's generic `list` and `size` methods, including the guarded
main-thread task completion. A real CC computer booted `startup.lua` from a
physical disk drive and exchanged masks 0, 1, 32768, 21845, 43690 and 65535 with
OC I/O in both directions, producing actual per-channel events. Normal CC
computers reverse left/right compared with turtles; the fixture uses their real
side mapping. The report is
`build/api-compat-addon/devices/platform-20261027-g5/device-results.txt`.
Previous explicit-disable, relay, direct-peripheral and media proofs remain
applicable; the configuration gate and registrations retain their default-off behavior.

Official dependency and API references:
[CC: Tweaked 1.21.1 Java API](https://tweaked.cc/mc-1.21.x/javadoc/),
[peripheral registration](https://tweaked.cc/mc-1.21.x/javadoc/dan200/computercraft/api/peripheral/package-summary.html),
[LuaFunction contract](https://tweaked.cc/mc-1.21.x/javadoc/dan200/computercraft/api/lua/LuaFunction.html),
[MethodResult](https://tweaked.cc/mc-1.21.x/javadoc/dan200/computercraft/api/lua/MethodResult.html).
