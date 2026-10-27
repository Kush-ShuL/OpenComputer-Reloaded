# 数据卡与量子链接卡

行为来源为 MightyPirates/OpenComputers 固定提交
`258943363ea8fc4ef14948e6bc6f67375061580c` 的 `DataCard.scala`、
`LinkedCard.scala`、`QuantumNetwork.scala`，以及相应物品、驱动和配对合成。
保留原署名与许可证，以 Java 实现。

## 数据卡（D09）

三级数据卡分别保留原级别的操作：一级提供 Base64、deflate/inflate、
CRC-32、MD5、SHA-256；二级增加 HMAC、AES-128 CBC 和安全随机数；
三级增加 256/384 位 EC 密钥、ECDH 与 ECDSA。输入与结果中的 Lua 字符串按
原版作为二进制字节处理，哈希返回原始字节，CRC-32 使用小端序。
Base64 解码兼容两种字母表并忽略非编码字符，保留原 Commons Codec 的宽松行为。

原操作的 direct 标记、调用次数、参数检查、费用、软硬大小限制和超出软限制后的
暂停保持对应关系。普通运算在检查输入后按原倍率从真实连接器扣费；
密钥生成、签名、验签和共享密钥保留各自的原始顺序与费用。
随机数使用线程独立的 SHA1PRNG。节点与组件均为 Neighbors 可见性。

EC userdata 保留公开/私有类型、DER 编码、`isPublic`、`keyType`、`serialize`
和 `Type` / `Data` 存档字段。Java 嵌套类名仍为 `DataCard$ECUserdata`。
物品的三级稀有度、card 槽和对应硬件等级不变；补齐原物品说明、
短地址及高级提示中的等级。只有保存了节点地址的单件卡才限制堆叠为 1。

## 量子链接卡（D22）

保留 `tunnel` 组件、`send`、`getChannel`、`maxPacketSize`、`getWakeMessage`
和 `setWakeMessage`。节点为 Network 可达，组件为 Neighbors 可见。
原配对配方为两张卡写入同一 UUID 通道；未配对的默认通道仍为 `creative`。
通道字段本身不改变物品的堆叠上限。

发送保留原数据包类型/大小限制、端口 0、无线最大距离对应费用以及发送方和目标
过滤。数据包参数直接保留二进制字符串。通道注册使用弱引用，断开时移除，
重新加载已连接卡时先移除旧通道再注册，避免继续收到旧通道的数据。
唤醒匹配保留精确与模糊两种模式、原信号和宿主标脏路径。

恢复时只有存在 `oc:tunnel` 或 `wakeMessage` 字段才覆盖相应值，
恢复 `wakeMessageFuzzy`，与原可选字段语义一致。
物品补齐原说明、通道短显示、短地址与高级等级；tier、槽位、
驱动注册及宿主黑名单沿用原始定义。

本族的算法、连接和保存路径已按上述固定源码核对。跨客户端载荷、菜单与模型的
整体检查统一由清单 C19、C20、C23 承担。
