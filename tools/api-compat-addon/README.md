# External API compatibility addon

This is a separate NeoForge 1.21.1 addon. It compiles only against the published SDK JAR or Maven artifact, and runs against the complete OpenComputer-Reloaded mod JAR. It does not include API sources or import `li.cil.oc.server`, `li.cil.oc.common`, or other implementation classes.

All class files, temporary worlds, configuration, and logs go under the repository's ignored `build/api-compat-addon` directory. `RuntimeContract.java` is the preserved external runtime exercise provided alongside the fixture.

## Compile and run on Windows

Use the repository's wrapper and Windows Java 21. From the repository root:

```powershell
.\gradlew.bat -p tools/api-compat-addon build `
  "-PocApiCoordinate=com.github.Kush-ShuL:OpenComputer-Reloaded:api-8.0.0-alpha.1" `
  "-PocApiMaven=G:/Projects/OpenComputer-Reloaded/build/jitpack-maven" `
  "-PocModJar=ABSOLUTE_PATH_TO_STAGED_MOD.jar" `
  "-PlegacyApiJar=G:/Projects/OpenComputer-Reloaded/build/api-compat-addon/legacy-sdk/opencomputers-api-two-arg.jar"
```

To consume the JitPack SDK's actual local JAR instead of its Maven publication, replace `ocApiCoordinate`/`ocApiMaven` with `-PocApiJar=ABSOLUTE_PATH_TO_SDK.jar`. When exactly one non-sources/non-javadoc JAR exists in `build/sdk/libs`, the fixture selects it by default.

Run the isolated dedicated server with the same properties and the `runServer` task. Its working directory is `build/api-compat-addon/run`; use that directory's `eula.txt` for the local server's EULA setting. The run task generates its own `config/opencomputers/settings.conf` from the shipped defaults and sets `registerLuaJArchitecture: true`. It does not touch the normal development client's config or world.

The isolated run automatically starts the contracts and stops after completion. It writes `run/contract-results.txt` under the fixture build directory, with `status=PASS` or `status=FAIL` and individual results. `runServer` removes the previous report before launch and succeeds only when the new report starts with `status=PASS`; a missing report also fails the task, even if NeoForge exits with code zero after a startup error. The log additionally contains `API_FIXTURE_COMPLETE status=PASS`. This is an intended acceptance step, not evidence that has already been obtained.

With the addon installed in another test instance, `/oc_api_compat verify` starts the public API contracts and `/oc_api_compat results` prints results. Automatic startup, temporary block placement, and automatic shutdown are enabled only by the dedicated fixture run's system properties. The optional block exercise requires two empty cells at the top of the fixture world's spawn chunk and restores both cells in `finally`.

## Contracts

- Public facades are present during addon setup. The new SDK's `API.ID_OWNER` matches actual runtime mod metadata. The client Manual backend accepts a content provider during common setup; dedicated servers keep it absent.
- An addon item driver registers through `Driver.add`; detached `dataTag` state commits through `saveDataTag` without deleting another mod's custom data.
- Addon nodes merge/split, preserve message source/payload, keep Neighbors visibility separate from reachability, and share connector capacity and power.
- Single/sided block entity prefabs persist node addresses using the registry overloads and detach on chunk unload. A real external `SimpleComponent` is discovered, dispatches its callback, serializes its node attachment, and detaches after block removal.
- `ItemStackArrayValue` preserves counts, names, custom data and empty entries using an explicit level registry provider.
- Actual native/LuaJ kernels drive component callbacks and custom `Value.apply`, `Value.call`, and `Value` callbacks; custom converters receive the invoking machine's exact registry provider and run only once per boundary. A non-machine context receives null.
- The addon's inter-mod enqueue lifecycle sends `IMC.registerProgramDiskLabel`; native/LuaJ BIOS code checks the received mapping through `computer.getProgramLocations()` after server startup.
- Registry-aware persistence reaches an addon's nested filesystem and label through the public managed/read-only wrappers. The addon's old single-argument methods deliberately throw.
- Replacement-host checkpoints retain native Lua 5.2/5.3 coroutine locals, live addon Values, the synchronized return stack, open tmpfs handles and queued signals. LuaJ follows its upstream reboot-on-load behavior. A direct callback calls Context.start/pause to exercise the separate coroutine-thread lock boundary.
- A real assembler consumes an external IMC template with staged power and input consumption; a real disassembler processes the external guaranteed-drop callback into an adjacent chest. Only the fixture's marked items participate, and temporary blocks are restored.

