# 完整移植固定清单

此文件由 `python tools/migration_status.py --write` 生成；
唯一状态源为 [MIGRATION-CHECKLIST.json](MIGRATION-CHECKLIST.json)。

原版基线：[`258943363ea8fc4ef14948e6bc6f67375061580c`](https://github.com/MightyPirates/OpenComputers/tree/258943363ea8fc4ef14948e6bc6f67375061580c)。

**总计 120 项，已闭合 24 项，剩余 96 项。**

剩余包括：已有实现待核对/验收 88 项，有明确缺口 8 项。

这是固定工作项数量，不是工时比例；已有实现待验收不等于尚未编写该功能。

## 计数与结束条件

61个原设备/组件族 + 16个独立工具/附加玩法族 + 19个原integration一级模块 + 24个基础/交付项目。tier、共享trait、helper、修复、测试场景不新增项目。

该总表冻结于原版PIN；发现同范围遗漏行为归入现有ID，回归重开原ID。只有用户明确改变原版范围才修订基线总数，不为缩小剩余而删行。

每项按其列明的原版行为与有限验收条件闭合，提供代码接入与必要运行证据；不要求穷举外部mod自身实现、所有宿主架构笛卡尔积或未知未来场景。模型/菜单/多人等跨模块验收统一由C项承担。120项全部闭合且最终C24通过后才可宣称完整移植。

跨模块的模型、菜单、多人等工作由对应 C 项统一负责，不能用某个设备的局部运行通过替代这些项目。

已闭合项若出现回归，重开原 ID；不添加新的总项目。状态计数不替代证据。

## 数量

| 分组 | 总数 | 已闭合 | 剩余 |
| --- | ---: | ---: | ---: |
| 基础与交付 | 24 | 13 | 11 |
| 设备与组件 | 61 | 6 | 55 |
| 工具与附加玩法 | 16 | 0 | 16 |
| 可选集成 | 19 | 5 | 14 |

## 范围归属

- D01-D61覆盖原common/tileentity、common/entity、server/component的独立功能族；同族硬件item、driver、data、inventory与trait归该族。
- T01-T16覆盖common/item中无独立组件类的工具/升级/材料，以及纳米机器、人工末地石与终端服务器。
- I01-I19逐一覆盖原integration的19个外部/原版平台模块；opencomputers和util为内建框架，归C05和相应D项。
- C01-C24覆盖API、平台/启动/ASM替代、网络/VM/文件系统、程序资源、配置/配方、创造预设、菜单/载荷、模型/纹理/字体/声音、翻译/命令及交付。
- 原工具类、proxy、共享traits、原文档和资源归调用功能及对应C/T项，不以文件数、测试数或发现的bug数增加项目。

## 基础与交付

### C01 · 许可与上游署名 · 已闭合

完成条件：保留原版及第三方许可、作者和资源署名；分发条款与来源说明一致。

原版范围：`LICENSE`、`LICENSE-jnlua`、`LICENSE-luaj`、`LICENSE-mods`

当前接入：[LICENSE](../LICENSE)、[NOTICE](../NOTICE)

闭合依据：[LICENSE](../LICENSE)、[NOTICE](../NOTICE)

### C02 · 独立仓库、Java 21 与 NeoForge 构建 · 已闭合

完成条件：独立 Git 历史；1.21.1/NeoForge 完整 Java 构建；不依赖 Scala。

原版范围：`src/main/scala/li/cil/oc/OpenComputers.scala`、`build.gradle`

当前接入：[build.gradle](../build.gradle)、[settings.gradle](../settings.gradle)

闭合依据：[docs/PORTING.md](../docs/PORTING.md)、[build/launch-support/windows-hardware-build.log](../build/launch-support/windows-hardware-build.log)

### C03 · 公开 SDK 与 JitPack API 发布 · 已闭合

完成条件：独立 compileOnly SDK；旧 Converter 二参二进制兼容、注册表持久化及外部消费者通过；JitPack API 发行不附资产。

原版范围：`src/main/java/li/cil/oc/api/`

当前接入：[api/](../api/)、[src/main/java/li/cil/oc/api/](../src/main/java/li/cil/oc/api/)

闭合依据：[docs/API.md](../docs/API.md)、[docs/PORTING.md](../docs/PORTING.md)、[jitpack.yml](../jitpack.yml)

### C04 · 模组生命周期、注册与专用服务端隔离 · 已闭合

完成条件：注册设备与能力，完成 common/client 初始化，服务端不加载客户端实现；旧加载器/ASM的必需行为由现代事件与能力承接。

原版范围：`src/main/scala/li/cil/oc/OpenComputers.scala`、`src/main/scala/li/cil/oc/common/init/`、`src/main/scala/li/cil/oc/common/launch/`、`src/main/scala/li/cil/oc/common/asm/`

当前接入：[src/main/java/li/cil/oc/OpenComputers.java](../src/main/java/li/cil/oc/OpenComputers.java)、[src/main/java/li/cil/oc/common/init/](../src/main/java/li/cil/oc/common/init/)

闭合依据：[build/launch-support/windows-projectred-default.log](../build/launch-support/windows-projectred-default.log)、[build/launch-support/windows-jade-default.log](../build/launch-support/windows-jade-default.log)

### C05 · 驱动、转换器与 IMC 扩展框架 · 已闭合

完成条件：外部 SDK 驱动发现、Value/Converter、程序盘与真实装配/拆解 IMC 链路通过；各可选模组驱动归对应 I 项。

原版范围：`src/main/scala/li/cil/oc/server/driver/`、`src/main/scala/li/cil/oc/common/IMC.scala`、`src/main/scala/li/cil/oc/integration/opencomputers/`、`src/main/scala/li/cil/oc/integration/util/`

当前接入：[src/main/java/li/cil/oc/server/driver/](../src/main/java/li/cil/oc/server/driver/)、[src/main/java/li/cil/oc/common/IMC.java](../src/main/java/li/cil/oc/common/IMC.java)

闭合依据：[docs/API.md](../docs/API.md)、[docs/PORTING.md](../docs/PORTING.md)、[tools/api-compat-addon/src/main/java/ocfixture/ProductionContract.java](../tools/api-compat-addon/src/main/java/ocfixture/ProductionContract.java)

### C06 · 组件网络、可见性、消息与能量连接器 · 已闭合

完成条件：节点生命周期、可见/可达、合并/拆分、消息和缓冲传播符合原接口；真实宿主私有网/外部网保持隔离。

原版范围：`src/main/scala/li/cil/oc/server/network/`、`src/main/scala/li/cil/oc/server/ComponentTracker.scala`、`src/main/scala/li/cil/oc/common/ComponentTracker.scala`

当前接入：[src/main/java/li/cil/oc/server/network/](../src/main/java/li/cil/oc/server/network/)

闭合依据：[docs/PORTING.md](../docs/PORTING.md)、[tools/api-compat-addon/src/main/java/ocfixture/BaseContract.java](../tools/api-compat-addon/src/main/java/ocfixture/BaseContract.java)

### C07 · 机器调度、预算、线程与异常处理 · 已有实现，待核对/验收

完成条件：原回调预算、内存限制、暂停/停止/超时、线程安全与错误传播闭合；已有死锁修复保留。

剩余：核对剩余调度/资源限制分支；已有原生及 LuaJ 回归只覆盖部分路径。

原版范围：`src/main/scala/li/cil/oc/server/machine/`、`src/main/scala/li/cil/oc/common/SaveHandler.scala`

当前接入：[src/main/java/li/cil/oc/server/machine/](../src/main/java/li/cil/oc/server/machine/)

### C08 · 原生 Lua 5.2 运行与状态恢复 · 已闭合

完成条件：真实 Lua 5.2 启动、同步回调、协程/Value/文件位置与独立 JVM、区块恢复通过；不要求穷举全部宿主组合。

原版范围：`src/main/scala/li/cil/oc/server/machine/`

当前接入：[src/main/java/li/cil/oc/server/machine/](../src/main/java/li/cil/oc/server/machine/)

闭合依据：[docs/PORTING.md](../docs/PORTING.md)、[build/api-compat-addon/persistence/native52-20261026-202339/resume-results.txt](../build/api-compat-addon/persistence/native52-20261026-202339/resume-results.txt)

### C09 · 原生 Lua 5.3 运行与状态恢复 · 已闭合

完成条件：真实 Lua 5.3 回调、协程/Value/打开文件、队列与独立 JVM 恢复通过。

原版范围：`src/main/scala/li/cil/oc/server/machine/`

当前接入：[src/main/java/li/cil/oc/server/machine/](../src/main/java/li/cil/oc/server/machine/)

闭合依据：[docs/PORTING.md](../docs/PORTING.md)、[build/api-compat-addon/hosts/robot/robot-native53-20261026-203657/resume-results.txt](../build/api-compat-addon/hosts/robot/robot-native53-20261026-203657/resume-results.txt)

### C10 · LuaJ 运行及原版重启式读档 · 已闭合

完成条件：LuaJ 实际回调转换通过，读档真实重启一次并保留磁盘/EEPROM数据；不冒充原生栈恢复。

原版范围：`src/main/scala/li/cil/oc/server/machine/`

当前接入：[src/main/java/li/cil/oc/server/machine/](../src/main/java/li/cil/oc/server/machine/)

闭合依据：[docs/PORTING.md](../docs/PORTING.md)、[build/api-compat-addon/persistence/luaj-20261026-181932/resume-results.txt](../build/api-compat-addon/persistence/luaj-20261026-181932/resume-results.txt)

### C11 · 文件系统、沙箱、配额与句柄 · 已有实现，待核对/验收

完成条件：原路径/只读/配额/句柄和读写/寻址/重命名/删除行为闭合；硬件介质归 D14，程序盘归 C15。

剩余：已有真实读写和恢复；仍需把路径、只读、配额、句柄边界按原实现闭合。

原版范围：`src/main/scala/li/cil/oc/server/fs/`、`src/main/scala/li/cil/oc/server/component/FileSystem.scala`

当前接入：[src/main/java/li/cil/oc/server/fs/](../src/main/java/li/cil/oc/server/fs/)、[src/main/java/li/cil/oc/server/component/FileSystem.java](../src/main/java/li/cil/oc/server/component/FileSystem.java)

### C12 · 物理宿主保存、冷恢复与区块生命周期 · 已闭合

完成条件：Case 的 5.2/5.3/LuaJ 与 Robot/Drone/Tablet/Microcontroller/Rack 的 5.3 独立进程保存恢复及已实现区块生命周期通过；玩家网络流程由 C19验收。

原版范围：`src/main/scala/li/cil/oc/common/SaveHandler.scala`、`src/main/scala/li/cil/oc/server/agent/`、`src/main/scala/li/cil/oc/common/item/data/`

当前接入：[src/main/java/li/cil/oc/server/agent/](../src/main/java/li/cil/oc/server/agent/)、[src/main/java/li/cil/oc/server/portable/](../src/main/java/li/cil/oc/server/portable/)

闭合依据：[docs/PORTING.md](../docs/PORTING.md)、[tools/api-compat-addon/README.md](../tools/api-compat-addon/README.md)

### C13 · OpenOS 安装与基础系统程序 · 已有实现，待核对/验收

完成条件：保留原系统及许可；空白介质安装、重启、终端输入、文件/组件基础程序通过。

剩余：已有开发客户端启动使用记录；需按固定安装到重启流程闭合。

原版范围：`src/main/resources/assets/opencomputers/loot/openos/`

当前接入：[src/main/resources/assets/opencomputers/loot/](../src/main/resources/assets/opencomputers/loot/)

### C14 · Plan9k 安装与基础系统程序 · 已有实现，待核对/验收

完成条件：保留原系统与许可；安装、引导、基础进程/终端/文件操作通过。

剩余：保留系统并修过卡死；需闭合真实安装、重启及基础程序流程。

原版范围：`src/main/resources/assets/opencomputers/loot/plan9k/`

当前接入：[src/main/resources/assets/opencomputers/loot/](../src/main/resources/assets/opencomputers/loot/)

### C15 · 程序盘、箱子掉落及存档自定义盘 · 已闭合

完成条件：真实目标箱子生成保留原版战利品；程序盘完整、标记清除、零权重过滤、真实挂载读字节，自定义程序来源保留。

原版范围：`src/main/scala/li/cil/oc/common/Loot.scala`、`src/main/resources/assets/opencomputers/loot/`

当前接入：[src/main/java/li/cil/oc/common/integration/LootIntegration.java](../src/main/java/li/cil/oc/common/integration/LootIntegration.java)、[src/main/java/li/cil/oc/common/integration/ProgramDiskLootModifier.java](../src/main/java/li/cil/oc/common/integration/ProgramDiskLootModifier.java)

闭合依据：[docs/loot-and-updates.md](../docs/loot-and-updates.md)、[build/api-compat-addon/devices/devices-loot-20261026-203027/device-results.txt](../build/api-compat-addon/devices/devices-loot-20261026-203027/device-results.txt)

### C16 · HOCON 配置加载、升级与保留 · 已闭合

完成条件：默认项、旧版本补丁、数组/范围、错误文件保留和未知键保留通过；具体设备配置消费者由该设备项负责。

原版范围：`src/main/scala/li/cil/oc/Settings.scala`、`src/main/resources/application.conf`

当前接入：[src/main/java/li/cil/oc/Settings.java](../src/main/java/li/cil/oc/Settings.java)、[tools/config-verification/](../tools/config-verification/)

闭合依据：[docs/CONFIGURATION.md](../docs/CONFIGURATION.md)、[tools/config-verification/src/main/java/li/cil/oc/tools/config/ConfigurationVerification.java](../tools/config-verification/src/main/java/li/cil/oc/tools/config/ConfigurationVerification.java)

### C17 · 合成、配方档案、重合成与材料返还 · 已有实现，待核对/验收

完成条件：原配方档案、动态配方、装配/拆解材料与重合成保留；数据包覆盖及许可资源一致。

剩余：原HOCON档案、user.recipes include、自定义shaped/shapeless/furnace、布尔禁用/隐藏、generic/lootdisks和数据包重载已接入；实际自定义与重载通过。尚需闭合GregTech机器配方处理器、流体材料和其余生产/拆解动态配方差异。

原版范围：`src/main/scala/li/cil/oc/common/recipe/`、`src/main/resources/assets/opencomputers/recipes/`

当前接入：[src/main/java/li/cil/oc/common/recipe/](../src/main/java/li/cil/oc/common/recipe/)、[src/main/resources/data/opencomputers/](../src/main/resources/data/opencomputers/)

闭合依据：[docs/CONFIGURATION.md](../docs/CONFIGURATION.md)、[tools/api-compat-addon/src/main/java/ocfixture/RecipeConfigContract.java](../tools/api-compat-addon/src/main/java/ocfixture/RecipeConfigContract.java)、[build/api-compat-addon/devices/recipe-config-20261027-1249/device-results.txt](../build/api-compat-addon/devices/recipe-config-20261027-1249/device-results.txt)

### C18 · 创造物品栏与四类预配置设备 · 已闭合

完成条件：原配置的 Robot/Drone/Tablet/Microcontroller 真实放置及合法硬件、软盘容器路径可用。

原版范围：`src/main/scala/li/cil/oc/CreativeTab.scala`、`src/main/scala/li/cil/oc/common/init/`

当前接入：[src/main/java/li/cil/oc/common/init/Items.java](../src/main/java/li/cil/oc/common/init/Items.java)

闭合依据：[docs/PORTING.md](../docs/PORTING.md)、[tools/api-compat-addon/src/main/java/ocfixture/DeviceContract.java](../tools/api-compat-addon/src/main/java/ocfixture/DeviceContract.java)

### C19 · 多人载荷、菜单、权限与玩家生命周期 · 已有实现，待核对/验收

完成条件：原包族映射与真实两客户端菜单/输入/权限/关闭/切换宿主流程通过；每个包族一次代表路径，不穷举玩家组合。

剩余：当前不少证明使用本地mock transport；还缺真实远程客户端往返和玩家保存/退出流程。

原版范围：`src/main/scala/li/cil/oc/common/PacketType.scala`、`src/main/scala/li/cil/oc/common/PacketBuilder.scala`、`src/main/scala/li/cil/oc/server/PacketHandler.scala`、`src/main/scala/li/cil/oc/client/PacketHandler.scala`、`src/main/scala/li/cil/oc/common/container/`

当前接入：[src/main/java/li/cil/oc/common/network/](../src/main/java/li/cil/oc/common/network/)、[src/main/java/li/cil/oc/common/menu/](../src/main/java/li/cil/oc/common/menu/)

### C20 · 全部方块/物品模型、纹理与动态图层 · 已有实现，待核对/验收

完成条件：各原模型族的默认/活动/掉落物状态无缺面、紫黑或错误UV；每个模型族一次可视验收。

剩余：已修机箱、机器人、驱动器等具体问题；其他原模型族仍需固定清单中的可视验收。

原版范围：`src/main/scala/li/cil/oc/client/renderer/`、`src/main/scala/li/cil/oc/client/Textures.scala`、`src/main/resources/assets/opencomputers/textures/`

当前接入：[src/main/java/li/cil/oc/client/](../src/main/java/li/cil/oc/client/)、[src/main/resources/assets/opencomputers/](../src/main/resources/assets/opencomputers/)

### C21 · 文字缓冲、字体、屏幕与着色渲染 · 已有实现，待核对/验收

完成条件：字体、缩放、颜色、Unicode、GPU缓冲显示与原屏幕交互正确；相关接口本身归D18/D37。

剩余：源码与缓存修复已有；尚需闭合文字/显存显示场景。

原版范围：`src/main/scala/li/cil/oc/common/component/`、`src/main/scala/li/cil/oc/client/renderer/font/`、`src/main/scala/li/cil/oc/client/renderer/gui/BufferRenderer.scala`

当前接入：[src/main/java/li/cil/oc/common/component/](../src/main/java/li/cil/oc/common/component/)、[src/main/java/li/cil/oc/client/renderer/](../src/main/java/li/cil/oc/client/renderer/)

### C22 · 客户端效果、声音、宠物、命令与更新提示 · 已有实现，待核对/验收

完成条件：原声音/粒子/宠物/显示设置/客户端及服务器命令/调试与更新提示各一条代表路径可用。PIN的Achievement是空占位，保留其无行为语义，无需创造额外成就。

剩余：已注册实现；部分实际玩家显示、声音范围与更新网络流程待验收。

原版范围：`src/main/scala/li/cil/oc/client/Sound.scala`、`src/main/scala/li/cil/oc/common/Sound.scala`、`src/main/scala/li/cil/oc/client/renderer/PetRenderer.scala`、`src/main/scala/li/cil/oc/server/PetVisibility.scala`、`src/main/scala/li/cil/oc/client/command/`、`src/main/scala/li/cil/oc/common/Achievement.scala`、`src/main/scala/li/cil/oc/server/command/`、`src/main/scala/li/cil/oc/common/command/`

当前接入：[src/main/java/li/cil/oc/client/](../src/main/java/li/cil/oc/client/)、[src/main/java/li/cil/oc/common/integration/UpdateChecker.java](../src/main/java/li/cil/oc/common/integration/UpdateChecker.java)

### C23 · 翻译、全部菜单布局与提示文本 · 已有实现，待核对/验收

完成条件：原菜单族全部有背景、槽位、可读文本和本地化；提示不残留、不遮挡主操作，正常帧率。

剩余：机器人GUI已由玩家确认约60FPS；其他原菜单族和长文本仍需一次可视验收。

原版范围：`src/main/scala/li/cil/oc/Localization.scala`、`src/main/scala/li/cil/oc/client/gui/`、`src/main/resources/assets/opencomputers/lang/`

当前接入：[src/main/java/li/cil/oc/client/gui/](../src/main/java/li/cil/oc/client/gui/)、[src/main/resources/assets/opencomputers/lang/](../src/main/resources/assets/opencomputers/lang/)

### C24 · 最终完整模组构建与玩家安装说明 · 已有实现，待核对/验收

完成条件：其余固定项目闭合后，用最终同一版本在Windows客户端和专用服务端启动，提供主模组与独立SDK的明确安装说明；无需额外公开资产发行。

剩余：须在其余项目闭合后完成最终交付验证。

原版范围：`build.gradle`、`README.md`

当前接入：[build.gradle](../build.gradle)、[README.md](../README.md)、[docs/PORTING.md](../docs/PORTING.md)


## 设备与组件

### D01 · APU · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/server/component/APU.scala`

当前接入：[src/main/java/li/cil/oc/server/component/APU.java](../src/main/java/li/cil/oc/server/component/APU.java)

### D02 · 适配器 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/common/tileentity/Adapter.scala`

当前接入：[src/main/java/li/cil/oc/common/block/entity/AdapterBlockEntity.java](../src/main/java/li/cil/oc/common/block/entity/AdapterBlockEntity.java)

### D03 · 装配机 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/common/tileentity/Assembler.scala`

当前接入：[src/main/java/li/cil/oc/common/block/entity/AssemblerBlockEntity.java](../src/main/java/li/cil/oc/common/block/entity/AssemblerBlockEntity.java)

### D04 · CPU · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/server/component/CPU.scala`

当前接入：[src/main/java/li/cil/oc/server/driver/item/Processor.java](../src/main/java/li/cil/oc/server/driver/item/Processor.java)

### D05 · 线缆 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/common/tileentity/Cable.scala`

当前接入：[src/main/java/li/cil/oc/common/block/entity/CableBlockEntity.java](../src/main/java/li/cil/oc/common/block/entity/CableBlockEntity.java)

### D06 · 普通及地毯电容 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/common/tileentity/Capacitor.scala`、`src/main/scala/li/cil/oc/common/tileentity/CarpetedCapacitor.scala`

当前接入：[src/main/java/li/cil/oc/common/block/entity/CapacitorBlockEntity.java](../src/main/java/li/cil/oc/common/block/entity/CapacitorBlockEntity.java)、[src/main/java/li/cil/oc/common/block/entity/CarpetedCapacitorBlockEntity.java](../src/main/java/li/cil/oc/common/block/entity/CarpetedCapacitorBlockEntity.java)

### D07 · 机箱 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/common/tileentity/Case.scala`

当前接入：[src/main/java/li/cil/oc/common/block/entity/CaseBlockEntity.java](../src/main/java/li/cil/oc/common/block/entity/CaseBlockEntity.java)

### D08 · 充电器 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/common/tileentity/Charger.scala`

当前接入：[src/main/java/li/cil/oc/common/block/entity/ChargerBlockEntity.java](../src/main/java/li/cil/oc/common/block/entity/ChargerBlockEntity.java)

### D09 · 数据卡 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/server/component/DataCard.scala`

当前接入：[src/main/java/li/cil/oc/server/component/DataCard.java](../src/main/java/li/cil/oc/server/component/DataCard.java)

### D10 · 调试卡 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/server/component/DebugCard.scala`

当前接入：[src/main/java/li/cil/oc/server/component/DebugCard.java](../src/main/java/li/cil/oc/server/component/DebugCard.java)

### D11 · 拆解机 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/common/tileentity/Disassembler.scala`

当前接入：[src/main/java/li/cil/oc/common/block/entity/DisassemblerBlockEntity.java](../src/main/java/li/cil/oc/common/block/entity/DisassemblerBlockEntity.java)

### D12 · 外置软盘驱动器 · 已闭合

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

原版范围：`src/main/scala/li/cil/oc/common/tileentity/DiskDrive.scala`

当前接入：[src/main/java/li/cil/oc/common/block/entity/DiskDriveBlockEntity.java](../src/main/java/li/cil/oc/common/block/entity/DiskDriveBlockEntity.java)

闭合依据：[docs/PORTING.md](../docs/PORTING.md)、[tools/api-compat-addon/src/main/java/ocfixture/DeviceContract.java](../tools/api-compat-addon/src/main/java/ocfixture/DeviceContract.java)

### D13 · 机架式软盘驱动器 · 已闭合

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

原版范围：`src/main/scala/li/cil/oc/server/component/DiskDriveMountable.scala`

当前接入：[src/main/java/li/cil/oc/server/component/DiskDriveMountable.java](../src/main/java/li/cil/oc/server/component/DiskDriveMountable.java)

闭合依据：[docs/PORTING.md](../docs/PORTING.md)、[tools/api-compat-addon/src/main/java/ocfixture/DeviceContract.java](../tools/api-compat-addon/src/main/java/ocfixture/DeviceContract.java)

### D14 · 硬盘及软盘存储 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/server/component/Drive.scala`

当前接入：[src/main/java/li/cil/oc/server/component/Drive.java](../src/main/java/li/cil/oc/server/component/Drive.java)

### D15 · 无人机 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：共享流体控制缺口已修，Robot原生路径通过；仍需本族其他公开飞行/动作与行为核对，未宣称Drone流体或整族验收完成。

原版范围：`src/main/scala/li/cil/oc/common/entity/Drone.scala`、`src/main/scala/li/cil/oc/server/component/Drone.scala`

当前接入：[src/main/java/li/cil/oc/common/entity/DroneEntity.java](../src/main/java/li/cil/oc/common/entity/DroneEntity.java)、[src/main/java/li/cil/oc/server/component/Drone.java](../src/main/java/li/cil/oc/server/component/Drone.java)

### D16 · EEPROM · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/server/component/EEPROM.scala`

当前接入：[src/main/java/li/cil/oc/server/component/EEPROM.java](../src/main/java/li/cil/oc/server/component/EEPROM.java)

### D17 · 地质分析仪 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/common/tileentity/Geolyzer.scala`、`src/main/scala/li/cil/oc/server/component/Geolyzer.scala`

当前接入：[src/main/java/li/cil/oc/common/block/entity/GeolyzerBlockEntity.java](../src/main/java/li/cil/oc/common/block/entity/GeolyzerBlockEntity.java)、[src/main/java/li/cil/oc/server/component/Geolyzer.java](../src/main/java/li/cil/oc/server/component/Geolyzer.java)

### D18 · 显卡及显存 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/server/component/GraphicsCard.scala`

当前接入：[src/main/java/li/cil/oc/server/component/GraphicsCard.java](../src/main/java/li/cil/oc/server/component/GraphicsCard.java)

### D19 · 全息投影 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/common/tileentity/Hologram.scala`

当前接入：[src/main/java/li/cil/oc/common/block/entity/HologramBlockEntity.java](../src/main/java/li/cil/oc/common/block/entity/HologramBlockEntity.java)、[src/main/java/li/cil/oc/server/component/Hologram.java](../src/main/java/li/cil/oc/server/component/Hologram.java)

### D20 · 因特网卡 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/server/component/InternetCard.scala`

当前接入：[src/main/java/li/cil/oc/server/component/InternetCard.java](../src/main/java/li/cil/oc/server/component/InternetCard.java)

### D21 · 键盘 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/common/tileentity/Keyboard.scala`、`src/main/scala/li/cil/oc/server/component/Keyboard.scala`

当前接入：[src/main/java/li/cil/oc/common/block/entity/KeyboardBlockEntity.java](../src/main/java/li/cil/oc/common/block/entity/KeyboardBlockEntity.java)、[src/main/java/li/cil/oc/server/component/Keyboard.java](../src/main/java/li/cil/oc/server/component/Keyboard.java)

### D22 · 量子链接卡 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/server/component/LinkedCard.scala`

当前接入：[src/main/java/li/cil/oc/server/component/LinkedCard.java](../src/main/java/li/cil/oc/server/component/LinkedCard.java)

### D23 · 内存 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/server/component/Memory.scala`

当前接入：[src/main/java/li/cil/oc/server/driver/item/Memory.java](../src/main/java/li/cil/oc/server/driver/item/Memory.java)

### D24 · 微控制器 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/common/tileentity/Microcontroller.scala`

当前接入：[src/main/java/li/cil/oc/common/block/entity/MicrocontrollerBlockEntity.java](../src/main/java/li/cil/oc/common/block/entity/MicrocontrollerBlockEntity.java)

### D25 · 运动传感器 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/common/tileentity/MotionSensor.scala`、`src/main/scala/li/cil/oc/server/component/MotionSensor.scala`

当前接入：[src/main/java/li/cil/oc/common/block/entity/MotionSensorBlockEntity.java](../src/main/java/li/cil/oc/common/block/entity/MotionSensorBlockEntity.java)、[src/main/java/li/cil/oc/server/component/MotionSensor.java](../src/main/java/li/cil/oc/server/component/MotionSensor.java)

### D26 · 网络分离器 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/common/tileentity/NetSplitter.scala`

当前接入：[src/main/java/li/cil/oc/common/block/entity/NetSplitterBlockEntity.java](../src/main/java/li/cil/oc/common/block/entity/NetSplitterBlockEntity.java)

### D27 · 有线网卡 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/server/component/NetworkCard.scala`

当前接入：[src/main/java/li/cil/oc/server/component/NetworkCard.java](../src/main/java/li/cil/oc/server/component/NetworkCard.java)

### D28 · 能源转换器 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/common/tileentity/PowerConverter.scala`

当前接入：[src/main/java/li/cil/oc/common/block/entity/PowerConverterBlockEntity.java](../src/main/java/li/cil/oc/common/block/entity/PowerConverterBlockEntity.java)

### D29 · 能源分配器 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/common/tileentity/PowerDistributor.scala`

当前接入：[src/main/java/li/cil/oc/common/block/entity/PowerDistributorBlockEntity.java](../src/main/java/li/cil/oc/common/block/entity/PowerDistributorBlockEntity.java)

### D30 · 打印方块 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/common/tileentity/Print.scala`

当前接入：[src/main/java/li/cil/oc/common/block/entity/PrintBlockEntity.java](../src/main/java/li/cil/oc/common/block/entity/PrintBlockEntity.java)

### D31 · 3D打印机 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/common/tileentity/Printer.scala`

当前接入：[src/main/java/li/cil/oc/common/block/entity/PrinterBlockEntity.java](../src/main/java/li/cil/oc/common/block/entity/PrinterBlockEntity.java)、[src/main/java/li/cil/oc/server/component/Printer.java](../src/main/java/li/cil/oc/server/component/Printer.java)

### D32 · 机架 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/common/tileentity/Rack.scala`

当前接入：[src/main/java/li/cil/oc/common/block/entity/RackBlockEntity.java](../src/main/java/li/cil/oc/common/block/entity/RackBlockEntity.java)

### D33 · 磁盘阵列 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/common/tileentity/Raid.scala`

当前接入：[src/main/java/li/cil/oc/common/block/entity/RaidBlockEntity.java](../src/main/java/li/cil/oc/common/block/entity/RaidBlockEntity.java)

### D34 · 普通及集束红石 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/common/tileentity/Redstone.scala`、`src/main/scala/li/cil/oc/server/component/Redstone.scala`、`src/main/scala/li/cil/oc/server/component/RedstoneBundled.scala`、`src/main/scala/li/cil/oc/server/component/RedstoneSignaller.scala`、`src/main/scala/li/cil/oc/server/component/RedstoneVanilla.scala`、`src/main/scala/li/cil/oc/server/component/RedstoneWireless.scala`

当前接入：[src/main/java/li/cil/oc/common/block/entity/RedstoneBlockEntity.java](../src/main/java/li/cil/oc/common/block/entity/RedstoneBlockEntity.java)、[src/main/java/li/cil/oc/server/component/Redstone.java](../src/main/java/li/cil/oc/server/component/Redstone.java)、[src/main/java/li/cil/oc/server/component/RedstoneBundled.java](../src/main/java/li/cil/oc/server/component/RedstoneBundled.java)

### D35 · 中继器 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/common/tileentity/Relay.scala`

当前接入：[src/main/java/li/cil/oc/common/block/entity/RelayBlockEntity.java](../src/main/java/li/cil/oc/common/block/entity/RelayBlockEntity.java)

### D36 · 机器人 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：跨罐交换、空罐和世界流体控制已由真实Robot原生Lua通过；其余机器人动作、交互与原版差异核对仍待闭合。

原版范围：`src/main/scala/li/cil/oc/common/tileentity/Robot.scala`、`src/main/scala/li/cil/oc/common/tileentity/RobotProxy.scala`、`src/main/scala/li/cil/oc/server/component/Robot.scala`

当前接入：[src/main/java/li/cil/oc/common/block/entity/RobotBlockEntity.java](../src/main/java/li/cil/oc/common/block/entity/RobotBlockEntity.java)、[src/main/java/li/cil/oc/server/agent/RobotController.java](../src/main/java/li/cil/oc/server/agent/RobotController.java)、[src/main/java/li/cil/oc/server/component/Robot.java](../src/main/java/li/cil/oc/server/component/Robot.java)

### D37 · 屏幕 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/common/tileentity/Screen.scala`

当前接入：[src/main/java/li/cil/oc/common/block/entity/ScreenBlockEntity.java](../src/main/java/li/cil/oc/common/block/entity/ScreenBlockEntity.java)

### D38 · 服务器刀片 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/server/component/Server.scala`

当前接入：[src/main/java/li/cil/oc/server/component/Server.java](../src/main/java/li/cil/oc/server/component/Server.java)

### D39 · 平板电脑 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/server/component/Tablet.scala`

当前接入：[src/main/java/li/cil/oc/server/portable/TabletHost.java](../src/main/java/li/cil/oc/server/portable/TabletHost.java)

### D40 · 转运器 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：外部储罐显式nil参数已恢复原版严格校验；转运器物品/流体搬运及数据库的整族实际操作验收仍待闭合。

原版范围：`src/main/scala/li/cil/oc/common/tileentity/Transposer.scala`、`src/main/scala/li/cil/oc/server/component/Transposer.scala`

当前接入：[src/main/java/li/cil/oc/common/block/entity/TransposerBlockEntity.java](../src/main/java/li/cil/oc/common/block/entity/TransposerBlockEntity.java)、[src/main/java/li/cil/oc/server/component/Transposer.java](../src/main/java/li/cil/oc/server/component/Transposer.java)

### D41 · 天使升级 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/server/component/UpgradeAngel.scala`

当前接入：[src/main/java/li/cil/oc/server/component/UpgradeAngel.java](../src/main/java/li/cil/oc/server/component/UpgradeAngel.java)

### D42 · 条码读取升级 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/server/component/UpgradeBarcodeReader.scala`

当前接入：[src/main/java/li/cil/oc/server/component/UpgradeBarcodeReader.java](../src/main/java/li/cil/oc/server/component/UpgradeBarcodeReader.java)

### D43 · 电池升级 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/server/component/UpgradeBattery.scala`

当前接入：[src/main/java/li/cil/oc/server/component/UpgradeBattery.java](../src/main/java/li/cil/oc/server/component/UpgradeBattery.java)

### D44 · 区块加载升级 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/server/component/UpgradeChunkloader.scala`

当前接入：[src/main/java/li/cil/oc/server/component/UpgradeChunkloader.java](../src/main/java/li/cil/oc/server/component/UpgradeChunkloader.java)

### D45 · 合成升级 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/server/component/UpgradeCrafting.scala`

当前接入：[src/main/java/li/cil/oc/server/component/UpgradeCrafting.java](../src/main/java/li/cil/oc/server/component/UpgradeCrafting.java)

### D46 · 数据库升级 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/server/component/UpgradeDatabase.scala`

当前接入：[src/main/java/li/cil/oc/server/component/UpgradeDatabase.java](../src/main/java/li/cil/oc/server/component/UpgradeDatabase.java)

### D47 · 经验升级 · 已闭合

完成条件：原经验获得/公式/组件可见性与容量、consume完整吞掉物品、满级拒绝、保存恢复、分析仪事件和物品分数等级接齐；真实原生机器人验证消耗、两个等级状态、XP/地址/1234.5电量恢复、一次真实分析入口及实际物品tooltip。C19多人/C23实屏显示统一跟踪。

原版范围：`src/main/scala/li/cil/oc/server/component/UpgradeExperience.scala`

当前接入：[src/main/java/li/cil/oc/server/component/UpgradeExperience.java](../src/main/java/li/cil/oc/server/component/UpgradeExperience.java)

闭合依据：[build/api-compat-addon/devices/experience-20261027-120333/device-results.txt](../build/api-compat-addon/devices/experience-20261027-120333/device-results.txt)、[tools/api-compat-addon/src/main/java/ocfixture/ExperienceContract.java](../tools/api-compat-addon/src/main/java/ocfixture/ExperienceContract.java)、[src/main/java/li/cil/oc/server/component/UpgradeExperience.java](../src/main/java/li/cil/oc/server/component/UpgradeExperience.java)、[src/main/java/li/cil/oc/common/init/AgentUpgrades.java](../src/main/java/li/cil/oc/common/init/AgentUpgrades.java)、[docs/PORTING.md](../docs/PORTING.md)

### D48 · 发电机升级 · 已闭合

完成条件：原燃料队列、容量/类型/零数量参数、容器部分回收和拒绝回退、燃烧/满缓冲计时、原始状态保存及物理移除行为完成。真实原生机器人16库存阶段/世界燃烧/热拔验证，另用真实driver环境的公开update和独立实际connector图证明发电能量，避开创造机器人自动满电干扰。

原版范围：`src/main/scala/li/cil/oc/server/component/UpgradeGenerator.scala`

当前接入：[src/main/java/li/cil/oc/server/component/UpgradeGenerator.java](../src/main/java/li/cil/oc/server/component/UpgradeGenerator.java)

闭合依据：[build/api-compat-addon/devices/generator-20261027-115447/device-results.txt](../build/api-compat-addon/devices/generator-20261027-115447/device-results.txt)、[tools/api-compat-addon/src/main/java/ocfixture/GeneratorContract.java](../tools/api-compat-addon/src/main/java/ocfixture/GeneratorContract.java)、[src/main/java/li/cil/oc/server/component/UpgradeGenerator.java](../src/main/java/li/cil/oc/server/component/UpgradeGenerator.java)、[docs/PORTING.md](../docs/PORTING.md)

### D49 · 物品栏控制器 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/server/component/UpgradeInventoryController.scala`

当前接入：[src/main/java/li/cil/oc/server/component/UpgradeInventoryController.java](../src/main/java/li/cil/oc/server/component/UpgradeInventoryController.java)

### D50 · 拴绳升级 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/server/component/UpgradeLeash.scala`

当前接入：[src/main/java/li/cil/oc/server/component/UpgradeLeash.java](../src/main/java/li/cil/oc/server/component/UpgradeLeash.java)

### D51 · 多功能升级 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/server/component/UpgradeMF.scala`

当前接入：[src/main/java/li/cil/oc/server/component/UpgradeMF.java](../src/main/java/li/cil/oc/server/component/UpgradeMF.java)

### D52 · 导航升级 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/server/component/UpgradeNavigation.scala`

当前接入：[src/main/java/li/cil/oc/server/component/UpgradeNavigation.java](../src/main/java/li/cil/oc/server/component/UpgradeNavigation.java)

### D53 · 普通及粘性活塞升级 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/server/component/UpgradePiston.scala`

当前接入：[src/main/java/li/cil/oc/server/component/UpgradePiston.java](../src/main/java/li/cil/oc/server/component/UpgradePiston.java)

### D54 · 告示牌升级 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/server/component/UpgradeSign.scala`、`src/main/scala/li/cil/oc/server/component/UpgradeSignInAdapter.scala`、`src/main/scala/li/cil/oc/server/component/UpgradeSignInRotatable.scala`

当前接入：[src/main/java/li/cil/oc/server/component/UpgradeSign.java](../src/main/java/li/cil/oc/server/component/UpgradeSign.java)

### D55 · 太阳能升级 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/server/component/UpgradeSolarGenerator.scala`

当前接入：[src/main/java/li/cil/oc/server/component/UpgradeSolarGenerator.java](../src/main/java/li/cil/oc/server/component/UpgradeSolarGenerator.java)

### D56 · 储罐升级 · 已闭合

完成条件：原16,000mB容量、产品/容量信息、模拟不变、执行正负事件（来源地址+一基索引+delta）、FluidStack组件与节点的注册表保存恢复闭合；真实原生Robot验证。跨宿主生命周期归C12，GUI/多人归C20/C23/C19。

原版范围：`src/main/scala/li/cil/oc/server/component/UpgradeTank.scala`

当前接入：[src/main/java/li/cil/oc/server/component/UpgradeTank.java](../src/main/java/li/cil/oc/server/component/UpgradeTank.java)

闭合依据：[build/api-compat-addon/devices/agent-fluid-20261027-112607/device-results.txt](../build/api-compat-addon/devices/agent-fluid-20261027-112607/device-results.txt)、[tools/api-compat-addon/src/main/java/ocfixture/AgentFluidContract.java](../tools/api-compat-addon/src/main/java/ocfixture/AgentFluidContract.java)、[src/main/java/li/cil/oc/server/component/UpgradeTank.java](../src/main/java/li/cil/oc/server/component/UpgradeTank.java)、[docs/PORTING.md](../docs/PORTING.md)

### D57 · 流体控制器 · 已闭合

完成条件：原公开十个控制器回调的接入、参数/缺省/显式nil及空罐语义完成；原生真实Robot调用外部源水查询、内部罐查询、容器查询和桶fill/drain，模拟/资源数量与无罐分支通过。标准IFluidHandlerItem负责可处理堆叠限制，实际两桶拒绝不丢物；不要求穷举外部mod自身容器实现。

原版范围：`src/main/scala/li/cil/oc/server/component/UpgradeTankController.scala`

当前接入：[src/main/java/li/cil/oc/server/component/UpgradeTankController.java](../src/main/java/li/cil/oc/server/component/UpgradeTankController.java)

闭合依据：[build/api-compat-addon/devices/agent-fluid-20261027-112607/device-results.txt](../build/api-compat-addon/devices/agent-fluid-20261027-112607/device-results.txt)、[tools/api-compat-addon/src/main/java/ocfixture/AgentFluidContract.java](../tools/api-compat-addon/src/main/java/ocfixture/AgentFluidContract.java)、[src/main/java/li/cil/oc/server/component/UpgradeTankController.java](../src/main/java/li/cil/oc/server/component/UpgradeTankController.java)、[docs/PORTING.md](../docs/PORTING.md)

### D58 · 牵引光束升级 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/server/component/UpgradeTractorBeam.scala`

当前接入：[src/main/java/li/cil/oc/server/component/UpgradeTractorBeam.java](../src/main/java/li/cil/oc/server/component/UpgradeTractorBeam.java)

### D59 · 交易升级 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/server/component/Trade.scala`、`src/main/scala/li/cil/oc/server/component/UpgradeTrading.scala`

当前接入：[src/main/java/li/cil/oc/server/component/UpgradeTrading.java](../src/main/java/li/cil/oc/server/component/UpgradeTrading.java)

### D60 · 航点 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/common/tileentity/Waypoint.scala`

当前接入：[src/main/java/li/cil/oc/common/block/entity/WaypointBlockEntity.java](../src/main/java/li/cil/oc/common/block/entity/WaypointBlockEntity.java)

### D61 · 无线网卡 · 已有实现，待核对/验收

完成条件：按原版该族公开操作、参数/错误、状态消耗、配置、保存及事件行为闭合；所有tier同族，辅助实现和共享trait一并负责。GUI/模型统一归C20/C23，远程包归C19，不重复扩充分母。

剩余：已有Java实现及注册映射；仍需完成本族原版行为核对和必要的实际操作验收。

原版范围：`src/main/scala/li/cil/oc/server/component/WirelessNetworkCard.scala`

当前接入：[src/main/java/li/cil/oc/server/component/WirelessNetworkCard.java](../src/main/java/li/cil/oc/server/component/WirelessNetworkCard.java)


## 工具与附加玩法

### T01 · 分析仪 · 已有实现，待核对/验收

完成条件：完成该族原版全部行为及注册/数据持久化；显示由C20/C23统一验收。

剩余：原RobotAnalyzeEvent已在真实持久宿主上发射，经验统计listener已接入；诊断、剪贴板、权限及玩家可见交互仍待闭合。

原版范围：`src/main/scala/li/cil/oc/common/item/Analyzer.scala`

当前接入：[src/main/java/li/cil/oc/common/item/AnalyzerItem.java](../src/main/java/li/cil/oc/common/item/AnalyzerItem.java)

### T02 · 扳手 · 已有实现，待核对/验收

完成条件：完成该族原版全部行为及注册/数据持久化；显示由C20/C23统一验收。

剩余：旋转、拆卸、工具损耗和保护事件的原操作路径待闭合。

原版范围：`src/main/scala/li/cil/oc/common/item/Wrench.scala`

当前接入：[src/main/java/li/cil/oc/common/item/WrenchItem.java](../src/main/java/li/cil/oc/common/item/WrenchItem.java)

### T03 · 游戏内手册 · 已有实现，待核对/验收

完成条件：完成该族原版全部行为及注册/数据持久化；显示由C20/C23统一验收。

剩余：物品打开/上下文页/历史与原Markdown特性、图片和链接闭合。

原版范围：`src/main/scala/li/cil/oc/common/item/Manual.scala`、`src/main/scala/li/cil/oc/client/Manual.scala`、`src/main/scala/li/cil/oc/client/renderer/markdown/`

当前接入：[src/main/java/li/cil/oc/common/item/ManualItem.java](../src/main/java/li/cil/oc/common/item/ManualItem.java)、[src/main/java/li/cil/oc/client/manual/ManualRegistry.java](../src/main/java/li/cil/oc/client/manual/ManualRegistry.java)

### T04 · 调试器物品 · 已有实现，待核对/验收

完成条件：完成该族原版全部行为及注册/数据持久化；显示由C20/C23统一验收。

剩余：原操作、访问权限及显示/诊断交互待闭合。

原版范围：`src/main/scala/li/cil/oc/common/item/Debugger.scala`

当前接入：[src/main/java/li/cil/oc/common/item/DebuggerItem.java](../src/main/java/li/cil/oc/common/item/DebuggerItem.java)

### T05 · 纹理选择器 · 已有实现，待核对/验收

完成条件：完成该族原版全部行为及注册/数据持久化；显示由C20/C23统一验收。

剩余：实际选择方块材质及复制/显示流程待闭合。

原版范围：`src/main/scala/li/cil/oc/common/item/TexturePicker.scala`

当前接入：[src/main/java/li/cil/oc/common/item/TexturePickerItem.java](../src/main/java/li/cil/oc/common/item/TexturePickerItem.java)

### T06 · 悬浮靴 · 已有实现，待核对/验收

完成条件：完成该族原版全部行为及注册/数据持久化；显示由C20/C23统一验收。

剩余：充电、悬浮/跌落、耐久和装备交互待闭合。

原版范围：`src/main/scala/li/cil/oc/common/item/HoverBoots.scala`

当前接入：[src/main/java/li/cil/oc/common/item/HoverBootsItem.java](../src/main/java/li/cil/oc/common/item/HoverBootsItem.java)

### T07 · 悬浮升级 · 已有实现，待核对/验收

完成条件：完成该族原版全部行为及注册/数据持久化；显示由C20/C23统一验收。

剩余：各等级高度和运动/能耗行为待闭合。

原版范围：`src/main/scala/li/cil/oc/common/item/UpgradeHover.scala`

当前接入：[src/main/java/li/cil/oc/common/item/FunctionalUpgradeItem.java](../src/main/java/li/cil/oc/common/item/FunctionalUpgradeItem.java)

### T08 · 物品栏升级 · 已有实现，待核对/验收

完成条件：完成该族原版全部行为及注册/数据持久化；显示由C20/C23统一验收。

剩余：升级容量、插槽增减与多升级组合的原规则待闭合。

原版范围：`src/main/scala/li/cil/oc/common/item/UpgradeInventory.scala`

当前接入：[src/main/java/li/cil/oc/server/agent/RobotController.java](../src/main/java/li/cil/oc/server/agent/RobotController.java)

### T09 · 扩展卡容器 · 已有实现，待核对/验收

完成条件：完成该族原版全部行为及注册/数据持久化；显示由C20/C23统一验收。

剩余：各等级合法插槽与卸载/热插拔流程待闭合。

原版范围：`src/main/scala/li/cil/oc/common/item/UpgradeContainerCard.scala`

当前接入：[src/main/java/li/cil/oc/common/init/Agents.java](../src/main/java/li/cil/oc/common/init/Agents.java)

### T10 · 扩展升级容器 · 已有实现，待核对/验收

完成条件：完成该族原版全部行为及注册/数据持久化；显示由C20/C23统一验收。

剩余：各等级合法升级与卸载/热插拔流程待闭合。

原版范围：`src/main/scala/li/cil/oc/common/item/UpgradeContainerUpgrade.scala`

当前接入：[src/main/java/li/cil/oc/common/init/Agents.java](../src/main/java/li/cil/oc/common/init/Agents.java)

### T11 · 组件总线 · 已有实现，待核对/验收

完成条件：完成该族原版全部行为及注册/数据持久化；显示由C20/C23统一验收。

剩余：各级组件容量与服务器实际限制待闭合。

原版范围：`src/main/scala/li/cil/oc/common/item/ComponentBus.scala`

当前接入：[src/main/java/li/cil/oc/common/item/ComponentBusItem.java](../src/main/java/li/cil/oc/common/item/ComponentBusItem.java)

### T12 · 无线终端及终端服务器 · 已有实现，待核对/验收

完成条件：完成该族原版全部行为及注册/数据持久化；显示由C20/C23统一验收。

剩余：绑定、距离、访问权、屏幕输入与断连/重连待闭合。

原版范围：`src/main/scala/li/cil/oc/common/item/Terminal.scala`、`src/main/scala/li/cil/oc/common/item/TerminalServer.scala`、`src/main/scala/li/cil/oc/common/item/Interweb.scala`、`src/main/scala/li/cil/oc/common/component/TerminalServer.scala`

当前接入：[src/main/java/li/cil/oc/common/item/TerminalItem.java](../src/main/java/li/cil/oc/common/item/TerminalItem.java)

### T13 · 纳米机器与行为提供器 · 已有实现，待核对/验收

完成条件：完成该族原版全部行为及注册/数据持久化；显示由C20/C23统一验收。

剩余：安装/卸载、网络命令、输入限制、效果/能耗/保存和公开Provider API待闭合。

原版范围：`src/main/scala/li/cil/oc/common/item/Nanomachines.scala`、`src/main/scala/li/cil/oc/common/item/Acid.scala`、`src/main/scala/li/cil/oc/common/nanomachines/`

当前接入：[src/main/java/li/cil/oc/common/nanomachines/](../src/main/java/li/cil/oc/common/nanomachines/)

### T14 · 季节礼物 · 已有实现，待核对/验收

完成条件：完成该族原版全部行为及注册/数据持久化；显示由C20/C23统一验收。

剩余：原日期、概率/奖励和使用行为待闭合。

原版范围：`src/main/scala/li/cil/oc/common/item/Present.scala`

当前接入：[src/main/java/li/cil/oc/common/item/PresentItem.java](../src/main/java/li/cil/oc/common/item/PresentItem.java)

### T15 · 人工末地石 · 已有实现，待核对/验收

完成条件：完成该族原版全部行为及注册/数据持久化；显示由C20/C23统一验收。

剩余：原生成/特殊行为、掉落与配方闭合。

原版范围：`src/main/scala/li/cil/oc/common/block/`

当前接入：[src/main/java/li/cil/oc/common/init/MiscDevices.java](../src/main/java/li/cil/oc/common/init/MiscDevices.java)

### T16 · 合成材料、机壳与墨盒耗材 · 已有实现，待核对/验收

完成条件：完成该族原版全部行为及注册/数据持久化；显示由C20/C23统一验收。

剩余：原独立材料/耗材全部注册且保留属性和使用行为；配方归C17、外观归C20。

原版范围：`src/main/scala/li/cil/oc/common/item/ALU.scala`、`src/main/scala/li/cil/oc/common/item/Acid.scala`、`src/main/scala/li/cil/oc/common/item/ArrowKeys.scala`、`src/main/scala/li/cil/oc/common/item/ButtonGroup.scala`、`src/main/scala/li/cil/oc/common/item/Chamelium.scala`、`src/main/scala/li/cil/oc/common/item/CircuitBoard.scala`、`src/main/scala/li/cil/oc/common/item/ControlUnit.scala`、`src/main/scala/li/cil/oc/common/item/CuttingWire.scala`、`src/main/scala/li/cil/oc/common/item/DiamondChip.scala`、`src/main/scala/li/cil/oc/common/item/DroneCase.scala`、`src/main/scala/li/cil/oc/common/item/InkCartridge.scala`、`src/main/scala/li/cil/oc/common/item/InkCartridgeEmpty.scala`、`src/main/scala/li/cil/oc/common/item/Microchip.scala`、`src/main/scala/li/cil/oc/common/item/MicrocontrollerCase.scala`、`src/main/scala/li/cil/oc/common/item/NumPad.scala`、`src/main/scala/li/cil/oc/common/item/PrintedCircuitBoard.scala`、`src/main/scala/li/cil/oc/common/item/RawCircuitBoard.scala`、`src/main/scala/li/cil/oc/common/item/TabletCase.scala`、`src/main/scala/li/cil/oc/common/item/Transistor.scala`

当前接入：[src/main/java/li/cil/oc/common/item/CraftingMaterialItem.java](../src/main/java/li/cil/oc/common/item/CraftingMaterialItem.java)


## 可选集成

### I01 · appeng · 已有实现，待核对/验收

完成条件：该一级目录原公开驱动/转换/工具/能量/数据功能按现代公开API接入；可选加载和原禁用配置有效，完成相应实际调用；全部子模块/driver同一ID。

剩余：模糊导出、运行中取消、Craftable/状态值冷恢复等原接口路径尚未闭合。

原版范围：`src/main/scala/li/cil/oc/integration/appeng/`

当前接入：[src/main/java/li/cil/oc/common/integration/appeng/](../src/main/java/li/cil/oc/common/integration/appeng/)

### I02 · charset · 有明确缺口

完成条件：该一级目录原公开驱动/转换/工具/能量/数据功能按现代公开API接入；可选加载和原禁用配置有效，完成相应实际调用；全部子模块/driver同一ID。

剩余：原版专属集成尚缺现代Java接入；现代官方目标/API待确认，仍计入剩余，未自行排除。

原版范围：`src/main/scala/li/cil/oc/integration/charset/`

### I03 · cofh · 有明确缺口

完成条件：该一级目录原公开驱动/转换/工具/能量/数据功能按现代公开API接入；可选加载和原禁用配置有效，完成相应实际调用；全部子模块/driver同一ID。

剩余：原版专属集成尚缺现代Java接入；现代官方目标/API待确认，仍计入剩余，未自行排除。

原版范围：`src/main/scala/li/cil/oc/integration/cofh/`

### I04 · computercraft · 已有实现，待核对/验收

完成条件：该一级目录原公开驱动/转换/工具/能量/数据功能按现代公开API接入；可选加载和原禁用配置有效，完成相应实际调用；全部子模块/driver同一ID。

剩余：显式启用/禁用、外设/介质已验；集束红石及余下原桥接路径待闭合。

原版范围：`src/main/scala/li/cil/oc/integration/computercraft/`

当前接入：[src/main/java/li/cil/oc/common/integration/computercraft/](../src/main/java/li/cil/oc/common/integration/computercraft/)

### I05 · ec · 有明确缺口

完成条件：该一级目录原公开驱动/转换/工具/能量/数据功能按现代公开API接入；可选加载和原禁用配置有效，完成相应实际调用；全部子模块/driver同一ID。

剩余：原版专属集成尚缺现代Java接入；现代官方目标/API待确认，仍计入剩余，未自行排除。

原版范围：`src/main/scala/li/cil/oc/integration/ec/`

### I06 · enderio · 已闭合

完成条件：该一级目录原公开驱动/转换/工具/能量/数据功能按现代公开API接入；可选加载和原禁用配置有效，完成相应实际调用；全部子模块/driver同一ID。

原版范围：`src/main/scala/li/cil/oc/integration/enderio/`

当前接入：[src/main/java/li/cil/oc/common/integration/enderio/](../src/main/java/li/cil/oc/common/integration/enderio/)

闭合依据：[docs/enderio-integration.md](../docs/enderio-integration.md)、[tools/api-compat-addon/src/main/java/ocfixture/EnderIOContract.java](../tools/api-compat-addon/src/main/java/ocfixture/EnderIOContract.java)、[build/api-compat-addon/devices/enderio-20261027-1234/device-results.txt](../build/api-compat-addon/devices/enderio-20261027-1234/device-results.txt)、[build/api-compat-addon/devices/enderio-disabled-20261027-1238/device-results.txt](../build/api-compat-addon/devices/enderio-disabled-20261027-1238/device-results.txt)

### I07 · enderstorage · 已闭合

完成条件：五个原回调、16色频率/边界、所有者保留、真实物品/流体及禁用/通用驱动组合通过；外部模组自身持久化不新增本项验收。

原版范围：`src/main/scala/li/cil/oc/integration/enderstorage/`

当前接入：[src/main/java/li/cil/oc/common/integration/enderstorage/](../src/main/java/li/cil/oc/common/integration/enderstorage/)

闭合依据：[docs/enderstorage-integration.md](../docs/enderstorage-integration.md)

### I08 · forestry · 有明确缺口

完成条件：该一级目录原公开驱动/转换/工具/能量/数据功能按现代公开API接入；可选加载和原禁用配置有效，完成相应实际调用；全部子模块/driver同一ID。

剩余：原版专属集成尚缺现代Java接入；现代官方目标/API待确认，仍计入剩余，未自行排除。

原版范围：`src/main/scala/li/cil/oc/integration/forestry/`

### I09 · ic2 · 有明确缺口

完成条件：该一级目录原公开驱动/转换/工具/能量/数据功能按现代公开API接入；可选加载和原禁用配置有效，完成相应实际调用；全部子模块/driver同一ID。

剩余：原版专属集成尚缺现代Java接入；现代官方目标/API待确认，仍计入剩余，未自行排除。

原版范围：`src/main/scala/li/cil/oc/integration/ic2/`

### I10 · jei · 已有实现，待核对/验收

完成条件：该一级目录原公开驱动/转换/工具/能量/数据功能按现代公开API接入；可选加载和原禁用配置有效，完成相应实际调用；全部子模块/driver同一ID。

剩余：类别/文档导航、插槽高亮和世界磁盘刷新待客户端验收。

原版范围：`src/main/scala/li/cil/oc/integration/jei/`

当前接入：[src/main/java/li/cil/oc/client/integration/jei/](../src/main/java/li/cil/oc/client/integration/jei/)

### I11 · mekanism · 已闭合

完成条件：Configurator公开工具模式与真实交互、ChemicalStack转换/64位边界、ID开关及原黑名单门控通过。

原版范围：`src/main/scala/li/cil/oc/integration/mekanism/`

当前接入：[src/main/java/li/cil/oc/common/integration/mekanism/](../src/main/java/li/cil/oc/common/integration/mekanism/)

闭合依据：[docs/mekanism-integration.md](../docs/mekanism-integration.md)

### I12 · minecraft · 已有实现，待核对/验收

完成条件：该一级目录原公开驱动/转换/工具/能量/数据功能按现代公开API接入；可选加载和原禁用配置有效，完成相应实际调用；全部子模块/driver同一ID。

剩余：已补World/WorldProvider与现代储罐转换，保留原版三个维度UUID/名称并修复minecraft黑名单在转换、地质事件和普通红石输入的门控；启用/禁用真实场景已过。其他原版驱动、配方平台及自定义维度/客户端级别的整项验收仍待闭合。

原版范围：`src/main/scala/li/cil/oc/integration/minecraft/`

当前接入：[src/main/java/li/cil/oc/common/integration/VanillaDrivers.java](../src/main/java/li/cil/oc/common/integration/VanillaDrivers.java)、[src/main/java/li/cil/oc/server/driver/converter/World.java](../src/main/java/li/cil/oc/server/driver/converter/World.java)、[src/main/java/li/cil/oc/server/driver/converter/WorldProvider.java](../src/main/java/li/cil/oc/server/driver/converter/WorldProvider.java)、[src/main/java/li/cil/oc/server/driver/converter/FluidTank.java](../src/main/java/li/cil/oc/server/driver/converter/FluidTank.java)

闭合依据：[build/api-compat-addon/devices/vanilla-20261027-115447/device-results.txt](../build/api-compat-addon/devices/vanilla-20261027-115447/device-results.txt)、[build/api-compat-addon/devices/vanilla-disabled-20261027-115447/device-results.txt](../build/api-compat-addon/devices/vanilla-disabled-20261027-115447/device-results.txt)、[tools/api-compat-addon/src/main/java/ocfixture/VanillaContract.java](../tools/api-compat-addon/src/main/java/ocfixture/VanillaContract.java)

### I13 · minecraftforge · 已闭合

完成条件：原MinecraftForge三个源文件职责完成：通用标准FE物品充电、OC方块侧面/无侧FE能力、四个只读能源驱动回调。真实Charger与FE物品能量守恒、simulate不写、余量/取整/Int.MAX边界、能力拒绝与两组黑名单通过；minecraft驱动独立禁用仍可FE接入。

原版范围：`src/main/scala/li/cil/oc/integration/minecraftforge/`

当前接入：[src/main/java/li/cil/oc/common/capabilities/](../src/main/java/li/cil/oc/common/capabilities/)

闭合依据：[build/api-compat-addon/devices/forge-energy-20261027-110738/device-results.txt](../build/api-compat-addon/devices/forge-energy-20261027-110738/device-results.txt)、[build/api-compat-addon/devices/forge-energy-power-disabled-20261027-110738/device-results.txt](../build/api-compat-addon/devices/forge-energy-power-disabled-20261027-110738/device-results.txt)、[build/api-compat-addon/devices/forge-energy-integration-disabled-20261027-110738/device-results.txt](../build/api-compat-addon/devices/forge-energy-integration-disabled-20261027-110738/device-results.txt)、[build/api-compat-addon/devices/forge-energy-minecraft-disabled-20261027-110738/device-results.txt](../build/api-compat-addon/devices/forge-energy-minecraft-disabled-20261027-110738/device-results.txt)、[tools/api-compat-addon/src/main/java/ocfixture/ForgeEnergyContract.java](../tools/api-compat-addon/src/main/java/ocfixture/ForgeEnergyContract.java)、[src/main/java/li/cil/oc/common/integration/forge/ForgeEnergyItemCharge.java](../src/main/java/li/cil/oc/common/integration/forge/ForgeEnergyItemCharge.java)、[docs/PORTING.md](../docs/PORTING.md)

### I14 · projectred · 已闭合

完成条件：公开桥接16通道输入/输出、无符号边界/旋转/停机/禁用与真实工具损耗通过；外部线缆拓扑本身不新增OC需求。

原版范围：`src/main/scala/li/cil/oc/integration/projectred/`

当前接入：[src/main/java/li/cil/oc/common/integration/projectred/](../src/main/java/li/cil/oc/common/integration/projectred/)

闭合依据：[docs/projectred-integration.md](../docs/projectred-integration.md)

### I15 · railcraft · 有明确缺口

完成条件：该一级目录原公开驱动/转换/工具/能量/数据功能按现代公开API接入；可选加载和原禁用配置有效，完成相应实际调用；全部子模块/driver同一ID。

剩余：原版专属集成尚缺现代Java接入；现代官方目标/API待确认，仍计入剩余，未自行排除。

原版范围：`src/main/scala/li/cil/oc/integration/railcraft/`

### I16 · thaumcraft · 有明确缺口

完成条件：该一级目录原公开驱动/转换/工具/能量/数据功能按现代公开API接入；可选加载和原禁用配置有效，完成相应实际调用；全部子模块/driver同一ID。

剩余：原版专属集成尚缺现代Java接入；现代官方目标/API待确认，仍计入剩余，未自行排除。

原版范围：`src/main/scala/li/cil/oc/integration/thaumcraft/`

### I17 · tis3d · 已有实现，待核对/验收

完成条件：该一级目录原公开驱动/转换/工具/能量/数据功能按现代公开API接入；可选加载和原禁用配置有效，完成相应实际调用；全部子模块/driver同一ID。

剩余：串行接口注册、16位收发队列、读取开关、重置、持久化和手册桥已用Java接入；官方TIS-3D 2.0.0要求NeoForge至少21.1.248，待在兼容版本完成实际串行收发及手册验证。

原版范围：`src/main/scala/li/cil/oc/integration/tis3d/`

当前接入：[src/main/java/li/cil/oc/common/integration/tis3d/](../src/main/java/li/cil/oc/common/integration/tis3d/)

闭合依据：[docs/tis3d-integration.md](../docs/tis3d-integration.md)

### I18 · waila → Jade · 已有实现，待核对/验收

完成条件：该一级目录原公开驱动/转换/工具/能量/数据功能按现代公开API接入；可选加载和原禁用配置有效，完成相应实际调用；全部子模块/driver同一ID。

剩余：原Waila→Jade；服务器真实数据已验，繁忙装配机和客户端三开关待闭合。

原版范围：`src/main/scala/li/cil/oc/integration/waila/`

当前接入：[src/main/java/li/cil/oc/client/integration/jade/](../src/main/java/li/cil/oc/client/integration/jade/)

### I19 · wrcbe · 有明确缺口

完成条件：该一级目录原公开驱动/转换/工具/能量/数据功能按现代公开API接入；可选加载和原禁用配置有效，完成相应实际调用；全部子模块/driver同一ID。

剩余：原版专属集成尚缺现代Java接入；现代官方目标/API待确认，仍计入剩余，未自行排除。

原版范围：`src/main/scala/li/cil/oc/integration/wrcbe/`
