# 物品栏控制器与转运器

行为来源为 MightyPirates/OpenComputers 固定提交
`258943363ea8fc4ef14948e6bc6f67375061580c` 的 `UpgradeInventoryController.scala`、
`Transposer.scala` 和 `WorldInventoryAnalytics`、`InventoryAnalytics`、
`InventoryWorldControlMk2`、`ItemInventoryControl`、`InventoryTransfer`、
`WorldTankAnalytics` 及对应物品、驱动。保留原许可、署名，使用 Java 实现。

## 物品栏控制器（D49）

适配器公开外部物品栏分析；无人机增加内部物品栏、世界指定槽搬运和物品内置物品栏；
机器人另提供 `equip`，并将操作方向限制为前、上、下。其他有效但不支持的方向
返回原 `unsupported side`，超出六向枚举才是 `invalid side`。
节点维持 Network 可达，组件在 Agent 中为 Neighbors、适配器中为 Network。
没有额外能量连接器。

世界物品栏访问优先使用方块的实际侧面能力，支持实体物品栏、原假玩家交互检查，
保留槽位范围、物品检查配置和空物品栏的返回值。现代物品标签承接共享
OreDictionary 分类；检查 NBT 的操作比较实际数据组件。所有原 Lua 回调说明恢复。

数据库访问按原网络中的实际数据库组件解析，保留 `no such component` 与
`not a database` 的区别。`store` 和 `storeInternal` 都把物品堆副本交给数据库，
让数据库将描述数量归一化为一件，不能改动真实背包的物品数量及数据组件。

吸取物品按选中槽到背包末尾、再从零槽到选中槽之前的顺序插入。
`dropIntoSlot` 先按原顺序检查方向、数量和可选访问面；选中槽为空时直接返回
单个 `false`。成功搬空选中槽时通过真实库存入口清空该槽，部分搬运标脏，
只有成功才暂停相应动作时间。可选访问面显式传入 nil 仍做严格的整数检查。
物品内部物品栏操作由真实 `Driver.itemHandlerFor` 提供，不另加“目标槽就是
选中槽则拒绝”的限制；保留原返回数量和没有物品栏时的 `0, "no item inventory"`。

## 转运器（D40）

方块和升级共享原分析与搬运接口。六向参数为世界方向；方块组件为 Network
可见，升级为 Neighbors。补齐原转运器物品驱动：该方块物品也可安装到升级槽，
保留一级驱动和原环境类型，驱动与环境提供器在启动时各注册一次。
转运器从实际网络连接器按每次操作扣除
`power.cost.transposer`，包括数量为零或没有目标的操作；不足时先返回
`nil, "not enough energy"`。方块成功付费时发送活动提示。

没有指定源槽时，物品搬运按原顺序尝试各源槽，完成第一次有效搬运即返回数量。
指定源槽时先验证源物品栏和槽，再验证目标物品栏及可选目标槽；缺少物品栏
保留原参数异常，与未指定源槽时的 `nil, "no inventory"` 有区别。
正常搬运使用实际 NeoForge 物品能力的模拟提取、模拟插入和执行入口。

流体搬运保留可选数量和原零基源罐选择，模拟排出/接收后执行，根据实际搬运量
暂停。空或不存在的指定源罐继续使用原自动选择规则。世界水源、可放置流体、
水含方块和真实流体能力沿用共享 `FluidUtils`；此前修复的跨罐交换及世界源液体
边界保留。槽/罐分析的显式 nil、总容量取最大值和物品检查配置维持原行为。

## 运行验证

主模组 Windows 构建及 NeoForge 21.1.224 批次 `device-batch-20261027-c3` 通过。
真实机器人将带数据组件的 37 件物品存入数据库，描述为一件而背包仍为 37 件；
实际安装的转运器升级读取相邻物品栏。指定槽丢入七件、从选中槽后的位置吸取两件，
空手和显式 nil 访问面的返回及异常均符合原语义。

真实转运器从箱子指定槽搬运五件，正常和零数量操作均扣除一次配置费用；
世界水源搬运一桶后核对源、目标两格；排空真实连接器后拒绝搬运且库存不变。
测试使用实际 Case/Robot Machine Context、公开 SDK 驱动和真实物品/流体入口。
报告位于 `build/api-compat-addon/devices/device-batch-20261027-c3/device-results.txt`。

模型、菜单、远程载荷与整体字体显示由固定清单的对应 C 项统一负责。
