# OpenComputer-Reloaded

使用 **Java 21** 将 OpenComputers 的可编程电脑和机器人带到 **Minecraft Java 1.21.1 · NeoForge**。

**目前正在移植，尚无可游玩的 1.21.1 模组正式版。** 当前源码、已转换资源和 API 包不代表所有游戏功能已经可用。请等待 Releases 中明确标注的模组版本。

## 在 Minecraft 里编程

这次移植的目标是保留原版 OpenComputers 的完整玩法：

- 从机箱、CPU、内存、硬盘、显卡、屏幕和键盘组装自己的电脑，用 Lua 和 OpenOS 编写程序。
- 制作机器人与无人机，让程序参与建造、搬运、探索和自动化。
- 用有线网络、无线网卡、中继器、服务器机架和网络组件连接设备。
- 使用红石、转运器、地质分析仪、全息投影、3D 打印机、平板电脑和纳米机器等原有设备。

不同等级的硬件拥有不同的性能、插槽和能耗。原有 Lua 程序、系统磁盘与游戏内手册会随移植保留。

## 安装与下载

目标环境为 Minecraft **1.21.1**、**NeoForge 21.1.224 或更新的 21.1.x**、**Java 21**。客户端与服务器需要安装相同版本。

可游玩版本发布后，从 [Releases](https://github.com/Kush-ShuL/OpenComputer-Reloaded/releases) 中明确标为模组版本的发布页下载主 JAR，放入实例的 `mods` 文件夹。API 发布页供模组开发者使用，不提供游戏模组下载；API、sources 和 Javadoc 包无需放入玩家的 `mods` 文件夹。

旧版 Minecraft 存档和已安装的电脑不能直接视为兼容。迁移支持确认前，请保留原存档备份。

## 入门

可游玩版发布后，建议从一级机箱、CPU、内存、显卡、屏幕、键盘和硬盘开始，搭配 EEPROM 与 OpenOS 安装软盘完成系统安装，再使用游戏内手册了解设备与 Lua API。

开发测试版的手册可用一本书和一级芯片无序合成，或用 `/give @s opencomputers:manual` 获取。拿着手册右键阅读；蹲下右键重置阅读位置，对有文档的方块右键可打开对应页面。

配置位置和常用开关见 [配置说明](docs/CONFIGURATION.md)，CC:Tweaked 兼容需要手动启用。当前迁移进展与已知缺口见 [移植说明](docs/PORTING.md)。遇到问题可提交 [Issue](https://github.com/Kush-ShuL/OpenComputer-Reloaded/issues)，附上模组版本、游戏日志和复现步骤。

移植范围已锁定为 [120 项固定清单](docs/MIGRATION-CHECKLIST.md)，其中逐项列出完成条件、当前状态和剩余工作；已有代码但尚未完成核对或验收的项目仍计入剩余。

开发测试版的程序盘箱子掉落、存档自定义程序盘和更新提示，见 [程序盘与更新说明](docs/loot-and-updates.md)。

## 致谢与许可

本项目仅以 [MightyPirates/OpenComputers](https://github.com/MightyPirates/OpenComputers) 原仓库为源码基础，是独立的社区移植。感谢 MightyPirates 与所有原版贡献者。

代码遵循原版 [MIT 许可证](LICENSE)。字体、Lua 库及其他第三方内容保留各自许可证与署名，详见 [NOTICE](NOTICE)。整合包作者可按这些许可分发已发布版本。

需要与电脑和组件集成的模组作者，请阅读 [API 接入文档](docs/API.md)。
