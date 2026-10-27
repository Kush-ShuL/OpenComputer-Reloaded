# 电池与太阳能升级

实现依据 MightyPirates/OpenComputers 固定提交 `258943363ea8fc4ef14948e6bc6f67375061580c` 的两类物品、驱动及组件，以及原 `Chargeable.Provider`（MIT 许可证）。它们都是纯电源，没有新增 Lua 回调。

电池升级占用升级槽，提供一级、二级和三级容量，读取 `power.buffer.batteryUpgrades`（默认 10000、15000、20000 OC）。每件电池是一个可达性为 `Network` 的连接器，不增加 Lua 组件数量。多件电池通过实际 OC 网络共同供电；能量保存在物品 `oc:data.node.buffer`，挂载、拆除和保存继续使用现有节点序列化链。

电池物品保留 OC `Chargeable` 充放电和电量条；NeoForge FE 能力由独立 `BatteryEnergyStorage` 注册。它沿用原接口的“返回未使用能量”规则换算收、放电，支持模拟操作不写入，并通过现有 `ExternalPower.expose` 遵守外部电源集成开关和黑名单。该 FE 物品边界使用原 `FE = OC × ratioForgeEnergy` 换算。

太阳能升级是二级升级，提供一个没有本地容量的实际网络连接器。首次更新立即判断日照，之后每经过 100 次组件更新再判断一次，期间缓存结果；可发电时每 tick 向网络增加 `power.solarGeneratorEfficiency`（默认 0.2 OC）。该间隔不依赖世界总时间是否整除 100，也不会暂停机器就清空计时。

日照判断使用宿主上方位置：白天、非下界性质维度、可以透天，并且当前生物群系不会下雨或世界没有雨与雷暴。旧 `canBlockSeeSky` 对应现代 `canSeeSkyFromBelowWater`，保留海平面以下透过水观察天空的规则；降雪与无降水生物群系不会被笼统视为雨区。现代维度使用 `ultraWarm` 表达原下界性质，降水使用位置对应的 `Biome.Precipitation`；原版单独的生物群系 `canRain` 标志已不存在，因此高海拔雨雪分界沿用现代游戏规则。

日照缓存和倒计时按原版不写入 NBT，重新创建组件后的首次更新重新判断。太阳能本身不储存多余产能：网络已满时多余部分由连接器规则拒绝，不产生虚假电量。电池与太阳能的 DeviceInfo 保留 `class=power`、原厂商和产品名，电池同时报告配置容量。

物品属性、驱动和 FE 能力注册均已接入；Windows Java 21/NeoForge 构建通过。
服务端 `upgrade-storage-20261027-a4` 验证了三档真实电池的 FE 收放、模拟操作、
OC 无限量充放及连接器保存/重挂后的电量保持。
