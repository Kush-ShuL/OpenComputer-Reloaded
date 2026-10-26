# 程序盘战利品与更新提示

这两个功能均读取 `config/opencomputers/settings.conf`。修改后重启游戏或服务器。

```hocon
opencomputers.misc {
  lootProbability = 5
  updateCheck = true
}
```

`lootProbability` 是抽取权重，不是百分比。程序盘参与地牢、沙漠神殿、丛林神庙与要塞图书馆箱子主物品池的抽取，保留该池自己的抽取次数、条件和其他物品权重。值为零或负数时不加入程序盘。现代原版箱子分为多个池，优先使用含铁锭、金锭或钻石的物资池；图书馆使用第一个非空池。数据包有意提供的空表不会被填满。

抽到程序盘后，再按照原 `assets/opencomputers/loot/loot.properties` 的独立权重选择内容。Digger 的权重为 2，其他可生成盘为 1；OpenOS、OPPM、Data Card Software 和 Generator Upgrade Software 的权重为 0，不会自然生成，但仍可制作或从创造物品栏取得。生成结果保留原盘名称、染色与 `oc:lootFactory`、`oc:data` 等数据。

存档可以增加自己的只读程序盘。在 `<世界存档>/opencomputers/loot/loot.properties` 中添加：

```properties
myprogram=我的程序盘:2:dyeLime
```

将程序文件放入同目录的 `myprogram/` 文件夹。格式为 `目录=显示名称[:权重[:原染料名称]]`；未指定权重时为 1，未指定颜色时为浅灰色。世界磁盘与原内置盘一起参与内容抽样，并出现在该世界的扳手换盘配方中；同名文件系统优先读取当前世界的文件夹。外置文件系统按真实机器所在存档解析，离开世界后卸载，不污染其他存档或其他模组注册的磁盘。客户端登录时同步物品模板，断线时清除客户端快照。

更新检查在本次进程第一次符合条件的玩家登录时启动：单机玩家与专用服务器 OP 可以收到提示。`updateCheck=false` 时不会发起网络请求，也不会发送更新提示。查询运行于后台，设有连接、读取及响应大小限制，每个进程只检查一次；成功结果在游戏目录 `cache/opencomputers/update-check.json` 缓存 24 小时，过期后使用 GitHub ETag 条件请求。失败不会重试刷请求，也不会给玩家报错。

检查目标仅为 [Kush-ShuL/OpenComputer-Reloaded 的正式发行](https://github.com/Kush-ShuL/OpenComputer-Reloaded/releases)。发行必须不是草稿或预发行，有稳定数字版本标签（例如 `v0.1.0` 或 `1.21.1/v0.1.0`），并有已上传、可下载的完整模组 JAR。`api-*` 标签、API 包、源码包、Javadoc 包及开发包都不会作为模组更新。只有版本确实高于当前安装版本时才提示，点击版本号会打开发行页面。

实现依据为 MightyPirates/OpenComputers 固定提交 `258943363ea8fc4ef14948e6bc6f67375061580c` 的 `Loot.scala`、`UpdateCheck.scala` 与登录事件（MIT 许可证）。该提交中的旧 ChestGenHooks 注入代码已被注释；本移植恢复其中记录的四类箱子及相对权重设计，用 NeoForge 物品池和全局战利品修改器实现。原程序盘资源与各自许可证保持原样。

GitHub 字段和缓存行为依据官方 [Releases REST API](https://docs.github.com/en/rest/releases/releases#list-releases) 与 [REST API 使用建议](https://docs.github.com/en/rest/using-the-rest-api/best-practices-for-using-the-rest-api)。单次检查最多读取最近 500 条发行记录。

Windows 隔离世界验证已对四类箱子分别执行 256 次固定种子生成：保留原版物品、生成完整程序盘、未泄漏内部标记，且未生成零权重盘；其中一张真实生成盘装入机架后成功读取了内置文件。该验证覆盖生成与挂载流程，不代表统计分布、全部外置世界盘生命周期或更新检查联网流程已验收。