## Old binary versus new source

`src/legacy/java` is compiled in its own source set against a preserved SDK with only the two-argument `Converter` SAM. `compileLegacyJava` rejects an SDK that already has the registry-aware overload. That already-compiled lambda is packaged with the addon and exercised through the new runtime's normal conversion pipeline. It does not carry the old API JAR at runtime.

The default legacy input is the saved `build/api-compat-addon/legacy-sdk/opencomputers-api-two-arg.jar`, independent of later builds in `build/libs`. Use `-PlegacyApiJar=ABSOLUTE_PATH_TO_OLD_SDK.jar` to select another preserved two-argument SDK.

This proves the specific Converter method/SPI compatibility case. It does not claim binary compatibility for every historical snapshot: constants such as the old Forge `API.ID_OWNER` may have been inlined into callers and require recompilation.

## Real world cold restart

`runPersistenceServer` exercises a real `Case` block entity across two separate server processes. It does not run the older replacement-host, base, production or optional ComputerCraft contracts. It still compiles only against the published OC SDK and public game APIs; it imports no OC implementation classes, replaces no machine host, and uses no private reflection.

Run both stages from the repository root with the same explicit run id and mod/SDK artifacts. A new id creates a separate directory under `build/api-compat-addon/persistence/<id>` and a world named `ocfixture-persistence-world`. The task refuses to prepare over an existing world. It never resets or copies a player world. Its EULA file is copied only when the existing isolated `build/api-compat-addon/run/eula.txt` already records acceptance; otherwise set the EULA in the newly created run directory and retry.

```powershell
$fixture = @(
  '-p', 'tools/api-compat-addon',
  '-PocApiJar=ABSOLUTE_PATH_TO_PUBLISHED_SDK.jar',
  '-PocModJar=ABSOLUTE_PATH_TO_STAGED_MOD.jar',
  '-PlegacyApiJar=G:/Projects/OpenComputer-Reloaded/build/api-compat-addon/legacy-sdk/opencomputers-api-two-arg.jar'
)
$nativeRun = 'native53-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
& .\gradlew.bat @fixture runPersistenceServer "-PocPersistenceRunId=$nativeRun" '-PocPersistenceArchitecture=Lua 5.3' '-PocPersistenceStage=prepare'
if ($LASTEXITCODE -ne 0) { throw 'Native prepare failed; inspect its report before continuing' }
& .\gradlew.bat @fixture runPersistenceServer "-PocPersistenceRunId=$nativeRun" '-PocPersistenceArchitecture=Lua 5.3' '-PocPersistenceStage=resume'
if ($LASTEXITCODE -ne 0) { throw 'Native resume failed' }

$luaJRun = 'luaj-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
& .\gradlew.bat @fixture runPersistenceServer "-PocPersistenceRunId=$luaJRun" '-PocPersistenceArchitecture=LuaJ' '-PocPersistenceStage=prepare'
if ($LASTEXITCODE -ne 0) { throw 'LuaJ prepare failed; inspect its report before continuing' }
& .\gradlew.bat @fixture runPersistenceServer "-PocPersistenceRunId=$luaJRun" '-PocPersistenceArchitecture=LuaJ' '-PocPersistenceStage=resume'
if ($LASTEXITCODE -ne 0) { throw 'LuaJ resume failed' }
```

