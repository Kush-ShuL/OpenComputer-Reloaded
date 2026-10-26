# OpenComputer-Reloaded API

目标环境：Minecraft **1.21.1**、NeoForge **21.1.224 或更新的 21.1.x**、Java **21**。
API ABI 版本为 `8.0.0-alpha.1`，沿用 `li.cil.oc.api` 包名。它来自
[MightyPirates/OpenComputers](https://github.com/MightyPirates/OpenComputers/tree/258943363ea8fc4ef14948e6bc6f67375061580c/src/main/java/li/cil/oc/api)，
保留原 MIT 许可证。Minecraft 平台类型、持久化和渲染签名已更新，因此旧 Forge
扩展需要重新编译和适配。API 包可以独立构建；具体功能的运行支持仍取决于主模组对应系统的迁移状态。

## 获取 API

玩家只安装主模组。`-api.jar` 供其他模组编译集成使用，不要放进玩家的 `mods` 文件夹，
不要把 API 打进其他模组，也不要将 API 与主模组作为两个模组一起安装。

在项目根目录执行：

```sh
./gradlew apiJar apiSourcesJar apiJavadocJar
./gradlew publishApiPublicationToLocalDistributionRepository
```

独立 API 的 Maven 坐标：

```groovy
repositories {
    // 本地构建生成的 Maven 仓库；替换成实际路径。
    maven { url = uri('/path/to/OpenComputer-Reloaded/build/maven') }
}
dependencies {
    compileOnly 'io.github.kush-shul:opencomputer-reloaded-api:0.1.0-SNAPSHOT'
}
```

也可从 GitHub Release 下载同版本 `-api.jar` 和 `-api-sources.jar`，
通过 `compileOnly files('libs/实际的-api.jar文件名.jar')` 使用。运行开发实例时仍需加载完整主模组。
使用 GitHub Packages 时，该服务可能要求 GitHub 身份验证；Release 附件和本地 Maven 仓库无需依赖该服务。

## 组件与回调

组件通过 `Network.newNode` 创建节点，用 `@Callback` 暴露 Lua 方法。
`Context` 与 `Arguments`、直接调用预算、主线程同步和可见性规则沿用上游。
操作世界或非线程安全对象的方法使用默认的同步调用，不要随意设置 `direct = true`。

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
