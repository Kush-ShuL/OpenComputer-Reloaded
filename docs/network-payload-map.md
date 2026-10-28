# 原版网络消息映射

范围固定为 MightyPirates/OpenComputers 提交 `258943363ea8fc4ef14948e6bc6f67375061580c` 的 `common/PacketType.scala`，并对照同提交两端的 `PacketHandler`、`PacketSender`。共有 **88 个非哨兵枚举项**，其中 15 项是 `TextBufferMulti` 内部的子命令；`EndOfList` 为结束标记，不另算一种功能消息。下表序号仅用于清点，不能作为旧协议的实际 wire ID。

当前 `common/network` 下有 12 个网络类，加 `server/PacketSender` 为 13 个核心模块。完整范围还包括 `common/command/CommandNetworking`、`common/integration/LootIntegration`、`common/nanomachines/NanomachineNetwork`、`common/item/ComponentItem` 中的载荷，不能只依据核心模块数量判断原范围是否完整。

表中“原生”指 Minecraft 的方块状态、方块实体更新、实体跟踪、菜单槽位或菜单数据同步。方向按原处理器实际分发，包含枚举注释未注明的双向用途。固定 88 项映射及下述 C19 远程运行范围已完成，实际证据见文末。

| 序号 | 原消息 | 当前路径与处理方式 |
| --- | --- | --- |
| 1 | `AdapterState` | 原生 `AdapterBlockEntity.getUpdateTag/loadAdditional`，同步开放侧。 |
| 2 | `Analyze` | `ToolNetworking.Analyze` → `AnalyzerClient.receive`。 |
| 3 | `ChargerState` | 原生 `ChargerBlockEntity` 更新充电速度、电源和反相状态。 |
| 4 | `ClientLog` | 核心目录外的 `CommandNetworking.ClientLog`，客户端日志接收。 |
| 5 | `ColorChange` | 对应宿主的方块实体颜色字段及更新；屏幕颜色也在 `ScreenNetworking.Sync` 快照中。 |
| 6 | `ComputerState` | Case/Micro 原生状态、错误标记；Robot 的 BE 运行/错误镜像已修复并通过真实双客户端变化验证，见 G2。 |
| 7 | `ComputerUserList` | Case/Micro 原生 BE 的 `oc:users`；Robot 同名镜像已修复并通过远程用户增删及权限变化验证，见 G2。 |
| 8 | `ContainerUpdate` | 各菜单的原生槽位、`DataSlot/ContainerData` 和打开菜单时的额外数据；挂载设备资料另由 Rack BE 提供。 |
| 9 | `DisassemblerActiveChange` | `DisassemblerBlockEntity` 的原生工作状态和更新。 |
| 10 | `FileSystemActivity` | `PacketSender.Activity` 恢复双端 API 事件及 NBT，真实远程 Case/Robot 路径已验证；取消和冷却的专项范围见 G1。 |
| 11 | `FloppyChange` | `DiskDriveBlockEntity` 原生 BE 更新磁盘显示状态，菜单原生同步真实槽位。 |
| 12–20 | `HologramArea`, `HologramClear`, `HologramColor`, `HologramPowerChange`, `HologramRotation`, `HologramRotationSpeed`, `HologramScale`, `HologramTranslation`, `HologramValues` | 合并为 `HologramNetworking.Sync` 的完整/增量 NBT 和 revision；`Request` 请求重建快照，`HologramClient` 应用。 |
| 21 | `LootDisk` | 核心目录外的 `LootIntegration.DiskSync`，`LootClientLifecycle` 更新实际世界磁盘目录与 JEI。 |
| 22 | `CyclingDisk` | 同一 `DiskSync` 世界目录；`Recipes.cyclingDisks` 合并本地注册的可轮换磁盘及世界磁盘，供实际轮换配方使用。 |
| 23–25 | `NanomachinesConfiguration`, `NanomachinesInputs`, `NanomachinesPower` | 核心目录外的 `NanomachineNetwork.State`，mode 0/1/2 分别同步配置、输入、电量；跟踪实体及玩家自己。 |
| 26 | `NetSplitterState` | `NetSplitterBlockEntity` 原生方块状态/BE，保留各侧开关及反转。 |
| 27 | `NetworkActivity` | `PacketSender.Activity` 恢复原双端 API 事件和 NBT，真实远程 Case/Robot 路径已验证；原 API 实际未声明可取消。 |
| 28 | `ParticleEffect` | 原生粒子发送或对应客户端宿主的粒子 tick；m7 已验证真实生产发送及两端接收，具体视觉效果见 C22 独立运行证据。 |
| 29 | `PetVisibility` | `PetNetworking.SetVisibility/Visibility` 双向同步及加入时的全量状态；玩家键使用 UUID。 |
| 30 | `PowerState` | `PowerDistributorBlockEntity` 等原生 BE 的总电量/容量；菜单电量另用原生菜单数据。 |
| 31 | `PrinterState` | `PrinterBlockEntity` 原生预览/工作状态与 `PrinterMenu` 数据。 |
| 32–33 | `RackInventory`, `RackMountableData` | `RackBlockEntity` 原生 BE 快照以及 `RackMenu` 槽位、状态、挂载设备资料。 |
| 34 | `RaidStateChange` | `RaidNetworking.State`，另有 `RaidBlockEntity` 原生 BE 初始快照。 |
| 35 | `RedstoneState` | 各宿主 BE 的客户端字段为 `RedstoneState.saveClient/loadClient`。本批已修正 Robot 客户端格式读取并通过实际远程侧输出检查，见 G4；其余宿主按各自客户端加载路径处理。 |
| 36–37 | `RobotAnimateSwing`, `RobotAnimateTurn` | `RobotController.writeClient/load` 的动画类型、剩余/总 tick，经原生 Robot BE 更新。 |
| 38 | `RobotAssemblingState` | `AssemblerBlockEntity` 原生装配状态和 `WorkstationMenu` 进度数据。 |
| 39 | `RobotInventoryChange` | Robot BE 快照中的装备/库存、`AgentMenu` 原生槽位。 |
| 40 | `RobotLightChange` | `RobotData.lightColor` 随 `writeClient/load` 的 BE 快照同步。 |
| 41 | `RobotMove` | 原生目标方块/旧位置 afterimage、Robot BE 快照；`AgentClient` 按地址复用稳定 controller。m7 已验证真实移动后的 controller/地址，以及两个玩家离开维度、卸载旧区块并返回后的完整镜像。 |
| 42–43 | `RobotNameChange`, `RobotSelectedSlotChange` | Robot BE 快照中的名字和选中槽，另有实际菜单槽位同步。 |
| 44 | `RotatableState` | 各设备原生朝向方块状态；实体使用原生实体旋转/跟踪。 |
| 45 | `SwitchActivity` | `NetworkBlockNetworking.RelayActivity` → `NetworkBlockClient`。 |
| 46 | `TextBufferInit` | 双向初始化：实体屏幕 `ScreenNetworking.Request/Sync`；终端和 Tablet 各自的 Request/Update；Agent 通过 `AgentNetworking.REQUEST` 与 BE/菜单完整屏幕快照。 |
| 47 | `TextBufferMulti` | `ScreenNetworking.Sync` 的 `commands` 列表；Terminal/Tablet 在其 Update 中复用编解码；Agent 提交更新时直接同步最终屏幕快照。 |
| 48 | `TextBufferRamInit` | `ramInit`：真实辅助显存页载入。PIN 的 `TextBufferInit` 只传屏幕栅格；现代实体屏幕的完整快照已补充此前实际发布的引用页，修复重新跟踪后 clean bitblt 缺页，见 G5。 |
| 49 | `TextBufferBitBlt` | `bitblt`：`ClientGpuTextBufferHandler` 应用实际显存复制。 |
| 50 | `TextBufferRamDestroy` | `ramDestroy`：移除对应 owner/id 显存页。 |
| 51 | `TextBufferMultiColorChange` | `color`：前景/背景及调色板标记。 |
| 52 | `TextBufferMultiCopy` | `copy`。 |
| 53 | `TextBufferMultiDepthChange` | `depth`。 |
| 54 | `TextBufferMultiFill` | `fill`。 |
| 55 | `TextBufferMultiPaletteChange` | `palette`。 |
| 56 | `TextBufferMultiResolutionChange` | `resolution`。 |
| 57 | `TextBufferMultiViewportResolutionChange` | `viewport`。 |
| 58 | `TextBufferMultiMaxResolutionChange` | `maxResolution`。 |
| 59 | `TextBufferMultiSet` | `set`，保留横向/纵向参数。 |
| 60–62 | `TextBufferMultiRawSetText`, `TextBufferMultiRawSetBackground`, `TextBufferMultiRawSetForeground` | `rawText/rawBackground/rawForeground`，传递真实二维数组。48–62 均为批内子命令，实体屏幕及 Terminal/Tablet 有明确应用分支。 |
| 63 | `TextBufferPowerChange` | `power` 命令和完整快照中的电源/渲染状态；Agent 为最终屏幕快照。 |
| 64 | `ScreenTouchMode` | `ScreenBlockEntity` 快照的 `inverted` 和 `synchronizeTouchMode`；这是原触摸反相状态，不能误当作 `precise`。 |
| 65 | `SoundEffect` | `DiskSounds` 使用原生 `ClientboundSoundPacket`，保留原 range/音量换算及冷却。 |
| 66–67 | `Sound`, `SoundPattern` | `PacketSender.Speaker`，客户端实际音频播放。 |
| 68 | `TransposerActivity` | `AutomationNetworking.TransposerActivity`。 |
| 69 | `WaypointLabel` | `NetworkBlockNetworking.WaypointLabel` 客户端提交，BE 同步结果；另有打开编辑界面的 `WaypointOpen`。 |
| 70 | `ComputerPower` | `ComputerNetworking.Power`；Robot 与 Drone 的菜单开关走 `AgentNetworking.POWER`。 |
| 71 | `CopyToAnalyzer` | `ToolNetworking.CopyScreenLine`；打开屏幕界面时也可用 `ScreenNetworking.Input.COPY_LINE`。 |
| 72–73 | `DriveLock`, `DriveMode` | 核心目录外的 `ComponentItem.DriveSettingsPayload`，服务端处理手持物品的真实锁定/模式和地址变更。 |
| 74 | `DronePower` | `AgentNetworking.POWER`，真实 Drone 预启动及机器操作；运行状态用 `DroneEntity` 的原生实体同步。 |
| 75–76 | `KeyDown`, `KeyUp` | `ScreenNetworking.Input`、`AgentNetworking.Input`、`PortableNetworking.TabletInput`、`RackNetworking.TerminalInput` 的键盘操作。 |
| 77 | `Clipboard` | 上述四种 Input 的客户端→服务端粘贴；原服务端→客户端用途由 `DebugNetworking.Clipboard` 等明确客户端写剪贴板入口提供。 |
| 78–79 | `MachineItemStateRequest`, `MachineItemStateResponse` | Tablet 不再保留旧请求/响应包；`TabletHost.tick/save` 写真实 ItemStack 的 `oc:isRunning`，由原生背包/装备同步，打开界面时再发 `TabletOpen/Update`。m7 已验证另一真实远程玩家看到的实际手持运行状态。 |
| 80 | `MouseClickOrDrag` | 四种屏幕/便携/终端 Input 中的 `TOUCH/DRAG`。 |
| 81 | `MouseScroll` | 四种 Input 中的 `SCROLL/MOUSE_SCROLL`。 |
| 82 | `MouseUp` | 四种 Input 的 `DROP`；实体屏幕、Terminal、Agent 和 Tablet 均接受边界外 `(-1,-1)`。Agent/Tablet 本批源码已补，实际验证见 G3。 |
| 83 | `MultiPartPlace` | PIN 留有 `sendMultiPlace()`，但其服务端 dispatch 没有处理分支；属于原已失效的 Multipart 辅助消息，不能据此虚构一个现代成功路径，也不能据此删除整项 Multipart 集成范围。 |
| 84 | `RackMountableMapping` | `RackNetworking.Edit` operation 0，服务端当前 Rack 菜单验证后修改映射。 |
| 85 | `RackRelayState` | `RackNetworking.Edit` operation 1。 |
| 86 | `RobotAssemblerStart` | 原生菜单按钮包 → `WorkstationMenu.clickMenuButton`。 |
| 87 | `RobotStateRequest` | 初始/进入跟踪区域由原生 chunk+Robot BE 完整快照提供；已打开菜单可发 `AgentNetworking.REQUEST`。m7 已验证实际菜单请求及两个客户端卸载旧区块、跨维度返回后的完整状态。 |
| 88 | `ServerPower` | `RackNetworking.Edit` operation 2，核对当前挂载式 `ServerMenu` 后操作真实 Server。 |

