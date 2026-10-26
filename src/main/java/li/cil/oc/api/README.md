# OpenComputer-Reloaded integration API

This API preserves OpenComputers' component, driver, filesystem, machine,
network, manual, robot event, and nanomachine extension points on Minecraft
1.21.1 with NeoForge and Java 21. It is derived from the original
MightyPirates/OpenComputers API and remains under the original MIT license.

Use the standalone `opencomputer-reloaded-api` Maven artifact or the `-api.jar`
release asset as a **compile-only** dependency. The full mod supplies the runtime
implementations. Do not ship/shade the API in another mod, and do not install the
API JAR in a player's `mods` directory.

See [API integration documentation](https://github.com/Kush-ShuL/OpenComputer-Reloaded/blob/main/docs/API.md)
for setup, callbacks, data component persistence, registry-aware saves, and
changes from the legacy Forge API. API compilation does not establish that a
particular runtime subsystem has completed its Minecraft migration.
