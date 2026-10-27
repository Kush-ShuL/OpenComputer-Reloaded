# 因特网卡

实现仅对照 MightyPirates/OpenComputers 提交
`258943363ea8fc4ef14948e6bc6f67375061580c` 的 `InternetCard`、
`InternetFilteringRule`、物品与驱动源码。保留项目原有许可证和署名。

因特网卡占用二级卡槽，提供 HTTP 请求和 TCP 连接。卡只能由直接安装它的
计算机打开连接；其他计算机即使能看到组件，也不能借用它访问网络。

## 请求和连接

`request(url, postData, headers, method)` 返回 HTTP 请求对象。省略请求体时
默认使用 GET，提供请求体时默认使用 POST，也可指定方法。自定义请求头受
配置控制；默认 User-Agent 会替换当前版本号。

HTTP 对象提供 `finishConnect()`、`response()`、`read(n)` 和 `close()`。
`response()` 返回状态码、说明和响应头。在连接或读取尚未完成时，读取返回
空字符串；结束后返回 nil。HTTP 错误响应仍可查看状态和响应头，读取会报错，
与原版一致。

`connect(address, port)` 返回 TCP 对象，也接受地址中已有的端口。对象提供
`finishConnect()`、`read(n)`、`write(data)`、`id()` 和 `close()`。
写入返回本次实际写入的字节数；读取区分等待时的空字符串和结束时的 nil。
异步连接失败的本次轮询返回 false，之后使用该已关闭对象会报
`connection lost`，保留原版顺序。

TCP 就绪消息为 `internet_ready`，携带该对象的 ID。读取或完成连接后重新
等待下一次可读通知。读写和域名查询不在 Minecraft 主线程阻塞执行；关闭
HTTP 响应也交给后台处理。

## 配置和生命周期

`internet.enableHttp`、`internet.enableTcp`、`internet.enableHttpHeaders`
分别控制对应功能。每张卡的 HTTP 和 TCP 对象共同占用
`internet.maxTcpConnections` 上限，读取大小受文件系统的
`maxReadBuffer` 限制。请求超时和后台线程数量沿现有配置接入。

`internet.filteringRules` 按顺序采用第一条匹配规则，无匹配时拒绝访问。
保留 all、default、private、bogon、IPv4、IPv6、内嵌 IPv4、域名和
IP/CIDR 条件，以及 NAT64 地址检查。域名规则只解析一次；解析安排在后台，
无效规则会禁用访问。公开规则求值与卡的策略使用同一份已解析规则状态。
HTTP 重定向继续逐个检查目标地址。

计算机停止、重新启动、卡被卸下或宿主断开时关闭其连接。卡的节点身份可
随物品保存，已经建立的操作系统 socket 和 HTTP 请求不会随存档恢复为活连接；
恢复出的旧对象保持关闭。

## 验证入口

`InternetCardContract` 使用真实 Case 安装卡，并通过公开组件和 userdata
回调访问同一测试进程内的 HTTP/TCP 回环服务。服务仅在场景存续期间运行，
测试配置只属于隔离世界。验证请求体与请求头、响应状态与二进制数据、TCP
实际写入和就绪消息、拒绝地址、连接上限、非所有者、卸卡关闭和旧对象恢复。
外网服务、HTTPS 证书及远程客户端不包含在该回环场景内。

2026-10-27 Windows NeoForge 21.1.224 的 `advanced-20261027-e2` 已通过上述
实际回环场景，包括混合四连接限额、卸卡关闭、旧 userdata 恢复关闭及重新装卡
后的完整新响应。就绪观察者使用正常公开 SDK 可见组件，接收实际选择器发出的
消息，不手工发信号或手动更新组件。
报告：`build/api-compat-addon/devices/advanced-20261027-e2/device-results.txt`。
HTTP/TCP 开关、回环过滤和限额只写入该隔离测试世界的配置。