`EndOfList` 的批末结束作用已由现代 NBT 命令列表长度取代。上述 88 项均保留在表内，包括批内命令、替代同步方式和原本失效的消息。

本次源码核对及运行定位并修复了五个具体缺口，均已通过对应实际运行：

- **G1：活动 API 事件及数据**。PIN `server/PacketSender` 在发送文件系统/网络活动前 post 对应 `.Server` 事件，传递其可修改 NBT；客户端处理器再 post 对应 `.Client` 事件。核对公开 API 的实际注解后，只有 `FileSystemAccessEvent` 可取消；`NetworkActivityEvent` 虽有相关文档及发送端判断，却未声明可取消，本次保留其实际行为且不更改 SDK。发现时当前载荷没有 NBT、两端事件也未 post。现已补两端事件、精确坐标及方块实体/坐标宿主分支；文件系统取消阻止声音和默认活动，按原 node+sound 节流，取消不推进冷却；Rack 的默认节点匹配更新也置于服务端事件成功之后。m7 已通过真实 Case 和移动后 Robot 的双端事件、服务器修改 NBT 及两客户端接收。独立 `client-activity-20261028-l1` 也已 **PASS**，实际验证精确坐标、嵌套 NBT/有符号字节、服务端取消不发活动且不推进冷却、立即重试，以及客户端取消抑制默认声音和机箱指示。
- **G2：Robot 的错误与用户列表镜像**。PIN `ComputerState`、`ComputerUserList` 和客户端初始 NBT 同时覆盖 RobotProxy。发现时 Case/Micro 已有 `oc:hasErrored/oc:users`，Robot 只有运行标记与所有者。现已补 Robot BE 快照、稳定 controller 载入、状态变化时同步和客户端 `canInteract` 的镜像查询；客户端权限判断不再依赖服务器机器对象。m7 已实际验证两个远程客户端上的用户增删、权限变化和真实 Machine.crash 错误/停机镜像；独立活动场景也通过真实移动、重启后的错误清除和空用户列表权限恢复。
- **G3：Agent/Tablet 边界外松开**。PIN `client/gui/Screen.scala` 在拖动后移出缓冲区松开时发送 `MouseUp(-1,-1)`。发现时 `AgentScreen`、`TabletScreen` 仅在坐标仍有效时发送 DROP，`AgentNetworking`、`PortableNetworking` 也直接拒绝负坐标。本批已补界面的边界外松开和两端精确的 `DROP(-1,-1)` 处理，其他负坐标仍按原输入范围拒绝。真实 GUI 输入见[客户端效果](client-effects.md)的 `client-effects-20261028-l1`，m7 另通过真实远程 Robot/Tablet/Terminal 输入及实际底层信号。
- **G4：Robot 红石客户端格式**。`RobotController.writeClient` 调用 `redstone.saveClient` 写 `isOutputEnabled/output`，而发现时 `RobotController.load` 始终调用只读取 `oc:rs.*` 的保存格式 `redstone.load`，未恢复客户端输出镜像。真实双客户端运行 `tcp-20261028-l3` 在含 Robot 侧输出检查的 109 阶段超时，随后读取两端生产源码确认该格式缺口。本批已按明确的 `output` 整数数组识别客户端格式并调用 `loadClient`，其余数据继续使用保存格式 `load`，不依赖加载时是否已附加世界。m7 已通过原生远程 Robot 上侧输出 15 的镜像断言、后续用户/错误变化，以及两个玩家离开维度后的完整状态恢复。
- **G5：重新跟踪后的 clean bitblt 连续性**。m5 已通过返回后的真实屏幕栅格、全息体与 Robot 镜像检查；随后实际 GPU 的未修改页被复制到新位置，服务端四格已经为 `BACK`，客户端收到增量后却不满足相同字符，且没有所引用页。PIN 的已知 clean 页不会重复发 `ramInit`，缺页客户端会跳过复制。本批在实体屏幕的服务端发布线程按 owner/id 保存 `ramInit` 的私有 NBT 副本，按 `ramDestroy` 删除，只在完整快照携带这些引用页；正常增量及 GPU dirty/预算不变。离线 owner 的发布缓存被移除，并排队到下次原 GPU 复制路径清理旧引用、重新发正常初始化，主线程不锁定或遍历 VM 页。客户端保留同一个 TextBuffer，仅以完整快照替换页集合；同时补齐 PIN 初始化已有的最大宽高。Tablet/Terminal 的完整快照仅改为读取 `ram.data().save`，避免只查看状态就清除服务端 dirty。m7 已通过两端重新跟踪后真实 clean 页复制，服务端及两客户端四格均为 `[66, 65, 67, 75]`（`BACK`），最大分辨率恢复为 `80×25`，后续脏页增量继续生效。实际 GPU 释放、物理断开屏幕后清除页、恢复连线及 `bind(reset=false)` 后再次复制均通过；两个正常 Tablet/Terminal 全量查看入口也保持真实 GPU 页的 dirty 标记。

