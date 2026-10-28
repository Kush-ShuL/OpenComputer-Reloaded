package ocfixture;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import li.cil.oc.api.API;
import li.cil.oc.api.CreativeTab;
import li.cil.oc.api.Driver;
import li.cil.oc.api.driver.item.MutableProcessor;
import li.cil.oc.api.event.FileSystemAccessEvent;
import li.cil.oc.api.event.NetworkActivityEvent;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.internal.Robot;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Node;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Real activity callbacks cross the normal client connection; no packet or client event is injected. */
@OnlyIn(Dist.CLIENT)
public final class ActivityClientContract {
    private static final String MARKER = "ocfixture:activity";
    private static final byte[] BIOS = "while true do computer.pullSignal(1) end".getBytes(StandardCharsets.UTF_8);
    private static volatile Run active;
    private static boolean registered;
    private ActivityClientContract() {}

    public static void start(Minecraft client, Path directory, List<String> results) {
        require(active == null && client.player != null && client.level != null && client.hasSingleplayerServer(), "Activity proof requires its own normally loaded integrated world");
        if (!registered) {
            registered = true;
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ActivityClientContract::serverFile);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ActivityClientContract::clientFile);
            NeoForge.EVENT_BUS.addListener(ActivityClientContract::serverNetwork);
            NeoForge.EVENT_BUS.addListener(ActivityClientContract::clientNetwork);
            NeoForge.EVENT_BUS.addListener(ActivityClientContract::sound);
            NeoForge.EVENT_BUS.addListener(ActivityClientContract::serverTick);
        }
        active = new Run(client, results);
        try { active.start(); } catch (Throwable error) { active.fail(error); }
    }
    public static boolean isRunning() { return active != null; }
    public static void tick() {
        Run run = active;
        if (run == null) return;
        try { run.tick(); } catch (Throwable error) { run.fail(error); }
        if (run.finishing && run.cleanup != null && run.cleanup.isDone()) {
            try { run.cleanup.join(); } catch (Throwable error) { run.results.add("FAIL C19.activity cleanup: " + error); }
            active = null;
        }
    }
    public static void close() { Run run = active; if (run != null) run.fail(new AssertionError("Client closed before actual activity/state proof completed")); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static ItemStack item(String name) { var entry = li.cil.oc.api.Items.get(name); require(entry != null, "Missing actual activity fixture hardware " + name); return entry.createItemStack(1); }

    private static void serverFile(FileSystemAccessEvent.Server event) {
        Run run = active;
        if (run == null || run.finishing || !run.owns(event.getNode()) || run.marker.isEmpty()) return;
        run.decorate(event.getData(), event.getNode(), false);
        if (run.cancelServer) event.setCanceled(true);
        run.sounds.add(ResourceLocation.parse(event.getSound().contains(":") ? event.getSound() : "opencomputers:" + event.getSound()));
        run.serverEvents.add(new Seen(false, event.getTileEntity() != null, event.getX(), event.getY(), event.getZ(), event.getData().copy(), event.isCanceled()));
    }
    private static void clientFile(FileSystemAccessEvent.Client event) {
        Run run = active;
        if (run == null || run.finishing || !event.getData().contains(MARKER)) return;
        if (run.cancelClient) event.setCanceled(true);
        run.clientEvents.add(new Seen(false, event.getTileEntity() != null, event.getX(), event.getY(), event.getZ(), event.getData().copy(), event.isCanceled()));
    }
    private static void serverNetwork(NetworkActivityEvent.Server event) {
        Run run = active;
        if (run == null || run.finishing || !run.owns(event.getNode()) || run.marker.isEmpty()) return;
        run.decorate(event.getData(), event.getNode(), true);
        run.serverEvents.add(new Seen(true, event.getTileEntity() != null, event.getX(), event.getY(), event.getZ(), event.getData().copy(), false));
    }
    private static void clientNetwork(NetworkActivityEvent.Client event) {
        Run run = active;
        if (run == null || run.finishing || !event.getData().contains(MARKER)) return;
        CompoundTag data = event.getData();
        run.clientEvents.add(new Seen(true, event.getTileEntity() != null, event.getX(), event.getY(), event.getZ(), data.copy(), false));
        if (data.getBoolean("barrier")) run.barrier = data.getString(MARKER);
    }
    private static void sound(PlaySoundEvent event) { Run run = active; if (run != null && event.getSound() != null && run.sounds.contains(event.getSound().getLocation())) run.soundCount.incrementAndGet(); }
    private static void serverTick(ServerTickEvent.Post event) {
        Run run = active;
        if (run == null || run.finishing || event.getServer() != run.server) return;
        try { run.serverTick(); } catch (Throwable error) { run.serverError = error; }
    }
    private record Seen(boolean network, boolean blockHosted, double x, double y, double z, CompoundTag data, boolean canceled) {}

    private static final class Run {
        final Minecraft client;
        final MinecraftServer server;
        final ServerLevel level;
        final List<String> results;
        final BlockPos casePos, robotPos;
        final Map<BlockPos, BlockState> restored = new LinkedHashMap<>();
        final Set<Long> forced = new LinkedHashSet<>();
        final ConcurrentLinkedQueue<Seen> serverEvents = new ConcurrentLinkedQueue<>(), clientEvents = new ConcurrentLinkedQueue<>();
        final Set<ResourceLocation> sounds = ConcurrentHashMap.newKeySet();
        final AtomicInteger soundCount = new AtomicInteger();
        final long started = System.nanoTime(), delay;
        Case computer;
        Robot robot;
        Component caseFs, robotFs, caseModem, robotModem, control;
        BlockPos movedPos;
        Object originalClientController;
        CompletableFuture<Void> placement, command, cleanup;
        volatile Throwable serverError;
        volatile String marker = "", barrier = "";
        volatile boolean ready, finishing, cancelServer, cancelClient;
        String playerName;
        int sequence, phase;
        long nextAt, previousLight;

        Run(Minecraft client, List<String> results) {
            this.client = client; server = client.getSingleplayerServer(); level = server.getLevel(client.level.dimension()); this.results = results;
            casePos = client.player.blockPosition().east(5).above(3); robotPos = casePos.south(5);
            delay = API.config.getInt("misc.diskActivitySoundDelay");
        }
        void remember(BlockPos pos) {
            if (restored.containsKey(pos)) return;
            if (level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true)) forced.add(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            level.getChunkAt(pos); require(level.isEmptyBlock(pos) && level.getBlockEntity(pos) == null, "Activity proof refuses occupied cell " + pos);
            restored.put(pos, level.getBlockState(pos));
        }
        void start() {
            require(delay >= 0 && API.config.getBoolean("computer.canComputersBeOwned"), "Activity/state proof requires enabled actual activity and ownership configuration");
            placement = CompletableFuture.runAsync(() -> {
                remember(casePos);
                require(level.setBlock(casePos, li.cil.oc.api.Items.get("case3").block().defaultBlockState(), 3), "Could not place actual activity Case");
                computer = (Case) level.getBlockEntity(casePos);
                for (ItemStack part : List.of(item("hdd1"), item("lancard"))) {
                    boolean inserted = false;
                    for (int slot = 0; slot < computer.getContainerSize(); slot++) if (computer.getItem(slot).isEmpty() && computer.canPlaceItem(slot, part)) { computer.setItem(slot, part); inserted = true; break; }
                    require(inserted, "Actual Case rejected activity hardware " + part);
                }
                CreativeTab.instance.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess()));
                ItemStack preset = null;
                for (ItemStack stack : CreativeTab.instance.getDisplayItems()) if (stack.is(item("robot").getItem()) && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt("oc:tier") == 3) { preset = stack.copy(); break; }
                require(preset != null, "CreativeTab supplied no real configured Robot");
                var architecture = li.cil.oc.api.Machine.architectures().stream().filter(type -> "Lua 5.3".equals(li.cil.oc.api.Machine.getArchitectureName(type))).findFirst().orElseThrow();
                CompoundTag data = preset.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); ListTag changed = new ListTag();
                int cpus = 0, flashes = 0;
                for (Tag raw : data.getList("oc:components", Tag.TAG_COMPOUND)) {
                    ItemStack part = ItemStack.parseOptional(level.registryAccess(), (CompoundTag) raw); var driver = Driver.driverFor(part);
                    if (driver instanceof MutableProcessor processor) { processor.setArchitecture(part, architecture); cpus++; }
                    if (driver != null && "eeprom".equals(driver.slot(part))) { part = li.cil.oc.api.Items.registerEEPROM("Activity/state native BIOS", BIOS, new byte[0], false); flashes++; }
                    changed.add(part.saveOptional(level.registryAccess()));
                }
                require(cpus == 1 && flashes == 1, "Activity Robot must retain its real CPU/EEPROM layout");
                data.put("oc:components", changed); preset.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
                remember(robotPos); for (Direction side : Direction.values()) remember(robotPos.relative(side));
                var actor = server.getPlayerList().getPlayer(client.player.getUUID()); require(actor != null, "Actual integrated player is unavailable"); playerName = actor.getGameProfile().getName();
                ItemStack held = actor.getMainHandItem().copy();
                try { actor.setItemInHand(InteractionHand.MAIN_HAND, preset); require(((BlockItem) preset.getItem()).place(new BlockPlaceContext(actor, InteractionHand.MAIN_HAND, preset, new BlockHitResult(Vec3.atCenterOf(robotPos), Direction.UP, robotPos, false))).consumesAction(), "Actual Robot placement failed"); }
                finally { actor.setItemInHand(InteractionHand.MAIN_HAND, held); }
                robot = (Robot) level.getBlockEntity(robotPos); movedPos = robotPos.relative(robot.toGlobal(Direction.SOUTH));
            }, server);
        }
        Component component(li.cil.oc.api.machine.Machine machine, String name, java.util.function.Predicate<String> installed) {
            for (Node node : machine.node().network().nodes()) if (node instanceof Component component && name.equals(component.name()) && installed.test(node.address())) return component;
            throw new AssertionError("Actual host has no installed " + name + " component");
        }
        void serverTick() {
            if (ready || placement == null || !placement.isDone() || placement.isCompletedExceptionally() || robot == null) return;
            if (computer.machine().node().network() == null || robot.machine().node().network() == null) return;
            caseFs = component(computer.machine(), "filesystem", address -> computer.componentSlot(address) >= 0);
            caseModem = component(computer.machine(), "modem", address -> computer.componentSlot(address) >= 0);
            robotFs = component(robot.machine(), "filesystem", address -> robot.componentSlot(address) >= 0);
            robotModem = component(robot.machine(), "modem", address -> robot.componentSlot(address) >= 0);
            control = component(robot.machine(), "robot", address -> true);
            require(robot.machine().users().length == 0 && robot.machine().start(), "Actual sound-style Robot native BIOS could not start cleanly");
            ready = true;
        }
        boolean owns(Node node) { return node == caseFs || node == caseModem || node == robotFs || node == robotModem; }
        void decorate(CompoundTag data, Node node, boolean network) {
            data.putString(MARKER, marker); data.putInt("sequence", ++sequence); data.putBoolean("network", network);
            data.putString("host", node == caseFs || node == caseModem ? "case" : "robot");
            data.putBoolean("barrier", node == caseModem);
            CompoundTag nested = new CompoundTag(); nested.putByteArray("bytes", new byte[]{0, 127, (byte)128, (byte)255}); data.put("custom", nested);
        }
        interface Operation { void run() throws Exception; }
        void execute(Operation operation) { command = CompletableFuture.runAsync(() -> { try { operation.run(); } catch (Exception error) { throw new IllegalStateException(error); } }, server); }
        boolean done() { if (!command.isDone()) return false; command.join(); return true; }
        void begin(String name, Operation operation) {
            marker = name; barrier = ""; soundCount.set(0); execute(operation);
        }
        void file(Component fs) throws Exception { fs.invoke("exists", fs == robotFs ? robot.machine() : computer.machine(), "ocfixture-activity-missing"); }
        void broadcast(Component modem) throws Exception { require(Boolean.TRUE.equals(modem.invoke("broadcast", modem == robotModem ? robot.machine() : computer.machine(), 13821, marker)[0]), "Actual modem broadcast failed"); }
        List<Seen> seen(ConcurrentLinkedQueue<Seen> queue) { return queue.stream().filter(event -> marker.equals(event.data.getString(MARKER))).toList(); }
        void transported() {
            List<Seen> sent = seen(serverEvents);
            for (Seen received : seen(clientEvents)) {
                Seen source = sent.stream().filter(event -> event.data.getInt("sequence") == received.data.getInt("sequence")).findFirst().orElseThrow();
                require(source.network == received.network && source.blockHosted == received.blockHosted && source.x == received.x && source.y == received.y && source.z == received.z && source.data.equals(received.data), "Real activity payload changed mutable custom NBT/precise coordinates/host branch");
            }
        }
        Object controller(BlockPos pos) throws ReflectiveOperationException {
            Object proxy = client.level.getBlockEntity(pos); if (!(proxy instanceof Robot)) return null;
            return proxy.getClass().getMethod("controller").invoke(proxy);
        }
        boolean error(Object controller) throws ReflectiveOperationException { return controller.getClass().getField("clientHasErrored").getBoolean(controller); }
        boolean canInteract(Object controller, String name) throws ReflectiveOperationException { return (boolean) controller.getClass().getMethod("canInteract", String.class).invoke(controller, name); }
        long light() throws ReflectiveOperationException { Object tile = client.level.getBlockEntity(casePos); return tile.getClass().getField("lastFileSystemAccess").getLong(tile); }

        void tick() throws Exception {
            if (finishing) return;
            if (System.nanoTime() - started > TimeUnit.MINUTES.toNanos(2)) throw new AssertionError("Activity/state timeout phase=" + phase + ", marker=" + marker + ", barrier=" + barrier + ", serverEvents=" + serverEvents.size() + ", clientEvents=" + clientEvents.size() + ", paused=" + client.isPaused());
            if (serverError != null) throw new IllegalStateException("Actual activity/state server operation failed", serverError);
            if (placement.isDone()) placement.join();
            if (phase == 0) {
                if (!ready || client.level.getBlockEntity(casePos) == null || controller(robotPos) == null) return;
                originalClientController = controller(robotPos); require(canInteract(originalClientController, "OC_Visitor"), "Fresh actual client Robot already has a restricted users mirror");
                begin("normal", () -> { file(caseFs); file(robotFs); broadcast(robotModem); broadcast(caseModem); }); phase = 1;
            } else if (phase == 1) {
                if (!done() || !marker.equals(barrier)) return;
                transported(); require(seen(serverEvents).size() == 4 && seen(clientEvents).size() == 4, "Actual two-host filesystem/network events did not all cross the connection");
                require(seen(clientEvents).stream().filter(event -> event.blockHosted).count() == 2 && seen(clientEvents).stream().filter(event -> !event.blockHosted).count() == 2, "Actual Case/Robot host branches were flattened");
                require(soundCount.get() == 2 && light() > 0, "Actual default filesystem sound/Case indicator did not run after the client event");
                results.add("PASS C19.activity.normal real Case/Robot HDD and modem callbacks post both Server/Client APIs with exact custom nested NBT, signed byte data, precise coordinates and BE/coordinate host branches; actual filesystem sounds and Case indicator run");
                nextAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delay + 50); phase = 2;
            } else if (phase == 2) {
                if (System.nanoTime() < nextAt) return;
                begin("server-cancel", () -> { cancelServer = true; file(caseFs); file(caseFs); cancelServer = false; file(caseFs); broadcast(caseModem); }); phase = 3;
            } else if (phase == 3) {
                if (!done() || !marker.equals(barrier)) return;
                transported(); require(seen(serverEvents).stream().filter(event -> !event.network && event.canceled).count() == 2 && seen(clientEvents).stream().filter(event -> !event.network).count() == 1 && soundCount.get() == 1, "Actual Server filesystem cancellation sent a packet/sound or incorrectly advanced node/sound cooldown");
                results.add("PASS C19.activity.serverCancel two canceled real filesystem callbacks still reach Server listeners; neither sends client activity/sound nor advances cooldown, so immediate allowed retry crosses the real connection");
                nextAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delay + 50); phase = 4;
            } else if (phase == 4) {
                if (System.nanoTime() < nextAt) return;
                previousLight = light(); cancelClient = true;
                begin("client-cancel", () -> { file(caseFs); broadcast(caseModem); }); phase = 5;
            } else if (phase == 5) {
                if (!done() || !marker.equals(barrier)) return;
                transported(); require(seen(clientEvents).stream().filter(event -> !event.network && event.canceled).count() == 1 && soundCount.get() == 0 && light() == previousLight, "Actual canceled Client filesystem event still played sound or changed the Case activity indicator");
                cancelClient = false; marker = "";
                results.add("PASS C19.activity.clientCancel real received Client API cancellation suppresses default sound and Case indicator; ordered real modem event is the connection barrier");
                execute(() -> { robot.machine().addUser(playerName); require(robot.machine().users().length == 1, "Actual Robot user registration failed"); require(robot.machine().crash("ocfixture robot mirror"), "Actual native Robot did not crash through its public Machine API"); }); phase = 6;
            } else if (phase == 6) {
                if (!done()) return;
                Object mirror = controller(robotPos); if (mirror == null || !error(mirror) || canInteract(mirror, "OC_Visitor")) return;
                require(mirror == originalClientController && canInteract(mirror, playerName), "Actual Robot error/users snapshot replaced its stable controller or denied its real user");
                results.add("PASS C19.robot.mirror actual Machine.addUser/crash updates the native client error/users mirror; permitted player allowed and an absent name rejected without a client VM");
                execute(() -> { Object[] moved = control.invoke("move", robot.machine(), 3); require(moved != null && Boolean.TRUE.equals(moved[0]) && level.getBlockEntity(movedPos) instanceof Robot, "Actual stopped Robot move failed"); robot = (Robot) level.getBlockEntity(movedPos); }); phase = 7;
            } else if (phase == 7) {
                if (!done()) return;
                Object mirror = controller(movedPos); if (mirror == null) return;
                require(mirror == originalClientController && error(mirror) && canInteract(mirror, playerName) && !canInteract(mirror, "OC_Visitor"), "Actual Robot proxy move lost its stable error/users mirror");
                execute(() -> { require(robot.machine().removeUser(playerName), "Actual Robot user removal failed"); require(robot.machine().start(), "Actual native Robot could not restart"); }); phase = 8;
            } else {
                if (!done()) return;
                Object mirror = controller(movedPos); if (mirror == null || error(mirror) || !canInteract(mirror, "OC_Visitor")) return;
                require(mirror == originalClientController, "Actual restart/user removal replaced the client controller");
                results.add("PASS C19.robot.clear actual move preserves error/users across proxy rebinding; real restart and Machine.removeUser clear the error and restore empty-list client access");
                results.add("LIMIT C19.activity: real normally loaded integrated connection, native callbacks and client mirrors; dedicated remote TCP, Rack Server indicators and client listening quality remain separate evidence");
                finish();
            }
        }
        void fail(Throwable error) { if (finishing) return; results.add("FAIL C19.activity phase=" + phase + ": " + error); finish(); }
        void finish() {
            if (finishing) return; finishing = true;
            cleanup = CompletableFuture.runAsync(() -> {
                if (robot != null) robot.machine().stop();
                var entries = new ArrayList<>(restored.entrySet()); java.util.Collections.reverse(entries);
                for (var entry : entries) { if (level.getBlockEntity(entry.getKey()) instanceof Container inventory) inventory.clearContent(); level.setBlock(entry.getKey(), entry.getValue(), 3); }
                for (long chunk : forced) level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
            }, server);
        }
    }
}
