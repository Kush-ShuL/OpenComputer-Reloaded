# Forestry 集成

在适配器旁放置 Forestry 分析机、蜂房或养蜂箱，即可通过 `forestry_analyzer` 和 `bee_housing` 组件读取工作状态、蜂种与育种信息。`integration.modBlacklist` 中加入 `forestry` 会关闭本集成。

分析机提供 `isWorking`、`getProgress` 和 `getIndividualOnDisplay`。进度保留原版的 `1 - getProgressScaled(100) / 100` 结果。蜂房提供 `canBreed`、`getDrone`、`getQueen`、`getBeeBreedingData`、`listAllSpecies` 和 `getBeeParents`；其中 `listAllSpecies` 沿用原版，仅列出突变结果中的种类。

个体信息保留原有 `displayName`、`ident`、`isAnalyzed`、`isSecret`，以及适用的生命、蜜蜂品质和类型字段。只有已分析个体才显示 `active`、`inactive` 遗传信息。蜜蜂、树木及可选蝴蝶内容的原字段均保留；物品信息还会附带真实 `individual` 或电路板的 `circuits` 列表。

新版物种标识使用 Forestry 的资源 ID。蜜蜂 `canSpawn` 对应实际存在的配偶基因，`isNatural` 对应 pristine 品质，`hasEffect` 对应原版的物品闪光属性。新版活动方式不再是一个布尔基因，原 `neverSleeps` 字段在活动类型为全天活动 `cathemeral` 时为 true。数字、布尔值和三维领地范围保持原 Lua 数据形状，其他基因名称通过 Forestry 自身的公开翻译接口读取。

实现行为仅依据 MightyPirates/OpenComputers 提交 `258943363ea8fc4ef14948e6bc6f67375061580c` 的 `integration/forestry/`。保留 MIT 许可及原 `ConverterIIndividual` 对 OpenPeripheral 的署名；Forestry 实现和资源不打包进本模组。

当前接入目标为官方 [Forestry: CE 1.21.1-3.0.0-alpha8](https://www.curseforge.com/minecraft/mc-mods/forestry-community-edition/files/8782913)，编译依赖坐标为 `curse.maven:forestry-community-edition-1056828:8782913`。实际发布 JAR 声明 NeoForge `[21.1,)`、必需 Patchouli `[1.21.1-92,)`；其源码开发环境使用 NeoForge 21.1.230，并不等于发布元数据声明最低为该版本。

运行依赖为 `vazkii.patchouli:Patchouli:1.21.1-92-NEOFORGE`，来自 [Patchouli 官方 Maven](https://maven.blamejared.com/vazkii/patchouli/Patchouli/1.21.1-92-NEOFORGE/)。蝴蝶 API 位于 Forestry 核心，蝴蝶内容模块可选，未安装时不要求其内容类。

初始化入口为 `ForestryIntegration.initializeDrivers()`，在 OC 公开驱动 API 初始化后调用。入口先检查模组与黑名单，再加载含 Forestry 类型的实现。模组元数据将 Forestry 声明为可选依赖；上游发布包保持原样，实际加载已通过下述验收。

Windows 实测记录：`build/api-compat-addon/devices/completion-20261027-h1/device-results.txt`，其中 `forestry.native` 为 PASS。

- 官方 Forestry 核心、Patchouli 92 与可选蝴蝶包实际加载成功。
- 真实 Case 的原生 Lua 5.3 经真实适配器调用分析机、养蜂箱的全部九个回调。
- 三类转换器通过真实个体、物品、电路板及突变数据验收，涵盖蜜蜂、树木、蝴蝶，未分析信息隐藏、显隐性异合基因、数值和领地形状、配偶及自然品质、物种与育种信息。

整批报告总状态为 FAIL，唯一失败是其他组 `recipe.vanilla` 的 phase 8 超时；Forestry 场景已通过。本次未等待完整繁殖或分析生产周期，也未重复共用库存、存档和客户端验收。
