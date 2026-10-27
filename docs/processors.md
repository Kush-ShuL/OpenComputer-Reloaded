# CPU 与 APU

CPU 占用 CPU 槽。APU 同样占用 CPU 槽，同时提供 GPU 组件、显存与图形回调，不需要再占用显卡槽。APU 的 CPU 等级比其图形等级高一级，最高为三级。

| 物品 | CPU 等级 | GPU 等级 | 默认组件上限 | 默认调用预算 |
| --- | --- | --- | --- | --- |
| CPU 1 | 1 | — | 8 | 0.5 |
| CPU 2 | 2 | — | 12 | 1.0 |
| CPU 3 | 3 | — | 16 | 1.5 |
| APU 1 | 2 | 1 | 12 | 1.0 |
| APU 2 | 3 | 2 | 16 | 1.5 |
| 创造 APU | 3 | 3 | 16 | 1.5 |

组件上限读取 `computer.cpuComponentCount`，调用预算读取 `computer.callBudgets`。创造 APU 按原版仍是三级 CPU，不使用组件上限数组中的第四项。APU 1/2 不能安装到无人机或微控制器；创造 APU 保留原版豁免。所有 APU 仍遵循其他模组通过 IMC 注册的主机黑名单，并遵循宿主槽位的等级限制。

手持 CPU 或 APU 潜行右键，可以依注册顺序轮换当前可选架构。选择写入物品数据的 `oc:archClass` 和 `oc:archName`，现代版本将这两项保存在 `minecraft:custom_data` 数据组件中。旧的抽象 `NativeLuaArchitecture` 类名按原版映射到当前 Lua 默认架构。已保存、仍可加载的 `Architecture` 类不会因退出可选架构列表而丢失；只有类已缺失、类型不符或加载失败时，才记录警告、清理旧选择并回退到首个已注册架构。可选列表为空时返回空架构。

普通 CPU 的 DeviceInfo 保留 `CPU`、`FlexiArch N Processor`、原厂商与调用预算时钟。APU 保留 `APU`、`FlexiArch N Processor (Builtin Graphics)`、显存容量、色深与 CPU/GPU 组合时钟；原版该描述中的 N 及 CPU 时钟取图形等级，而实际处理器组件上限及调用预算取上表的 CPU 等级。

本批补齐存活架构的持久化语义、APU 原有主机限制及潜行切换后的物品库存变更标记。
CPU/APU 等级、槽位、配置读取、图形组件和 DeviceInfo 沿用已对应原版的实现。
Windows 构建和本批设备回归通过。

依据：MightyPirates/OpenComputers 提交 `258943363ea8fc4ef14948e6bc6f67375061580c` 中 `DriverCPU.scala`、`DriverAPU.scala`、`ModOpenComputers.scala`、`CPULike.scala`、`CPU.scala` 与 `APU.scala`（MIT 许可证）。
