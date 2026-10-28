# Railcraft 集成

安装 Railcraft Reborn 后，可以使用撬棍操作支持扳手的 OpenComputers 方块。适配器连接 World Spike 后，会提供原版名称 `worldspike` 的组件。将 `railcraft` 加入 `integration.modBlacklist` 可关闭这项集成。

行为依据 MightyPirates/OpenComputers 提交 `258943363ea8fc4ef14948e6bc6f67375061580c` 的 `ModRailcraft`、`EventHandlerRailcraft` 和 `DriverWorldspike`。撬棍检测调用官方 `Crowbar.canWhack`，实际使用调用 `Crowbar.onWhack`；后者按现代 API 使用真正的 `ServerPlayer`，由工具本身处理耐久消耗。组件保留原版五个回调和 `NamedBlock` 优先级 5：

| 回调 | Railcraft Reborn 1.2.15 的行为 |
| --- | --- |
| `getFuel()` | 返回 0：现代 World Spike 无燃料储存或消耗。 |
| `getOwner()` | 读取实体的真实所有者；没有姓名或姓名为 `[unknown]` 时无返回值。 |
| `getType()` | 普通、个人 World Spike 分别返回 `world`、`personal`；无法识别的类型返回 `missing`。 |
| `getFuelSlotContents()` | 无返回值：现代实体没有燃料槽，采用原版不需要燃料时的分支。 |
| `isDisabled()` | 返回 false：现代实体放置后直接维持区块票据，红石输入不禁用它。 |

现代官方版本只有普通和个人 World Spike，原版 `admin`、`passive` 没有对应方块。这项集成保留五个查询接口，并反映现代设备实际行为；没有补造燃料槽、燃料数量或红石禁用装置。

目标为官方 [Railcraft Reborn 1.2.15](https://modrinth.com/mod/railcraft-reborn/version/pFiPGIUN)，Minecraft 1.21.1。官方版本声明 NeoForge 最低 21.1.50；开发环境使用 21.1.228，并不单独构成运行时最低要求。构建仅引用未修改的官方 Jar，运行时由玩家另行安装 Railcraft Reborn。

官方 API 和现代差异依据：[Crowbar](https://github.com/railcraft-reborn/railcraft/blob/1.2.15/src/api/java/mods/railcraft/api/item/Crowbar.java)、[Ownable](https://github.com/railcraft-reborn/railcraft/blob/1.2.15/src/api/java/mods/railcraft/api/core/Ownable.java)、[WorldSpikeBlock](https://github.com/railcraft-reborn/railcraft/blob/1.2.15/src/main/java/mods/railcraft/world/level/block/worldspike/WorldSpikeBlock.java)、[WorldSpikeBlockEntity](https://github.com/railcraft-reborn/railcraft/blob/1.2.15/src/main/java/mods/railcraft/world/level/block/entity/worldspike/WorldSpikeBlockEntity.java)。本桥移植 OC 的 MIT 代码并调用官方类型和方法，保留项目许可证与署名；外部 API 按其 MIT 许可使用，Railcraft 主模组保留其[官方独立许可证](https://github.com/railcraft-reborn/railcraft/blob/1.2.15/LICENSE.md)，没有将其实现或资源打入 OC。

Windows 专用服务端实际报告：`build/api-compat-addon/devices/railcraft-20261027-j1/device-results.txt` 和 `build/api-compat-addon/devices/railcraft-disabled-20261027-j1/device-results.txt`，两次整体状态均为 **PASS**，各用 6 个实际世界 tick 完成。

- 启用时，真实 Case/Adapter 连接官方普通、个人 World Spike，执行全部五个原回调；个人方块通过真正的 BlockItem 放置，所有者查询与官方实体资料一致，普通无主方块返回空所有者。实际实体没有库存或物品能力，燃料及燃料槽的现代映射通过。
- 官方铁撬棍通过已注册的检测和使用接口；非消耗检测保持原物品，消耗使用恰好损失一次耐久；真实 NetSplitter 右键也调用同一工具接口，普通石头被拒绝。
- 两个真实 World Spike 分别维持各自九个区块的官方控制器票据；真实红石电源加入、移除后票据保持，`isDisabled()` 均为 false。拆除普通 World Spike 后只撤销它的票据和适配器组件，个人方块仍保留自己的票据。
- 黑名单运行仍安装同一官方 Railcraft Jar；官方 World Spike 自身票据继续工作，但 OC 驱动、组件和撬棍回调均被停用。

I15 的原三文件和五回调范围已据此闭合。此批未验证客户端外观或远程多人；原 `admin/passive` 无现代对应类型的差异保留在上表，不虚构测试对象。
