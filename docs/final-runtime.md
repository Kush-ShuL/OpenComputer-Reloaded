# Final packaged-runtime acceptance

C24 checks the same final `build/hardware/libs/OpenComputer-Reloaded-0.1.0-SNAPSHOT.jar` on Windows Java 21 in a normal dedicated server and a normal client. It does not replace the device, renderer or multiplayer contracts. The fixed checklist is closed only after those required results and this final run have actual evidence.

## Clean launch profile

The local ModDevGradle 2.0.144 source confirms that `RunModel.loadedMods` controls source mod folders and `RunModel.sourceSet` controls the Java runtime classpath. The `finalServer` and `finalClient` profiles use an empty `productionRuntime` source set with no source/resource directories and `loadedMods.set([])`. Its only project runtime dependency is the final packaged main JAR; it inherits neither the addon source output nor the legacy SDK consumer output. The standard cached NeoForge/Minecraft runtime remains supplied by MDG.

`writeFinalRuntimeLaunchers` exports the actual generated JVM arguments, Java and legacy classpaths, and environment. It rejects main/legacy fixture output and the legacy SDK on either classpath, `ocfixture`/`oc_api_compat` arguments, nonempty source mod folders and a missing/duplicate selected main JAR. This uses the supported MDG model, without manually stripping arguments or modifying loader files. No compatibility-addon mod, test blocks, test drivers, fake world objects or test startup hooks are loaded.

Generated evidence is confined to `build/final-runtime/<run-id>/`. `runtime-manifest.json` records the exact JAR SHA-256, platform versions, selected empty source set and directories; `server-launch.json` and `client-launch.json` retain actual launch inputs. A changed JAR requires a new run ID.

## Runtime procedure

Prepare launch inputs after the final main-JAR build:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/run-final-runtime.ps1 -RunId final-YYYYMMDD-a -Mode prepare
```

When ready to run both:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/run-final-runtime.ps1 -RunId final-YYYYMMDD-a -Mode both -SkipPrepare
```

`-Mode server` and `-Mode client` can run the two sides separately against the same prepared JAR hash. The server uses a fresh local vanilla world under that run's `server/` folder. Its EULA file is copied only from the already accepted local server configuration. The runner waits for Minecraft's normal `Done` message, verifies the actual NeoForge mod list, sends `stop` through the real console input, and requires exit code zero plus all dimensions saved. Live logs are read with Windows read/write/delete sharing, and a runner error still sends `stop` to its owned server while the redirected console is available.

The client uses the existing regular root `run/` directory and Minecraft's native `--quickPlaySingleplayer OC-Showcase-20261028` option. That option was verified in the cached Minecraft 1.21.1 `Main` bytecode. It opens the already saved player base; no addon builder or recovery mode runs. The runner waits for the real local-player login and verifies the actual mod list, then leaves the game window open and interactive. It neither recreates the save nor changes the player's client options. Existing external Minecraft processes must be left untouched and must not share the same running game directory.

Both actual logs must contain exactly `minecraft`, `neoforge` and `opencomputers` in the runtime mod list, identify the main packaged JAR and contain no compatibility-fixture activity. Reports record the same SHA-256 and retain ordinary logs. `server-results.txt` and `client-results.txt` cover packaged-runtime startup/save behavior only; normal loaded-save screenshots and final visual review are recorded separately when performed.

## Current result

On 2026-10-28, `build/final-runtime/final-20261028-b/` recorded **PASS** for both actual actors, using the exact same main JAR accepted by the final TCP run:

```text
SHA-256 3dd0813c2dad2f2a293c89f7fb6092de8274b0cbe4da8e18805d7ac2376779bd
Minecraft 1.21.1 / NeoForge 21.1.224 / Windows Java 21
actual_mods=minecraft,neoforge,opencomputers
```

The dedicated server reached `Done (0.662s)!`, received the normal console `stop`, saved all three dimensions and exited with code zero. The client used native quick play to enter `OC-Showcase-20261028`; player `Dev` joined at 10:32:01. After the settling interval it remained alive and interactive. The manifest, both result reports and retained native logs record the same JAR and actual three-mod runtime. No addon, source-mod folder or legacy SDK was loaded.

The earlier `final-20261028-a` server also reached `Done`, but the launcher failed to read a live Windows log before retaining its console control. That owned empty test server was terminated, the shared-file reader and error cleanup were corrected, and the fresh `b` run supplied the normal-save/exit evidence. Run `a` is not counted as a successful shutdown test.

The selected main JAR was compiled and packaged successfully in `build/launch-support/windows-hardware-build.log`. A separate public-repository build reached `compileJava`, but was canceled because this Windows host intermittently resolves the NeoForge Maven domain to unrelated addresses and fails direct TLS connections; that attempt is not claimed as a successful clean online build. Third-party coordinates now use their existing official repositories exclusively, without an untracked repository override in the public build script. This host-network observation does not replace either packaged-runtime result.

C24 is closed together with the other 113 active requirements. The six explicitly skipped legacy integrations retain their original checklist IDs and remain outside the supported scope. The final game window is left open in the existing base.
