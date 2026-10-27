# EnderIO 可选扳手集成

本模块仅迁移 MightyPirates/OpenComputers 固定提交
[`258943363ea8fc4ef14948e6bc6f67375061580c`](https://github.com/MightyPirates/OpenComputers/tree/258943363ea8fc4ef14948e6bc6f67375061580c/src/main/scala/li/cil/oc/integration/enderio)
的两个文件：`ModEnderIO.scala` 注册工具检测与使用回调，`EventHandlerEnderIO.scala`
从主手识别旧 `ITool`，检查时调用 `canUse`、实际使用时调用 `used`。
原 OC 的 MIT 许可证与归属继续由 `LICENSE`、`NOTICE` 保留；不采用其他 OC 移植源码。

## 官方现代目标

目标是官方 [Ender IO 8.2.12-beta / Minecraft 1.21.1](https://github.com/Team-EnderIO/EnderIO/releases/tag/v8.2.12-beta)。
[该版本配置](https://github.com/Team-EnderIO/EnderIO/blob/v8.2.12-beta/gradle/libs.versions.toml)
使用 NeoForge 21.1.216，符合本项目当前 21.1.224 基线。

现代 Yeta 已没有旧 `ITool.canUse/used` 接口。
[官方 YetaWrenchItem](https://github.com/Team-EnderIO/EnderIO/blob/v8.2.12-beta/enderio/src/main/java/com/enderio/enderio/content/tools/YetaWrenchItem.java)
是仅设置单件堆叠的普通物品，不声明耐久、能量费用或额外使用许可。
因此 `changeDurability=false/true` 都只识别当前主手真实 Yeta，不自行扣除物品或耐久。
其 `onItemUseFirst` 会修改目标 EnderIO 侧配置，不是旧 `used` 的替代品，不能在 OC
已经执行工具动作后再次调用。

公开的现代协议为注册物品 `enderio:yeta_wrench` 与 `c:tools/wrench` 标签。
来源：[官方注册](https://github.com/Team-EnderIO/EnderIO/blob/v8.2.12-beta/enderio/src/main/java/com/enderio/enderio/init/EIOItems.java)、
[官方标签](https://github.com/Team-EnderIO/EnderIO/blob/v8.2.12-beta/enderio/src/generated/resources/data/c/tags/item/tools/wrench.json)、
[官方工具交互](https://github.com/Team-EnderIO/EnderIO/blob/v8.2.12-beta/enderio/src/main/java/com/enderio/enderio/foundation/block/WrenchableBlockHandler.java)。
本桥同时检查真实注册 ID 和该标签；尊重数据包移除标签的结果，也不会借 EnderIO
模块给其他模组的所有标签扳手额外授权。没有反射分支、内部类绑定或内嵌外部 API。

## 接线与黑名单

在现有 enqueued common setup 中调用：

```java
li.cil.oc.common.integration.enderio.EnderIOIntegration.initializeDrivers();
```

缺少 `enderio` 或 `integration.modBlacklist` 含 `enderio` 时不注册本模块回调。
黑名单忽略大小写和两端空白，使用回调也重新检查该门控。修改配置后重启以重新注册。
入口只引用 Minecraft、NeoForge 和现有 OC 工具注册表，不加载 EnderIO 类。
元数据使用 `enderio` 可选依赖、`side="BOTH"`、`ordering="AFTER"`。
无需添加编译依赖或将 EnderIO API 打包进 OC/SDK。

OC 现有工具注册表自身的通用标签识别仍存在；本模块黑名单控制的是本模块授权的
工具使用回调，不会撤销其他工具 provider 的独立授权。

## 验证结果

Windows 专用服务端加载官方完整 Ender IO 8.2.12-beta，以下两轮均通过：

- `enderio-20261027-1234`：真实 Yeta 经过注册的工具检测、模拟/实际使用回调，
  再通过 `BlockState.useItemOn` 操作 Waypoint 与 NetSplitter；普通点击与潜行点击
  仅改变对应面，物品数量及组件保持不变。
- `enderio-disabled-20261027-1238`：`integration.modBlacklist=[enderio]` 禁用本桥，
  NetSplitter 六面状态不变。两轮都拒绝普通物品和仅在副手持有 Yeta 的情况。

报告位于 `build/api-compat-addon/devices/<轮次>/device-results.txt`，验收入口为
`tools/api-compat-addon/src/main/java/ocfixture/EnderIOContract.java`。
独立黑名单场景应保持 OC 方块不变；没有安装 EnderIO 的环境仍应正常启动。
这些有限行为经过实际验收后才可记为 I06 完成，不宣称所有其他 EnderIO 系统或多人
交互已经验证。
