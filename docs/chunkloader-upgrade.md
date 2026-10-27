# 区块加载升级

行为来源为 MightyPirates/OpenComputers 固定提交
`258943363ea8fc4ef14948e6bc6f67375061580c` 的 `UpgradeChunkloader.scala`、
`ChunkloaderUpgradeHandler.scala`、`DriverUpgradeChunkloader.scala` 和内置主机黑名单。

升级保留原 tier 3、upgrade 槽位、`chunkloader` 组件和 DeviceInfo：
`generic / World stabilizer / MightyPirates GmbH & Co. KG / Realizer9001-CL`。
组件具有原来的 Network 可见性。原黑名单禁止适配器和平板，机器人、无人机、
微控制器与没有被 IMC 排除的兼容主机可以使用。

Lua 保留两个非 direct 回调及原说明：

```lua
component.chunkloader.isActive()            -- 当前是否持有加载票据。
component.chunkloader.setActive(true)        -- 启用；仅状态改变时返回 true。
component.chunkloader.setActive(false)       -- 禁用；仅状态改变时返回 true。
```

在不允许的维度显式启用仍报 `this dimension is blacklisted`；自动启用则保持关闭。
`chunkloader.dimWhitelist` 非空时必须命中，`dimBlacklist` 始终优先。
使用 Settings 已验证的配置值，支持现代维度资源名及原版数字 `-1 / 0 / 1` 映射。
未知旧数字维度由配置迁移层拒绝，不猜测其他模组维度。

启用后完整 ticking 加载主机所在区块及周围八块。机器人移动后立即更新边缘；
无人机在原 `tickFrequency` 节点更新位置。每个检查周期从真实网络连接器扣除
`chunkloaderCost * tickFrequency`，不足时释放全部票据，不生成虚拟电量。
`computer.started` 自动启用，`computer.stopped`、组件拆除/断网及世界卸载释放。
主机的当前维度与实际持票世界分开保存，移动到新维度先释放旧世界票据，再检查并
建立新范围；进入禁止维度时直接关闭。来自非服务器线程的网络生命周期通知只入队，
不在 VM/网络线程同步等待区块加载。

NeoForge 1.21.1 使用公开 `TicketController`，通过 `RegisterTicketControllersEvent`
注册 `opencomputers:chunkloader`。现代 API 的 UUID owner 表达原版票据所有者；
升级物品保留 `ticketIdentity`，并保存对应的 `ticketAddress`，避免复制物品的新网络
地址误用原物品票据。已有预览版只含 `ticketIdentity` 的存档仍可恢复。
区块集合由 NeoForge 的世界 `chunks` SavedData 持久化；它会恢复所保存的 ticking
集合，组件认领后立即归整为当前位置 3×3。原 Forge 使用票据 modData 的中心坐标
先加载所有者区块；现代版本由已保存的集合完成同一所有者加载过程，不引入另一套存档。

恢复检查在注册回调中移除已禁止维度的票据及本组件不会创建的非 ticking 票据。
未认领票据在该维度下次保存时清理，避免一次主世界保存误删其他维度尚待认领的票据。
世界卸载清理该世界的恢复快照及 live 状态，单人换档不继承上个世界的缓存。
共享 AgentRuntime 已在拆除和关闭时移除真实组件节点，无须另加一条卸载路径。

Windows NeoForge 21.1.224 批次 `agent-actions-20261027-b2` 已通过。
公开 SDK 驱动在真实 Case 上创建升级，使用实际 Machine Context 调用组件，
从 NeoForge 的持久票据公开存档视图确认完整 ticking 的 3×3 九块，
重复启用无变化、关闭清票、真实连接器欠电后按配置周期清票、节点拆除清票。
报告位于 `build/api-compat-addon/devices/agent-actions-20261027-b2/device-results.txt`。
共享注册与宿主过滤已接入，主模组 Windows 构建通过；维度切换和恢复逻辑依据
上述固定源码及现代 TicketController 生命周期对应关系核对。
