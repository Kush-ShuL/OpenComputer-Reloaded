# Menus and localization

The layout baseline is MightyPirates/OpenComputers commit `258943363ea8fc4ef14948e6bc6f67375061580c`, specifically `common/GuiType.scala`, the common/client `GuiHandler.scala` implementations and the concrete `client/gui` classes. Container helpers, slots, display/input traits and widgets belong to their consuming menus; they do not add layout families. Original resources and attribution remain retained.

## Fixed original scope

The original handlers expose **19 concrete layout families through 24 reachable GUI entries**. Shared modern screen classes do not collapse different original layouts. Conversely, held/mounted and terminal variants do not create additional original layout families.

| Original family | Reachable original entries | Modern screen |
| --- | --- | --- |
| Adapter | Adapter | `AdapterScreen` |
| Assembler | Assembler | `WorkstationScreen` (assembler layout) |
| Case | Case | `CaseScreen` |
| Charger | Charger | `ChargerScreen` |
| Database | Database | `DatabaseScreen` |
| Disassembler | Disassembler | `WorkstationScreen` (disassembler layout) |
| DiskDrive | world block, held mountable, mounted rack drive | `DiskDriveScreen`, `RackDiskScreen` |
| Drive | filesystem-drive configuration | `DriveConfigurationScreen` |
| Drone | actual drone inventory/status | `AgentScreen` (drone layout) |
| Manual | manual | `ManualScreen` |
| Printer | Printer | `PrinterScreen` |
| Rack | Rack | `RackScreen` |
| Raid | Raid | `RaidScreen` |
| Relay | Relay | `RelayScreen` |
| Robot | actual robot inventory/embedded terminal | `AgentScreen` (robot layout) |
| Screen | physical screen, held tablet display, paired terminal | `ScreenGui`, `TabletScreen`, `TerminalScreen` |
| Server | held blade, mounted rack blade | `ServerScreen` |
| Tablet | tablet internal inventory (`TabletInner`) | `TabletInventoryScreen` |
| Waypoint | Waypoint | `WaypointScreen` |

The old `GuiType.Switch` declaration has no client or server handler branch and no concrete GUI implementation; it is not an additional reachable original menu. The accepted modern `MicrocontrollerScreen` EEPROM menu is also exercised, as an extension rather than a change to the original 19/24 scope. This gives 25 actual opening paths in the finite client scenario.

## Actual client scenario

`tools/api-compat-addon/src/main/java/ocfixture/MenuClientContract.java` exposes `start(Minecraft, Path, List<String>)`, `tick()`, `isRunning()` and `close()`. It starts only after the normal owned integrated world and real player are available. It requires the selected Chinese language (`zh_cn`) and a real 1280×720 window; the shared launcher supplies the normal GUI scale.

The server places actual blocks, uses the real creative Robot/Drone/Tablet/MCU presets and replaces only their CPU architecture/EEPROM program for a small native Lua 5.3 display BIOS. A real Case supplies physical screen and keyboard components. A real rack contains a blade, mountable disk drive and terminal server; terminal pairing uses the actual held terminal and mountable activation. Ordinary block, item and entity openings use actual production interactions. Mounted blade/drive openings call their public `RackMountable.onActivate` with the real server player, which creates the normal production menu and sends its real opening packets. The scenario does not construct mock containers, screens or `GuiGraphics` instances.

Each entry waits for the actual expected screen. Container screens must be the player's installed nonzero-ID menu, and active slot coordinates must fit the actual panel. Visible OC translatable widget/title components must resolve in the selected language. The normal screenshot is taken after 30 real rendered frames; observed FPS and median frame interval are recorded rather than inferred from build success or the presence of textures.

