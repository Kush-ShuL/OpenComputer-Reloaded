# 客户端效果与声音

机箱和机器人开机后会播放持续运行声，停机后停止。机器人移动时沿用同一个声源并更新位置。声音随“方块”音量、主音量和暂停状态变化；微控制器沿用原版的静音行为。

实现依据 MightyPirates/OpenComputers 提交 `258943363ea8fc4ef14948e6bc6f67375061580c` 的 `client/Sound.scala`、`common/tileentity/traits/Computer.scala`、`Robot.scala` 和 `Microcontroller.scala`。运行声使用 `computer_running`，音量为 `0.5 × client.soundVolume`；运行状态变化后的启动延迟为 50–99ms，初次载入正在运行的设备时为 1000–2999ms。

`ComputerSounds` 从客户端已同步的运行状态维护声源。机器人以稳定宿主关联移动前后的方块代理。源码已接入停机、设备移除、区块卸载、世界退出和声音引擎重载的生命周期。`SoundEngineMixin` 在声音 tick 完成后定向清理本模组运行声的失效索引和重复登记，避免反复静音恢复积累 ticking 条目。

Windows 客户端实测报告：`build/api-compat-addon/integration-client/client-sound-20261027-h2/client-results.txt`，整体状态 **PASS**。本批通过真实声音引擎及其声道验证：

- 真实机箱和机器人运行原生 Lua 固件后，启动循环声并使用配置对应的音量。
- 真实机器人移动后，同一声音实例更新位置，播放次数没有增加。
- 机箱停机释放声道，重新开机建立新的运行声。
- 连续三轮方块音量归零和恢复，真实声道均停止并恢复；每轮检查原生声音表，当前机箱和机器人各只有一条 ticking 与声源登记，已停止的旧机箱声源没有 ticking、删除计时或声源索引残留。
- 真实暂停菜单使运行声静音，返回游戏后恢复；声音引擎重载后重新建立声道。
- 实际移除测试方块后，客户端设备消失，运行声道释放。

上述证据验证声音的原生生命周期，不代表已经人工确认听感。真实区块卸载、世界退出和远程多人场景仍需对应玩家验收。

软盘插拔声由 `DiskSounds` 恢复原版链路：同一宿主的同一种声音有 500ms 冷却，广播范围为 `15 × client.soundVolume`，客户端播放音量为 `range / 15 + 0.5`。其中 15 表示范围，不能直接作为播放音量。机箱、外置软盘驱动器、机架式软盘驱动器和机器人软盘装备槽已接入；同一机架内的驱动器共享机架宿主的冷却。这部分依据原 `common/Sound.scala`、服务端 `PacketSender.sendSound` 和客户端 `PacketHandler.onSoundEffect`，本批未单独进行实际插拔事件验收。

C22 的粒子、宠物、客户端设置、命令、剪贴板与更新检查仍保留各自的验收范围，未因本批声音通过而整体关闭。现有 `UpdateChecker` 已区分玩家模组发行与空 SDK/API 发行，本批保留其实现，未重复执行发布或更新检查测试。
