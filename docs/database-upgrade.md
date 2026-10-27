# 数据库升级

数据库保存物品样本，不消耗样本物品。三个等级分别提供 9、25、81 个样本槽。
右键打开样本界面，潜行右键清空已有数据库数据并恢复为默认物品。

每格样本数量为 1，保留物品的数据组件。数据库修改会立即写回提供它的物品；
卸载、重新安装后仍可读取。通过内部数据库接口取得的物品是独立副本。
内部设置接口按原库存规则接管传入样本并把数量限制为 1；保留原物品时应传入副本。

Lua 接口保留 `get`、`computeHash`、`indexOf`、`clear`、`copy`、`clone`。
Lua 槽号从 1 开始。`copy` 可以复制到同一网络中的另一数据库；第三参数省略表示
本数据库，显式传入 `nil` 仍是参数错误。`clone` 复制双方较小容量的槽位数，暂停
0.25 秒并返回复制数量。

哈希继续使用压缩后的物品 NBT 和 SHA-256。按原实现，空槽也有哈希值，
`indexOf` 可以查到首个具有该哈希的空槽；内部接口返回从 0 开始的下标，
Lua 接口返回从 1 开始的槽号，未找到均返回 -1。

数据库组件对网络可见，设备信息保留产品名和对应容量。原始依据为
MightyPirates/OpenComputers 提交 `258943363ea8fc4ef14948e6bc6f67375061580c`
中的 `UpgradeDatabase.scala`、`DatabaseInventory.scala`、`ItemStackInventory.scala`、
`DriverUpgradeDatabase.scala`、`UpgradeDatabase` 物品和 `DatabaseAccess.scala`。

Windows 服务端验证已通过三档容量、物品数据组件、独立副本、即时写回、两种
存档接口、独立 SHA-256 校验、空槽查找及跨数据库复制。入口为
`tools/api-compat-addon/src/main/java/ocfixture/UpgradeStorageContract.java`，
报告位于 `build/api-compat-addon/devices/upgrade-storage-20261027-a4/device-results.txt`。
