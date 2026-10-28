package ocfixture;

import com.mojang.blaze3d.platform.NativeImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import li.cil.oc.api.CreativeTab;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Manual;
import li.cil.oc.api.component.RackMountable;
import li.cil.oc.api.driver.item.MutableProcessor;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.internal.Drone;
import li.cil.oc.api.internal.Rack;
import li.cil.oc.api.internal.Robot;
import li.cil.oc.api.internal.TextBuffer;
import li.cil.oc.api.machine.Architecture;
import li.cil.oc.api.machine.Machine;
import li.cil.oc.api.network.Analyzable;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.items.IItemHandler;
import org.lwjgl.glfw.GLFW;

/** Nineteen original layout families, all twenty-four reachable entries, and the accepted MCU extension. */
public final class MenuClientContract {
    private static volatile Run active;
    private static boolean observing;
    private static final byte[] BIOS = ("local g=component.list('gpu')();local s=component.list('screen')();"
        + "if g and s then local p=component.proxy(g);p.bind(s);p.setResolution(40,12);p.setBackground(0);"
        + "p.setForeground(0xFFFFFF);p.fill(1,1,40,12,' ');p.set(1,1,'C23 actual native Lua 5.3');"
        + "p.set(1,3,'中文菜单与终端文字');p.set(1,5,'背景 / 插槽 / 输入 / 提示');end;"
        + "local d=component.list('drone')();if d then component.proxy(d).setStatusText('菜单验收\\n原生 Lua');end;"
        + "while true do computer.pullSignal(1) end").getBytes(StandardCharsets.UTF_8);
    private MenuClientContract() {}

