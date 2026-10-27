# 内存

把内存装入支持对应等级的内存槽，容量可以叠加。默认容量如下：

| 物品 | 所需槽位 | 容量 |
| --- | --- | --- |
| `ram1` | T1 | 192 KiB |
| `ram2` | T1 | 256 KiB |
| `ram3` | T2 | 384 KiB |
| `ram4` | T2 | 512 KiB |
| `ram5` | T3 | 768 KiB |
| `ram6` | T3 | 1024 KiB |

服务器可在 `computer.lua.ramSizes` 修改六档容量，在 `computer.callBudgets`
调整三个等级的调用预算。修改后重启。机器的总内存上限由
`computer.lua.maxTotalRam` 决定。

实现保留原版六档物品、三个驱动等级、内存槽类型、调用预算、相邻可见的节点及
DeviceInfo。节点随组件数据保存；内存本身没有独立的 Lua 方法或额外运行状态。
驱动对不支持的物品仍返回零容量和最低驱动等级。

原始依据为 MightyPirates/OpenComputers 提交
`258943363ea8fc4ef14948e6bc6f67375061580c` 中的 `DriverMemory.scala`、
`server/component/Memory.scala` 和 `common/item/Memory.scala`；
源码接入位于 `Items.initializeDrivers()`，实际内存消费由原生 Lua 和 LuaJ 架构处理。
