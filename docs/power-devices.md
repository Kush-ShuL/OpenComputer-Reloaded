# 电容、充电器与能源设备

行为来源仅为 MightyPirates/OpenComputers 固定提交
`258943363ea8fc4ef14948e6bc6f67375061580c` 的同名方块、tileentity，
以及 `Environment`、`PowerAcceptor`、`power/Common`、`PowerBalancer`、
`PowerInformation`、`ComponentInventory`、`Rotatable` 和 `ItemCharge`。
保留原署名与许可证，运行实现使用 Java。

## D06 电容与地毯电容

电容初建节点采用理论最大容量，读取电量后再计算实际容量，避免加载时先按最小
容量截断旧电量。容量为 `buffer.capacitor`，六个直接相邻电容各加
`buffer.capacitorAdjacencyBonus`，六个轴向距离为 2 的电容各加一半。
普通与地毯电容共同参与计算；不强制加载尚未加载的邻居区块。
入网、邻接变化和移除会更新受影响的电容。连接器保持 Network 可达性。
比较器按本地电量/本地容量计算并四舍五入为 0～15；容量和电量变化通知比较器。
设备信息保留理论容量及 `CapBank3x` / `CarpetedCapBank3x` 型号。

地毯电容每 20 刻扫描上方一格中的存活生物，至少两只羊产生一份 `sheepPower`，
至少两只猫科目标产生一份 `ocelotPower`，组内数量继续增加不叠加产能。
Minecraft 1.12 的 `EntityOcelot` 同时表示豹猫和家猫；现代版本的 `Ocelot`
与拆分后的 `Cat` 合并为同一组。两组的产能可以相加，直接存入真实 OC 网络。
每只生物依 `carpetDamageChance` 尝试受 1 点普通伤害并惊慌，首次命中后两组
共享一分钟电击冷却；产能不受该冷却影响。冷却沿原版仅保存在当前实例中。

## D08 充电器

充电器保留单格充电槽、前面禁止 OC/外部能源连接、其余五面可连接，以及原
`buffer.converter` 容量。实现公开 Rotatable，组件驱动获得与方块朝向一致的
坐标转换。没有额外 Lua 组件。分析器只显示充电速度；StateAware 继续依附近
外部充电目标及供电状态报告 CanWork / IsWorking。

最大红石输入限制为 15，充电速度为输入/15；扳手反相后为 `(15-输入)/15`。
反相立即更新速度与客户端状态。每 20 刻及邻接变化重新寻找六面相邻机器人、
附近无人机、安装纳米机器控制器的玩家，以及附近玩家主背包中的可充电物品。
机器人/无人机从其真实 OC 连接器充电；玩家控制器通过原控制器接口充电。

每 `power.tickFrequency` 刻，外部目标各按
`chargerChargeRate * chargeSpeed * tickFrequency`，内部物品与玩家装备各按
`chargerChargeRateTablet * chargeSpeed * tickFrequency` 申请电量。
保留原半个申请量的供电阈值和连续累积的 canCharge 判断。申请从实际源网络
扣除，把目标不能接收的余量退回；不会凭空补满目标或为模拟充电扣费。
`ignorePower` 保留内部无限供能行为，同时外部 FE 接收边界不接收电量。

槽位允许原 Tablet 驱动及 ItemCharge 注册的充电物品，充电回调按原注册顺序
选择首个匹配者。只有 Tablet 槽驱动创建内部组件环境；它们在真实网络连接、
断开、重新连接、物品移除和区块卸载时保存并连接/拆除，不把内部组件遗留在
旧网络。FE 接收沿已验证的换算和吞吐约定，在同一网络锁下计算并写入。
保留原新旧 `chargeSpeed`、`hasPower`、`invertSignal` NBT 字段的读取优先级，
写入带 `oc:` 的字段并同步客户端。

## D28 能源转换器

保留 Visibility.None 的 OC 连接器、`buffer.converter` 容量、所有面的外部
接收、`power.rate.powerConverter` 吞吐上限和全网络剩余容量限制。
FE 接收、模拟、整数舍入、黑名单及 OC/FE 换算继续使用当前已通过 I13 的约定；
不改变电池物品自身的另一条历史换算路径。设备信息保持
`Power converter / Transgizer-PX5` 及配置吞吐量。
原 NotAnalyzable 由公开 Analyzable 返回空结果承接，分析器不显示节点诊断。

`ignorePower=true` 时，根任务已在 `Items.registeredItems` 和 JEI 的现有
隐藏过滤点移除转换器展示，保留正常注册与已有存档身份。
原 IC2 / AE2 接受器的现代桥接分别归固定清单 I09 / I01，不通过删除集成缩小范围。

## D29 能源分配器

六个面分别保留独立、None 可见性的连接器，每个容量为 `buffer.distributor`。
这些面不传送数据包或共享组件可见性，主 node 仍为 null。
每个原 `tickFrequency` 周期按真实网络身份去重，在全部相关网络锁内汇总电量与
容量，再把每个独立网络调到同一充电比例；同一个网络接入多面不会被重复计数。
使用配置的原数值作周期计算，不把允许的小数周期截断成整数。

六个连接器按原面序保存于 `oc:connector`，保留地址和本地电量。
服务端统计的全局电量/容量同步到客户端；比例改变超过 5% 时立即同步，其他变化
由原 100 刻节奏合并。拆除及区块生命周期清理全部节点。
原 NotAnalyzable 同样通过返回空分析结果保留。

## 有限验收与已有证据

I13 的 `forge-energy*-20261027-110738` Windows 服务端报告已经覆盖真实充电器
向 FE 物品充电、模拟和剩余量、真实能源能力回调及两个黑名单；本批直接复用。
Jade 集成报告已经覆盖真实充电器的红石速度。设备保存与网络生命周期复用共同
连接器实现，不为每个外部模组重复展开矩阵。

主模组 Windows 构建通过。NeoForge 21.1.224 批次 `device-batch-20261027-c3`
通过普通—地毯—普通三电容的相邻/二格加成和依次移除；两个真实 Case 电网与
分配器另四面保持各自独立，共 100 OC 电量守恒并达到相同填充比例。
真实创造配置平板在充电器中挂载存储，实际移除充电器节点后旧文件系统脱网，
正常世界刻重新挂载时保留文件系统地址。
报告位于 `build/api-compat-addon/devices/device-batch-20261027-c3/device-results.txt`。
模型、菜单和远程客户端显示统一归 C20 / C23 / C19。
