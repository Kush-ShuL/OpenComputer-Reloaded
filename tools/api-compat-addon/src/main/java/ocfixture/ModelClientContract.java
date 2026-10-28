package ocfixture;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import li.cil.oc.api.CreativeTab;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.component.RackMountable;
import li.cil.oc.api.driver.item.MutableProcessor;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.internal.Drone;
import li.cil.oc.api.internal.Rack;
import li.cil.oc.api.internal.Robot;
import li.cil.oc.api.internal.Tablet;
import li.cil.oc.api.machine.Architecture;
import li.cil.oc.api.machine.Machine;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.SidedEnvironment;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Real native world, entity, GUI and GROUND frames; visual acceptance uses the saved PNGs. */
@OnlyIn(Dist.CLIENT)
public final class ModelClientContract {
    private static final String[] BLOCKS = {
        "adapter", "assembler", "cable", "capacitor", "case1", "case2", "case3", "casecreative", "chameliumblock",
        "charger", "disassembler", "diskdrive", "geolyzer", "hologram1", "hologram2", "keyboard", "motionsensor", "powerconverter",
        "powerdistributor", "printer", "raid", "redstone", "relay", "screen1", "screen2", "screen3", "rack",
        "waypoint", "microcontroller", "print", "robotafterimage", "robot", "endstone", "netsplitter", "transposer", "carpetedcapacitor"
    };
    private static final byte[] BIOS = "local c=computer; while true do c.pullSignal(1) end".getBytes(StandardCharsets.UTF_8);
    private static Run active;
    private static boolean events;
    private ModelClientContract() {}