The native prepare stage places real case/CPU/RAM/HDD/EEPROM hardware and an adjacent addon probe in an otherwise empty, distant chunk. Its EEPROM program reaches a paused checkpoint with an open tmpfs reader at offset three, a nested suspended coroutine, live locals, and a live addon `Value` containing named/custom-data item stacks and a public `ItemStackArrayValue` iterator. The test queues a signal, saves through Minecraft's world-saving API, and requests normal server shutdown. `prepare-results.txt` becomes `status=PASS` only after `ServerStoppedEvent`; a pre-shutdown report says `PENDING_STOP`.

The resume process loads that same real block entity from world storage. It checks persisted machine/tmpfs/HDD/EEPROM/probe addresses before releasing the deliberately paused checkpoint through the public `Machine.start()` API. The running Lua program must prove that the BIOS has not restarted, its locals and nested coroutine continue, the userdata was reconstructed with the invoking world's registry provider, the tmpfs descriptor retained its offset, and its queued signal and persistent HDD/EEPROM bytes survived. Native `Value` loading is required; a blank constructor or Java-side replacement cannot satisfy the checks.

Native runs then release the actual forced-chunk ticket, wait for `ChunkEvent.Unload` and node detachment without loading the chunk during the wait, and load a new block entity from disk. The program must continue through a second persisted signal, descriptor position and userdata iterator cursor. `-PocPersistenceChunks=false` limits the run to the two-process cold restart. `-PocPersistenceArchitecture=Lua 5.2` selects the other native implementation when available.

LuaJ has a separate run id and expectation: its BIOS must execute a second time, with the boot counter and HDD/EEPROM data preserved. The fixture does not substitute or claim a persistent LuaJ coroutine stack, and it does not run the native chunk-stack contract for LuaJ.

Each stage writes `prepare-results.txt` or `resume-results.txt` with a first line of `status=PASS` or `status=FAIL`, plus machine-readable evidence. A missing or incomplete report makes the Gradle run task fail even when the server process exits normally. The log includes `API_WORLD_PERSISTENCE_COMPLETE ... status=PASS` after successful shutdown. These are executable acceptance contracts, not claims that a test has already passed.

## Optional ComputerCraft runtime

The addon compiles against CC:Tweaked's official `core-api` and `forge-api` version `1.120.2` from `https://maven.squiddev.cc`. `-PocComputerCraftJar=ABSOLUTE_PATH_TO_FULL_CC_MOD.jar` installs that mod for the isolated `runServer` scenario. Compatibility remains disabled by default; only the separate explicit `-PocEnableComputerCraft=true` enables it. Supplying the JAR alone does not enable the bridge. `integration.modBlacklist` still overrides the enable flag; `computercraft` and `ComputerCraft` match without regard to case.

With the optional JAR present, this scenario runs only its ComputerCraft contract, avoiding another run of the older base/runtime/checkpoint/production contracts:

```powershell
# Installed but disabled: assert the actual relay/adapter/media bridges are absent.
& .\gradlew.bat @fixture runServer '-PocComputerCraftJar=ABSOLUTE_PATH_TO_FULL_CC_MOD.jar' '-PocEnableComputerCraft=false'

# Explicitly enabled: exercise the real relay packet flow, adapter calls and media I/O.
& .\gradlew.bat @fixture runServer '-PocComputerCraftJar=ABSOLUTE_PATH_TO_FULL_CC_MOD.jar' '-PocEnableComputerCraft=true'
```

Both choices regenerate only the isolated fixture's HOCON configuration and write `opencomputers.integration.computerCraft.enabled` to the selected boolean value. The Gradle task tracks that boolean as an input, so a previous enabled run cannot leave the next disabled run enabled through an up-to-date configuration. The runtime checks that the public configuration matches the explicit fixture selection.

OC-only runs do not load `ComputerCraftContract` or its CC API references. The persistence run keeps its two-stage cold-restart protocol and does not run either ComputerCraft contract, regardless of the optional mod's presence.