    public static void start(Minecraft client, Path directory, List<String> results) {
        require(active == null, "Menu client contract is already running");
        require(client.player != null && client.level != null && client.hasSingleplayerServer(), "Menu proof requires its real owned integrated world");
        if (!observing) {
            observing = true;
            NeoForge.EVENT_BUS.addListener(MenuClientContract::beforeFrame);
            NeoForge.EVENT_BUS.addListener(MenuClientContract::afterFrame);
            NeoForge.EVENT_BUS.addListener(MenuClientContract::tooltip);
            NeoForge.EVENT_BUS.addListener(MenuClientContract::tooltipText);
            NeoForge.EVENT_BUS.addListener(MenuClientContract::beforeScreen);
            NeoForge.EVENT_BUS.addListener(MenuClientContract::afterScreen);
            NeoForge.EVENT_BUS.addListener(MenuClientContract::joined);
        }
        Run run = new Run(client, directory, results); active = run;
        try { run.start(); } catch (Throwable error) { run.fail(error); }
    }
    public static boolean isRunning() { return active != null; }
    public static void tick() {
        Run run = active; if (run == null) return;
        try { run.tick(); } catch (Throwable error) { run.fail(error); }
        if (run.finishing && run.cleanup != null && run.cleanup.isDone()) {
            try { run.cleanup.join(); } catch (Throwable error) { run.results.add("FAIL C23.menus cleanup: " + error); }
            active = null;
        }
    }
    public static void close() { Run run = active; if (run != null && !run.finishing) run.fail(new AssertionError("Menu client contract closed before completion")); }
    private static void beforeFrame(RenderFrameEvent.Pre event) { Run run = active; if (run != null) { run.frame++; run.frameTooltips = 0; run.tooltipLines.clear(); } }
    private static void afterFrame(RenderFrameEvent.Post event) { Run run = active; if (run != null && !run.finishing) try { run.rendered(); } catch (Throwable error) { run.fail(error); } }
    private static void beforeScreen(ScreenEvent.Render.Pre event) { Run run = active; if (run != null && !run.finishing) try { run.beforeScreen(event); } catch (Throwable error) { run.fail(error); } }
    private static void afterScreen(ScreenEvent.Render.Post event) { Run run = active; if (run != null && !run.finishing) try { run.afterScreen(event); } catch (Throwable error) { run.fail(error); } }
    private static void tooltip(RenderTooltipEvent.Pre event) { Run run = active; if (run != null && !run.finishing && run.client.screen != null) try { run.frameTooltips++; for (var line : event.getComponents()) require(line.getWidth(event.getFont()) <= run.client.screen.width, "Actual tooltip line exceeds the screen width"); } catch (Throwable error) { run.fail(error); } }
    private static void tooltipText(RenderTooltipEvent.GatherComponents event) { Run run = active; if (run != null && !run.finishing) for (var line : event.getTooltipElements()) line.left().ifPresent(text -> run.tooltipLines.add(text.getString())); }
    private static void joined(EntityJoinLevelEvent event) { Run run = active; if (run != null && run.spawning && event.getLevel() == run.level && event.getEntity() instanceof Drone drone) { run.drone = drone; run.spawned = event.getEntity(); run.droneId = event.getEntity().getUUID(); } }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static ItemStack item(String name) { var info = li.cil.oc.api.Items.get(name); require(info != null, "Missing actual menu hardware " + name); return info.createItemStack(1); }
    private static CompoundTag tag(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); }
    private record Entry(String name, String screen, String block, String held, boolean sneak) {}
    private static final List<Entry> ENTRIES = List.of(
        new Entry("adapter", "AdapterScreen", "adapter", "", false),
        new Entry("assembler", "WorkstationScreen", "assembler", "", false),
        new Entry("case", "CaseScreen", "", "", false),
        new Entry("charger", "ChargerScreen", "charger", "", false),
        new Entry("database", "DatabaseScreen", "", "databaseupgrade3", false),
        new Entry("disassembler", "WorkstationScreen", "disassembler", "", false),
        new Entry("diskdrive", "DiskDriveScreen", "diskdrive", "", false),
        new Entry("drive", "DriveConfigurationScreen", "", "hdd3", false),
        new Entry("drone", "AgentScreen", "", "", false),
        new Entry("manual", "ManualScreen", "", "manual", false),
        new Entry("printer", "PrinterScreen", "printer", "", false),
        new Entry("rack", "RackScreen", "", "", false),
        new Entry("raid", "RaidScreen", "raid", "", false),
        new Entry("relay", "RelayScreen", "relay", "", false),
        new Entry("robot", "AgentScreen", "", "", false),
        new Entry("screen", "ScreenGui", "", "", false),
        new Entry("server-held", "ServerScreen", "", "server3", false),
        new Entry("tablet-inventory", "TabletInventoryScreen", "", "tablet", true),
        new Entry("waypoint", "WaypointScreen", "waypoint", "", false),
        new Entry("diskdrive-held", "RackDiskScreen", "", "diskdrivemountable", false),
        new Entry("diskdrive-mounted", "RackDiskScreen", "", "", false),
        new Entry("server-mounted", "ServerScreen", "", "", false),
        new Entry("tablet-screen", "TabletScreen", "", "tablet", false),
        new Entry("terminal", "TerminalScreen", "", "terminal", false),
        new Entry("microcontroller-extension", "MicrocontrollerScreen", "", "", true));

    private static final class Run {
        final Minecraft client; final MinecraftServer server; final ServerLevel level; final Path directory; final List<String> results;
        final List<Entry> entries;
        final BlockPos base, target, nativeCasePos, screenPos, keyboardPos, robotPos, dronePad, rackPos, mcuPos;
        final Map<BlockPos, BlockState> restored = new LinkedHashMap<>(); final Set<Long> forced = new LinkedHashSet<>();
        final List<ItemStack> inventory = new ArrayList<>(); final List<Machine> machines = new ArrayList<>(); final Set<Machine> startedMachines = new LinkedHashSet<>();
        final List<String> tooltipLines = new ArrayList<>(); final List<Double> intervals = new ArrayList<>();
        final long started = System.nanoTime(); final Vec3 previousPosition; final int previousSlot; final boolean previousFlying;
        CompletableFuture<Void> work, cleanup; Case computer; Robot robot; Drone drone; Rack rack;
        ItemStack tablet = ItemStack.EMPTY, serverStack = ItemStack.EMPTY, expectedHeld = ItemStack.EMPTY;
        Class<? extends Architecture> architecture; BlockPos clickPos; UUID droneId; Entity spawned;
        Entry entry; Button control; int index = -1, phase, ticks, phaseTicks, frame, stageFrame, frameTooltips, captureStage;
        long lastFrame; boolean finishing, directOpen, releaseTablet, spawning, inventoryCaptured; String waiting = "initial placement";
        NativeImage beforeWaypoint; boolean waypointBackdropVerified;

        Run(Minecraft client, Path directory, List<String> results) {
            this.client = client; this.server = client.getSingleplayerServer(); this.level = server.getLevel(client.level.dimension()); this.directory = directory; this.results = results;
            String focus = System.getProperty("ocfixture.menuFocus", "all"); var selected = List.of(focus.split(",")); entries = focus.equals("all") ? ENTRIES : ENTRIES.stream().filter(value -> selected.contains(value.name)).toList(); require(!entries.isEmpty(), "Unknown actual menu focus: " + focus);
            base = client.player.blockPosition().east(5).above(3); target = base; nativeCasePos = base.south(5); screenPos = nativeCasePos.above(); keyboardPos = screenPos.south();
            robotPos = base.west(4); dronePad = base.west(8).below(); rackPos = base.east(4); mcuPos = base.north(4);
            previousPosition = client.player.position(); previousSlot = client.player.getInventory().selected; previousFlying = client.player.getAbilities().flying;
        }
        ServerPlayer actor() { ServerPlayer actor = server.getPlayerList().getPlayer(client.player.getUUID()); require(actor != null, "Actual integrated player disappeared"); return actor; }
        void remember(BlockPos pos) {
            if (restored.containsKey(pos)) return;
            if (level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true)) forced.add(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            level.getChunkAt(pos); require(level.isEmptyBlock(pos) && level.getBlockEntity(pos) == null, "Menu fixture refuses occupied cell " + pos);
            restored.put(pos.immutable(), level.getBlockState(pos));
        }
        void place(BlockPos pos, BlockState state) { remember(pos); if (level.getBlockEntity(pos) instanceof Container container) container.clearContent(); require(level.setBlock(pos, state, 3), "Could not place actual menu host " + pos); }
        void place(String name, BlockPos pos) { place(pos, li.cil.oc.api.Items.get(name).block().defaultBlockState()); }
        void start() {
            require("zh_cn".equals(client.getLanguageManager().getSelected()), "C23 requires the actually selected zh_cn language");
            require(client.getWindow().getWidth() == 1280 && client.getWindow().getHeight() == 720, "C23 requires a 1280x720 actual frame");
            require(client.screen == null, "Menu fixture requires the ordinary loaded game view");
            work = CompletableFuture.runAsync(() -> {
                ServerPlayer actor = actor(); for (int slot = 0; slot < actor.getInventory().getContainerSize(); slot++) inventory.add(actor.getInventory().getItem(slot).copy()); inventoryCaptured = true;
                actor.getInventory().clearContent(); actor.getInventory().selected = 0;
                if (entries.stream().allMatch(value -> Set.of("waypoint", "manual", "drive").contains(value.name))) { actor.inventoryMenu.broadcastChanges(); return; }
                architecture = li.cil.oc.api.Machine.architectures().stream().filter(type -> "Lua 5.3".equals(li.cil.oc.api.Machine.getArchitectureName(type))).findFirst().orElseThrow();
                place("case3", nativeCasePos); computer = (Case) level.getBlockEntity(nativeCasePos);
                ItemStack cpu = item("cpu1"); ((MutableProcessor) Driver.driverFor(cpu)).setArchitecture(cpu, architecture);
                for (ItemStack part : List.of(cpu, item("ram3"), item("graphicscard1"), eeprom())) insert(computer, part);
                place("screen3", screenPos); place(keyboardPos.below(), Blocks.STONE.defaultBlockState()); place("keyboard", keyboardPos);
                CreativeTab.instance.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess()));
                ItemStack robotStack = preset("robot"); remember(robotPos);
                actor.setItemInHand(InteractionHand.MAIN_HAND, robotStack);
                require(((BlockItem) robotStack.getItem()).place(new BlockPlaceContext(actor, InteractionHand.MAIN_HAND, robotStack, new BlockHitResult(Vec3.atCenterOf(robotPos), Direction.UP, robotPos, false))).consumesAction(), "Actual creative Robot placement failed");
                require(level.getBlockEntity(robotPos) instanceof Robot, "Placed Robot has no published marker"); robot = (Robot) level.getBlockEntity(robotPos);
                place(dronePad, Blocks.STONE.defaultBlockState()); ItemStack droneStack = preset("drone"); actor.setItemInHand(InteractionHand.MAIN_HAND, droneStack);
                spawning = true;
                try { require(droneStack.getItem().useOn(new UseOnContext(actor, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(dronePad).add(0, .5, 0), Direction.UP, dronePad, false))).consumesAction(), "Actual creative Drone spawn failed"); }
                finally { spawning = false; }
                require(drone != null, "Actual Drone use did not publish its real spawn event");
                tablet = preset("tablet"); ItemStack mcu = preset("microcontroller"); remember(mcuPos); actor.setItemInHand(InteractionHand.MAIN_HAND, mcu);
                require(((BlockItem) mcu.getItem()).place(new BlockPlaceContext(actor, InteractionHand.MAIN_HAND, mcu, new BlockHitResult(Vec3.atCenterOf(mcuPos), Direction.UP, mcuPos, false))).consumesAction(), "Actual assembled MCU placement failed");
                serverStack = item("server3"); IItemHandler blade = Driver.itemHandlerFor(serverStack, actor); require(blade != null, "Real server blade inventory provider absent");
                ItemStack bladeCpu = cpu.copy(); for (ItemStack part : List.of(bladeCpu, item("ram3"), eeprom())) insert(blade, part);
                place("rack", rackPos); rack = (Rack) level.getBlockEntity(rackPos); rack.setItem(0, serverStack.copy()); rack.setItem(1, item("diskdrivemountable")); rack.setItem(2, item("terminalserver"));
                machines.add(computer.machine()); machines.add(robot.machine()); machines.add(drone.machine()); actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); actor.inventoryMenu.broadcastChanges();
            }, server);
        }
        ItemStack eeprom() { return li.cil.oc.api.Items.registerEEPROM("C23 native menu BIOS", BIOS, new byte[0], false); }
        void insert(Container container, ItemStack part) { for (int slot = 0; slot < container.getContainerSize(); slot++) if (container.getItem(slot).isEmpty() && container.canPlaceItem(slot, part)) { container.setItem(slot, part); return; } throw new AssertionError("Actual Case rejected " + part); }
        void insert(IItemHandler handler, ItemStack part) { for (int slot = 0; slot < handler.getSlots(); slot++) if (handler.insertItem(slot, part.copy(), true).isEmpty()) { require(handler.insertItem(slot, part, false).isEmpty(), "Real blade insertion changed acceptance"); return; } throw new AssertionError("Actual blade rejected " + part); }
        ItemStack preset(String name) {
            ItemStack best = ItemStack.EMPTY; int tier = -1;
            for (ItemStack stack : CreativeTab.instance.getDisplayItems()) if (stack.is(item(name).getItem())) { CompoundTag data = tag(stack); if ((data.contains("oc:components") || data.contains("oc:items")) && data.getInt("oc:tier") > tier) { best = stack.copy(); tier = data.getInt("oc:tier"); } }
            require(!best.isEmpty(), "No configured actual creative preset " + name); CompoundTag data = tag(best); boolean slotted = name.equals("tablet"); String key = slotted ? "oc:items" : "oc:components"; ListTag parts = new ListTag(); int cpus = 0, flashes = 0;
            for (Tag raw : data.getList(key, Tag.TAG_COMPOUND)) { CompoundTag slot = ((CompoundTag) raw).copy(); ItemStack part = ItemStack.parseOptional(level.registryAccess(), slotted ? slot.getCompound("item") : slot); var driver = Driver.driverFor(part);
                if (driver instanceof MutableProcessor processor) { processor.setArchitecture(part, architecture); cpus++; }
                if (driver != null && "eeprom".equals(driver.slot(part))) { part = eeprom(); flashes++; }
                if (slotted) { slot.put("item", part.saveOptional(level.registryAccess())); parts.add(slot); } else parts.add(part.saveOptional(level.registryAccess()));
            }
            // DroneData inherits MicrocontrollerData: its original creative preset omits
            // EEPROM, and load reserves an empty final slot when none is serialized.
            // Fill that reserved slot; Robot and Tablet already serialize their EEPROM.
            if ((name.equals("drone") || name.equals("microcontroller")) && flashes == 0) { parts.add(eeprom().saveOptional(level.registryAccess())); flashes++; }
            require(cpus == 1 && flashes == 1, "Preset did not retain one actual CPU/EEPROM: " + name + " cpus=" + cpus + " eeproms=" + flashes); data.put(key, parts); best.set(DataComponents.CUSTOM_DATA, CustomData.of(data)); return best;
        }
        void progress(int next) { phase = next; phaseTicks = ticks; stageFrame = frame; intervals.clear(); lastFrame = 0; }
        void tick() throws Exception {
            if (finishing) return;
            require(System.nanoTime() - started < TimeUnit.MINUTES.toNanos(8), "Menu timeout index=" + index + " phase=" + phase + " waiting=" + waiting + " screen=" + (client.screen == null ? "null" : client.screen.getClass().getName())); ticks++;
            if (work != null && !work.isDone()) return;
            if (work != null) { work.join(); work = null; }
            if (phase == 0) {
                if (ticks >= 12 && startedMachines.size() == machines.size()) { index = 0; prepare(); return; }
                work = CompletableFuture.runAsync(() -> { for (Machine machine : machines) { if (machine.node() instanceof Connector power) power.changeBuffer(Double.POSITIVE_INFINITY); if (machine.node().network() != null && startedMachines.add(machine)) require(machine.start(), "Actual native menu host did not start: " + machine.lastError()); } }, server);
                waiting = "actual native Case/Robot/Drone startup and physical network"; return;
            }
            if (phase == 1) {
                waiting = "real held item/world synchronization for " + entry.name;
                if (ticks - phaseTicks < 4 || !client.player.getMainHandItem().is(expectedHeld.getItem()) && !expectedHeld.isEmpty() || expectedHeld.isEmpty() && !client.player.getMainHandItem().isEmpty()) return;
                if (entry.name.equals("terminal") && !tag(expectedHeld).getString("oc:key").equals(tag(client.player.getMainHandItem()).getString("oc:key"))) return;
                if (directOpen) { progress(3); return; }
                sneak(entry.sneak);
                if (entry.name.equals("drone")) { Entity entity = null; for (Entity candidate : client.level.entitiesForRendering()) if (candidate.getUUID().equals(droneId)) { entity = candidate; break; } if (entity == null) return; client.gameMode.interact(client.player, entity, InteractionHand.MAIN_HAND); }
                else if (clickPos != null) { if (client.level.isEmptyBlock(clickPos)) return; client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(clickPos), Direction.UP, clickPos, false)); }
                else client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
                if (entry.name.startsWith("tablet-")) { releaseTablet = true; progress(2); } else progress(3);
                return;
            }
            if (phase == 2) { if (ticks - phaseTicks < 2) return; client.gameMode.releaseUsingItem(client.player); releaseTablet = false; progress(3); return; }
            if (phase == 3) {
                waiting = "normal network screen " + entry.screen;
                if (client.screen == null || !client.screen.getClass().getSimpleName().equals(entry.screen)) return;
                verifyScreen(); cursor(1, 1); captureStage = 0; progress(4); return;
            }
            if (phase == 5) {
                if (ticks - phaseTicks < 2) return;
                require(client.screen == null, "Actual menu did not close normally: " + entry.name);
                if (++index == entries.size()) { results.add(entries == ENTRIES ? "PASS C23.menu-entries all 19 original layout families / 24 reachable entries and the accepted MCU extension opened through actual host interactions and normal screen/menu packets; real rendered frames recorded for visual review" : "PASS C23.menu-focus actual normal opening/render/close: " + String.join(",", entries.stream().map(value -> value.name).toList())); results.add("LIMIT C23.menus: actual screenshots still require visual review; remote TCP/player combinations remain C19, and the original player-confirmed Robot 60 FPS evidence is retained"); finish(); }
                else prepare();
            }
        }
        void prepare() {
            entry = entries.get(index); clickPos = null; expectedHeld = ItemStack.EMPTY; directOpen = false; control = null; progress(1); cursor(1, 1); sneak(false);
            work = CompletableFuture.runAsync(() -> {
                ServerPlayer actor = actor(); actor.closeContainer(); actor.getInventory().selected = 0; actor.setShiftKeyDown(false); actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); actor.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                ItemStack longItem = new ItemStack(Items.PAPER); longItem.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("中文长名称：这是用于验证真实菜单文字自动换行与边界的说明物品，请保留完整文字"));
                longItem.set(DataComponents.LORE, new ItemLore(List.of(net.minecraft.network.chat.Component.literal("第一段：背景纹理、插槽与文字应清晰可辨；移动指针后提示应消失。"), net.minecraft.network.chat.Component.literal("第二段：这段中文来自真实物品组件，由正常 GuiGraphics 提示流程绘制。")))); actor.getInventory().setItem(9, longItem);
                if (!entry.block.isEmpty()) { place(entry.block, target); clickPos = target; if (level.getBlockEntity(target) instanceof Container container) { if (entry.name.equals("diskdrive")) container.setItem(0, item("floppy")); else if (entry.name.equals("raid")) for (int i = 0; i < 3; i++) container.setItem(i, item("hdd3")); else if (entry.name.equals("assembler") && !System.getProperty("ocfixture.menuFocus", "all").equals("assembler")) container.setItem(0, item("case2")); } }
                if (!entry.held.isEmpty()) expectedHeld = switch (entry.held) { case "tablet" -> tablet; case "server3" -> serverStack.copy(); default -> item(entry.held); };
                switch (entry.name) {
                    case "case" -> clickPos = nativeCasePos;
                    case "robot" -> clickPos = robotPos;
                    case "screen" -> clickPos = keyboardPos;
                    case "rack" -> clickPos = rackPos;
                    case "microcontroller-extension" -> clickPos = mcuPos;
                    case "terminal" -> { RackMountable terminal = rack.getMountable(2); require(terminal != null && terminal.node().network() != null, "Actual terminal mountable did not join its rack network"); actor.setItemInHand(InteractionHand.MAIN_HAND, expectedHeld); require(terminal.onActivate(actor, InteractionHand.MAIN_HAND, expectedHeld, .5f, .5f), "Actual terminal pairing rejected its real held item"); if (terminal instanceof Analyzable analyzed) for (var node : analyzed.onAnalyze(actor, rack.facing(), .5f, .5f, .5f)) if (node.host() instanceof TextBuffer buffer) { if (node instanceof Connector power) power.changeBuffer(Double.POSITIVE_INFINITY); buffer.setResolution(40, 12); buffer.setForegroundColor(0xFFFFFF); buffer.setBackgroundColor(0); buffer.fill(0, 0, 40, 12, ' '); buffer.set(0, 0, "C23 actual paired terminal", false); buffer.set(0, 2, "真实服务器缓冲 / 中文菜单", false); } expectedHeld = actor.getMainHandItem().copy(); }
                    default -> {}
                }
                BlockPos near = entry.name.equals("terminal") || entry.name.endsWith("mounted") ? rackPos : entry.name.equals("drone") ? dronePad.above() : clickPos == null ? target : clickPos;
                actor.connection.teleport(near.getX() + .5, near.getY() + .5, near.getZ() + 3, 180, 0); actor.getAbilities().flying = true; actor.onUpdateAbilities();
                actor.setItemInHand(InteractionHand.MAIN_HAND, expectedHeld); actor.inventoryMenu.broadcastChanges();
                if (entry.name.equals("server-mounted") || entry.name.equals("diskdrive-mounted")) { directOpen = true; require(rack.getMountable(entry.name.equals("server-mounted") ? 0 : 1).onActivate(actor, InteractionHand.MAIN_HAND, ItemStack.EMPTY, .5f, .5f), "Actual mounted menu activation failed"); }
            }, server);
        }
        void sneak(boolean value) { if (client.player == null) return; client.player.setShiftKeyDown(value); client.player.connection.send(new ServerboundPlayerCommandPacket(client.player, value ? ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY : ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY)); }
        void cursor(double x, double y) {
            double windowX = x * client.getWindow().getWidth() / client.getWindow().getGuiScaledWidth();
            double windowY = y * client.getWindow().getHeight() / client.getWindow().getGuiScaledHeight();
            try {
                // GLFW cursor warps do not deliver callbacks on this Windows driver.
                // Replay the actual vanilla input callback, as in the JEI scenario.
                var move = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
                move.setAccessible(true); move.invoke(client.mouseHandler, client.getWindow().getWindow(), windowX, windowY);
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Could not move the owned client's actual pointer", error); }
        }
        void localized(net.minecraft.network.chat.Component text) { ComponentContents content = text.getContents(); if (content instanceof TranslatableContents translation && (translation.getKey().startsWith("oc:") || translation.getKey().contains("opencomputers"))) require(I18n.exists(translation.getKey()), "Visible GUI translation is missing: " + translation.getKey()); for (var child : text.getSiblings()) localized(child); }
        void verifyScreen() {
            Screen screen = client.screen; localized(screen.getTitle()); for (var child : screen.children()) if (child instanceof AbstractWidget widget && widget.visible) localized(widget.getMessage());
            if (screen instanceof AbstractContainerScreen<?> container) { require(container.getMenu().containerId != 0 && client.player.containerMenu == container.getMenu(), "Menu was not installed through the normal client container path"); for (var slot : container.getMenu().slots) if (slot.isActive()) require(slot.x >= 0 && slot.y >= 0 && slot.x + 16 <= container.getXSize() && slot.y + 16 <= container.getYSize(), "Active slot outside actual GUI panel: " + entry.name + " slot=" + slot.index + " at=" + slot.x + "," + slot.y); }
            if (entry.name.equals("manual")) {
                String page = "%LANGUAGE%/block/robot.md";
                Iterable<String> content = Manual.contentFor(page);
                require(content != null && content.iterator().hasNext() && content.iterator().next().contains("机器人"), "Actual Chinese robot manual page did not resolve");
                Manual.navigate(page);
            }
        }
        void beforeScreen(ScreenEvent.Render.Pre event) {
            if (phase != 4 || !entry.name.equals("waypoint") || waypointBackdropVerified || frame - stageFrame < 30 || event.getScreen() != client.screen) return;
            event.getGuiGraphics().flush(); beforeWaypoint = Screenshot.takeScreenshot(client.getMainRenderTarget());
        }
        void afterScreen(ScreenEvent.Render.Post event) throws Exception {
            if (beforeWaypoint == null || event.getScreen() != client.screen) return;
            event.getGuiGraphics().flush();
            try (NativeImage before = beforeWaypoint; NativeImage after = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                beforeWaypoint = null; int changed = 0, outside = 0; double scale = client.getWindow().getGuiScale();
                int halfWidth = (int) (96 * scale), halfHeight = (int) (20 * scale);
                for (int y = 0; y < before.getHeight(); y++) for (int x = 0; x < before.getWidth(); x++) {
                    boolean panel = Math.abs(x - before.getWidth() / 2) <= halfWidth && Math.abs(y - before.getHeight() / 2) <= halfHeight;
                    if (panel) { if (before.getPixelRGBA(x, y) != after.getPixelRGBA(x, y)) changed++; }
                    else { outside++; require(before.getPixelRGBA(x, y) == after.getPixelRGBA(x, y), "Actual Waypoint changed/blurred the world outside its compact panel at " + x + "," + y); }
                }
                require(changed > 0, "Actual Waypoint screen drew no visible panel/field");
                before.writeToFile(directory.resolve("menu-19-waypoint-world-before.png")); after.writeToFile(directory.resolve("menu-19-waypoint-world-after.png"));
                waypointBackdropVerified = true; results.add("PASS C23.waypoint-backdrop actual same-frame screen Pre/Post preserves " + outside + " world pixels outside the compact panel, visible_panel_pixels=" + changed + " unchanged_global_blur=" + client.options.getMenuBackgroundBlurriness());
                results.add("SCREENSHOT menu-19-waypoint-world-before.png / menu-19-waypoint-world-after.png actual same-frame normal Screen.renderWithTooltip");
            }
        }
        void rendered() throws Exception {
            if (phase != 4 || client.screen == null) return;
            require(client.screen.getClass().getSimpleName().equals(entry.screen), "Actual GUI changed during frame capture: " + entry.name);
            long now = System.nanoTime(); if (lastFrame != 0) intervals.add((now - lastFrame) / 1_000_000d); lastFrame = now;
            if (frame - stageFrame < (captureStage == 0 ? 30 : 8)) return;
            if (captureStage == 0) {
                if (entry.name.equals("waypoint")) require(waypointBackdropVerified, "Actual Waypoint same-frame blur comparison did not run");
                if (List.of("robot", "screen", "tablet-screen", "terminal").contains(entry.name)) verifyDisplay();
                require(frameTooltips == 0, "Tooltip retained with pointer outside the actual GUI: " + entry.name); screenshot("");
                List<Double> sorted = new ArrayList<>(intervals); Collections.sort(sorted); double median = sorted.isEmpty() ? 0 : sorted.get(sorted.size() / 2); results.add("FRAME C23." + entry.name + " actual_fps=" + client.getFps() + " median_ms=" + String.format(java.util.Locale.ROOT, "%.2f", median));
                if (entry.name.equals("case") || entry.name.equals("robot")) { control = client.screen.children().stream().filter(child -> child instanceof Button button && button.getWidth() == 18 && button.getHeight() == 18).map(child -> (Button) child).findFirst().orElseThrow(() -> new AssertionError("Actual power control missing")); cursor(control.getX() + 8, control.getY() + 8); captureStage = 1; stageFrame = frame; return; }
                closeEntry(); return;
            }
            if (captureStage == 1) { require(frameTooltips > 0, "Real power-button hover rendered no tooltip"); screenshot("-power-hover"); require(client.screen.mouseClicked(control.getX() + 8, control.getY() + 8, 0), "Actual power-button click was not handled"); client.screen.mouseReleased(control.getX() + 8, control.getY() + 8, 0); cursor(1, 1); captureStage = 2; stageFrame = frame; return; }
            if (captureStage == 2) { require(frameTooltips == 0, "Power tooltip persisted after actual click and pointer movement"); screenshot("-power-clear"); results.add("PASS C23.tooltip " + entry.name + " actual hover/click/move frames have no retained tooltip"); if (entry.name.equals("case")) { var container = (AbstractContainerScreen<?>) client.screen; var slot = container.getMenu().slots.stream().filter(value -> value.container instanceof net.minecraft.world.entity.player.Inventory && value.getContainerSlot() == 9).findFirst().orElseThrow(); cursor(container.getGuiLeft() + slot.x + 8, container.getGuiTop() + slot.y + 8); captureStage = 3; stageFrame = frame; return; } closeEntry(); return; }
            require(frameTooltips > 0 && tooltipLines.stream().anyMatch(line -> line.contains("中文长名称")) && tooltipLines.stream().anyMatch(line -> line.contains("第二段")), "Actual long Chinese item tooltip did not render its real name/lore"); screenshot("-long-chinese-tooltip"); cursor(1, 1); results.add("PASS C23.long-text actual Chinese item name/lore rendered through the normal tooltip event and real frame within screen width"); closeEntry();
        }
        void screenshot(String suffix) throws Exception { String name = String.format(java.util.Locale.ROOT, "menu-%02d-%s%s.png", ENTRIES.indexOf(entry) + 1, entry.name, suffix); try (NativeImage image = Screenshot.takeScreenshot(client.getMainRenderTarget())) { image.writeToFile(directory.resolve(name)); } results.add("SCREENSHOT " + name + " actual_screen=" + client.screen.getClass().getName() + " frame=" + frame); }
        void verifyDisplay() throws Exception {
            Object display;
            if (entry.name.equals("screen")) display = client.level.getBlockEntity(screenPos).getClass().getMethod("buffer").invoke(client.level.getBlockEntity(screenPos));
            else if (entry.name.equals("robot")) {
                Object host = client.player.containerMenu.getClass().getField("host").get(client.player.containerMenu);
                display = host.getClass().getField("clientScreen").get(host);
            } else {
                var field = client.screen.getClass().getDeclaredField("buffer"); field.setAccessible(true); display = field.get(client.screen);
            }
            require(display instanceof TextBuffer, "Actual menu has no synchronized text buffer: " + entry.name);
            TextBuffer buffer = (TextBuffer) display;
            require(buffer.get(0, 0) == 'C', "Actual menu did not receive its native/terminal text: " + entry.name + " first=" + (int) buffer.get(0, 0));
            require(buffer.getForegroundColor(0, 0) != buffer.getBackgroundColor(0, 0), "Actual menu fixture selected equal colors on its original screen tier: " + entry.name);
            results.add("PASS C23.display " + entry.name + " actual synchronized first character=C with contrasting original-tier foreground/background");
        }
        void closeEntry() { client.screen.onClose(); if (client.player.containerMenu != client.player.inventoryMenu) client.player.closeContainer(); sneak(false); progress(5); }
        void fail(Throwable error) { if (finishing) return; results.add("FAIL C23.menus entry=" + (entry == null ? "setup" : entry.name) + " phase=" + phase + " waiting=" + waiting + ": " + error); try { if (client.screen != null) screenshot("-failure"); } catch (Exception ignored) {} finish(); }
        void finish() {
            if (finishing) return; finishing = true;
            if (beforeWaypoint != null) { beforeWaypoint.close(); beforeWaypoint = null; }
            if (releaseTablet) client.gameMode.releaseUsingItem(client.player);
            if (client.screen != null) client.screen.onClose(); sneak(false);
            cleanup = CompletableFuture.runAsync(() -> { ServerPlayer actor = actor(); actor.closeContainer(); for (Machine machine : machines) machine.stop(); if (spawned != null) spawned.discard(); var cells = new ArrayList<>(restored.entrySet()); Collections.reverse(cells); for (var cell : cells) { if (level.getBlockEntity(cell.getKey()) instanceof Container container) container.clearContent(); level.setBlock(cell.getKey(), cell.getValue(), 3); } if (inventoryCaptured) { actor.getInventory().clearContent(); for (int slot = 0; slot < inventory.size(); slot++) actor.getInventory().setItem(slot, inventory.get(slot)); actor.getInventory().selected = previousSlot; actor.getAbilities().flying = previousFlying; actor.onUpdateAbilities(); actor.setShiftKeyDown(false); actor.connection.teleport(previousPosition.x, previousPosition.y, previousPosition.z, actor.getYRot(), actor.getXRot()); actor.inventoryMenu.broadcastChanges(); } for (long chunk : forced) level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false); }, server);
        }
    }
}
