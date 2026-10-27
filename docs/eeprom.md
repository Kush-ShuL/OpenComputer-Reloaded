# EEPROM

语义来源为 MightyPirates/OpenComputers 固定提交
`258943363ea8fc4ef14948e6bc6f67375061580c`：`server/component/EEPROM.scala`、
`integration/opencomputers/DriverEEPROM.scala`、`common/item/EEPROM.scala`，
以及 `common/init/Items.scala` 的 EEPROM 工厂和 Lua BIOS 初始化。
保留上游许可证和归属，不采用其他 OC 移植实现。

组件保留全部十个回调：`get`、`set`、`getLabel`、`setLabel`、`getSize`、
`getChecksum`、`makeReadonly`、`getDataSize`、`getData`、`setData`。
代码和用户数据容量分别取 `eepromSize`、`eepromDataSize`；省略参数或传入 `nil`
会清空对应字节数组，超容量报 `not enough space`。`set`、`setData` 先扣除
`eepromWriteCost` 再检查参数和容量，成功后分别暂停 2 秒和 1 秒，与原版顺序一致。
只读检查在代码写入的扣能之前；只读错误为 `storage is readonly`，
能量不足为 `not enough energy`。

标签去除首尾空白后最多保留 24 个 UTF-16 字符，运行时设置空标签恢复为 `EEPROM`。
CRC32 校验和使用小端四字节的小写十六进制表示。`makeReadonly` 要求匹配当前校验和，
不匹配返回 `incorrect checksum`；只读锁定代码和标签，用户数据仍可写入。

物品和环境继续使用 `oc:data` 内的 `oc:eeprom`、`oc:userdata`、`oc:readonly`、
`oc:label`，包含继承的节点持久数据。加载已有存档不按当前配置再次截断内容。
EEPROM 驱动只在服务端创建环境，物品名称采用已存标签，潜行使用允许绕过方块交互。

`registerEEPROM` 按配置截断代码和数据，保留显式空标签，省略为 `null` 的可选字段，
保存只读标志，注册创造物品并返回独立副本。原 Lua BIOS 读取上限为 4 KiB，之后仍由
工厂按配置容量截断。

驱动和组件已符合这些原行为；Lua BIOS 工厂已恢复 4 KiB 读取上限。
Windows 构建及本批设备启动回归通过。
