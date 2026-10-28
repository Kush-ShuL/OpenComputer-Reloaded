# 模型与材质

模型、纹理和原始贴图来自 MightyPirates/OpenComputers
`258943363ea8fc4ef14948e6bc6f67375061580c`，沿用项目的原许可与署名。
Minecraft 1.21.1 使用 `models/item`，这里不采用 1.21.4 的物品定义格式。

## 固定范围：41 个模型族

原 `Blocks.scala` 的 36 个注册方块合并机箱四级、全息两级和屏幕三级后为
30 族；再加入下面 11 个实体、物品和附加渲染族，共 41 族。
原 33 个 blockstate 文件、178 个 item model 文件包含旧名和抽象父模型，
不能代替这个范围。原 `accesspoint`、`switch` 素材归入中继器，不能另算两个设备。

下表是固定的验收矩阵。世界图片包含前上方和后下方视角，物品页同时调用原生
GUI 与 GROUND 渲染。状态列中列出的活动层还需要真实设备状态对应的图片。
自动场景输出 `models-*` 图片和逐项记录；图片须经过实际查看后才计入视觉验收。

自动入口为 `ModelClientContract.start(Minecraft, Path, List<String>)`，随后调用
`tick/isRunning/close`。它与其他会移动相机的场景顺序执行，不同时执行。
不需要额外模组，使用已有的原生 Lua 5.3 架构和本次独立测试世界。
入口已接入统一客户端的 `models` 场景。相机通过实际服务器传送更新，并等待
玩家视点和世界相机到达；活动场景给真实节点供能，等装配机和拆解机的客户端
工作状态到达后再保存图片，避免把未工作的设备算成工作层验收。

仅这个模型测试窗口临时限制为 12 FPS，结束时恢复原上限，减少与玩家客户端
同时运行时的显卡占用。该场景检验实际画面，不用它判断正常游戏帧率。画廊随
相机逐组等待真实区块跟踪和模型到达，不要求初始视距一次加载 72 格长的全画廊。
装配机和拆解机通过实际补料连续工作；每组活动截图仍要求客户端当前工作状态
为真。机架使用实际机架式软盘驱动器，不能用同名的外置方块代替。

图片对应关系：`models-world-default-*` / `models-world-active-*` 按原注册
方块每组三个排列，分别拍摄两侧；`models-items-*` 收录实际注册物品、创造预设、
16 色盘和真实绑定终端；其余图片名直接标明 Drone、HoverBoots、运行平板、
外挂燃烧、MFU、无线、实际移动、错误层及两种选中轮廓。原未配置物品与实际配置
变体分开列出，不以空白 Print 代替打印形状。

| ID | 原模型族 / 注册变体 | 默认、放置和掉落 | 活动 / 动态层 |
| --- | --- | --- | --- |
| M01 | Adapter | 外壳、物品、掉落 | 六面开关灯 |
| M02 | Assembler | 外壳、物品、掉落 | 顶部和内侧工作层 |
| M03 | Cable | 中心、真实邻接臂、物品、掉落 | 染色、连接轮廓 |
| M04 | Capacitor | 外壳、物品、掉落 | 无专用活动模型 |
| M05 | Case 1/2/3/creative | 四级外壳、物品、掉落 | 运行灯、磁盘灯、错误闪烁 |
| M06 | Chamelium | 外壳、物品、掉落 | 真实染色 |
| M07 | Charger | 外壳、物品、掉落 | 充能区域和工作层 |
| M08 | Disassembler | 外壳、物品、掉落 | 侧面和顶部工作层 |
| M09 | DiskDrive | 外壳、物品、掉落 | 插入、拔出、磁盘活动 |
| M10 | Geolyzer | 外壳、物品、掉落 | 动画顶层 |
| M11 | Hologram 1/2 | 两级外壳、物品、掉落 | 投影几何、色板、旋转 |
| M12 | Keyboard | 外壳、物品、掉落 | 安装方向 |
| M13 | MotionSensor | 外壳、物品、掉落 | 无专用活动模型 |
| M14 | PowerConverter | 外壳、物品、掉落 | 无专用活动模型 |
| M15 | PowerDistributor | 外壳、物品、掉落 | 真实网络电量发光层 |
| M16 | Printer | 外壳、物品、掉落 | 实际配置的旋转预览 |
| M17 | Raid | 外壳、物品、掉落 | 三盘存在状态、活动灯 |
| M18 | Redstone | 外壳、物品、掉落 | 无专用活动模型 |
| M19 | Relay | 原 switch/accesspoint 外观、物品、掉落 | 分组活动、无线层 |
| M20 | Screen 1/2/3 | 三级外壳、物品、掉落 | 横向、纵向、二维拼接边框；文字另归 C21 |
| M21 | Rack | 外壳、物品、掉落 | 四个实际托架、服务器/终端/磁盘前板与灯 |
| M22 | Waypoint | 外壳、物品、掉落 | 无专用活动模型 |
| M23 | Microcontroller | 空外壳与配置预设、物品、掉落 | 运行 / 错误灯 |
| M24 | Print | 空白原物品及实际配置形状、物品、掉落 | 开/关两组形状、旋转、逐形状轮廓 |
| M25 | RobotAfterimage | 原版明确的不可见模型 | 移动期间只由稳定机器人代理渲染实体 |
| M26 | Robot | 创造预设、工具、物品、掉落 | 运行间隙、彩灯、移动、转向 |
| M27 | EndStone | 方块、物品、掉落 | 无专用活动模型 |
| M28 | NetSplitter | 中心/接口、物品、掉落 | 六面 LED 与反相状态 |
| M29 | Transposer | 外壳、物品、掉落 | 真实操作的渐隐层 |
| M30 | CarpetedCapacitor | 外壳、物品、掉落 | 原固定地毯顶面 |
| M31 | Drone | 创造预设实体、物品、掉落 | 四翼、灯色和运行状态 |
| M32 | 普通平面物品 | 所有实际注册物品及创造预设的 GUI / GROUND 页 | 不是只检查 JSON 父模型 |
| M33 | Floppy | 16 色 GUI / GROUND、真实插盘 | 原颜色模型选择 |
| M34 | Tablet | 创造预设 GUI / GROUND | 真实运行前/后物品模型 |
| M35 | Terminal | 未绑定和真实绑定后的 GUI / GROUND | 根据 server 绑定选择 on/off |
| M36 | HoverBoots | 物品、实际穿戴 | 原四翼平台、彩灯 |
| M37 | 三种升级外挂 | 真实机器人上的 inventory/crafting/generator | 实际挂点、发电机熄灭/燃烧贴图 |
| M38 | Pet | 原有资格玩家的真实宠物 | 显示/隐藏、颜色、挂点 |
| M39 | MFU target | 真实物品绑定后的目标 | 目标轮廓及选定面 |
| M40 | Wireless debug | 实际无线设备 | 命令启用后的范围几何 |
| M41 | Highlight | 平板实际命中面、Print 与 Cable | 加法绿色面、真实形状和连接轮廓 |

