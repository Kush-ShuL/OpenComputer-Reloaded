# OpenComputer-Reloaded API

目标环境：Minecraft **1.21.1**、NeoForge **21.1.224 或更新的 21.1.x**、Java **21**。
当前 API 验证版本为 `8.0.0-alpha.1`，沿用 `li.cil.oc.api` 包名，**尚未承诺稳定 ABI**。它来自
[MightyPirates/OpenComputers](https://github.com/MightyPirates/OpenComputers/tree/258943363ea8fc4ef14948e6bc6f67375061580c/src/main/java/li/cil/oc/api)，
保留原 MIT 许可证。Minecraft 平台类型、持久化和渲染签名已更新，因此旧 Forge
扩展需要重新编译和适配。API 包可以独立构建；接口存在不代表对应设备和游戏功能已完成迁移。

## 获取 API

玩家只安装主模组。API 是其他模组的 `compileOnly` 编译依赖，不要 shade、重定位或将 API
打进扩展模组，也不要单独放进玩家的 `mods` 文件夹。开发运行环境和玩家环境都需要另装
与 API 对应版本的完整主模组；API JAR 不能提供运行时实现。

API 通过 **JitPack** 分发。预览标签 `api-8.0.0-alpha.1` 已发布，
[远端构建已成功](https://jitpack.io/com/github/Kush-ShuL/OpenComputer-Reloaded/api-8.0.0-alpha.1/build.log)，
其 Maven POM 已可获取。它不是稳定 ABI 承诺；实际接入时固定精确标签：

```groovy
repositories {
    maven { url = uri('https://jitpack.io') }
}
dependencies {
    compileOnly 'com.github.Kush-ShuL:OpenComputer-Reloaded:api-8.0.0-alpha.1'
}
```

JitPack 的仓库名 artifact 仅包含 `li.cil.oc.api` SDK，不是完整模组。
仓库根目录的 `jitpack.yml` 使用 Java 21，运行独立 SDK 构建：

```sh
./gradlew -p api publishToMavenLocal --console=plain
```

这条命令只编译 API，不要求未完成的主模组通过构建。独立项目还生成 sources 和 Javadoc，
并在包内保留许可证和 NOTICE。维护者发布 API 时创建 tag 和**无附件的 GitHub Release**；
不向 Release 上传 JAR，也不通过 GitHub Packages 发布。消费者从 JitPack 获取包。

本地开发也可执行上述命令，然后使用本地 Maven 坐标。它与 JitPack 的坐标不同：

```groovy
repositories {
    mavenLocal()
}
dependencies {
    compileOnly 'io.github.kush-shul:opencomputer-reloaded-api:8.0.0-alpha.1'
}
```

`api/` 使用根目录的 `api_version` 作为本地版本；JitPack 使用完整 Git 标签作为依赖版本，
因此不要省略示例标签中的 `api-` 前缀，也不要使用会随分支变化的版本来约定稳定接口。

## 版本与兼容性

`API.ID_OWNER` 为真实模组 ID `opencomputers`，不是仓库名。依赖声明、IMC 和注册表相关
代码应使用这一 ID。Java 会把 `public static final String` 常量内联到调用方；如果扩展
此前针对错误的 `ID_OWNER` 值编译，需要重新编译扩展，只替换 API JAR 不会更新旧字节码。

Minecraft 1.21.1 的平台类型变化意味着旧 Forge 扩展必须重新编译。当前 alpha 版本也可能
继续调整适配接口；在明确宣布稳定前，应固定精确标签，并让扩展注明支持的主模组版本。

`Converter` 保留 `convert(Object, Map<Object, Object>)` 作为唯一抽象方法，所以原有两参数
lambda 和转换器实现仍可编译。新增的三参数 `convert(value, output, registries)` 是 default
方法，默认转调原方法；需要处理 ItemStack data components 的转换器可覆盖该方法，使用
调用机器所在 level 的 `HolderLookup.Provider`。上下文可能为 `null`，需要注册表的转换器
应明确报告缺少上下文，不能查全局服务器或静默丢弃数据。

## 组件与回调

组件通过 `Network.newNode` 创建节点，用 `@Callback` 暴露 Lua 方法。
`Context` 与 `Arguments`、直接调用预算、主线程同步和可见性规则沿用上游。
操作世界或非线程安全对象的方法使用默认的同步调用，不要随意设置 `direct = true`。

组件可能从 Lua 工作线程调用 `EnvironmentHost.markChanged()`。宿主应设置待保存标记，
在自己的服务器 tick 中处理区块更新；不要持有组件锁等待服务器线程，否则会与输入、
网络或保存流程形成循环等待。现有的原子标记模式可参考机箱与外置软盘驱动器。

```java
public final class ExampleEnvironment extends li.cil.oc.api.prefab.AbstractManagedEnvironment {
    public ExampleEnvironment() {
        setNode(li.cil.oc.api.Network.newNode(this,
                li.cil.oc.api.network.Visibility.Network)
                .withComponent("example", li.cil.oc.api.network.Visibility.Network)
                .create());
    }

    @li.cil.oc.api.machine.Callback(doc = "function(name:string):string -- Greet a player.")
    public Object[] greet(li.cil.oc.api.machine.Context context,
                          li.cil.oc.api.machine.Arguments args) {
        return new Object[]{"Hello, " + args.checkString(0)};
    }
}
```

环境由实际主机负责连接、断开和持久化。节点构造器必须在运行时 API 初始化后使用。
在 NeoForge common setup 的 `enqueueWork` 中注册 item/block driver，且不要在静态初始化期间调用工厂。
运行时节点仅用于服务器逻辑；客户端不要创建或连接组件网络。

`SimpleComponent` 保留为简单组件标记；现代端不使用旧 Forge ASM 自动注入，
也不再支持旧 `@Optional` 注解。平台集成需要把标记对象包装成环境。
需要直接管理网络时，实现 `Environment`，或使用 `TileEntityEnvironment` /
`TileEntitySidedEnvironment`。后两者构造器需要真实的 `BlockEntityType`、`BlockPos` 和 `BlockState`；
它们负责现代 BlockEntity 加载、移除、卸载和节点 NBT。分面环境可以在服务器
`BlockEntityTicker` 中调用 `update()`，并在连接状态变化时重新加入网络。

## ItemStack 持久化

1.21 使用不可变 data component。`DriverItem.dataTag(stack)` 返回数据快照，
修改快照后必须显式提交，否则修改不会写回物品：

```java
var tag = driver.dataTag(stack);
if (tag == null) {
    tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
            net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getCompound("oc:data");
}
environment.save(tag, host.world().registryAccess());
driver.saveDataTag(stack, tag);
host.markChanged();
```

默认数据位于 `DataComponents.CUSTOM_DATA` 的 `oc:data` 子项。
自定义 driver 如果使用别的键或 data component，必须同时实现读取和写回。
写回保留物品上其他模组的自定义数据。

`Persistable` 新增 `load(tag, registries)` 和 `save(tag, registries)`。
主机在保存环境、架构和 Lua userdata 时，应传入当前 level 的 `HolderLookup.Provider`。
纯 NBT 实现可继续使用单参数方法；默认的 registry 重载会转调原方法。
`ItemStackArrayValue` 的 registry 重载使用原生 ItemStack codec 保存全部 data components，
并保留 Lua 的 1 起始索引、`n`、迭代位置、`reset`、`count`、`getAll`。
如果使用其单参数保存方法，先通过带 registries 的构造器或加载方法绑定上下文；
它不会通过静态服务器单例查找注册表，也不会在缺少上下文时丢弃物品组件。

## 平台适配

| 旧接口 | 1.21.1 接口 / 使用方式 |
| --- | --- |
| `NBTTagCompound` | `CompoundTag` |
| `EnumFacing` | `Direction` |
| `World` / `IBlockAccess` | `Level` / `BlockGetter` |
| `TileEntity` / `IInventory` | `BlockEntity` / `Container` |
| `EntityPlayer` / `EnumHand` | `Player` / `InteractionHand` |
| `@Cancelable` | `ICancellableEvent`；在 NeoForge 事件总线上监听 |
| 无参数手册图标渲染 | `render(GuiGraphics)` |
| 手册图片渲染 | `render(GuiGraphics, mouseX, mouseY)` |
| GPU 文本 / 机器人升级渲染 | 显式传入 `PoseStack`、`MultiBufferSource` 与 light/overlay |
| 机架动态叠加层 | 事件提供渲染上下文；`renderOverlay` / `renderOverlayFromAtlas` 输出标准顶点 |
| 整数维度 ID | 无线网络离开接口使用 `ResourceKey<Level>` |
| 方块 metadata 匹配 | 独立 block 或 `BlockState` 属性；覆盖 `worksWith(BlockState)` 细分 |
| 直接修改物品 NBT | `dataTag` 后调用 `saveDataTag` |
| `IMC.blacklistHost(name, host, stack)` | 增加 `HolderLookup.Provider` 参数以编码 ItemStack |

机架叠加层可使用带 light 参数的 `renderOverlay`，为 LED 等选择 `LightTexture.FULL_BRIGHT`。
渲染接口只在客户端使用，调用方应按 NeoForge 物理端隔离相关代码。

## API 范围

原 API 的 `component`、`detail`、`driver`（含 item 扩展）、`event`、`fs`、`internal`、
`machine`、`manual`、`nanomachines`、`network`、`prefab`、`util` 全部保留。
`detail` 是主模组注入的实现桥，常规集成优先使用 `Driver`、`Network`、`FileSystem`、
`Machine`、`Items`、`Manual`、`Nanomachines` 等门面。不要在自己的模组中改写 `API` 静态实现。

手册扩展只在物理客户端注册。客户端的 `Manual` 后端在主模组构造阶段初始化，扩展可在
setup 阶段注册 `PathProvider`、`ContentProvider`、图片提供者与标签；专用服务器的
`API.manual` 按原约定保持 `null`。手册图片和标签渲染器使用 `GuiGraphics`，应放在客户端
隔离的类中。不要依赖主模组的 `li.cil.oc.client` 或 `li.cil.oc.server` 实现类。

独立 SDK 构建和 API 兼容性用例用于检查集成契约；它们不证明完整模组、所有宿主设备或
全部可选集成已经可用。游戏功能的迁移状态见 [移植说明](PORTING.md)。

当前版本已由独立编译的扩展在 Windows 专服运行验证：旧两参数 Converter 二进制、
注册表上下文、网络与 prefab 生命周期、外部 SimpleComponent、原生 Lua 5.3 / LuaJ 的
组件和 Value 回调，以及 IMC 程序磁盘映射。测试源见 `tools/api-compat-addon`。

后续的运行时回归也已验证：替换整台宿主后，原生 Lua 5.2、5.3 恢复协程局部变量、
同步回调返回栈、含完整 ItemStack 的 Value、临时文件句柄和待处理信号。
LuaJ 按上游约定在恢复时重启，不保存协程。LuaJ 直接回调中的 `Context.pause()` 和
`Context.start()` 已修正线程锁边界；宿主也保留第三方 driver 返回 `null` 数据标签时
回退到默认 `oc:data` 的行为。这些运行时修复不改变已发布 SDK 的公共签名。

组装、拆解 IMC 也已在真实设备上验证：外部模板的输入校验、分阶段能量消耗与产出，
以及拆解模板的保证掉落和相邻箱子输出。组装器会消费容器内容；模板回调应只返回结果，
不要自行扣除输入。包含库存的内置设备拆解使用设备所在世界的显式注册表上下文。
