# 配置文件

OpenComputer-Reloaded 使用原版 OpenComputers 的 HOCON 配置结构。游戏或服务器第一次启动后，在实例目录生成：

```text
config/opencomputers/settings.conf
```

文件使用 UTF-8。修改前关闭游戏或服务器，保存后重新启动。客户端的显示设置在客户端实例中修改；服务器的机器、网络、能耗和权限设置在服务器实例中修改。

`opencomputers.version` 记录生成配置的模组版本；`opencomputers.configSchemaVersion` 记录移植版配置格式。后者用于迁移识别，请勿手工更改。原有配置路径继续保留。

## 显式开启 CC:Tweaked 兼容

兼容层默认关闭。安装 CC:Tweaked 后，在配置中明确设置：

```hocon
opencomputers {
  integration {
    computerCraft {
      enabled: true
    }
  }
}
```

同时确认 `integration.modBlacklist` 中没有 `computercraft` 或旧名 `ComputerCraft`，然后重启。黑名单优先于启用开关。

缺少开关或设置为 `false` 时，不注册 OC 中继器外设、Adapter 的 CC 外设驱动、CC 磁盘驱动、Lua 对象转换和集束红石桥接。开发参数 `-PocWithComputerCraft=true` 仅添加 CC 测试模组，不能代替配置中的启用开关。兼容范围见 [CC 兼容说明](computercraft-integration.md)。

## 配置分区

以下路径位于 `opencomputers` 下。具体默认值和列表格式保留在配置文件的各项注释中。

| 分区 | 内容 |
| --- | --- |
| `client` | 屏幕文字、渲染距离、字体、声音和客户端显示 |
| `computer` | CPU 组件数量、调用预算、Lua 内存、超时和用户限制 |
| `robot` | 机器人动作、飞行高度、经验和升级参数 |
| `power` | 启用能耗、缓冲容量、动作消耗和外部能源换算 |
| `filesystem` | 硬盘/软盘/tmpfs 容量、句柄、缓冲和寻道 |
| `internet` | HTTP、TCP、请求头、线程和地址过滤规则 |
| `switch` | 中继器队列、转发间隔、吞吐和硬件增益 |
| `nanomachines` | 输入输出限制、效果白名单、范围和消耗 |
| `printer` | 打印材料、墨水、模型数量、亮度和碰撞参数 |
| `hologram` | 投影缩放、平移和更新参数 |
| `misc` | 屏幕大小、网络包、端口、无线范围、手册和其他设备参数 |
| `integration` | 可选集成、外设黑名单、假玩家和原版驱动 |
| `debug` | LuaJ、调试卡访问、持久化诊断和本地库选择 |
| `chunkloader` | 区块加载维度白名单/黑名单 |
| `gpu` | 显存和 GPU 操作参数 |

## 常用设置

| 路径 | 用途 |
| --- | --- |
| `integration.computerCraft.enabled` | 显式启用 CC 兼容，默认 `false` |
| `integration.modBlacklist` | 禁用指定可选集成；修改后重启 |
| `integration.vanilla.enableInventoryDriver` | 允许 Adapter 直接操作相邻物品栏，默认 `false` |
| `integration.vanilla.enableTankDriver` | 允许 Adapter 直接操作相邻流体容器，默认 `false` |
| `integration.vanilla.enableCommandBlockDriver` | 允许命令方块驱动，默认 `false`，还受服务器命令方块开关限制 |
| `misc.giveManualToNewPlayers` | 新玩家首次加入时发放手册 |
| `misc.maxClipboard` | 屏幕剪贴板输入长度上限 |
| `power.ignorePower` | 忽略设备能耗 |
| `power.value.ForgeEnergy` | NeoForge 能量与 OC 内部能量的换算值 |
| `power.modBlacklist` | `ForgeEnergy` / `NeoForgeEnergy` 禁用 OC 的 FE 方块能力及通用 FE 物品充电，内部 OC 充能仍可用 |
| `misc.serverRackSwitchTier` | 机架内置交换机的等级，影响队列、转发间隔和吞吐 |
| `misc.threadPriority` | 机器和后台工作线程优先级；默认值使用原线程工厂规则 |
| `misc.maxNetworkClientPacketDistance` | 普通同步包的额外距离上限；`0` 使用服务器视距范围 |
| `misc.maxNetworkClientSoundPacketDistance` | 声音包范围；`0` 表示零范围 |
| `internet.enableHttp` / `internet.enableTcp` | 分别控制 HTTP / TCP |
| `internet.filteringRules` | 按顺序匹配的网络地址过滤规则 |
| `nanomachines.potionWhitelist` | 可用效果资源 ID，接受 `minecraft:speed` 等名称 |
| `debug.forceLuaJ` | 强制使用 LuaJ；LuaJ 按原版规则在读档时重新启动 |
| `debug.debugCardAccess` | 调试卡的 `allow` / `deny` / `whitelist` 访问策略 |