Images are captured from the real main render target during `RenderFrameEvent.Post` and saved directly in the supplied owned run directory as `menu-NN-name.png`. The 25 normal entry frames are supplemented by five focused frames: actual Case/Robot power hover and post-click pointer-away states, plus a long Chinese item name/lore tooltip rendered from real item components. The normal tooltip events verify that a hovered power control renders its tooltip, and that clicking then moving away does not leave it rendered. The long-text path uses normal tooltip gathering and font widths. There is no synthetic draw-call or texture-file-presence substitute for these frames.

The fixture closes screens through their normal close paths and restores its own world cells, forced chunks, player inventory, position and flight state. Failure output identifies the current entry, phase and awaited state. Existing player confirmation that the Robot GUI recovered to approximately 60 FPS remains relevant; the new run records the other actual frames without repeating a broad performance matrix.

`-Docfixture.menuFocus=waypoint` selects only the actual Waypoint opening, frame and normal close, without initializing or probing the unrelated Case/Robot/native machines. The default `all` retains the full 25-path scenario; other entry names or a comma-separated list select their normal openings using the shared setup. This permits a corrected path to be rerun without repeating already accepted entries.

## Local background correction

The Drone's modern entity translation key, `entity.opencomputers.drone`, is now supplied in English and Simplified Chinese as well as the retained legacy key. This also gives Jade the localized entity name.

`WaypointScreen` was the remaining concrete screen inheriting Minecraft 1.21.1's blurred background. Its former `render` drew the compact panel and then called `Screen.render`, which dynamically called `Screen.renderBackground`, `renderBlurredBackground` and `GameRenderer.processBlurEffect`; this also blurred the already drawn panel before drawing the edit field. It now draws the original panel in its own `renderBackground`, so the ordinary inherited render draws the panel followed by the field. No Minecraft blur setting or global renderer is changed. The other plain OC screens already override that background; actual `AbstractContainerScreen.renderBackground` only draws a transparent gradient and the container panel, so those containers do not reach this blur path.

The actual Waypoint entry also captures the main framebuffer in `ScreenEvent.Render.Pre` and `Post` around the same normal screen draw, after flushing the real `GuiGraphics`. It requires all pixels outside the compact central panel to remain identical and some panel pixels to change, then saves `menu-19-waypoint-world-before.png` and `menu-19-waypoint-world-after.png`. The report records the unchanged global blur option. These are actual world/menu frame comparisons, not a constructed graphics object or an inferred pass from a method declaration.

## Original drive layout and Chinese text

The drive configuration now uses the original `drive.png` panel and `button_drive_mode.png` controls: two mode buttons, the read-only lock and the original destructive-change warnings. Its accessible title is the actual held drive name. Changing mode keeps the window open; a locked drive can still change mode, which clears its filesystem address and lock through `DriveData.setUnmanaged`, as in the original. Locking uses `DriveData.lock` rather than a separate replacement NBT implementation. This section records the production behavior; remote packet execution is verified separately under C19.

The manual title, additional-tab hint and physical-screen title have explicit English and Chinese translations. Missing manual pages use a translated message containing the requested path. The corrected manual scenario opens `%LANGUAGE%/block/robot.md` and requires its actual Chinese robot content before taking the normal manual frame.

## Windows results

The focused Windows run `client-waypoint-20261027-j2` **passed**: [actual report](../build/api-compat-addon/integration-client/client-waypoint-20261027-j2/client-results.txt). The global blur option remained **5**; the actual same-frame comparison preserved **890,415** pixels outside the compact panel while **16,824** panel pixels changed. The normal Waypoint frame recorded **59 FPS**, with a **16.67 ms** median frame interval. The main agent reviewed all three actual PNGs (`menu-19-waypoint.png` and the before/after pair) and confirmed a clear panel, readable field and unblurred surroundings. This local fix was committed as `3c8529e85`.

The finite scenario has **25 accepted opening paths**, covered by the union below, with actual normal frames and focused tooltip/long-text evidence reviewed. A later player screenshot exposed a purple template slot missed in the first Assembler review; the old image is rejected and replaced by separately reviewed filled-slot and empty-slot runs. This completes the fixed C23 menu/localization acceptance scope. An overall failed run supplies only the valid completed entries listed here, not a pass for its subsequent failure or unvisited entries.