## 本批确定修复

- 新 `HighlightRenderer` 恢复原版的平板目标面和 Print/Cable 专用轮廓，
  使用原 `hologram_effect` 材质，保持原加法混合与闪动设置。
- 平板和无线终端不再重复注册同一个运行属性。平板根据实际运行状态选模型；
  终端由唯一的注册入口根据真实 server 绑定选模型，与原版一致。
- 保留此前机器人的面、物品材质和磁盘插拔模型修复。

## 证据边界

`ModelClientContract` 使用正常连接的客户端、真实集成世界、原生方块/实体/
物品渲染器和实际配置预设，保存 PNG。不存在纹理、空几何和有意不可见是三种
不同情况：未配置 Print 和 RobotAfterimage 的原模型允许为空；实际配置的
Print、Robot、Drone、Tablet 仍须单独入镜。

Pet、机器人外挂等若已经在同一个真实 Effects 场景里渲染，可以复用该报告及
确切 PNG；不能把未执行的场景计划计为通过。GUI 背景另归 C18，文字及字体
另归 C21，网络包是否到达另归 C19。

M38 已有实际证据：[client-effects-20261028-l1 报告](../build/api-compat-addon/integration-client/client-effects-20261028-l1/client-results.txt)
为 PASS，原资格玩家实际进入宠物渲染路径并保留原颜色；
[`effects-pet-visible.png`](../build/api-compat-addon/integration-client/client-effects-20261028-l1/effects-pet-visible.png)
和 [`effects-pet-hidden.png`](../build/api-compat-addon/integration-client/client-effects-20261028-l1/effects-pet-hidden.png)
已由主代理实际查看，显示／隐藏正确。此项可以复用，不要求为模型画廊再重复该场景。

这份矩阵固定 C20 的 41 族模型范围，验收依据为实际画面。

`client-models-20261028-m1` 已在 Windows 原生客户端实际执行并正常退出，
报告为 PASS，保存 65 张 1280×720 原始 PNG。六页共 186 个原生物品条目，
默认两侧画廊以及活动、外挂、实体等图片已经逐张查看，未见缺失纹理棋盘。
其中首次远距离传送后的活动首排正面和 Print 高亮截图存在区块网格尚未就绪，
全息投影的独立展台也缺少真实底部储能。这三种情况不作为通过图片使用。

[`client-models-supplement-20261028-m2` 报告](../build/api-compat-addon/integration-client/client-models-supplement-20261028-m2/client-results.txt)
为 PASS，Windows 原生客户端正常保存全部维度并退出，Gradle 构建成功。
`ocfixture.modelFocus=supplement` 补拍的十张 1280×720 原始 PNG 已逐张查看：

| 补拍 | 实际结果 |
| --- | --- |
| 活动首排前、后两侧 | Adapter、Assembler、Cable 网格完整，装配机真实工作层可见 |
| 两档全息投影 | 实际底部电容器供电，客户端 `hasPower`、体素、色板、比例和 T2 旋转同步；两档投影均出现 |
| 外置驱动器插盘、退盘 | 同机位真实执行 `ejectMedium`，客户端槽位变空，盘片模型和指示灯随之改变 |
| Print 平板高亮 | 完整世界网格、真实打印形状、选中面和逐形状轮廓同时可见 |
| 配电器发光 | 实际客户端网络电量为 2766.34 / 3000，原发光层可见 |
| 机箱错误灯亮、灭 | 真实 `machine.crash` 后，同机位红色错误灯按原渲染时钟闪烁 |

补拍首次运行 `client-models-supplement-20261028-m1` 在第四张后因测试脚本从
客户端线程读取服务器方块实体而失败，报告保留；修复的是补拍脚本的读取线程，
未为此修改生产模型。后续 m2 的十张取代初次画廊中尚未就绪的画面。

固定的 C20 模型族验收已完成：原画廊 65 张、补拍 10 张与上述 M38 宠物证据
共同覆盖这 41 族，未见缺失纹理棋盘或应有模型面缺失。该结论对应这里列出的
模型族、状态和机位；GUI、文字、网络及完整移植进度仍由各自验收项记录。
