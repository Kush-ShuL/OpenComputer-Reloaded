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

## 已验证范围

Windows Java 21、NeoForge 21.1.224 与官方完整 Mekanism 10.7.19.85 已通过三个
独立隔离世界场景：默认启用且调试 ID 关闭、启用且调试 ID 开启、两个原模块 ID 同时
列入黑名单。测试通过真实 Configurator 八种模式和 `BlockState.useItemOn` 验证
NetSplitter 的六面状态及蹲下操作的反面选择。

真实机箱的原生 Lua 5.3 回调已验证 ChemicalStack 的数量、注册名、显示名、空栈、
调试 ID，以及 `2147483665`、`9007199254740993`、`9223372036854775807` 三个
整数边界；期望使用独立的 Lua 整数文字和十进制字符串。普通对象与普通表保持原有
转换行为。两个模块同时被禁用时，相同玩家交互与回调场景证明集成不再生效。

这些结果覆盖服务器行为和原生转换链。客户端提示、远程多人连接及两个黑名单项分别
单独关闭的场景仍未验收。

这里没有新增化学品库存或化学品容器驱动：原 pinned 模块也只提供返回值转换器。