    public static void start(Minecraft client, Path directory, List<String> results) {
        require(active == null, "Model contract already active");
        require(client.level != null && client.player != null && client.hasSingleplayerServer(), "Models require the owned integrated test world");
        if (!events) {
            events = true;
            NeoForge.EVENT_BUS.addListener(ModelClientContract::serverTick);
            NeoForge.EVENT_BUS.addListener(ModelClientContract::worldFrame);
            NeoForge.EVENT_BUS.addListener(ModelClientContract::afterFrame);
        }
        Run run = new Run(client, directory, results); active = run;
        try { run.start(); } catch (Throwable error) { run.fail(error); }
    }
    public static boolean isRunning() { return active != null; }
    public static void tick() {
        Run run = active; if (run == null) return;
        try { run.tick(); } catch (Throwable error) { run.fail(error); }
        if (run.finishing && run.cleanup != null && run.cleanup.isDone()) {
            try { run.cleanup.join(); } catch (Throwable error) { run.results.add("FAIL C20.models cleanup " + error); }
            active = null;
        }
    }
    public static void close() { Run run = active; if (run != null && !run.finishing) run.fail(new AssertionError("Model contract closed before completion")); }
    private static void serverTick(ServerTickEvent.Post event) {
        Run run = active; if (run == null || run.finishing || event.getServer() != run.server) return;
        try { run.serverTick(); } catch (Throwable error) { run.serverError = error; }
    }
    private static void worldFrame(RenderLevelStageEvent event) {
        Run run = active;
        if (run != null && !run.finishing && event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) run.worldFrames++;
    }
    private static void afterFrame(RenderFrameEvent.Post event) {
        Run run = active;
        if (run == null || run.finishing || run.shot == null || run.worldFrames < run.shotAfter + 12) return;
        try {
            if (!run.cameraReady()) return;
            if (run.shot.startsWith("models-world-") && !run.galleryReady()) return;
            if (run.shotTarget != null && !run.client.levelRenderer.isSectionCompiled(run.shotTarget)) return;
            if (run.shot.contains("error-blink-") && !run.errorBlinkReady()) return;
            if (run.sheet != null && (run.client.screen != run.sheet || run.sheet.renders < 3)) return;
            try (NativeImage pixels = Screenshot.takeScreenshot(run.client.getMainRenderTarget())) {
                Path path = run.directory.resolve(run.shot); pixels.writeToFile(path);
                run.results.add("SCREENSHOT C20 " + path + " actual=" + pixels.getWidth() + "x" + pixels.getHeight() + " " + run.caption);
                run.shot = null; run.shotDone = true;
            }
        } catch (Throwable error) { run.fail(error); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static Object invoke(Object target, String name, Class<?>[] types, Object... arguments) throws Exception { return target.getClass().getMethod(name, types).invoke(target, arguments); }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("opencomputers", path); }
    private record Sample(String name, BlockPos position, ItemStack item) {}
    private record Icon(String name, ItemStack item) {}
    @FunctionalInterface private interface Task { void run() throws Exception; }

    private static final class Run {
        final Minecraft client; final MinecraftServer server; final ServerLevel level; final Path directory; final List<String> results;
        final BlockPos base; final Vec3 oldPosition; final float oldYaw, oldPitch; final CameraType oldCamera; final boolean oldHideGui;
        final Map<BlockPos, BlockState> original = new LinkedHashMap<>(); final Set<Long> forced = new LinkedHashSet<>();
        final Map<String, Sample> samples = new LinkedHashMap<>(); final List<Icon> icons = new ArrayList<>(); final List<Entity> entities = new ArrayList<>();
        final List<Machine> computers = new ArrayList<>(); final long began = System.nanoTime();
        CompletableFuture<Void> task, cleanup, cameraUpdate; volatile Throwable serverError; volatile boolean ready, energized; volatile long serverTicks;
        Class<? extends Architecture> architecture; Case host; Robot robot; Drone drone; Rack rack; Tablet tablet; ItemStack savedHand, savedBoots, boundTerminal;
        int phase, page, worldFrames, shotAfter, framesAtAim; String shot, caption = ""; boolean shotDone, finishing; volatile boolean activeTour; ItemSheet sheet; BlockPos movedRobot;
        Vec3 cameraEye; boolean oldMayfly, oldFlying, oldNoGravity, oldOnGround, playerCaptured;
        final boolean oldClientNoGravity;
        final int oldFrameLimit;
        final boolean supplementOnly = "supplement".equals(System.getProperty("ocfixture.modelFocus", "all"));
        BlockPos shotTarget;
        int galleryGroup;
        Run(Minecraft client, Path directory, List<String> results) {
            this.client = client; server = client.getSingleplayerServer(); level = server.getLevel(client.level.dimension()); this.directory = directory; this.results = results;
            base = client.player.blockPosition().east(20).above(8); oldPosition = client.player.position(); oldYaw = client.player.getYRot(); oldPitch = client.player.getXRot();
            oldCamera = client.options.getCameraType(); oldHideGui = client.options.hideGui;
            oldClientNoGravity = client.player.isNoGravity();
            oldFrameLimit = client.getWindow().getFramerateLimit();
        }
        ItemStack item(String name) {
            var descriptor = li.cil.oc.api.Items.get(name); require(descriptor != null, "Missing real model material " + name); return descriptor.createItemStack(1);
        }
        ServerPlayer player() { return java.util.Objects.requireNonNull(server.getPlayerList().getPlayer(client.player.getUUID())); }
        void task(Task action) { task = CompletableFuture.runAsync(() -> { try { action.run(); } catch (Exception error) { throw new IllegalStateException(error); } }, server); }
        boolean complete() { if (task == null || !task.isDone()) return false; task.join(); return true; }
        void remember(BlockPos pos) {
            if (original.containsKey(pos)) return;
            if (level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true)) forced.add(ChunkPos.asLong(pos));
            level.getChunkAt(pos); require(level.isEmptyBlock(pos) && level.getBlockEntity(pos) == null, "Model fixture refuses occupied cell " + pos);
            original.put(pos.immutable(), level.getBlockState(pos));
        }
        void block(BlockPos pos, BlockState state) { remember(pos); require(level.setBlock(pos, state, 3), "Actual gallery placement failed " + pos); }
        void place(BlockPos pos, ItemStack stack) {
            require(stack.getItem() instanceof BlockItem, "Gallery sample is not a real BlockItem " + stack);
            remember(pos); if (level.isEmptyBlock(pos.below())) block(pos.below(), Blocks.SMOOTH_STONE.defaultBlockState());
            ServerPlayer player = player(); ItemStack held = player.getMainHandItem();
            try {
                player.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
                ItemStack actual = player.getMainHandItem();
                require(((BlockItem) actual.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, actual,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false))).consumesAction(), "Native BlockItem failed " + stack);
            } finally { player.setItemInHand(InteractionHand.MAIN_HAND, held); }
        }
        void drop(BlockPos pos, ItemStack stack) {
            if (stack.isEmpty()) return;
            ItemEntity entity = new ItemEntity(level, pos.getX() + 1.25, pos.getY() + .35, pos.getZ() + 1, stack.copyWithCount(1));
            entity.setNoGravity(true); entity.setDeltaMovement(Vec3.ZERO); entity.setNeverPickUp(); entity.setUnlimitedLifetime();
            require(level.addFreshEntity(entity), "Actual dropped item was not added " + stack); entities.add(entity);
        }
        ItemStack preset(String name) {
            for (ItemStack stack : CreativeTab.instance.getDisplayItems()) if (stack.is(item(name).getItem()) && tag(stack).getInt("oc:tier") == 3) return nativeHardware(stack.copy());
            throw new AssertionError("Actual creative configured preset missing " + name);
        }
        CompoundTag tag(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); }
        ItemStack eeprom() { return li.cil.oc.api.Items.registerEEPROM("Model gallery native BIOS", BIOS, new byte[0], false); }
        ItemStack nativeHardware(ItemStack stack) {
            CompoundTag data = tag(stack);
            for (String key : new String[]{"oc:components", "oc:items"}) if (data.contains(key, Tag.TAG_LIST)) {
                ListTag list = new ListTag(); boolean flashed = false;
                for (Tag raw : data.getList(key, Tag.TAG_COMPOUND)) {
                    CompoundTag entry = ((CompoundTag) raw).copy();
                    ItemStack part = ItemStack.parseOptional(level.registryAccess(), key.equals("oc:items") ? entry.getCompound("item") : entry);
                    var driver = Driver.driverFor(part);
                    if (stack.is(item("robot").getItem()) && part.is(item("hoverupgrade2").getItem())) { part = item("generatorupgrade"); driver = Driver.driverFor(part); }
                    if (driver instanceof MutableProcessor processor) processor.setArchitecture(part, architecture);
                    if (driver != null && "eeprom".equals(driver.slot(part))) { part = eeprom(); flashed = true; }
                    if (key.equals("oc:items")) { entry.put("item", part.saveOptional(level.registryAccess())); list.add(entry); }
                    else list.add(part.saveOptional(level.registryAccess()));
                }
                if (key.equals("oc:components") && !flashed) list.add(eeprom().saveOptional(level.registryAccess()));
                data.put(key, list);
            }
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data)); return stack;
        }
        ItemStack print() {
            ItemStack result = item("print"); CompoundTag data = tag(result);
            ListTag off = new ListTag(), on = new ListTag();
            off.add(shape(new byte[]{1, 0, 1, 15, 3, 15}, "minecraft:block/stone", 0xb0d9ff));
            off.add(shape(new byte[]{2, 3, 2, 5, 14, 5}, "minecraft:block/gold_block", 0xffffff));
            on.add(shape(new byte[]{1, 0, 1, 15, 3, 15}, "minecraft:block/stone", 0xb0d9ff));
            on.add(shape(new byte[]{5, 3, 5, 14, 12, 14}, "minecraft:block/redstone_block", 0xffffff));
            data.put("stateOff", off); data.put("stateOn", on); data.putString("label", "OC original two-state print");
            result.set(DataComponents.CUSTOM_DATA, CustomData.of(data)); return result;
        }
        CompoundTag shape(byte[] bounds, String texture, int tint) { CompoundTag data = new CompoundTag(); data.putByteArray("bounds", bounds); data.putString("texture", texture); data.putInt("tint", tint); return data; }
        void install(Container inventory, ItemStack stack) {
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) if (inventory.getItem(slot).isEmpty() && inventory.canPlaceItem(slot, stack)) { inventory.setItem(slot, stack); return; }
            throw new AssertionError("Actual gallery host rejected " + stack);
        }
        void start() {
            client.setScreen(null); client.options.setCameraType(CameraType.FIRST_PERSON); client.options.hideGui = false;
            client.getWindow().setFramerateLimit(12);
            results.add("C20.models.capture actual model test window capped at 12 FPS; original limit restored on finish; this scenario checks visuals rather than gameplay performance");
            results.add("C20.models.focus " + (supplementOnly ? "supplement: ten native frames for the remaining visual states" : "all"));
            client.player.setNoGravity(true);
            task(() -> {
                savedHand = player().getMainHandItem().copy(); savedBoots = player().getItemBySlot(EquipmentSlot.FEET).copy();
                oldMayfly = player().getAbilities().mayfly; oldFlying = player().getAbilities().flying;
                oldNoGravity = player().isNoGravity(); oldOnGround = player().onGround(); playerCaptured = true;
                player().setNoGravity(true); player().setOnGround(false); player().setDeltaMovement(Vec3.ZERO);
                player().getAbilities().mayfly = true; player().getAbilities().flying = true; player().onUpdateAbilities();
                architecture = li.cil.oc.api.Machine.architectures().stream().filter(type -> "Lua 5.3".equals(li.cil.oc.api.Machine.getArchitectureName(type))).findFirst().orElseThrow();
                CreativeTab.instance.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess()));
                for (int index = 0; index < BLOCKS.length; index++) {
                    String name = BLOCKS[index]; BlockPos pos = base.offset((index % 3) * 4, 0, (index / 3) * 6);
                    if (name.equals("robotafterimage")) { remember(pos); samples.put(name, new Sample(name, pos, ItemStack.EMPTY)); continue; }
                    ItemStack stack = switch (name) { case "robot", "microcontroller" -> preset(name); case "print" -> print(); default -> item(name); };
                    place(pos, stack); samples.put(name, new Sample(name, pos, stack)); drop(pos, stack);
                }
                for (var entry : BuiltInRegistries.ITEM.entrySet()) if (entry.getKey().location().getNamespace().equals("opencomputers")) icons.add(new Icon(entry.getKey().location().getPath(), new ItemStack(entry.getValue())));
                for (ItemStack stack : CreativeTab.instance.getDisplayItems()) if (!stack.isEmpty() && !tag(stack).isEmpty()) icons.add(new Icon("preset " + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), stack.copy()));
                icons.add(new Icon("configured Print", print()));
                for (int color = 0; color < 16; color++) { ItemStack disk = item("floppy"); CompoundTag data = tag(disk); data.putInt("oc:color", color); disk.set(DataComponents.CUSTOM_DATA, CustomData.of(data)); icons.add(new Icon("floppy color " + color, disk)); }
                host = (Case) level.getBlockEntity(samples.get("casecreative").position);
                ItemStack cpu = item("cpu3"); ((MutableProcessor) Driver.driverFor(cpu)).setArchitecture(cpu, architecture);
                for (ItemStack part : List.of(cpu, item("ram6"), eeprom(), item("hdd1"), item("wlancard2"))) install(host, part);
                robot = (Robot) level.getBlockEntity(samples.get("robot").position);
                require(robot.equipmentInventory().canPlaceItem(0, new ItemStack(Items.IRON_PICKAXE)), "Actual Robot rejected the vanilla tool slot");
                robot.equipmentInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
                rack = (Rack) level.getBlockEntity(samples.get("rack").position);
                rack.setItem(0, item("servercreative")); rack.setItem(1, item("terminalserver")); rack.setItem(2, item("diskdrivemountable"));
                ((Container) level.getBlockEntity(samples.get("diskdrive").position)).setItem(0, item("floppy"));
                Container raid = (Container) level.getBlockEntity(samples.get("raid").position); for (int slot = 0; slot < 3; slot++) raid.setItem(slot, item("hdd1"));
                BlockPos dronePos = base.east(16); block(dronePos.below(), Blocks.SMOOTH_STONE.defaultBlockState());
                ItemStack droneItem = preset("drone"); player().setItemInHand(InteractionHand.MAIN_HAND, droneItem);
                require(droneItem.useOn(new UseOnContext(player(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(dronePos), Direction.UP, dronePos, false))).consumesAction(), "Actual Drone placement failed");
                for (Entity entity : level.getEntities().getAll()) if (entity instanceof Drone value && entity.position().distanceToSqr(Vec3.atCenterOf(dronePos)) < 9) { drone = value; entities.add(entity); break; }
                require(drone != null, "Real configured Drone entity missing");
                player().setItemInHand(InteractionHand.MAIN_HAND, savedHand); ready = true;
            });
        }
        Node node(Object device) {
            if (device instanceof Environment environment && environment.node() != null) return environment.node();
            if (device instanceof SidedEnvironment environment) for (Direction side : Direction.values()) { Node node = environment.sidedNode(side); if (node != null) return node; }
            return null;
        }
        Component component(String name) {
            Node node = node(level.getBlockEntity(samples.get(name).position)); require(node instanceof Component, "Actual component missing " + name); return (Component) node;
        }
        void serverTick() throws Exception {
            serverTicks++;
            if (!ready || task != null && !task.isDone()) return;
            for (Sample sample : samples.values()) {
                if (!activeTour && (sample.name.equals("assembler") || sample.name.equals("disassembler"))) continue;
                Node node = node(level.getBlockEntity(sample.position)); if (node instanceof Connector connector) connector.changeBuffer(Double.POSITIVE_INFINITY);
            }
            if (!energized) {
                if (host.machine().node().network() == null || robot.machine().node().network() == null || rack.getMountable(1) == null) return;
                require(host.machine().node() instanceof Connector && robot.machine().node() instanceof Connector, "Actual model hosts lack their real energy connector");
                ((Connector) host.machine().node()).changeBuffer(Double.POSITIVE_INFINITY); ((Connector) robot.machine().node()).changeBuffer(Double.POSITIVE_INFINITY);
                computers.add(host.machine()); computers.add(robot.machine());
                for (int slot = 0; slot < 4; slot++) if (rack.getMountable(slot) instanceof li.cil.oc.api.internal.Server blade) {
                    ItemStack cpu = item("cpu1"); ((MutableProcessor) Driver.driverFor(cpu)).setArchitecture(cpu, architecture);
                    for (ItemStack part : List.of(cpu, item("ram1"), eeprom())) install((Container) blade, part);
                    computers.add(blade.machine());
                }
                boundTerminal = item("terminal"); RackMountable terminal = rack.getMountable(1);
                require(terminal.onActivate(player(), InteractionHand.MAIN_HAND, boundTerminal, .5f, .5f), "Actual TerminalServer rejected binding");
                require(!tag(boundTerminal).getString("oc:server").isEmpty(), "Actual bound terminal has no server address");
                icons.add(new Icon("terminal real bound", boundTerminal.copy())); energized = true;
            }
            for (Machine machine : computers) if (machine.node() instanceof Connector connector) connector.changeBuffer(Double.POSITIVE_INFINITY);
            if (activeTour) feedWorkstations();
            if (activeTour && serverTicks % 5 == 0 && host.machine().isRunning()) {
                for (Node node : host.machine().node().network().nodes()) if (node instanceof Component file && file.name().equals("filesystem") && host.componentSlot(node.address()) >= 0) file.invoke("exists", host.machine(), "/model-gallery");
                for (String name : List.of("diskdrive", "raid")) {
                    Object device = level.getBlockEntity(samples.get(name).position);
                    if (name.equals("diskdrive") && ((Container) device).isEmpty()) continue;
                    Node node = (Node) invoke(device, "filesystemNode", new Class<?>[0]);
                    require(node instanceof Component, "Actual model filesystem missing " + name); ((Component) node).invoke("exists", host.machine(), "/model-gallery");
                }
                installed(host.machine(), "modem").invoke("broadcast", host.machine(), 41720, "actual gallery activity");
                // The original Transposer flashes for a powered attempted transfer, including no-inventory results.
                component("transposer").invoke("transferItem", host.machine(), 0, 1, 1);
            }
        }
        Component installed(Machine machine, String name) {
            for (Node node : machine.node().network().nodes()) if (node instanceof Component component && component.name().equals(name)) return component;
            throw new AssertionError("Actual mounted component missing " + name);
        }
        void feedWorkstations() throws Exception {
            Object assembler = level.getBlockEntity(samples.get("assembler").position);
            if (!Boolean.TRUE.equals(invoke(assembler, "busy", new Class<?>[0]))) {
                Container inventory = (Container) assembler;
                inventory.setItem(0, item("case1")); inventory.setItem(16, item("cpu1")); inventory.setItem(17, item("ram1"));
                require(Boolean.TRUE.equals(invoke(assembler, "start", new Class<?>[]{boolean.class}, false)), "Actual repeated gallery assembly did not start");
            }
            Object disassembler = level.getBlockEntity(samples.get("disassembler").position);
            if (!Boolean.TRUE.equals(invoke(disassembler, "busy", new Class<?>[0])) && ((Container) disassembler).isEmpty())
                ((Container) disassembler).setItem(0, item("graphicscard3"));
        }
        void activate() throws Exception {
            for (Machine machine : computers) require(machine.start(), "Actual model computer refused native BIOS " + machine.lastError());
            for (Direction side : Direction.values()) {
                invoke(level.getBlockEntity(samples.get("adapter").position), "setSideOpen", new Class<?>[]{Direction.class, boolean.class}, side, side.getAxis() != Direction.Axis.X);
                component("netsplitter").invoke("open", host.machine(), side.ordinal());
            }
            Object assembler = level.getBlockEntity(samples.get("assembler").position); Container assembly = (Container) assembler;
            assembly.setItem(0, item("case1")); assembly.setItem(16, item("cpu1")); assembly.setItem(17, item("ram1"));
            require(Boolean.TRUE.equals(invoke(assembler, "start", new Class<?>[]{boolean.class}, false)), "Actual gallery assembly did not start");
            Container disassembler = (Container) level.getBlockEntity(samples.get("disassembler").position); disassembler.setItem(0, item("graphicscard3"));
            block(samples.get("charger").position.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            Node relayPlug = ((SidedEnvironment) level.getBlockEntity(samples.get("relay").position)).sidedNode(Direction.DOWN);
            require(relayPlug != null, "Actual relay plug is absent"); host.machine().node().connect(relayPlug);
            Component printer = component("printer");
            printer.invoke("addShape", host.machine(), 1, 0, 1, 15, 3, 15, "minecraft:block/stone", false, 0x88ccff);
            printer.invoke("addShape", host.machine(), 5, 3, 5, 11, 14, 11, "minecraft:block/gold_block", false);
            for (String name : List.of("hologram1", "hologram2")) {
                Component hologram = component(name); hologram.invoke("clear", host.machine());
                hologram.invoke("fill", host.machine(), 20, 20, 1, 14, 1); hologram.invoke("fill", host.machine(), 28, 20, 1, 14, 1);
                hologram.invoke("fill", host.machine(), 24, 20, 8, 9, 1); hologram.invoke("setScale", host.machine(), .5);
                hologram.invoke("setPaletteColor", host.machine(), 1, name.equals("hologram1") ? 0x55ddff : 0xbb88ff);
                if (name.equals("hologram2")) { hologram.invoke("setRotation", host.machine(), 20, 0, 1, 0); hologram.invoke("setRotationSpeed", host.machine(), 12, 0, 1, 0); }
            }
            invoke(level.getBlockEntity(samples.get("print").position), "toggle", new Class<?>[0]);
            // Actual adjacent blocks select cable arms and the multidirectional baked model.
            BlockPos cable = samples.get("cable").position;
            for (Direction side : List.of(Direction.WEST, Direction.EAST, Direction.NORTH, Direction.UP)) place(cable.relative(side), item("cable"));
            ServerPlayer actor = player(); ItemStack hand = actor.getMainHandItem();
            try {
                ItemStack dye = new ItemStack(Items.LIME_DYE); actor.setItemInHand(InteractionHand.MAIN_HAND, dye);
                require(level.getBlockState(cable).useItemOn(dye, level, actor, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(cable), Direction.SOUTH, cable, false)).consumesAction(), "Actual lime dye did not color the real Cable");
            } finally { actor.setItemInHand(InteractionHand.MAIN_HAND, hand); }
            BlockPos chamelium = samples.get("chameliumblock").position;
            var colorProperty = level.getBlockState(chamelium).getProperties().stream().filter(property -> property.getName().equals("color")).findFirst().orElseThrow();
            @SuppressWarnings("unchecked") var dyeProperty = (net.minecraft.world.level.block.state.properties.Property<DyeColor>) colorProperty;
            require(level.setBlock(chamelium, level.getBlockState(chamelium).setValue(dyeProperty, DyeColor.CYAN), 3), "Actual chamelium color state did not change");
            ItemStack coloredBlock = item("chameliumblock"); coloredBlock.set(DataComponents.BLOCK_STATE, new BlockItemStateProperties(Map.of("color", "cyan"))); icons.add(new Icon("chamelium cyan", coloredBlock));
            // Real single, horizontal, vertical and 3x3 merged shells exercise corners, edges and center.
            BlockPos screen = samples.get("screen3").position;
            for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) if (x != 0 || y != 0) place(screen.offset(x, y, 0), item("screen3"));
            for (int x = 1; x < 3; x++) place(samples.get("screen2").position.east(x), item("screen2"));
            for (int y = 1; y < 3; y++) place(samples.get("screen1").position.above(y), item("screen1"));
            Object micro = level.getBlockEntity(samples.get("microcontroller").position);
            Machine microMachine = (Machine) invoke(micro, "machine", new Class<?>[0]);
            if (microMachine.node() instanceof Connector connector) connector.changeBuffer(Double.POSITIVE_INFINITY);
            require(microMachine.start(), "Actual configured microcontroller did not start"); computers.add(microMachine);
            Machine droneMachine = drone.machine(); if (droneMachine.node() instanceof Connector connector) connector.changeBuffer(Double.POSITIVE_INFINITY);
            require(droneMachine.start(), "Actual configured drone did not start"); computers.add(droneMachine);
        }
        void aim(Vec3 center, Vec3 offset) {
            client.options.setCameraType(CameraType.FIRST_PERSON); Vec3 eye = center.add(offset), delta = center.subtract(eye);
            float yaw = (float) Math.toDegrees(-Math.atan2(delta.x, delta.z)); float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
            cameraEye = eye; client.player.setNoGravity(true); client.player.setDeltaMovement(Vec3.ZERO);
            cameraUpdate = CompletableFuture.runAsync(() -> {
                ServerPlayer actor = player(); actor.setNoGravity(true); actor.setOnGround(false); actor.setDeltaMovement(Vec3.ZERO);
                actor.getAbilities().mayfly = true; actor.getAbilities().flying = true; actor.onUpdateAbilities();
                actor.connection.teleport(eye.x, eye.y - actor.getEyeHeight(), eye.z, yaw, pitch);
            }, server);
            framesAtAim = worldFrames;
        }
        boolean cameraReady() {
            if (cameraUpdate == null || !cameraUpdate.isDone()) return cameraUpdate == null;
            cameraUpdate.join();
            return client.player != null && client.player.getEyePosition().distanceToSqr(cameraEye) < .04
                    && client.gameRenderer.getMainCamera().getPosition().distanceToSqr(cameraEye) < .04;
        }
        void screenshot(String name, String text) { shot = name; caption = text; shotDone = false; shotAfter = worldFrames; }
        void gallery(int index, boolean rear) {
            shotTarget = null;
            galleryGroup = index;
            int first = index * 3; Sample middle = samples.get(BLOCKS[first + 1]);
            aim(Vec3.atCenterOf(middle.position).add(0, .15, 0), rear ? new Vec3(-1.4, activeTour ? 4.2 : 1.8, -8.5) : new Vec3(1.4, activeTour ? 4.5 : 2.6, 8.5));
            caption = (activeTour ? "active " : "default ") + String.join(", ", BLOCKS[first], BLOCKS[first + 1], BLOCKS[first + 2]) + (rear ? " rear/top" : " front/top") + "; nearby ItemEntity are real drops";
            client.setScreen(new Caption(this));
            screenshot(String.format("models-world-%s-%02d-%s.png", activeTour ? "active" : "default", index + 1, rear ? "rear" : "front"), caption);
        }
        boolean galleryReady() {
            for (int index = galleryGroup * 3; index < galleryGroup * 3 + 3; index++) {
                Sample sample = samples.get(BLOCKS[index]);
                if (sample.name.equals("robotafterimage")) continue;
                BlockState state = client.level.getBlockState(sample.position);
                if (state.isAir() || !client.levelRenderer.isSectionCompiled(sample.position)) return false;
                BakedModel model = client.getBlockRenderer().getBlockModel(state);
                require(model != client.getModelManager().getMissingModel(), "Real native missing baked block model " + sample.name);
                if (!sample.name.equals("robot") && !sample.name.equals("print")) require(!model.getParticleIcon().contents().name().equals(MissingTextureAtlasSprite.getLocation()), "Real native missing block particle sprite " + sample.name);
                if (activeTour && (sample.name.equals("assembler") || sample.name.equals("disassembler"))) {
                    Object device = client.level.getBlockEntity(sample.position);
                    try { if (device == null || !Boolean.TRUE.equals(invoke(device, "active", new Class<?>[0]))) return false; }
                    catch (Exception error) { throw new IllegalStateException("Observing actual workstation activity", error); }
                }
            }
            return true;
        }
        void closeup(String sample, Vec3 offset, String image, String text) {
            shotTarget = samples.get(sample).position;
            aim(Vec3.atCenterOf(shotTarget), offset);
            client.setScreen(new Caption(this)); screenshot(image, text);
        }
        void prepareSupplement() throws Exception {
            activate();
            for (String name : List.of("hologram1", "hologram2")) {
                // The original projector only connects underneath and owns no energy capacity.
                // A real adjacent Capacitor supplies storage; filling an isolated projector cannot power it.
                block(samples.get(name).position.below(), li.cil.oc.api.Items.get("capacitor").block().defaultBlockState());
                Component hologram = component(name);
                hologram.invoke("setScale", host.machine(), 1.0);
                hologram.invoke("setTranslation", host.machine(), 0.0, .25, 0.0);
            }
        }
        boolean hologramReady(String name) throws Exception {
            Object entity = client.level.getBlockEntity(samples.get(name).position);
            if (entity == null) return false;
            Object hologram = invoke(entity, "hologram", new Class<?>[0]);
            if (!hologram.getClass().getField("hasPower").getBoolean(hologram)) return false;
            int[] volume = (int[]) hologram.getClass().getField("volume").get(hologram);
            boolean occupied = false; for (int bits : volume) if (bits != 0) { occupied = true; break; }
            if (!occupied || hologram.getClass().getField("scale").getDouble(hologram) != 1.0) return false;
            int[] colors = (int[]) hologram.getClass().getField("colors").get(hologram);
            if (colors[0] != (name.equals("hologram1") ? 0x55ddff : 0xbb88ff)) return false;
            if (name.equals("hologram2") && hologram.getClass().getField("rotationSpeed").getFloat(hologram) != 12) return false;
            return true;
        }
        boolean errorBlinkReady() throws Exception {
            Object entity = client.level.getBlockEntity(samples.get("casecreative").position);
            if (entity == null || !entity.getClass().getField("hasErrored").getBoolean(entity)) return false;
            long millis = Math.floorMod(System.currentTimeMillis() + entity.hashCode(), 1000);
            return shot.contains("-on.") ? millis >= 100 && millis <= 350 : millis >= 600 && millis <= 850;
        }
        void supplementTick() throws Exception {
            switch (phase) {
                case 200 -> {
                    if (!complete() || !host.machine().isRunning() || !robot.machine().isRunning()) return;
                    activeTour = true;
                    gallery(0, false); phase = 201;
                }
                case 201 -> { if (!shotDone) return; gallery(0, true); phase = 202; }
                case 202 -> {
                    if (!shotDone) return;
                    closeup("hologram1", new Vec3(1.5, 1.6, 2.7), "models-hologram1-powered.png", "M11 T1 real bottom Capacitor, actual synchronized voxels/palette and native projection"); phase = 203;
                }
                case 203 -> {
                    if (!hologramReady("hologram1")) { shotAfter = worldFrames; return; }
                    if (!shotDone) return;
                    results.add("OBSERVE C20.models.hologram1 actual client hasPower=true, nonempty synchronized voxels, scale=1, real Capacitor below and original palette callback");
                    closeup("hologram2", new Vec3(1.5, 1.6, 2.7), "models-hologram2-powered-rotating.png", "M11 T2 actual powered native projection, palette and component rotation speed"); phase = 204;
                }
                case 204 -> {
                    if (!hologramReady("hologram2")) { shotAfter = worldFrames; return; }
                    if (!shotDone) return;
                    results.add("OBSERVE C20.models.hologram2 actual client hasPower=true, nonempty synchronized voxels, scale=1, real Capacitor below and original palette/rotation callbacks");
                    Object drive = client.level.getBlockEntity(samples.get("diskdrive").position);
                    if (drive == null) return;
                    Direction front = (Direction) invoke(drive, "facing", new Class<?>[0]);
                    closeup("diskdrive", Vec3.atLowerCornerOf(front.getNormal()).scale(2.3).add(.4, .9, 0), "models-diskdrive-inserted.png", "M09 actual inserted floppy before native eject callback"); phase = 205;
                }
                case 205 -> {
                    if (!shotDone) return;
                    task(() -> {
                        Object drive = level.getBlockEntity(samples.get("diskdrive").position);
                        require(Boolean.TRUE.equals(invoke(drive, "ejectMedium", new Class<?>[]{double.class}, 0.0)), "Actual floppy eject rejected its inserted medium");
                        require(((Container) drive).isEmpty(), "Actual drive remained occupied after eject");
                    }); phase = 206;
                }
                case 206 -> {
                    if (!complete()) return;
                    Object drive = client.level.getBlockEntity(samples.get("diskdrive").position);
                    if (!(drive instanceof Container inventory) || !inventory.isEmpty()) return;
                    screenshot("models-diskdrive-ejected.png", "M09 same native camera after actual ejectMedium, actual client disk slot is empty"); phase = 207;
                }
                case 207 -> {
                    if (!shotDone) return;
                    task(() -> player().setItemInHand(InteractionHand.MAIN_HAND, preset("tablet"))); phase = 208;
                }
                case 208 -> {
                    if (!complete() || !client.player.getMainHandItem().is(item("tablet").getItem())) return;
                    closeup("print", new Vec3(1.2, 1.2, 2.5), "models-highlight-print-tablet-warm.png", "M41 actual held Tablet selected face and per-shape Print outline after native section compilation"); phase = 209;
                }
                case 209 -> {
                    if (!shotDone) return;
                    require(client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(samples.get("print").position), "Actual Tablet raycast did not hit the configured Print");
                    task(() -> player().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)); phase = 214;
                }
                case 214 -> {
                    if (!complete() || !client.player.getMainHandItem().isEmpty()) return;
                    closeup("powerdistributor", new Vec3(1.7, 1.5, 2.8), "models-powerdistributor-powered.png", "M15 actual synchronized network fill controls original emissive overlay"); phase = 210;
                }
                case 210 -> {
                    Object device = client.level.getBlockEntity(samples.get("powerdistributor").position);
                    if (device == null || ((Number) invoke(device, "globalBuffer", new Class<?>[0])).doubleValue() <= 0) { shotAfter = worldFrames; return; }
                    if (!shotDone) return;
                    results.add("OBSERVE C20.models.powerdistributor actual client network energy=" + invoke(device, "globalBuffer", new Class<?>[0]) + "/" + invoke(device, "globalBufferSize", new Class<?>[0]));
                    task(() -> host.machine().crash("C20 actual error overlay")); phase = 211;
                }
                case 211 -> {
                    if (!complete()) return;
                    Object clientCase = client.level.getBlockEntity(samples.get("casecreative").position);
                    if (clientCase == null || !clientCase.getClass().getField("hasErrored").getBoolean(clientCase)) return;
                    Vec3 front = Vec3.atLowerCornerOf(host.facing().getNormal()).scale(2.3).add(.35, .7, 0);
                    closeup("casecreative", front, "models-case-error-blink-on.png", "M05 real machine.crash error overlay, actual renderer clock phase ON"); phase = 212;
                }
                case 212 -> {
                    if (!shotDone) return;
                    screenshot("models-case-error-blink-off.png", "M05 same native camera and real errored Case, actual renderer clock phase OFF"); phase = 213;
                }
                case 213 -> {
                    if (!shotDone) return;
                    results.add("PASS C20.models.supplement actual warm native gallery, both powered projector tiers, inserted/ejected real drive, real Tablet Print outline, network-power overlay and real error blink on/off frames; visual review still required");
                    finish();
                }
                default -> throw new AssertionError("Unknown model supplement phase " + phase);
            }
        }
        void tick() throws Exception {
            if (finishing) return;
            require(System.nanoTime() - began < TimeUnit.MINUTES.toNanos(5), "Model contract timed out phase=" + phase + " page=" + page + " frames=" + worldFrames + " paused=" + client.isPaused() + " screen=" + client.screen);
            if (serverError != null) throw new IllegalStateException("Actual model server operation failed", serverError);
            if (phase >= 200) { supplementTick(); return; }
            switch (phase) {
                case 0 -> {
                    if (!complete() || !energized || worldFrames < 10) return;
                    if (supplementOnly) { task(this::prepareSupplement); phase = 200; }
                    else { page = 0; gallery(0, false); phase = 1; }
                }
                case 1 -> {
                    if (!shotDone) return;
                    if ((page & 1) == 0) { page++; gallery(page / 2, true); }
                    else if (++page < 24) gallery(page / 2, false);
                    else { client.setScreen(null); task(this::activate); phase = 2; }
                }
                case 2 -> {
                    if (!complete() || !host.machine().isRunning() || !robot.machine().isRunning()) return;
                    if (host.machine().upTime() <= 0 || robot.machine().upTime() <= 0) return;
                    activeTour = true;
                    for (String name : List.of("assembler", "disassembler")) {
                        Object actual = client.level.getBlockEntity(samples.get(name).position);
                        if (actual == null || !Boolean.TRUE.equals(invoke(actual, "active", new Class<?>[0]))) return;
                    }
                    page = 0; gallery(0, false); phase = 3;
                }
                case 3 -> {
                    if (!shotDone) return;
                    if ((page & 1) == 0) { page++; gallery(page / 2, true); }
                    else if (++page < 24) gallery(page / 2, false);
                    else {
                        results.add("PASS C20.models.world actual 36 original block IDs/30 families, default+active front/rear native frames and real ItemEntity drops; RobotAfterimage intentionally invisible");
                        client.setScreen(null); aim(Vec3.atCenterOf(base.east(16)), new Vec3(2.3, 1.7, 3.5));
                        client.setScreen(new Caption(this)); screenshot("models-drone-running.png", "M31 actual native configured Drone entity, wings/lights"); phase = 4;
                    }
                }
                case 4 -> {
                    if (!shotDone) return;
                    task(() -> { player().setItemSlot(EquipmentSlot.FEET, item("hoverboots")); }); phase = 5;
                }
                case 5 -> {
                    if (!complete() || !client.player.getItemBySlot(EquipmentSlot.FEET).is(item("hoverboots").getItem())) return;
                    sheet = new ItemSheet(this, -1); client.setScreen(sheet); screenshot("models-hoverboots-worn.png", "M36 native player armor renderer wearing the real hover boots"); phase = 6;
                }
                case 6 -> {
                    if (!shotDone) return;
                    sheet = new ItemSheet(this, 0); client.setScreen(sheet); page = 0; screenshot("models-items-01.png", "Actual registered items/configured creative presets; left GUI, right GROUND"); phase = 7;
                }
                case 7 -> {
                    if (!shotDone) return;
                    require(sheet.rendered == Math.min(sheet.capacity, icons.size() - page * sheet.capacity), "Native ItemRenderer did not draw the complete actual page");
                    if ((page + 1) * sheet.capacity < icons.size()) { page++; sheet = new ItemSheet(this, page); client.setScreen(sheet); screenshot(String.format("models-items-%02d.png", page + 1), "Actual registered items/configured creative presets; left GUI, right GROUND"); }
                    else {
                        results.add("PASS C20.models.items actual GUI+GROUND frames=" + icons.size() + ", every registered OC item plus original configured creative variants, configured Print, all 16 floppy colors, actual bound Terminal; native hover armor and Drone entity frames");
                        sheet = null; client.setScreen(null); task(() -> player().setItemInHand(InteractionHand.MAIN_HAND, preset("tablet"))); phase = 8;
                    }
                }
                case 8 -> {
                    if (!complete() || !client.player.getMainHandItem().is(item("tablet").getItem())) return;
                    task(() -> {
                        tablet = (Tablet) Class.forName("li.cil.oc.server.portable.TabletManager").getMethod("get", ItemStack.class, net.minecraft.world.entity.player.Player.class).invoke(null, player().getMainHandItem(), player());
                        if (tablet.machine().node() instanceof Connector connector) connector.changeBuffer(Double.POSITIVE_INFINITY);
                        require(tablet.machine().start(), "Actual held Tablet failed native boot"); computers.add(tablet.machine());
                    }); phase = 13;
                }
                case 13 -> {
                    if (!complete() || !tag(client.player.getMainHandItem()).getBoolean("oc:isRunning")) return;
                    icons.add(new Icon("tablet actual running", client.player.getMainHandItem().copy()));
                    sheet = new ItemSheet(this, (icons.size() - 1) / (Math.max(1, (client.getWindow().getGuiScaledWidth() - 16) / 88) * Math.max(1, (client.getWindow().getGuiScaledHeight() - 42) / 54)));
                    client.setScreen(sheet); screenshot("models-tablet-running.png", "M34 actual native-booted Tablet on model, alongside native item page"); phase = 14;
                }
                case 14 -> {
                    if (!shotDone) return; sheet = null; client.setScreen(null);
                    aim(Vec3.atCenterOf(samples.get("print").position), new Vec3(1.2, 1.2, 2.5)); screenshot("models-highlight-print-tablet.png", "M41 actual held Tablet target projection and real per-shape Print outline"); phase = 9;
                }
                case 9 -> {
                    if (!shotDone) return;
                    require(client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(samples.get("print").position), "Real tablet raycast did not hit the configured Print");
                    aim(Vec3.atCenterOf(samples.get("cable").position), new Vec3(1, 1.2, 2.5)); screenshot("models-highlight-cable-tablet.png", "M41 actual Tablet projection and Cable connection-aware outline"); phase = 10;
                }
                case 10 -> {
                    if (!shotDone) return;
                    require(client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(samples.get("cable").position), "Real tablet raycast did not hit the Cable");
                    ItemStack mfu = item("mfu");
                    task(() -> {
                        player().setShiftKeyDown(true); player().setItemInHand(InteractionHand.MAIN_HAND, mfu);
                        try { BlockPos pos = samples.get("geolyzer").position; require(mfu.getItem().onItemUseFirst(mfu, new UseOnContext(player(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false))).consumesAction(), "Real MFU binding failed"); }
                        finally { player().setShiftKeyDown(false); }
                    }); phase = 11;
                }
                case 11 -> {
                    if (!complete() || tag(client.player.getMainHandItem()).getIntArray("oc:coord").length != 5) return;
                    aim(Vec3.atCenterOf(samples.get("geolyzer").position), new Vec3(1.8, 1.3, 3)); screenshot("models-mfu-target.png", "M39 actual sneak-bound MFU target outline and selected face"); phase = 12;
                }
                case 12 -> {
                    if (!shotDone) return;
                    results.add("PASS C20.models.highlight actual Tablet/Print/Cable and real MFU binding rendered; screenshots require visual review");
                    Vec3 rear = Vec3.atLowerCornerOf(robot.facing().getOpposite().getNormal()).scale(2.5).add(.45, 1, 0);
                    aim(Vec3.atCenterOf(samples.get("robot").position), rear);
                    screenshot("models-robot-mounts-idle.png", "M37 actual inventory/crafting/generator mounts before real fuel insertion"); phase = 15;
                }
                case 15 -> {
                    if (!shotDone) return;
                    task(() -> {
                        robot.mainInventory().setItem(0, new ItemStack(Items.COAL)); robot.setSelectedSlot(0);
                        Object[] result = installed(robot.machine(), "generator").invoke("insert", robot.machine(), 1);
                        require(result != null && Boolean.TRUE.equals(result[0]), "Real generator rejected its selected real coal");
                    }); phase = 16;
                }
                case 16 -> {
                    if (!complete()) return;
                    Object clientRobot = client.level.getBlockEntity(samples.get("robot").position);
                    require(clientRobot instanceof Robot, "Actual model Robot client proxy missing");
                    boolean burning = false;
                    for (int slot = 0; slot < ((Robot) clientRobot).getContainerSize(); slot++) {
                        ItemStack part = ((Robot) clientRobot).getItem(slot);
                        if (part.is(item("generatorupgrade").getItem()) && tag(part).getCompound("oc:data").getInt("remainingTicks") > 0) burning = true;
                    }
                    if (!burning) return;
                    screenshot("models-robot-mounts-burning.png", "M37 actual selected coal burned by mounted generator; synchronized original front UV state"); phase = 17;
                }
                case 17 -> {
                    if (!shotDone) return;
                    task(() -> {
                        int result = server.getCommands().getDispatcher().execute("oc_wlan true", player().createCommandSourceStack());
                        require(result == 1, "Actual wireless debug command failed");
                    }); phase = 18;
                }
                case 18 -> {
                    if (!complete()) return;
                    var bounds = (List<?>) Class.forName("li.cil.oc.common.command.OriginalCommands").getMethod("wirelessBounds", net.minecraft.resources.ResourceKey.class).invoke(null, client.level.dimension());
                    if (bounds.isEmpty()) return;
                    aim(Vec3.atCenterOf(samples.get("robot").position), new Vec3(4, 3, 6)); screenshot("models-wireless-debug.png", "M40 actual oc_wlan tree bounds from mounted wireless cards"); phase = 19;
                }
                case 19 -> {
                    if (!shotDone) return;
                    results.add("PASS C20.models.mounts actual three built-in upgrade meshes, real generator idle/burning UV and command-enabled real wireless tree native frames");
                    client.setScreen(null);
                    task(() -> {
                        movedRobot = BlockPos.containing(robot.xPosition(), robot.yPosition(), robot.zPosition()).relative(robot.toGlobal(Direction.SOUTH)); remember(movedRobot);
                        Object[] result = installed(robot.machine(), "robot").invoke("move", robot.machine(), Direction.SOUTH.ordinal());
                        require(result != null && Boolean.TRUE.equals(result[0]), "Actual Robot component refused real gallery movement");
                    }); phase = 20;
                }
                case 20 -> {
                    if (!complete() || !(client.level.getBlockEntity(movedRobot) instanceof Robot)) return;
                    aim(Vec3.atCenterOf(movedRobot), new Vec3(1.4, 1.3, 2.5)); screenshot("models-robot-real-move.png", "M25/M26 actual moving Robot with native tool; previous proxy is intentionally invisible Afterimage"); phase = 21;
                }
                case 21 -> {
                    if (!shotDone) return;
                    task(() -> host.machine().crash("C20 actual error overlay")); phase = 22;
                }
                case 22 -> {
                    if (!complete()) return;
                    Object clientCase = client.level.getBlockEntity(samples.get("casecreative").position);
                    if (clientCase == null || !clientCase.getClass().getField("hasErrored").getBoolean(clientCase)) return;
                    Vec3 front = Vec3.atLowerCornerOf(host.facing().getNormal()).scale(2.3).add(.35, .7, 0);
                    aim(Vec3.atCenterOf(samples.get("casecreative").position), front); screenshot("models-case-real-error.png", "M05 actual machine.crash synchronized error; original overlay blinks every 500ms"); phase = 23;
                }
                case 23 -> {
                    if (!shotDone) return;
                    results.add("PASS C20.models.robot actual component movement/tool and Case real crash error mirror rendered");
                    results.add("C20.models.external M38 reuses client-effects-20261028-l1 actual entitled Pet visible/hidden frames and original tint observation; native world/items PNGs still require visual review");
                    finish();
                }
                default -> throw new AssertionError("Unknown model phase " + phase);
            }
        }
        void fail(Throwable error) { results.add("FAIL C20.models phase=" + phase + " page=" + page + " " + error); finish(); }
        void finish() {
            if (finishing) return; finishing = true; shot = null; sheet = null; client.setScreen(null); client.options.setCameraType(oldCamera); client.options.hideGui = oldHideGui;
            client.getWindow().setFramerateLimit(oldFrameLimit);
            if (client.player != null) client.player.setNoGravity(oldClientNoGravity);
            cleanup = CompletableFuture.runAsync(() -> {
                for (Machine machine : computers) machine.stop();
                if (tablet != null) try { invoke(tablet, "close", new Class<?>[]{boolean.class}, false); } catch (Exception error) { throw new IllegalStateException(error); }
                try { server.getCommands().getDispatcher().execute("oc_wlan false", player().createCommandSourceStack()); } catch (Exception error) { throw new IllegalStateException(error); }
                for (Entity entity : entities) if (!entity.isRemoved()) entity.discard();
                for (var entry : original.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 3);
                // Block removal can legitimately drop stored media/hardware; these are owned fixture cells.
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(Vec3.atLowerCornerOf(base.offset(-2, -2, -2)), Vec3.atLowerCornerOf(base.offset(20, 5, 74))))) item.discard();
                for (long chunk : forced) level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
                ServerPlayer actor = player(); if (savedHand != null) actor.setItemInHand(InteractionHand.MAIN_HAND, savedHand); if (savedBoots != null) actor.setItemSlot(EquipmentSlot.FEET, savedBoots);
                actor.connection.teleport(oldPosition.x, oldPosition.y, oldPosition.z, oldYaw, oldPitch);
                if (playerCaptured) { actor.setNoGravity(oldNoGravity); actor.setOnGround(oldOnGround); actor.setDeltaMovement(Vec3.ZERO); actor.getAbilities().mayfly = oldMayfly; actor.getAbilities().flying = oldFlying; actor.onUpdateAbilities(); }
            }, server);
        }
    }

    private static final class Caption extends Screen {
        final Run run;
        Caption(Run run) { super(net.minecraft.network.chat.Component.literal("OpenComputers model gallery")); this.run = run; }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
            graphics.fill(4, 4, width - 4, 29, 0xd0101010); graphics.drawString(font, "C20 original model gallery", 10, 8, 0xffffff);
            graphics.drawString(font, font.plainSubstrByWidth(run.caption, width - 20), 10, 19, 0xe0e0e0);
        }
    }
    private static final class ItemSheet extends Screen {
        final Run run; final int page; int columns, rows, capacity, renders, rendered;
        ItemSheet(Run run, int page) { super(net.minecraft.network.chat.Component.literal("OpenComputers native item models")); this.run = run; this.page = page; }
        @Override public boolean isPauseScreen() { return false; }
        @Override protected void init() { columns = Math.max(1, (width - 16) / 88); rows = Math.max(1, (height - 42) / 54); capacity = columns * rows; }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
            graphics.fill(0, 0, width, height, 0xff20252c); graphics.drawString(font, page < 0 ? "M36 actual player wearing HoverBoots" : "C20 native ItemRenderer: left GUI / right GROUND; page " + (page + 1), 8, 8, 0xffffff);
            if (page < 0) {
                InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, width / 2 - 80, 32, width / 2 + 80, height - 8, Math.max(40, (height - 55) / 2), .0625f, width / 2f, height / 2f, run.client.player);
                renders++; return;
            }
            rendered = 0;
            for (int slot = 0, index = page * capacity; slot < capacity && index < run.icons.size(); slot++, index++) {
                Icon icon = run.icons.get(index); int x = 8 + (slot % columns) * 88, y = 30 + (slot / columns) * 54;
                graphics.fill(x, y, x + 84, y + 50, 0xff353d48);
                BakedModel model = run.client.getItemRenderer().getModel(icon.item, run.client.level, run.client.player, index);
                require(model != run.client.getModelManager().getMissingModel(), "Actual native missing item model " + icon.name);
                // Empty unconfigured Print and invisible Afterimage are original models; configured variants are separate entries.
                graphics.pose().pushPose(); graphics.pose().translate(x + 8, y + 4, 0); graphics.pose().scale(1.5f, 1.5f, 1.5f); graphics.renderItem(icon.item, 0, 0); graphics.pose().popPose();
                graphics.flush(); Lighting.setupFor3DItems(); PoseStack pose = graphics.pose(); pose.pushPose();
                pose.translate(x + 59, y + 18, 150); pose.scale(30, -30, 30); pose.mulPose(Axis.XP.rotationDegrees(22)); pose.mulPose(Axis.YP.rotationDegrees(35));
                run.client.getItemRenderer().renderStatic(icon.item, ItemDisplayContext.GROUND, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, run.client.renderBuffers().bufferSource(), run.client.level, index);
                run.client.renderBuffers().bufferSource().endBatch(); pose.popPose(); Lighting.setupFor3DItems();
                graphics.drawString(font, font.plainSubstrByWidth(icon.name, 80), x + 2, y + 38, 0xffffff, false); rendered++;
            }
            renders++;
        }
    }
}
