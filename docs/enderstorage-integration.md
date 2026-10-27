# EnderStorage 可选集成

OpenComputers 行为依据仅为 MightyPirates/OpenComputers
`258943363ea8fc4ef14948e6bc6f67375061580c` 的两个原文件：

- `src/main/scala/li/cil/oc/integration/enderstorage/DriverFrequencyOwner.java`
- `src/main/scala/li/cil/oc/integration/enderstorage/ModEnderStorage.scala`

原版权与 MIT 许可保留在项目 `LICENSE`、`NOTICE`。迁移使用 Java 21，不包含 Scala，
不参考其他 OC 移植，也不打包 EnderStorage 或 CodeChickenLib 的 API/实现。

## 接线与可选加载

在 common setup 排队任务中、OC Driver 注册仍开放时调用：

```java
li.cil.oc.common.integration.enderstorage.EnderStorageIntegration.initializeDrivers();
```

入口先检查 `integration.modBlacklist` 中的 `enderstorage`，再检查 NeoForge 的实际
模组加载状态。未安装或列入黑名单时不解析 EnderStorage、CodeChickenLib 类型。
启用后通过现有 `Driver.add` 注册真实 Adapter block driver。

实现通过窄反射绑定官方公开方法，因此不需要新增编译依赖或构建缓存。运行时由玩家安装
真实 EnderStorage 1.21.1 及其所需 CodeChickenLib。官方 1.21.1 源码的版本配置为
EnderStorage 2.13.0、NeoForge 最低 21.1.72、CodeChickenLib `[4.6.0,5.0.0)`，
目标项目的 NeoForge 21.1.224 满足该最低要求。[官方版本配置](https://github.com/TheCBProject/EnderStorage/blob/1.21.1/gradle.properties)、
[官方模组依赖声明](https://github.com/TheCBProject/EnderStorage/blob/1.21.1/src/main/resources/META-INF/neoforge.mods.toml)

## Adapter 回调

Adapter 识别真实 `TileFrequencyOwner`，为 Ender Chest 暴露 `ender_chest`，为 Ender Tank
暴露 `ender_tank`。两个组件均保留原来的 `NamedBlock` 优先级 0。

| 回调 | 行为 |
| --- | --- |
| `getFrequency()` | 返回 `{left, middle, right}`，每个颜色数为 0–15 |
| `setFrequency(packed)` | 接受 0–4095，按高、中、低三个四位字段解释 |
| `setFrequency(left, middle, right)` | 接受三个 0–15 颜色数 |
| `getOwner()` | 公共频道返回 `global`，私人频道返回已存显示名；仅存 UUID 时返回 UUID 字符串 |
| `getFrequencyColors()` | 返回原颜色枚举数组，经 OC 标准返回值转换得到颜色名 |
| `getColors()` | 返回以 0–15 为键、颜色枚举为值的映射，保留原实现的零起点键 |

参数范围检查与 `invalid frequency` 错误保留原实现。`setFrequency` 不返回值。
颜色顺序沿用官方 CodeChickenLib `EnumColour` 的 wool/ordinal 顺序，WHITE=0，BLACK=15；
原枚举的标准字符串形式为 `WHITE`、`ORANGE`、`LIGHT_BLUE` 等，不引入另一套颜色编号。
[官方颜色定义](https://github.com/TheCBProject/CodeChickenLib/blob/1.21.1/src/main/java/codechicken/lib/colour/EnumColour.java)

## 现代 API 与 owner 保留

现代 `TileFrequencyOwner.frequency` 已不是公开字段。集成改用官方公开
`getFrequency()`、`setFreq(Frequency)`；真正的 setter 会执行保存、客户端同步和对应的
capability 失效通知，不通过写 NBT 或私有字段模拟切换。
[官方 TileFrequencyOwner](https://github.com/TheCBProject/EnderStorage/blob/1.21.1/src/main/java/codechicken/enderstorage/tile/TileFrequencyOwner.java)

现代 `Frequency` 将原来的 owner 字符串拆为 `Optional<UUID> owner` 与
`Optional<Component> ownerName`。`setFrequency` 依次调用其公开不可变操作
`withLeft/withMiddle/withRight`，同时保留 owner UUID 和 ownerName。不会将私人频道
降为公共频道，不改变 owner，也不推断或补造缺失的玩家身份。
[官方 Frequency](https://github.com/TheCBProject/EnderStorage/blob/1.21.1/src/main/java/codechicken/enderstorage/api/Frequency.java)

原 OC 模块只有 `getOwner`，没有 `setOwner`。此迁移也不提供 owner 写入接口，不调用
`withOwner/withoutOwner`，不以强制改 owner 绕过私人频道隔离。`getOwner` 的 UUID 后备值
是现代 ownerName 可缺失时的兼容选择，不触发联网查询玩家名。

回调默认走 OC 的同步调用路径。被替换、移除或卸载的目标会报告 `is no longer present`，
不会继续修改已经脱离世界的旧 block entity。

## 已完成的真实服务端验证

Windows 隔离服务器已使用官方 EnderStorage 2.13.0.191、CodeChickenLib 4.6.1.529，
在真实 Case 的 native Lua 5.3 中通过真实 Adapter 调用两个设备的五个回调。
`ender-default-20261026-230907` 通过 packed/三颜色形式、0/4095 边界、非法参数拒绝、
全部 16 色编号映射，并通过设备的真实 item/fluid capability 确认两个公共频率和两个
私人 owner 的库存/流体隔离、重新切换后的内容恢复，以及 owner UUID/显示名保留。

测试仅使用机器实际可见的复合组件。Adapter 内部子节点按原版设置为 Neighbors；
不能将同名但对机器不可见的内部节点当成玩家可调用的组件。

另两个 Windows 运行 `ender-disabled-20261027-085245` 与
`ender-disabled-generic-20261027-085245` 通过了集成黑名单场景。通用库存/储罐驱动
默认关闭时，真实 Adapter 不产生这两个设备的 wrapper，Lua 中没有可调用的专属入口；
测试配置显式启用通用驱动时，真实 capability、Driver 和两个可见 wrapper 均必须存在，
再逐一验证两个 wrapper 的五个 EnderStorage 专属方法被拒绝。两种场景仍通过实际
item/fluid capability 写入、取回标记内容，并确认频率及 owner 没有改变。

私人 owner 的前置状态由官方公开 Frequency API 设置，不声称钻石右键交互已经验收。
远端多人同步、玩家交互和跨进程保存恢复仍需各自验证。
