package ocfixture;

import static ocfixture.MultiplayerProtocol.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import li.cil.oc.api.CreativeTab;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Nanomachines;
import li.cil.oc.api.Network;
import li.cil.oc.api.component.RackMountable;
import li.cil.oc.api.driver.item.MutableProcessor;
import li.cil.oc.api.event.FileSystemAccessEvent;
import li.cil.oc.api.event.NetworkActivityEvent;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.internal.Drone;
import li.cil.oc.api.internal.Rack;
import li.cil.oc.api.internal.Robot;
import li.cil.oc.api.internal.TextBuffer;
import li.cil.oc.api.machine.Architecture;
import li.cil.oc.api.machine.MachineHost;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.SidedEnvironment;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.items.IItemHandler;

/** Finite production-payload families added to the real two-client TCP lifecycle. */
final class MultiplayerFamilies {
    static final BlockPos HOLOGRAM = MultiplayerServerContract.COMPUTER.above();
    static final BlockPos RACK = new BlockPos(5, 81, 11), ROBOT = new BlockPos(11, 81, 11), DRONE_PAD = new BlockPos(12, 80, 5);
    static final BlockPos TRANSPOSER = new BlockPos(10, 81, 5), ASSEMBLER = new BlockPos(11, 81, 13), PRINTER = new BlockPos(13, 81, 10), DISASSEMBLER = new BlockPos(12, 81, 13);
    static final BlockPos MICROCONTROLLER = new BlockPos(12, 81, 9);
    static final Map<BlockPos, String> NATIVE = new LinkedHashMap<>();
    static {
        NATIVE.put(new BlockPos(3, 81, 8), "adapter"); NATIVE.put(new BlockPos(3, 81, 9), "charger");
        NATIVE.put(new BlockPos(3, 81, 10), "netsplitter"); NATIVE.put(new BlockPos(3, 81, 11), "diskdrive");
        NATIVE.put(new BlockPos(3, 81, 12), "powerdistributor"); NATIVE.put(new BlockPos(3, 81, 13), "relay");
        NATIVE.put(PRINTER, "printer"); NATIVE.put(new BlockPos(11, 81, 13), "assembler");
        NATIVE.put(new BlockPos(12, 81, 13), "disassembler");
    }
    static final String MARKER = "oc:tcp-observer";
    private static final byte[] BIOS = ("local g=component.list('gpu')();local s=component.list('screen')();if g and s then "
        + "component.invoke(g,'bind',s);component.invoke(g,'setResolution',32,8);component.invoke(g,'set',1,1,'TCP actual native host');end;"
        + "while true do computer.pullSignal(.05) end").getBytes(StandardCharsets.UTF_8);
    private static MultiplayerFamilies active;
    final MinecraftServer server; final ServerLevel level; final Case computer; final List<String> results;
    final BiConsumer<Integer, CompoundTag> advance;
    Observer observer = new Observer(); final ConcurrentLinkedQueue<String> activity = new ConcurrentLinkedQueue<>();
    Rack rack; Robot robot; Drone drone; Entity droneEntity; MachineHost tablet, microcontroller; ItemStack tabletStack;
    Component gpu, hologram; int page, stage, age; TextBuffer buffer, terminalBuffer;
    boolean spawning, activityEnabled, printingPower; String activityMarker;
    Object terminal; BlockPos movedRobot;
    MultiplayerFamilies(MinecraftServer server, ServerLevel level, Case computer, List<String> results, BiConsumer<Integer, CompoundTag> advance) {
        this.server = server; this.level = level; this.computer = computer; this.results = results; this.advance = advance;
        active = this;
        NeoForge.EVENT_BUS.addListener(MultiplayerFamilies::joined);
        NeoForge.EVENT_BUS.addListener(MultiplayerFamilies::fileActivity);
        NeoForge.EVENT_BUS.addListener(MultiplayerFamilies::networkActivity);
    }
    static final class Observer extends AbstractManagedEnvironment {
        final ConcurrentLinkedQueue<List<Object>> signals = new ConcurrentLinkedQueue<>();
        Observer() { setNode(Network.newNode(this, Visibility.Network).create()); }
        @Override public void onMessage(Message message) {
            if ((message.name().equals("computer.checked_signal") || message.name().equals("computer.signal")) && message.data().length > 0)
                signals.add(java.util.Arrays.asList(message.data().clone()));
        }
        boolean contains(Object value) { return signals.stream().anyMatch(values -> values.contains(value)); }
        // Input payload (-1,-1) becomes the original non-precise Lua coordinates (0,0).
        boolean drop() { return signals.stream().anyMatch(values -> values.size() > 4 && values.get(1).equals("drop") && values.get(2) instanceof Number x && values.get(3) instanceof Number y && x.doubleValue() == 0 && y.doubleValue() == 0); }
        void clear() { signals.clear(); }
    }
    private static void joined(EntityJoinLevelEvent event) {
        MultiplayerFamilies run = active;
        if (run != null && run.spawning && event.getLevel() == run.level && event.getEntity() instanceof Drone value) { run.drone = value; run.droneEntity = event.getEntity(); }
    }
    private static void fileActivity(FileSystemAccessEvent.Server event) {
        MultiplayerFamilies run = active;
        if (run != null && run.activityEnabled && event.getWorld() == run.level) { event.getData().putString(MARKER, run.activityMarker); run.activity.add("filesystem"); }
    }
    private static void networkActivity(NetworkActivityEvent.Server event) {
        MultiplayerFamilies run = active;
        if (run != null && run.activityEnabled && event.getWorld() == run.level) { event.getData().putString(MARKER, run.activityMarker); run.activity.add("network"); }
    }
    static ItemStack item(String name) { var descriptor = li.cil.oc.api.Items.get(name); require(descriptor != null, "Missing actual TCP family item " + name); return descriptor.createItemStack(1); }
    static CompoundTag tag(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); }
    ServerPlayer player(boolean primary) { return server.getPlayerList().getPlayerByName(primary ? MultiplayerServerContract.ONE : MultiplayerServerContract.TWO); }
    void step(int id) { stage = id; age = 0; advance.accept(id, new CompoundTag()); }
    void step(int id, CompoundTag data) { stage = id; age = 0; advance.accept(id, data); }
    void near(BlockPos pos) {
        for (boolean primary : new boolean[]{true, false}) {
            ServerPlayer actor = player(primary); actor.closeContainer(); actor.setShiftKeyDown(false); actor.teleportTo(level, pos.getX() + (primary ? .5 : 1.5), pos.getY(), pos.getZ() - 2.5, java.util.Set.of(), 0, 0);
        }
    }
    void place(BlockPos pos, String name) { require(level.isEmptyBlock(pos), "TCP family fixture refuses occupied host cell " + pos); require(level.setBlock(pos, li.cil.oc.api.Items.get(name).block().defaultBlockState(), 3), "Actual host placement failed: " + name); }
    void notify(BlockPos pos) { var state = level.getBlockState(pos); level.sendBlockUpdated(pos, state, state, 3); }
    void observe(Node node) { observer.node().remove(); observer = new Observer(); node.connect(observer.node()); }
    Component component(MachineHost host, String name) {
        for (Node node : host.machine().node().network().nodes()) if (node instanceof Component value && value.name().equals(name) && (!List.of("gpu", "modem", "filesystem").contains(name) || host.componentSlot(node.address()) >= 0)) return value;
        throw new AssertionError("Actual TCP host has no " + name);
    }
    Object[] invoke(Component component, MachineHost host, String name, Object... arguments) throws Exception { return component.invoke(name, host.machine(), arguments); }
    void insert(Container inventory, ItemStack stack) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) if (inventory.getItem(slot).isEmpty() && inventory.canPlaceItem(slot, stack)) { inventory.setItem(slot, stack); return; }
        throw new AssertionError("Actual TCP inventory rejected " + stack);
    }
    ItemStack preset(String name) {
        ItemStack selected = ItemStack.EMPTY;
        for (ItemStack candidate : CreativeTab.instance.getDisplayItems()) if (candidate.is(item(name).getItem()) && tag(candidate).getInt("oc:tier") == 3) { selected = candidate.copy(); break; }
        require(!selected.isEmpty(), "No real creative TCP preset " + name);
        CompoundTag data = tag(selected); boolean slotted = name.equals("tablet"); String key = slotted ? "oc:items" : "oc:components";
        ListTag changed = new ListTag(); int processors = 0, flashes = 0;
        Class<? extends Architecture> architecture = li.cil.oc.api.Machine.architectures().stream().filter(type -> "Lua 5.3".equals(li.cil.oc.api.Machine.getArchitectureName(type))).findFirst().orElseThrow();
        for (Tag raw : data.getList(key, Tag.TAG_COMPOUND)) {
            CompoundTag slot = ((CompoundTag) raw).copy(); ItemStack part = ItemStack.parseOptional(level.registryAccess(), slotted ? slot.getCompound("item") : slot); var driver = Driver.driverFor(part);
            if (driver instanceof MutableProcessor processor) { processor.setArchitecture(part, architecture); processors++; }
            if (driver != null && driver.slot(part).equals("eeprom")) { part = li.cil.oc.api.Items.registerEEPROM("TCP native host", BIOS, new byte[0], false); flashes++; }
            if (slotted) { slot.put("item", part.saveOptional(level.registryAccess())); changed.add(slot); } else changed.add(part.saveOptional(level.registryAccess()));
        }
        if ((name.equals("drone") || name.equals("microcontroller")) && flashes == 0) { changed.add(li.cil.oc.api.Items.registerEEPROM("TCP reserved EEPROM", BIOS, new byte[0], false).saveOptional(level.registryAccess())); flashes++; }
        require(processors == 1 && flashes == 1, "Actual TCP preset CPU/EEPROM mismatch " + name + " " + processors + "/" + flashes);
        data.put(key, changed); selected.set(DataComponents.CUSTOM_DATA, CustomData.of(data)); return selected;
    }
    void begin() throws Exception {
        near(MultiplayerServerContract.COMPUTER);
        for (var entry : NATIVE.entrySet()) place(entry.getKey(), entry.getValue());
        place(TRANSPOSER, "transposer");
        for (int x = 4; x <= 8; x++) place(new BlockPos(x, 81, 12), "cable");
        // The real Case's south/front face is closed; approach its west connector.
        place(new BlockPos(7, 81, 11), "cable"); place(new BlockPos(7, 81, 10), "cable"); place(new BlockPos(7, 81, 9), "cable");
        place(HOLOGRAM, "hologram2"); place(RACK, "rack"); rack = (Rack) level.getBlockEntity(RACK);
        buffer = (TextBuffer) call(level.getBlockEntity(MultiplayerServerContract.SCREEN), "buffer"); gpu = component(computer, "gpu");
        hologram = (Component) ((SidedEnvironment) level.getBlockEntity(HOLOGRAM)).sidedNode(Direction.DOWN);
        computer.machine().node().connect(observer.node());
        for (ItemStack stack : List.of(item("hdd3"), item("lancard"))) insert(computer, stack);
        step(100);
    }
    boolean tick(Map<String, CompoundTag> receipts) throws Exception {
        age++; require(age < 1200, "TCP family timeout stage=" + stage + " receipts=" + receipts.keySet() + " signals=" + observer.signals);
        if (computer.machine().node() instanceof Connector power) power.changeBuffer(Double.POSITIVE_INFINITY);
        if (rack != null) for (int slot = 0; slot < 4; slot++) if (rack.getMountable(slot) instanceof MachineHost host && host.machine().node() instanceof Connector power) power.changeBuffer(Double.POSITIVE_INFINITY);
        if (printingPower) ((Connector) ((Environment) level.getBlockEntity(PRINTER)).node()).changeBuffer(1);
        if (receipts.size() < 2) return false;
        switch (stage) {
            case 100 -> {
                buffer.setMaximumResolution(80, 25); buffer.setResolution(32, 10); buffer.setViewport(30, 8); buffer.setColorDepth(TextBuffer.ColorDepth.EightBit);
                buffer.setPaletteColor(1, 0x123456); buffer.setForegroundColor(1, true); buffer.setBackgroundColor(0x112233);
                buffer.fill(0, 0, 32, 10, ' '); buffer.set(0, 0, "TCP delta", false); buffer.set(20, 0, "VERT", true);
                buffer.fill(0, 2, 3, 1, 'F'); buffer.copy(0, 2, 3, 1, 4, 0);
                buffer.rawSetText(0, 4, new int[][]{{'R','A','W'}}); buffer.rawSetForeground(0, 4, new int[][]{{0xFF0000,0xFF0000,0xFF0000}}); buffer.rawSetBackground(0, 4, new int[][]{{0x00FF00,0x00FF00,0x00FF00}});
                page = ((Number) invoke(gpu, computer, "allocateBuffer", 4, 2)[0]).intValue(); invoke(gpu, computer, "setActiveBuffer", page); invoke(gpu, computer, "set", 1, 1, "VRAM"); invoke(gpu, computer, "setActiveBuffer", 0); invoke(gpu, computer, "bitblt", 0, 1, 7, 4, 1, page, 1, 1);
                call(buffer, "flushCommands");
                invoke(hologram, computer, "clear"); invoke(hologram, computer, "set", 1, 2, 3, 2); invoke(hologram, computer, "fill", 2, 2, 1, 4, 1);
                invoke(hologram, computer, "setPaletteColor", 1, 0x112233); invoke(hologram, computer, "setScale", 1.25); invoke(hologram, computer, "setTranslation", .25, 0, -.5);
                invoke(hologram, computer, "setRotation", 30, 0, 1, 0); invoke(hologram, computer, "setRotationSpeed", 45, 0, 1, 0); call(level.getBlockEntity(HOLOGRAM), "flushPendingUpdates");
                step(101);
            }
            case 101 -> {
                results.add("PASS C19.screen-delta both real remote handlers apply color/palette/depth/resolution/viewport/maxResolution/set/vertical/fill/copy/raw text+foreground+background and actual GPU ramInit/bitblt");
                results.add("PASS C19.hologram both real remote projectors apply voxel/area, palette, scale, translation and rotation/speed from actual SDK callbacks");
                invoke(gpu, computer, "freeBuffer", page); buffer.setPowerState(false); call(buffer, "flushCommands"); invoke(hologram, computer, "clear"); call(level.getBlockEntity(HOLOGRAM), "flushPendingUpdates"); step(102);
            }
            case 102 -> {
                results.add("PASS C19.screen-clear both real remote handlers apply ramDestroy and screen power-off; actual hologram clear reaches both clients");
                buffer.setPowerState(true); buffer.set(0, 0, "TCP before tracking", false); call(buffer, "flushCommands");
                call(computer, "setColor", 0x224466);
                for (var entry : NATIVE.entrySet()) {
                    Object device = level.getBlockEntity(entry.getKey());
                    if (entry.getValue().equals("adapter")) call(device, "setSideOpen", Direction.EAST, false);
                    if (entry.getValue().equals("netsplitter")) call(device, "setSideOpen", Direction.EAST, true);
                    if (entry.getValue().equals("charger")) call(device, "invert");
                    if (entry.getValue().equals("diskdrive")) ((Container) device).setItem(0, item("floppy"));
                    notify(entry.getKey());
                }
                Component installedDisk = component(computer, "filesystem"); ItemStack actualDisk = computer.removeItem(computer.componentSlot(installedDisk.address()), 1);
                require(!tag(actualDisk).getCompound("oc:data").getCompound("node").getString("address").isEmpty(), "Real Case removal did not persist its actual HDD address");
                insert(computer, item("hdd1")); near(RACK); player(true).setItemInHand(InteractionHand.MAIN_HAND, actualDisk); player(true).inventoryMenu.broadcastChanges();
                call(type("li.cil.oc.common.command.CommandNetworking"), "clientLog", player(true), "OC_TCP_ACTUAL_COMMAND_LOG");
                var controller = Nanomachines.installController(player(true)); controller.changeBuffer(Double.POSITIVE_INFINITY);
                call(type("li.cil.oc.common.nanomachines.NanomachineNetwork"), "sendConfiguration", player(true)); if (controller.getTotalInputCount() > 0) controller.setInput(0, true);
                call(type("li.cil.oc.common.nanomachines.NanomachineNetwork"), "sendInputs", controller); call(type("li.cil.oc.common.nanomachines.NanomachineNetwork"), "sendPower", controller);
                CompoundTag data = new CompoundTag(); data.putUUID("owner", player(true).getUUID()); step(103, data);
            }
            case 103 -> {
                require(tag(player(true).getMainHandItem()).getBoolean("oc:unmanaged") && !tag(player(true).getMainHandItem()).getString("oc:lock").isEmpty(), "Real remote DriveMode/DriveLock did not update the held HDD");
                require(tag(player(true).getMainHandItem()).getCompound("oc:data").getCompound("node").getString("address").isEmpty(), "Actual unmanaged transition retained the old HDD node address");
                results.add("PASS C19.native native BE open sides/charger/disk state and native inventory, Command ClientLog, nonempty world loot/cycling catalog and nanomachine config/inputs/power cross TCP");
                step(123);
            }
            case 104 -> {
                require(((Number) call(rack, "mapping", 0, 0)).intValue() == Direction.EAST.get3DDataValue() && (Boolean) call(rack, "relayEnabled"), "Actual rack edits were not applied by production menu-scoped handler");
                results.add("PASS C19.rack real rack menu receives native mountable slots/data and production mapping/relay edits");
                player(true).closeContainer(); RackMountable blade = rack.getMountable(0); require(blade.onActivate(player(true), InteractionHand.MAIN_HAND, ItemStack.EMPTY, .5f, .5f), "Actual mounted blade did not open its production menu"); step(105);
            }
            case 105 -> {
                MachineHost blade = (MachineHost) rack.getMountable(0); require(blade.machine().isRunning(), "Real remote ServerPower did not start the mounted blade");
                player(true).closeContainer(); player(true).setItemInHand(InteractionHand.MAIN_HAND, item("terminal")); terminal = rack.getMountable(2);
                require(((RackMountable) terminal).onActivate(player(true), InteractionHand.MAIN_HAND, player(true).getMainHandItem(), .5f, .5f), "Actual rack terminal did not pair its held item");
                terminalBuffer = (TextBuffer) call(terminal, "buffer"); observe(terminalBuffer.node()); terminalBuffer.setResolution(32, 8); terminalBuffer.set(0, 0, "TCP terminal initial", false); call(terminalBuffer, "flushCommands");
                player(true).inventoryMenu.broadcastChanges(); observer.clear(); step(106);
            }
            case 106 -> {
                if (!observer.contains("TCP terminal input") || !observer.drop()) return false;
                results.add("PASS C19.terminal actual paired held terminal opens remotely; production key/clipboard/touch/drag/scroll/outside-drop input reaches the actual terminal buffer network");
                terminalBuffer.set(0, 0, "TCP terminal delta", false); call(terminalBuffer, "flushCommands"); step(107);
            }
            case 107 -> { results.add("PASS C19.terminal-delta actual production terminal update changes the open remote buffer"); prepareAgents(); step(108); }
            default -> { return later(receipts); }
        }
        return false;
    }
    void prepareRack() {
        ItemStack blade = item("server3"); IItemHandler inventory = Driver.itemHandlerFor(blade, player(true)); require(inventory != null, "Actual blade inventory provider absent");
        ItemStack cpu = item("cpu1"); ((MutableProcessor) Driver.driverFor(cpu)).setArchitecture(cpu, li.cil.oc.api.Machine.architectures().stream().filter(type -> "Lua 5.3".equals(li.cil.oc.api.Machine.getArchitectureName(type))).findFirst().orElseThrow());
        for (ItemStack part : List.of(cpu, item("ram3"), li.cil.oc.api.Items.registerEEPROM("TCP blade BIOS", BIOS, new byte[0], false))) {
            boolean inserted = false; for (int slot = 0; slot < inventory.getSlots(); slot++) if (inventory.insertItem(slot, part.copy(), true).isEmpty()) { require(inventory.insertItem(slot, part, false).isEmpty(), "Blade insertion changed"); inserted = true; break; } require(inserted, "Actual blade rejected " + part);
        }
        rack.setItem(0, blade); rack.setItem(1, item("diskdrivemountable")); rack.setItem(2, item("terminalserver"));
        player(true).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); player(true).inventoryMenu.broadcastChanges();
    }
    void prepareAgents() throws Exception {
        near(ROBOT); CreativeTab.instance.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess()));
        ItemStack robotItem = preset("robot"); player(true).setItemInHand(InteractionHand.MAIN_HAND, robotItem);
        require(((BlockItem) robotItem.getItem()).place(new BlockPlaceContext(player(true), InteractionHand.MAIN_HAND, robotItem, new BlockHitResult(Vec3.atCenterOf(ROBOT), Direction.UP, ROBOT, false))).consumesAction(), "Actual TCP Robot placement failed");
        robot = (Robot) call(level.getBlockEntity(ROBOT), "controller"); observe(robot.machine().node());
        if (robot.machine().node() instanceof Connector connector) connector.changeBuffer(Double.POSITIVE_INFINITY);
        ItemStack micro = preset("microcontroller"); player(true).setItemInHand(InteractionHand.MAIN_HAND, micro);
        require(level.isEmptyBlock(MICROCONTROLLER) && ((BlockItem) micro.getItem()).place(new BlockPlaceContext(player(true), InteractionHand.MAIN_HAND, micro, new BlockHitResult(Vec3.atCenterOf(MICROCONTROLLER), Direction.UP, MICROCONTROLLER, false))).consumesAction(), "Actual TCP Microcontroller placement failed");
        microcontroller = (MachineHost) level.getBlockEntity(MICROCONTROLLER);
        tabletStack = preset("tablet"); player(true).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); player(true).inventoryMenu.broadcastChanges();
    }
    boolean later(Map<String, CompoundTag> receipts) throws Exception {
        switch (stage) {
            case 123 -> {
                CompoundTag state = tag(player(true).getMainHandItem()); require(!state.getBoolean("oc:unmanaged") && state.getString("oc:lock").isEmpty() && state.getCompound("oc:data").getCompound("node").getString("address").isEmpty(), "Actual locked HDD mode transition failed to wipe address and clear its old lock");
                results.add("PASS C19.drive actual physically removed addressed HDD crosses unmanaged+lock and locked-to-managed+wiped/unlocked transitions through the real remote handler");
                prepareRack(); step(104);
            }
            case 108 -> {
                // Both clients first retain the real controller from its initial world snapshot.
                require(robot.machine().start(), "Actual native TCP Robot did not start");
                require(microcontroller.machine().start(), "Actual native TCP Microcontroller did not start"); microcontroller.machine().addUser(MultiplayerServerContract.ONE); microcontroller.machine().addUser(MultiplayerServerContract.TWO);
                robot.equipmentInventory().setItem(0, new ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
                BlockPos fire = ROBOT.relative(robot.toGlobal(Direction.SOUTH)); require(level.isEmptyBlock(fire), "Real Robot swing target is occupied"); level.setBlock(fire, Blocks.FIRE.defaultBlockState(), 3);
                require(Boolean.TRUE.equals(invoke(component(robot, "robot"), robot, "swing", 3)[0]), "Actual Robot did not extinguish the real adjacent fire"); step(124);
            }
            case 124 -> { require(Boolean.TRUE.equals(invoke(component(robot, "robot"), robot, "turn", true)[0]), "Actual TCP Robot turn failed"); step(125); }
            case 125 -> {
                robot.setName("TCP robot"); robot.setSelectedSlot(1); robot.mainInventory().setItem(1, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 3));
                robot.machine().addUser(MultiplayerServerContract.ONE); robot.machine().addUser(MultiplayerServerContract.TWO);
                invoke(component(robot, "redstone"), robot, "setOutput", Direction.UP.get3DDataValue(), 15);
                Component control = component(robot, "robot"); invoke(control, robot, "setLightColor", 0x336699);
                require(Boolean.TRUE.equals(invoke(control, robot, "move", 3)[0]), "Actual TCP Robot movement failed");
                movedRobot = BlockPos.containing(robot.xPosition(), robot.yPosition(), robot.zPosition()); require(!movedRobot.equals(ROBOT), "Actual Robot reported movement without relocating its physical proxy");
                near(movedRobot); observer.clear(); CompoundTag data = new CompoundTag(); data.putLong("position", movedRobot.asLong()); step(109, data);
            }
            case 109 -> {
                if (!observer.contains("TCP robot input") || !observer.drop()) return false;
                require(robot.selectedSlot() == 2, "Remote Agent SELECT did not reach the actual server Robot");
                results.add("PASS C19.robot actual native Robot movement preserves its remote controller identity/address, name/light/inventory/users mirrors and authenticated menu input/selection including outside mouse-up");
                robot.machine().removeUser(MultiplayerServerContract.TWO); robot.machine().crash("C19 actual machine error"); call(robot, "synchronize");
                microcontroller.machine().removeUser(MultiplayerServerContract.TWO); microcontroller.machine().crash("C19 actual MCU error");
                step(110);
            }
            case 110 -> {
                results.add("PASS C19.robot-state actual remote Robot and Microcontroller receive user removal and genuine Machine.crash error/running-state mirrors");
                near(DRONE_PAD.above()); ItemStack droneItem = preset("drone"); player(true).setItemInHand(InteractionHand.MAIN_HAND, droneItem); spawning = true;
                try { require(droneItem.getItem().useOn(new UseOnContext(player(true), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(DRONE_PAD).add(0, .5, 0), Direction.UP, DRONE_PAD, false))).consumesAction(), "Actual TCP Drone item spawn failed"); } finally { spawning = false; }
                require(drone != null, "Actual Drone spawn event absent"); drone.setName("TCP Drone"); call(drone, "setStatusText", "TCP entity"); call(drone, "setLightColor", 0x225588);
                player(true).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); player(true).inventoryMenu.broadcastChanges();
                CompoundTag data = new CompoundTag(); data.putUUID("entity", droneEntity.getUUID()); step(111, data);
            }
            case 111 -> {
                require(drone.machine().isRunning(), "Remote DronePower did not start the actual native Drone");
                results.add("PASS C19.drone actual vanilla entity tracking carries Drone name/status/light and real Agent menu power starts its native VM over TCP");
                near(MultiplayerServerContract.SCREEN); player(true).setItemInHand(InteractionHand.MAIN_HAND, tabletStack); tablet = (MachineHost) call(type("li.cil.oc.server.portable.TabletManager"), "get", tabletStack, player(true));
                require(tablet.machine().node() instanceof Connector, "Actual held Tablet has no connector"); ((Connector) tablet.machine().node()).changeBuffer(Double.POSITIVE_INFINITY);
                observe(tablet.machine().node()); require(tablet.machine().start(), "Actual native TCP Tablet did not start"); player(true).inventoryMenu.broadcastChanges(); observer.clear(); step(112);
            }
            case 112 -> {
                if (!observer.contains("TCP tablet input") || !observer.drop()) return false;
                require(tag(tabletStack).getBoolean("oc:isRunning"), "Actual running Tablet did not write its real held-stack state");
                results.add("PASS C19.tablet actual native held-stack running state and production Tablet open/full/session input reach the actual host, including outside DROP");
                TextBuffer screen = tabletBuffer(); screen.set(0, 0, "TCP tablet delta", false); call(screen, "flushCommands"); step(113);
            }
            case 113 -> {
                results.add("PASS C19.tablet-delta the production TabletUpdate delta changes the open real remote buffer");
                observer.clear(); step(114);
            }
            case 114 -> {
                if (age < 25) return false;
                require(player(true).getInventory().selected == 1, "Actual remote vanilla carried-slot packet did not change the selected hotbar slot");
                require(!observer.contains("TCP stale tablet"), "Former held Tablet session accepted input after actual slot change");
                require(!((Map<?, ?>) field(type("li.cil.oc.common.network.PortableNetworking"), "SESSIONS")).containsKey(player(true).getUUID()), "Former held Tablet session survived slot change");
                results.add("PASS C19.tablet-session actual held-slot change closes the production session and rejects old-token input");
                near(MultiplayerServerContract.COMPUTER); activityEnabled = true; activityMarker = "real-tcp-case";
                invoke(component(computer, "filesystem"), computer, "exists", "tcp-activity-missing"); invoke(component(computer, "modem"), computer, "broadcast", 123, "TCP activity");
                activityMarker = "real-tcp-agent"; invoke(component(robot, "filesystem"), robot, "exists", "tcp-agent-activity-missing"); invoke(component(robot, "modem"), robot, "broadcast", 123, "TCP agent activity");
                activityEnabled = false;
                call(type("li.cil.oc.server.PacketSender"), "sendSound", level, 8.5, 82d, 8.5, 440, 100);
                call(type("li.cil.oc.server.PacketSender"), "sendSound", level, 8.5, 82d, 8.5, ".-");
                call(level.getBlockEntity(TRANSPOSER), "onTransposerActivity");
                Object relay = level.getBlockEntity(new BlockPos(3, 81, 13)); call(relay, "tryEnqueuePacket", Direction.NORTH, Network.newPacket(observer.node().address(), null, 321, new Object[]{"TCP relay"}));
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, 8.5, 83, 8.5, 6, .1, .1, .1, 0);
                player(true).setItemInHand(InteractionHand.MAIN_HAND, item("analyzer")); player(true).inventoryMenu.broadcastChanges(); call(type("li.cil.oc.common.network.ToolNetworking"), "sendAnalyze", computer.machine().node().address(), player(true));
                step(115);
            }
            case 115 -> {
                require(activity.contains("filesystem") && activity.contains("network"), "Actual production activity did not fire the original server API events");
                results.add("PASS C19.activity both TCP clients receive real Case and moving-agent filesystem/network API events with server-mutated NBT; real speaker/pattern, vanilla particles, relay and transposer production effects also cross TCP");
                prepareWorkstations(); step(130);
            }
            case 130 -> {
                require((Boolean) call(level.getBlockEntity(ASSEMBLER), "busy"), "Actual remote RobotAssemblerStart did not consume its valid real template");
                player(true).closeContainer();
                Object printer = level.getBlockEntity(PRINTER); Container inventory = (Container) printer; inventory.setItem(0, item("chamelium")); inventory.setItem(1, item("inkcartridge"));
                Component printing = (Component) ((Environment) printer).node(); invoke(printing, computer, "addShape", 0, 0, 0, 16, 16, 16, "minecraft:block/stone"); invoke(printing, computer, "setLabel", "TCP print");
                require(Boolean.TRUE.equals(invoke(printing, computer, "commit", 1)[0]), "Actual printer template failed to commit");
                printingPower = true;
                Container disassembler = (Container) level.getBlockEntity(DISASSEMBLER); require(disassembler.canPlaceItem(0, item("case1")), "Actual disassembler rejected recipe-recoverable Case"); disassembler.setItem(0, item("case1"));
                call(level.getBlockEntity(MultiplayerServerContract.SCREEN), "setInvertTouchMode", true); call(level.getBlockEntity(MultiplayerServerContract.SCREEN), "synchronizeTouchMode", true);
                step(131);
            }
            case 131 -> {
                results.add("PASS C19.workstations real assembler menu button and actual printer/disassembler jobs reach native block/menu update handlers; physical screen inverted-touch state also reaches both remote mirrors");
                near(MultiplayerServerContract.SCREEN);
                for (boolean primary : new boolean[]{true, false}) { player(primary).setItemInHand(InteractionHand.MAIN_HAND, item("analyzer")); player(primary).inventoryMenu.broadcastChanges(); call(type("li.cil.oc.common.network.ScreenNetworking"), "open", player(primary), level.getBlockEntity(MultiplayerServerContract.SCREEN)); }
                observe(computer.machine().node()); step(118);
            }
            case 118 -> {
                require(observer.drop() && tag(player(true).getMainHandItem()).getString("oc:clipboard").contains("TCP before tracking"), "Actual physical-screen outside DROP / CopyToAnalyzer did not reach its real handlers");
                ServerLevel nether = server.getLevel(Level.NETHER); require(nether != null, "Actual default Nether unavailable");
                nether.setChunkForced(0, 0, true); for (int x = 6; x <= 11; x++) for (int z = 3; z <= 8; z++) nether.setBlock(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState(), 3);
                for (boolean primary : new boolean[]{true, false}) { player(primary).closeContainer(); player(primary).teleportTo(nether, primary ? 7.5 : 9.5, 81, 5.5, java.util.Set.of(), 0, 0); }
                step(119);
            }
            case 119 -> {
                if (age < 25) return false;
                require(!observer.contains("TCP wrong dimension"), "Old-dimensional screen session accepted remote input");
                require(((Map<?, ?>) field(call(level.getBlockEntity(MultiplayerServerContract.KEYBOARD), "keyboard"), "pressedKeys")).isEmpty(), "Dimension departure left real keyboard keys pressed");
                buffer.set(0, 0, "TCP tracking full", false);
                page = ((Number) invoke(gpu, computer, "allocateBuffer", 4, 2)[0]).intValue(); invoke(gpu, computer, "setActiveBuffer", page); invoke(gpu, computer, "set", 1, 1, "BACK"); invoke(gpu, computer, "setActiveBuffer", 0); invoke(gpu, computer, "bitblt", 0, 1, 7, 4, 1, page, 1, 1); call(buffer, "flushCommands");
                invoke(hologram, computer, "set", 1, 2, 3, 3); call(level.getBlockEntity(HOLOGRAM), "flushPendingUpdates");
                near(MultiplayerServerContract.SCREEN); step(120);
            }
            case 120 -> {
                results.add("PASS C19.dimension-tracking both actual clients leave the dimension/old chunks, reject old screen tokens and release held keys; re-entry rebuilds changed screen+VRAM/hologram and Robot full mirrors");
                buffer.set(0, 0, "TCP delta after return", false); invoke(gpu, computer, "setActiveBuffer", page); invoke(gpu, computer, "set", 1, 1, "NEW2"); invoke(gpu, computer, "setActiveBuffer", 0); invoke(gpu, computer, "bitblt", 0, 1, 7, 4, 1, page, 1, 1); call(buffer, "flushCommands");
                invoke(hologram, computer, "set", 1, 2, 3, 1); call(level.getBlockEntity(HOLOGRAM), "flushPendingUpdates"); step(121);
            }
            case 121 -> {
                results.add("PASS C19.continuity production screen/VRAM/hologram deltas remain continuous after actual unload, dimension roundtrip and full-state recovery on both independent TCP clients");
                results.add("SCOPE C19: original 88-entry map retained, including fifteen text subcommands and the pinned non-dispatched MultiPartPlace declaration; these finite scenes verify transport/application, with device semantics and visual/audio quality reusing their independent actual reports");
                return true;
            }
            default -> throw new AssertionError("Unknown TCP family phase " + stage);
        }
        return false;
    }
    TextBuffer tabletBuffer() { return (TextBuffer) call(field(tablet, "runtime"), "screen"); }
    void prepareWorkstations() {
        near(ASSEMBLER); Container inventory = (Container) level.getBlockEntity(ASSEMBLER); inventory.setItem(0, item("case1")); inventory.setItem(16, item("cpu1")); inventory.setItem(17, item("ram1"));
        player(true).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); player(true).inventoryMenu.broadcastChanges();
    }
    void close() { active = null; observer.node().remove(); if (robot != null) robot.machine().stop(); if (microcontroller != null) microcontroller.machine().stop(); if (droneEntity != null) droneEntity.discard(); if (tablet != null) tablet.machine().stop(); ServerLevel nether = server.getLevel(Level.NETHER); if (nether != null) nether.setChunkForced(0, 0, false); }
}
