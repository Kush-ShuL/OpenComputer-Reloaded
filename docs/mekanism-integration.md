# Mekanism 可选集成

本实现的 OpenComputers 语义依据仅为 MightyPirates/OpenComputers
`258943363ea8fc4ef14948e6bc6f67375061580c` 中的四个文件：

- `integration/mekanism/EventHandlerMekanism.scala`
- `integration/mekanism/ModMekanism.scala`
- `integration/mekanism/gas/ConverterGasStack.scala`
- `integration/mekanism/gas/ModMekanismGas.scala`

原作者与 MIT 许可保留在项目 `LICENSE` 和 `NOTICE`。本次是 Java 21 / Minecraft
1.21.1 NeoForge 迁移，不包含 Scala，不打包 Mekanism API 或实现，也不参考其他 OC 移植。

## 启动接线

在 OpenComputers 的 common setup 排队任务内、`API.driver` 已初始化且 Driver 注册仍开放时调用一次：

```java
li.cil.oc.common.integration.mekanism.MekanismIntegration.initializeDrivers();
```

入口先检查 `ModList` 中的 `mekanism`，再分别检查 `integration.modBlacklist`。
未安装 Mekanism 时不会解析任何 Mekanism 类。两个原模块保留独立开关：

| 黑名单项 | 关闭功能 |
| --- | --- |
| `mekanism` | Mekanism 扳手检查及使用回调 |
| `MekanismAPI\|gas` | ChemicalStack 转换器，保留原气体模块 ID |
| `mekanismgas` | 同一转换器的现代便捷别名 |

同时禁用扳手和转换器需加入 `mekanism` 与 `MekanismAPI|gas`。禁用转换器时不会解析
`ChemicalStack` 或 `MekanismAPI`。入口不增加新的配置项，也不要求新的编译依赖。

## 扳手

原实现通过 IMC 注册 `isWrench` 与 `useWrench`。迁移直接将这两个回调接入现有
`WrenchTools.addCheck/addUsage`，与外部 IMC 注册的回调共享同一真实使用链。

现代 Mekanism 已删除 `IMekWrench`，其公开替代协议为 NeoForge 的命名 `ItemAbility`。
按照 [MekanismItemAbilities 官方声明](https://github.com/mekanism/Mekanism/blob/1.21.x/src/api/java/mekanism/api/MekanismItemAbilities.java)，
集成自行获取 `ItemAbility.get("wrench_dismantle")`，无需加载 Mekanism 类。
[官方 Configurator 实现](https://github.com/mekanism/Mekanism/blob/1.21.x/src/main/java/mekanism/common/item/ItemConfigurator.java)
仅在 WRENCH 模式提供该能力。ROTATE、EMPTY 与各种配置模式不通过此检查。

原 `isWrench` 的接口类型判定无法在已删除的接口上继续执行；现代判定以当前物品栈是否声明
`wrench_dismantle` 为准，因此会随 Configurator 模式变化。`useWrench` 保留主手限定以及
忽略 `changeDurability` 的原行为，不消耗耐久或能量。OC 自带扳手和其他 IMC 回调仍沿用
现有 `WrenchTools` 路径。

## ChemicalStack 转换

现代 Mekanism 将旧 GasStack 合并为统一 ChemicalStack，所以迁移转换所有该类型实例。
转换器只用反射绑定已核对的公开 API，未访问私有字段或 Mekanism 内部实现：

- [ChemicalStack](https://github.com/mekanism/Mekanism/blob/1.21.x/src/api/java/mekanism/api/chemical/ChemicalStack.java)
  的 `getChemical()`、`getAmount(): long`、`getTextComponent()`。
- [MekanismAPI](https://github.com/mekanism/Mekanism/blob/1.21.x/src/api/java/mekanism/api/MekanismAPI.java)
  的公开 `CHEMICAL_REGISTRY`，使用 Minecraft `Registry.getKey/getId`。

输出保留原键名：

| 键 | 现代值 |
| --- | --- |
| `amount` | 化学品数量，Java `Long`，不截断成原来的 32 位整数 |
| `name` | 注册表中的完整资源名，例如 `mekanism:hydrogen` |
| `label` | ChemicalStack 的显示组件转换成的字符串 |
| `id` | 仅当 `debug.insertIdsInConverters=true`，输出当前注册表的数字 ID |

数字 ID 随注册表变化，仅用于原有调试用途。空 ChemicalStack 按官方 API 返回零数量及
`mekanism:empty` 的名字和显示名。Java 转换边界保留 64 位整数；最终 Lua 数字精度仍由
所选架构决定，只有浮点数字的 Lua 架构无法精确表示所有大于 2^53 的整数。

官方 API 不匹配时，启动日志报告具体绑定错误，其他模块继续初始化。普通对象不受转换器影响。

## 真实 Mekanism 验证步骤

以下是后续实际验收流程，本次源码实现不表示已经在真实 Mekanism 客户端验收：

1. 在隔离的 Minecraft 1.21.1 / NeoForge 环境加入真实 Mekanism 10.7.x；例如 10.7.19
   需要 NeoForge 至少 21.1.194。启动应无 chemical API 绑定错误。
2. 持 Configurator 切到 WRENCH 模式，对 OC Adapter 或 Net Splitter 使用扳手，确认对应
   侧面的连接状态确实改变；切到 ROTATE、EMPTY 和配置模式，确认不触发该 OC 扳手操作。
   客户端屏幕扳手提示也应与模式同步。
3. 在一个真实 Java 回调中返回 Mekanism 注册表中的氢气 ChemicalStack，让 OC Driver
   转换链处理它；确认 Lua 可读到 `amount/name/label`。使用大于 `Integer.MAX_VALUE`
   的数量确认 Java 转换结果没有 32 位溢出，并依据 Lua 架构评估更大的整数。
4. 启用 `debug.insertIdsInConverters` 后比较 `id` 与真实化学品注册表 ID；关闭后不应有
   `id`。空 ChemicalStack 应为零数量和官方空化学品身份。
5. 将两个原 ID 同时加入黑名单后重启，确认上述集成不注册；分别禁用它们可以独立关闭
   扳手与转换器。移除 Mekanism 后启动，确认 OC 本体可以运行。

这里没有新增化学品库存或化学品容器驱动：原 pinned 模块也只提供返回值转换器。