ProjectRed 束线与螺丝刀集成在安装 Core 和 Transmission 后启用；黑名单可使用
`projectred-transmission`、`projectred_transmission` 或 `projectred`。
Jade 的机器信息在双方安装 Jade 后启用，黑名单中的 `jade` 或旧名 `waila` 均可禁用。
Jade 设置界面还可分别控制 OC 地址、能量和组件名的显示。

充电器可给带标准 NeoForge Energy 能力的物品充电，换算使用 `power.value.ForgeEnergy`。
`integration.modBlacklist` 中的 `forge` 或 `neoforge` 也可禁用这组接入；单独禁用
`minecraft` 的原版驱动不会关闭 FE 接入。

## 修改配方

首次启动会在 `config/opencomputers/` 生成 `user.recipes` 和四套原版配方档案。
直接编辑 `user.recipes`；四个内置档案会在启动时恢复，适合作为参考。
默认包含 `default.recipes`，也可以按顺序包含 `hardmode.recipes`、`peaceful.recipes`
或自己的文件，后面的定义覆盖前面的定义。

例如，将分析仪改为使用一颗钻石制作，并禁用晶体管配方：

```hocon
include file("default.recipes")

analyzer {
  type: shapeless
  input: ["minecraft:diamond"]
}
transistor = false
```

`false` 同时隐藏创造物品栏和 JEI 中的该物品；`true` 只禁用配方，保留物品展示。
支持 `shaped`（二维材料列表）、`shapeless` 和 `furnace`，以及原档案中的
`lootdisks`、`generic` 条目。材料可用现代物品 ID、`#命名空间:标签`，或保留的
原版 OC 材料别名，例如 `oc:materialTransistor`。旧模组的数字 ID / metadata
请改为对应的现代物品 ID。

服务器执行 `/reload` 后重新读取配方文件；修改物品的隐藏状态后重启客户端。
数据包仍可通过同名配方覆盖这些定义，例如
`data/opencomputers/recipe/default/analyzer.json`。
GregTech 档案作为原始资源保留。固定上游源码实际只注册 `shaped`、`shapeless`、
`furnace` 三种处理器，没有实现档案注释中的 `gt_*` 机器处理器；切换档案不会
使这些机器配方生效。流体材料解析函数也没有原版调用方，不能据此视为已支持流体机器配方。

HOCON 合成配方沿用原来的数据处理：处理器写入默认架构，导航升级保存实际地图，
配对卡在服务端建立通道，重制设备保留硬件并更换 EEPROM，软盘格式化或复制按原规则
处理数据。BIOS 和程序盘保留配方中配置的产出数量。`minecraft` 集成黑名单也会阻止
注册上述三种 HOCON 处理器；独立重合成配方和数据包自身的定义仍各自处理。

配方档案、自定义覆盖和数据包重载已在 Windows 服务端验证，验证入口为
`tools/api-compat-addon/src/main/java/ocfixture/RecipeConfigContract.java`。

## 从旧配置迁移

迁移会补齐缺少的默认项，保留未知键和原有自定义值。原版历史版本中语义发生变化的项目按原迁移规则处理；已有的 `0.1.x` 移植版配置不按早期 OC 版本重置。

旧网络地址白名单/黑名单、原版数字效果 ID 和已变更的原版类名有专门迁移逻辑。旧模组的数字 ID 无法可靠推断，请改用现代资源 ID。旧方块 metadata 表达式也应改成对应的现代物品 ID，例如 `minecraft:mossy_stone_bricks`。

类型错误或语法错误会指出配置路径，并保留原文件。数组长度和数值范围按原配置规则检查。`API.config` 继续暴露用户配置；运行时使用经过校验的派生值。

部分原版可选模组尚无本移植版适配。保留相关配置项不代表对应集成已生效；当前进展见 [移植说明](PORTING.md)。

## 开发验证

`gradlew.bat verifyConfiguration` 会在 `build/` 下创建独立配置文件，验证默认开关、已有配置连续加载、旧格式迁移、错误文件保留及数组回退。工具不写入游戏实例配置，也不打包进模组。成功时输出 `CONFIGURATION_VERIFY status=PASS groups=3`。
