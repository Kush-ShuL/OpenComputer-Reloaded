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

## Old binary versus new source

`src/legacy/java` is compiled in its own source set against a preserved SDK with only the two-argument `Converter` SAM. `compileLegacyJava` rejects an SDK that already has the registry-aware overload. That already-compiled lambda is packaged with the addon and exercised through the new runtime's normal conversion pipeline. It does not carry the old API JAR at runtime.

The default legacy input is the saved `build/api-compat-addon/legacy-sdk/opencomputers-api-two-arg.jar`, independent of later builds in `build/libs`. Use `-PlegacyApiJar=ABSOLUTE_PATH_TO_OLD_SDK.jar` to select another preserved two-argument SDK.

This proves the specific Converter method/SPI compatibility case. It does not claim binary compatibility for every historical snapshot: constants such as the old Forge `API.ID_OWNER` may have been inlined into callers and require recompilation.
