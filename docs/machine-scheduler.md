# 机器调度与资源预算

C07 的语义来源仅为 MightyPirates/OpenComputers 固定提交
`258943363ea8fc4ef14948e6bc6f67375061580c` 的 `server/machine/Machine.scala`、
架构和回调接口、`ThreadPoolFactory` 及其原生 Lua / LuaJ 调用路径。运行实现为
Java，保留上游许可与署名。

原十个状态的顺序保持不变，存档仍使用对应序号。启动检查真实网络、CPU、
组件数、内存和供电；文件系统按四分之一组件计数，并扣除计算机自身。
CPU 与所有 CallBudget 硬件的预算按原算术平均值合成，每个世界刻恢复额度。
direct 回调按注解和动态调用成本扣除；架构尚未初始化及同步调用阶段不扣该额度。
超额调用抛出原 LimitReachedException，由架构切换到同步调用，不进入未获准的
回调体。负成本按零处理。

后台执行沿原配置的线程数量、daemon 线程、线程优先级及 executionDelay 调度。
同步调用在服务器主线程执行，返回后保留 synchronized-return 标志。暂停在当前
工作结束后生效；同步调用中的暂停先经过世界刻等待，再带返回标志继续执行。
睡眠可被已有或新加入的信号唤醒，有限信号队列保持先进先出，队列满时拒绝新增。
软重启保留或清空 tmpfs 由原 eraseTmpOnReboot 配置决定；完全关闭清空临时盘。
重启与停止保留原网络 computer.started / computer.stopped 消息。

普通 ExecutionResult.Error 保留错误消息和原报错蜂鸣；后台或同步架构抛错
转为原内部错误。原生同步路径的 Error("not enough memory") 恢复为内存不足
错误，同时保留现代 JVM OutOfMemoryError 的已有处理。未知执行结果或错误状态
沿原断言边界终止机器，不遗留无法关闭的 Running 状态。

执行、保存与恢复保持 Machine→状态锁的顺序。已有 LuaJ 协程直接回调中的
start/pause 免等待执行器 monitor 路径继续保留；宿主标脏与活动反馈继续通过
服务器刻处理，不恢复此前造成主线程和虚拟机互等的区块等待。
对正在执行的机器调用 start 会立即返回 false；停止后的重启请求及恢复暂停也
沿原状态锁直接提交，不等待尚在运行的架构线程。只有真正初始化新架构时才
进入执行器 monitor。
原生执行时间与内存限制、LuaJ 限制仍分别由原架构实现处理；对应已有 C09/C10
与内存、冷恢复证明复用，不再把所有宿主和架构组合成重复验收矩阵。

本批 SchedulerContract 使用普通外部 Processor、CallBudget 和 Architecture
公开接口，在八台实际世界 Case 上执行状态程序；它不替换 Context、不写机器
私有字段，也不声称是 Lua 解释器测试。有限场景覆盖预算平均和超额、主线程
同步调用、暂停返回、睡眠信号、队列容量、软重启、停止和架构异常。原生 Lua / 
LuaJ 与独立进程持久化继续由既有真实执行场景负责；多人和客户端显示统一归
C19/C20/C23。

2026-10-27 Windows NeoForge 21.1.224 的 `advanced-20261027-e2` 在 30 个世界
刻内通过本批全部场景。八台实际 Case 验证了预算平均、同步/暂停/睡眠/信号、
重启停止、真正执行期间的主线程开关机，以及五类异常的资源关闭和正确错误。
同批复用既有检查点场景，实际 Lua 5.2、Lua 5.3 均在替换宿主后恢复协程、
同步返回、Value、tmpfs 句柄和排队信号；LuaJ 依原语义重启恢复。
其真实 direct 回调还验证了运行中 start 返回 false 及 pause，不以测试架构
替代原生或 LuaJ 执行证明。
报告：`build/api-compat-addon/devices/advanced-20261027-e2/device-results.txt`。
