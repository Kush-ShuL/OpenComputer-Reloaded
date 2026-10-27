# ProjectRed 可选集成

OC 行为依据仅为 MightyPirates/OpenComputers
[`258943363ea8fc4ef14948e6bc6f67375061580c`](https://github.com/MightyPirates/OpenComputers/tree/258943363ea8fc4ef14948e6bc6f67375061580c)
的 `integration/projectred/ModProjectRed.scala`、`EventHandlerProjectRed.scala`，以及
`common/tileentity/traits/BundledRedstoneAware.scala` 的 ProjectRed 输出接口。
原作者、MIT 许可和资源通知保留在 `LICENSE`、`NOTICE`。此迁移使用 Java 21，
不采用其他 OC 移植代码，也不打包 ProjectRed 的实现或 API。

## 入口与可选加载

在 OC common setup 的排队任务中调用：

```java
li.cil.oc.common.integration.projectred.ProjectRedIntegration.initializeDrivers();
```

入口先检查 `projectred_transmission` 是否实际安装，并检查 `integration.modBlacklist`。
以下名字大小写不敏感：`projectred-transmission`（原版）、`projectred_transmission`、
`projectred`、`projectred-core`、`projectred_core`。任一项禁用整个桥接；缺模组或禁用时
不解析外部类、不注册工具或红石 provider。只有 Core 而没有 Transmission 时，保持
原模块没有启用的行为。

通过反射绑定官方公开接口，不新增编译依赖或把外部 API 放进 SDK。模组 metadata
应将 `projectred_transmission` 声明为双方可选、排序 `AFTER`。现代 API 以官方
[1.21.1 / 4.23.0 源码](https://github.com/MrTJP/ProjectRed/tree/v1.21.1-4.23.0/api/src/main/java/mrtjp/projectred/api)
为准；其 Transmission 静态初始化会设置 API 实例，因此 common setup 时可以绑定。
[官方初始化](https://github.com/MrTJP/ProjectRed/blob/v1.21.1-4.23.0/transmission/src/main/java/mrtjp/projectred/transmission/ProjectRedTransmission.java)

## 16 通道双向信号

输入通过官方 `ITransmissionAPI.getBundledInput` 查询，传入 **OC 宿主坐标与向外方向**，
由 ProjectRed 查相邻线缆；不额外偏移或反转方向。返回字节逐通道按 `& 0xFF` 转为
0–255，再交给现有 OC `BundledRedstone` provider 聚合和 `RedstoneState` 缓存、事件处理。
本桥不提供额外的普通单线电平，保留原 provider 的 `computeInput=0`。
[官方输入 API](https://github.com/MrTJP/ProjectRed/blob/v1.21.1-4.23.0/api/src/main/java/mrtjp/projectred/api/ITransmissionAPI.java)

输出使用官方 `registerBundledTileInteraction` 注册真实 `IBundledTileInteraction` 动态代理，
代替在每个 OC 方块类型上硬实现可选接口。它仅识别已加载的 `RedstoneHost`，复用其
真实 `RedstoneState`，不会建立另一套输出数据。与原 `isOutputEnabled` 规则一致，
输出未启用时不允许束线连接；读取返回零信号。启用时在调用方给定的世界方向读取
16 个通道，逐个夹至 0–255 后转为 ProjectRed 字节。方向旋转仍由现有宿主状态负责。
正常的 OC 邻居通知触发 ProjectRed 重新查询，不直接调用其内部传播器。
[官方外部 tile 交互 API](https://github.com/MrTJP/ProjectRed/blob/v1.21.1-4.23.0/api/src/main/java/mrtjp/projectred/api/IBundledTileInteraction.java)

## 螺丝刀

现有 `WrenchTools` 接入 ProjectRed `IScrewdriver`：检测只确认真实接口类型；使用时先
调用现代公开 `canUse(player, MAIN_HAND)`。`changeDurability=false` 仅检查可用性，
`true` 才调用一次真实 `damageScrewdriver`。不会自行改耐久或绕过 ProjectRed 配置。
[官方工具 API](https://github.com/MrTJP/ProjectRed/blob/v1.21.1-4.23.0/api/src/main/java/mrtjp/projectred/api/IScrewdriver.java)、
[官方工具实现](https://github.com/MrTJP/ProjectRed/blob/v1.21.1-4.23.0/core/src/main/java/mrtjp/projectred/core/item/ScrewdriverItem.java)

## 验证范围

Windows 主构建和外部 SDK 消费者编译均已通过。2026-10-27 的独立服务端场景
`projectred-20261027-101421` 使用官方 ProjectRed Core / Transmission 4.23.0、
CodeChickenLib 4.6.1.529、CBMultipart 3.5.0.161，通过真实物品右键放置一段框架束线。
真实机箱的原生 Lua 5.3、内部二级红石卡和外置 Redstone I/O 经这段线缆双向通信；
官方 Transmission API、Java SDK 回调和原生 Lua 读取一致，覆盖全部 16 通道、
0 / 1 / 127 / 128 / 254 / 255、两种机箱朝向、错误方向为零、清零及普通停机后的断开。
真实螺丝刀通过 Waypoint 无消耗检测，再操作 NetSplitter 东面，耐久恰好从 0 变为 1。
独立场景及参数见 [外部 SDK 验证工具](../tools/api-compat-addon/README.md)。

第二个新世界 `projectred-disabled-20261027-101529` 将历史黑名单项
`projectred-transmission` 显式启用后也通过：原生 OC 输出仍可编程，但真实束线
不连接 OC，两端输入保持零；螺丝刀不改变 OC 方块或损耗耐久。

这只确认一段中性框架束线。分支、贴面线、盖板、长线、多源合并、区块重载和远程
多人同步仍需各自验收；当前客户端未安装 ProjectRed，未宣称其提示或线缆外观已验收。
