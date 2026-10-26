# Migration to Minecraft 1.21.1 NeoForge

## Source of truth

- Only MightyPirates/OpenComputers is used as the implementation source.
- Branch: master-MC1.12.
- Commit: 258943363ea8fc4ef14948e6bc6f67375061580c.
- Official master-MC1.16 is an older ancestor of this 1.12.2 branch, not a finished 1.16 port.
- No community port code is used.

The original source tree is retained in the upstream/master-MC1.12 Git reference. Lua programs, manuals, assets and license notices remain in the main tree. Runtime code is being rewritten in Java 21; there is no Scala compiler or Scala runtime dependency. A rewritten module is not automatically validated in game.

## Delivery requirements

Completion requires the whole mod, not just registration or an API JAR: computers and persistent Lua VM state; native Lua and LuaJ; OpenOS and loot disks; driver discovery and component networks; storage and EEPROM; graphics and input; robots and upgrades; drones; tablets and terminals; servers and racks; redstone, transposers and fluid/inventory automation; printers, holograms, geolyzers and motion sensors; nanomachines; recipes/configuration; multiplayer menus and payloads; rendering; and compatible optional integrations.

The main source set contains Java implementations. Original Scala files remain in the upstream reference for semantic comparison and are not shipped as a runtime implementation. Systems awaiting Java migration remain incomplete, even if the current Java modules compile. Empty components are not a substitute for their original behavior.

## Current state

Work in progress. Current Java modules and the independent API compile. The Windows development client has started with Minecraft 1.21.1, NeoForge 21.1.224 and OpenComputer-Reloaded loaded. This is a partial development implementation, not a complete playable release.

The current implementation includes API types, core network/filesystem/machine/driver logic, LuaJ and native architecture code, CPU/memory/storage/GPU/network-card items, cases, screens, keyboards, cables, configuration/recipes and nanomachines. Module presence and client startup do not establish functional completion.

The current visual corrections restore the layered case panel, component sockets and power/activity/error overlays; the original six-face and joined-screen texture selection; screen text bounds and font filtering; and floppy color model selection. Screen menus bypass the modern default background blur. Legacy display transforms that were ignored by 1.12 are not promoted into active modern hand transforms. Player screenshots identified additional screen-window sizing, case-title overlap and button-tooltip focus problems. The follow-up version compiled, packaged and started in a Windows singleplayer world with these corrections; visual acceptance of the follow-up is still pending.

The creative catalog now includes ordinary hardware as well as BIOS/loot-disk variants: three CPUs, six RAM levels, three GPUs, three hard drives, EEPROMs, blank floppy disks and network cards. These items use the migrated component drivers. Their in-game specifications and stack limits are implemented; installing and booting a complete computer still requires acceptance.

The next compiled modules include the three original APUs, the external floppy disk drive with its filesystem node/menu/media insertion and ejection, FE power conversion, isolated six-side power distribution, and normal/carpeted capacitors with adjacency capacity bonuses. The original `oc_spawnComputer` (`oc_sc`) and disassembly-protection commands are registered with operator level 2. The spawn command creates the original creative-case/APU/RAM/HDD/BIOS/OpenOS combination. These modules compile and package; functional device acceptance is pending.

A live Windows probe traced the OpenOS/Plan9k boot failure to shared energy accounting: the installed system disk was visible and its `init.lua` could be read by the backend, but the component rejected the read as out of power because a complete fractional energy withdrawal left a floating-point remainder. The connector/network implementation now follows the upstream whole-request branch, setting the remainder to zero when the request is fully satisfied. A fresh-client boot is required to validate this fix.

The next live boot exposed a separate host-threading deadlock. The computer
worker held the machine state monitor while `CaseBlockEntity.markChanged()`
called Minecraft chunk access and waited for the server thread; that thread was
waiting for the same state monitor. The upstream scheduled-dirty behavior is
restored using atomic flags consumed by server ticks. Screen/keyboard/power
hosts and screen snapshot/command publication follow the same thread boundary.
The deadlocked client was terminated by the player; the corrected build still
requires a fresh-client boot check.

Windows startup initially failed because the obsolete Typesafe implementation-package hook conflicted with the standalone Typesafe module; the unused hook was removed. Module resource lookup now finds and extracts the original native Lua libraries. JNLua was then found in the bootstrap class loader while OC was in the game class loader. The build wraps the unchanged upstream JNLua classes with an FML game-library manifest so JNI and OC share the game loader. The latest Windows run successfully loaded native Lua 5.2, 5.3 and 5.4; the configured architectures are Lua 5.3 and 5.2, with Lua 5.3 selected by default. Saved coroutine restoration and complete computer boot still require functional validation.

Still required: robots/drones and upgrades; servers/racks, tablets/terminals and microcontrollers; automation/power/production devices; holograms/printers/prints; remaining item/block/component families; commands/manual UI/loot/configuration parity; optional integrations; saved VM restoration; and full client/server functional validation. Existing menus, payloads, rendering, energy, persistence and crafting implementations also need functional acceptance.

A successful API compilation only validates the SDK. It does not establish mod startup, computer boot, saved VM restoration, automation, multiplayer synchronization, graphics fidelity, or completeness.

## API and release packaging

`./gradlew apiJar apiSourcesJar apiJavadocJar` builds the independent SDK and its documentation without requiring the unfinished runtime to compile. `./gradlew build` must compile the complete mod before a playable release is published.

The API has changed ABI for Minecraft 1.21.1. Integrations must be recompiled. ItemStack persistence now uses explicit HolderLookup.Provider context, dimensions use ResourceKey<Level>, and CustomData modifications must be committed to the stack. See API.md.

Maven publications are configured for the complete mod and a separate `io.github.kush-shul:opencomputer-reloaded-api` artifact. A local distribution repository is created with `publishApiPublicationToLocalDistributionRepository`; authenticated GitHub Packages publishing requires a token with package write permissions. The CLI credential cannot create workflows, so CI templates are retained under `docs/ci` and the API is distributed through GitHub Release attachments. A runtime release must not call an incomplete migration a complete port.