| Actual run/report | Accepted opening paths and focused frames |
| --- | --- |
| [`client-menus-20261027-k1`](../build/api-compat-addon/integration-client/client-menus-20261027-k1/client-results.txt) | Adapter normal frame. The Assembler frame is rejected because its template slot is purple; Case's focused acceptance uses the following run. |
| [`client-case-20261027-k1`](../build/api-compat-addon/integration-client/client-case-20261027-k1/client-results.txt) | Case normal frame, actual power hover, post-click pointer-away frame and long Chinese item-name/lore tooltip. The tooltip fits the real screen width. |
| [`client-menus-20261027-k2`](../build/api-compat-addon/integration-client/client-menus-20261027-k2/client-results.txt) | Charger, Database, Disassembler and world DiskDrive normal frames. |
| [`client-menus-20261027-k3`](../build/api-compat-addon/integration-client/client-menus-20261027-k3/client-results.txt) | Drive, Drone, Printer, Rack, Raid, Relay, held Server, Tablet inventory, held/mounted DiskDrive, mounted Server and the Microcontroller extension. The original drive panel and Chinese warnings are visible. |
| [`client-waypoint-20261027-j2`](../build/api-compat-addon/integration-client/client-waypoint-20261027-j2/client-results.txt) | Waypoint normal frame and actual same-frame background comparison described above. |
| [`client-menus-20261028-l1`](../build/api-compat-addon/integration-client/client-menus-20261028-l1/client-results.txt) | Chinese Robot manual page, Robot, physical Screen, held Tablet display and paired Terminal. The four actual synchronized displays have readable Unicode text on black backgrounds. |
| [`client-assembler-20261028-l2`](../build/api-compat-addon/integration-client/client-assembler-20261028-l2/client-results.txt) | Assembler with its actual case/template item installed: normal opening/render/close PASS; filled slots and panel reviewed. |
| [`client-assembler-empty-20261028-l3`](../build/api-compat-addon/integration-client/client-assembler-empty-20261028-l3/client-results.txt) | Assembler with the template slot empty, matching the player's screenshot: normal opening/render/close PASS; original blank slot background reviewed with no purple icon. |

The first failed hover check was a fixture cursor issue: this Windows GLFW warp did not deliver vanilla mouse movement, so the fixture now dispatches the actual `MouseHandler.onMove` callback. The second run exposed the untranslated drive-configuration title and replacement drive layout; those production issues were corrected before the accepted Drive frame. The earlier `k3` report passed its structural checks but its manual path omitted the language directory, and its monochrome display BIOS used a nonzero background that original one-bit color conversion makes white. The focused `l1` run corrects those fixture inputs, verifies actual contrasting synchronized colors and replaces the affected frames. It does not change the production text renderer.

The Assembler defect was a production call to nonexistent `icons/template.png`. The original static template slot has no icon, so `WorkstationScreen` now draws only the retained base slot for that kind, while the twelve original registered icon kinds keep their textures. The first replacement run had an installed case covering the slot; the additional `assemblerempty` selection removes that fixture item and directly verifies the previously purple empty slot. Both replacement frames have been reviewed; the empty-slot run recorded 57 FPS and a 16.44 ms median interval.

Robot recorded 60 FPS with a 16.83 ms median interval in `k3`; the later focused run recorded 53 FPS and 16.44 ms while other background acceptance work was running. These are observations of those real runs, not a claim that every menu or machine configuration holds 60 FPS. The previously confirmed recovery from the reported 15 FPS Robot GUI remains recorded independently.

Case's focused hover, cleared tooltip and long-Chinese frames have been visually reviewed. Robot's replacement `l1` hover/clear pair has also been reviewed and matches its passing actual hover/click/move checks. Shared world models/fonts and remote multiplayer transport remain under C20/C21/C19 and do not add menu families or global checklist items.