C19 固定范围现已完成。Windows 原生专用服务器与两个真实远程客户端的最终运行为
`tcp-20261028-m7`：服务端 **31 个子范围全部 PASS**，两个客户端均为 **PASS**，
最后正常断线并保存世界。报告位于：

- `build/api-compat-addon/multiplayer/tcp-20261028-m7/server/server-results.txt`
- `build/api-compat-addon/multiplayer/tcp-20261028-m7/OcTcpOne/client-results.txt`
- `build/api-compat-addon/multiplayer/tcp-20261028-m7/OcTcpTwo/client-results.txt`
- `build/api-compat-addon/integration-client/client-activity-20261028-l1/client-results.txt`（G1 专项，PASS）

真实 TCP 范围覆盖原生 Lua 机箱、所有权及会话拒绝、菜单切换/距离/离线按键释放、
同 UUID 和真实背包重连、宠物、RAID/剪贴板/路标、全部屏幕批命令与显存创建/复制/销毁、
全息体、原生设备与工作站状态、手持硬盘模式/锁定、世界磁盘与纳米机器、机架映射/中继、
Terminal/Robot/Drone/Tablet 的实际交互、状态及输入、活动 API 和生产效果发送。
两个客户端实际离开维度并卸载旧区块，返回后恢复屏幕/全息体/Robot 完整镜像，
再验证 clean 页的新位置复制、后续增量、释放及物理断开/重连后的页生命周期。
Drone 名字按 PIN 保留为服务端物品数据；原未分发的 `MultiPartPlace` 也按原实际行为记录。
以上有限场景核对 88 项映射的传输和应用，设备功能及图像/音频质量沿用各自的独立实际报告。

历史定位保留：m5 整体 **FAIL** 于 126 阶段真实 clean 页复制，确证 G5；
G5 修复后的 m6 已通过该复制、脏页保持及断开清理，但在 129 阶段因夹具提前要求
被动更新键盘界面字段而整体 **FAIL**。正常打开屏幕才会重新计算该字段，m7 改为实际右键
新接入键盘、打开真实 `ScreenGui` 后断言，随后完成全程。m6 的修正仅涉及夹具操作前提，
未为此改变生产同步语义。
