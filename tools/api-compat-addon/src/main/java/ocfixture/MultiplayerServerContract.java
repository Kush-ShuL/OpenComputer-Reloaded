package ocfixture;

import static ocfixture.MultiplayerProtocol.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.item.MutableProcessor;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;

/** An owned dedicated server with two independent, non-memory TCP clients. */
public final class MultiplayerServerContract {
    static final BlockPos SCREEN = new BlockPos(8, 82, 8), KEYBOARD = SCREEN.below(), COMPUTER = KEYBOARD.south();
    static final BlockPos RAID = new BlockPos(11, 81, 8), WAYPOINT = RAID.south();
    static final String ONE = "OcTcpOne", TWO = "OcTcpTwo";
    private static final byte[] BIOS = ("local p=component.list('tcp_probe')(); local g=component.list('gpu')(); local s=component.list('screen')(); " +
        "assert(p and g and s,'TCP physical hardware absent'); component.invoke(g,'bind',s); component.invoke(g,'setResolution',40,8); " +
        "component.invoke(g,'set',1,1,'TCP native ready'); component.invoke(p,'record','boot'); " +
        "while true do local e={computer.pullSignal(.05)}; if e[1] then component.invoke(p,'record',table.unpack(e)) end end").getBytes(StandardCharsets.UTF_8);
    private static MinecraftServer server;
    private static ServerLevel level;
    private static Case computer;
    private static Probe probe;
    private static final Map<String, CompoundTag> receipts = new HashMap<>();
    private static final List<String> results = new ArrayList<>();
    private static int stage, ticks, stageTick, reconnectSeen;
    private static long started;
    private static UUID firstId, oldToken;
    private static ServerPlayer originalPlayer;
    private static boolean offlineSeen, finished;
    private static MultiplayerFamilies families;
    private MultiplayerServerContract() {}
    public static boolean enabled() { return Boolean.getBoolean("ocfixture.multiplayerServer"); }
    public static final class Probe extends AbstractManagedEnvironment {
        final ConcurrentLinkedQueue<List<String>> events = new ConcurrentLinkedQueue<>();
        Probe() { setNode(Network.newNode(this, Visibility.Network).withComponent("tcp_probe").create()); }
        @Callback(direct = true) public Object[] record(Context context, Arguments args) {
            List<String> values = new ArrayList<>();
            for (int i = 0; i < args.count(); i++) values.add(args.isString(i) ? args.checkString(i) : String.valueOf(args.checkAny(i)));
            events.add(values); return new Object[0];
        }
        boolean contains(String text) { return events.stream().anyMatch(event -> event.contains(text)); }
        boolean key(String name) { return events.stream().anyMatch(event -> event.size() > 1 && event.get(0).equals("key_down") && event.contains(name)); }
    }
    public static void start(MinecraftServer actual) {
        server = actual; level = actual.overworld(); started = System.nanoTime();
        try {
            require(!server.isSingleplayer(), "TCP contract must run on an actual dedicated server");
            level.setChunkForced(0, 0, true);
            for (int x = 2; x <= 14; x++) for (int z = 2; z <= 14; z++) level.setBlock(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState(), 3);
            level.setDefaultSpawnPos(new BlockPos(8, 81, 5), 0);
            Map<BlockPos, String> placements = new LinkedHashMap<>();
            placements.put(COMPUTER, "case3"); placements.put(SCREEN, "screen3"); placements.put(KEYBOARD, "keyboard"); placements.put(RAID, "raid"); placements.put(WAYPOINT, "waypoint");
            for (var entry : placements.entrySet()) {
                var state = li.cil.oc.api.Items.get(entry.getValue()).block().defaultBlockState();
                if (entry.getKey().equals(KEYBOARD)) {
                    state = state.setValue((net.minecraft.world.level.block.state.properties.DirectionProperty) state.getBlock().getStateDefinition().getProperty("pitch"), net.minecraft.core.Direction.NORTH)
                        .setValue((net.minecraft.world.level.block.state.properties.DirectionProperty) state.getBlock().getStateDefinition().getProperty("yaw"), net.minecraft.core.Direction.NORTH);
                }
                require(level.setBlock(entry.getKey(), state, 3), "Could not place actual TCP host " + entry.getValue());
            }
            computer = (Case) level.getBlockEntity(COMPUTER);
            var architecture = li.cil.oc.api.Machine.architectures().stream().filter(type -> "Lua 5.3".equals(li.cil.oc.api.Machine.getArchitectureName(type))).findFirst().orElseThrow();
            ItemStack cpu = item("cpu3"); ((MutableProcessor) Driver.driverFor(cpu)).setArchitecture(cpu, architecture);
            for (ItemStack part : List.of(cpu, item("ram2"), item("graphicscard3"), li.cil.oc.api.Items.registerEEPROM("TCP native BIOS", BIOS, new byte[0], false))) {
                boolean inserted = false;
                for (int slot = 0; slot < computer.getContainerSize(); slot++) if (computer.getItem(slot).isEmpty() && computer.canPlaceItem(slot, part)) { computer.setItem(slot, part); inserted = true; break; }
                require(inserted, "TCP Case rejected real hardware " + part);
            }
            probe = new Probe(); computer.machine().node().connect(probe.node());
            Files.writeString(Path.of(System.getProperty("ocfixture.multiplayerReport")).resolveSibling("server-ready.txt"), "dedicated-server-ready\n", StandardCharsets.UTF_8);
        } catch (Throwable error) { fail(error); }
    }
    static ItemStack item(String name) { return li.cil.oc.api.Items.get(name).createItemStack(1); }
    private static ServerPlayer player(String name) { return server.getPlayerList().getPlayerByName(name); }
    private static boolean both() { return player(ONE) != null && player(TWO) != null; }
    private static Object screen() { return level.getBlockEntity(SCREEN); }
    private static Map<?, ?> keys() { return (Map<?, ?>) field(call(level.getBlockEntity(KEYBOARD), "keyboard"), "pressedKeys"); }
    public static void ack(ServerPlayer sender, MultiplayerProtocol.Ack packet) {
        if (server == null || finished || packet.id() != stage || !Set.of(ONE, TWO).contains(sender.getGameProfile().getName())) return;
        if (packet.data().contains("failure")) { fail(new AssertionError(sender.getGameProfile().getName() + ": " + packet.data().getString("failure"))); return; }
        receipts.put(sender.getGameProfile().getName(), packet.data().copy());
    }
    private static void step(int next) { step(next, new CompoundTag()); }
    private static void step(int next, CompoundTag data) {
        stage = next; stageTick = ticks; receipts.clear();
        System.out.println("OC_TCP_STAGE " + next);
        for (String name : List.of(ONE, TWO)) if (player(name) != null) PacketDistributor.sendToPlayer(player(name), new MultiplayerProtocol.Step(next, data));
    }
    private static void open(ServerPlayer player) { call(type("li.cil.oc.common.network.ScreenNetworking"), "open", player, screen()); }
    public static void tick() {
        if (finished || server == null) return;
        try {
            ticks++;
            Path abort = Path.of(System.getProperty("ocfixture.multiplayerReport")).resolveSibling("abort-requested.txt");
            if (Files.exists(abort)) throw new AssertionError("Owned TCP run aborted: " + Files.readString(abort));
            if (System.nanoTime() - started > java.util.concurrent.TimeUnit.MINUTES.toNanos(12)) throw new AssertionError("TCP contract timeout stage=" + stage + " receipts=" + receipts.keySet() + " players=" + server.getPlayerList().getPlayerNamesArray().length + " native=" + probe.events);
            if (computer.machine().node() instanceof Connector power) power.changeBuffer(Double.POSITIVE_INFINITY);
            if (stage >= MultiplayerProtocol.FAMILY_FIRST) { if (families.tick(receipts)) step(11); return; }
            switch (stage) {
                case 0 -> {
                    if (!both()) return;
                    for (String name : List.of(ONE, TWO)) {
                        player(name).setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                        player(name).teleportTo(level, name.equals(ONE) ? 7.5 : 9.5, 81, 5.5, Set.of(), 0, 0);
                    }
                    firstId = player(ONE).getUUID(); originalPlayer = player(ONE);
                    computer.machine().addUser(ONE);
                    require(!computer.machine().canInteract(TWO), "Second real player unexpectedly has owner/operator permission");
                    step(1);
                }
                case 1 -> { if (receipts.size() == 2) { results.add("PASS C19.tcp two distinct real remote clients observed their loaded production block entities over non-memory connections"); step(2); } }
                case 2 -> {
                    require(computer.machine().lastError() == null, "Actual TCP native BIOS failed: " + computer.machine().lastError());
                    if (receipts.size() < 2 || !probe.contains("boot")) return;
                    require(computer.machine().isRunning(), "Unauthorized remote power request stopped the owned Case");
                    results.add("PASS C19.power actual owner can start the native Case; the second non-operator's production power request is rejected");
                    open(player(ONE)); open(player(TWO)); step(3);
                }
                case 3 -> {
                    if (receipts.size() < 2 || !probe.key(ONE)) return;
                    require(!probe.key(TWO), "Foreign owner input reached the real native machine");
                    oldToken = receipts.get(ONE).getUUID("token");
                    require(!oldToken.equals(receipts.get(TWO).getUUID("token")), "Different players shared a screen session token");
                    CompoundTag data = new CompoundTag(); data.putUUID("foreignToken", oldToken); step(4, data);
                }
                case 4 -> {
                    if (receipts.size() < 2 || !probe.contains("valid TCP clipboard") || ticks - stageTick < 10) return;
                    require(!probe.contains("forged TCP clipboard"), "Foreign session token authorized screen input");
                    results.add("PASS C19.input real ScreenGui input reaches native Lua with the authenticated username; other owners and cross-player session reuse are rejected"); step(5);
                }
                case 5 -> {
                    if (receipts.size() < 2 || ticks - stageTick < 5) return;
                    require(!keys().containsKey(player(ONE)) && keys().containsKey(player(TWO)), "Closing one real GUI did not release only its player's held key");
                    require(player(ONE).containerMenu != player(ONE).inventoryMenu, "Owner did not open the actual remote Case menu");
                    results.add("PASS C19.close switching the owner from the real screen to a real container releases only that player's pressed keys");
                    for (int x = 999; x <= 1001; x++) for (int z = 999; z <= 1001; z++) level.setBlock(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState(), 3);
                    player(ONE).closeContainer(); player(TWO).teleportTo(level, 1000.5, 81, 1000.5, Set.of(), 0, 0); step(6);
                }
                case 6 -> {
                    if (receipts.size() < 2 || ticks - stageTick < 20) return;
                    require(!keys().containsKey(player(TWO)), "Distant player retained a server keyboard press");
                    require(!((Map<?, ?>) field(type("li.cil.oc.common.network.ScreenNetworking"), "SESSIONS")).containsKey(player(TWO).getUUID()), "Unreachable remote screen session was retained");
                    results.add("PASS C19.reach an actual distant player's input invalidates the session and releases its held key");
                    player(ONE).getInventory().setItem(8, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 7));
                    open(player(ONE)); step(7);
                }
                case 7 -> {
                    if (player(ONE) == null) {
                        offlineSeen = true;
                        require(!keys().containsKey(originalPlayer), "Disconnect retained the original real player's pressed key");
                        require(Files.isRegularFile(server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(firstId + ".dat")), "Normal logout did not save the real player data");
                    } else if (offlineSeen) {
                        if (reconnectSeen == 0) reconnectSeen = ticks;
                        if (ticks - reconnectSeen < 40) return;
                        require(player(ONE) != originalPlayer && player(ONE).getUUID().equals(firstId), "TCP reconnect did not create a fresh player with the original identity");
                        require(player(ONE).getInventory().getItem(8).is(net.minecraft.world.item.Items.DIAMOND) && player(ONE).getInventory().getItem(8).getCount() == 7, "Normal player save/reload lost the actual inventory marker");
                        results.add("PASS C19.logout real TCP disconnect releases pressed keys and persists player data; a separate connection restores the same UUID and inventory into a fresh ServerPlayer");
                        player(ONE).teleportTo(level, 7.5, 81, 5.5, Set.of(), 0, 0); open(player(ONE));
                        CompoundTag data = new CompoundTag(); data.putUUID("oldToken", oldToken); step(8, data);
                    }
                }
                case 8 -> {
                    if (receipts.size() < 2 || !probe.contains("fresh TCP session") || ticks - stageTick < 10) return;
                    require(!probe.contains("stale TCP session"), "Old session survived disconnect/reconnect");
                    results.add("PASS C19.reconnect a new real screen session accepts input while the pre-logout token is rejected");
                    player(TWO).teleportTo(level, 9.5, 81, 5.5, Set.of(), 0, 0);
                    CompoundTag data = new CompoundTag(); data.putUUID("owner", firstId); step(9, data);
                }
                case 9 -> {
                    if (receipts.size() < 2) return;
                    results.add("PASS C19.pet actual client visibility request is broadcast through the production server handler and applied by both remote PetRenderers");
                    ((net.minecraft.world.Container) level.getBlockEntity(RAID)).setItem(0, item("hdd1"));
                    call(type("li.cil.oc.common.network.DebugNetworking"), "clipboard", player(ONE), "OC real TCP clipboard");
                    call(type("li.cil.oc.common.network.NetworkBlockNetworking"), "openWaypoint", player(ONE), level.getBlockEntity(WAYPOINT));
                    step(10);
                }
                case 10 -> {
                    if (receipts.size() < 2 || ticks - stageTick < 10 || !"C19 TCP".equals(call(level.getBlockEntity(WAYPOINT), "label"))) return;
                    results.add("PASS C19.families actual RAID presence reaches both mirrors, debug clipboard reaches its target, and waypoint open/edit crosses TCP and changes the real server label");
                    families = new MultiplayerFamilies(server, level, computer, results, MultiplayerServerContract::step); families.begin();
                }
                case 11 -> { if (receipts.size() == 2 && server.getPlayerList().getPlayerCount() == 0) finish(false); }
                default -> throw new AssertionError("Unknown TCP stage " + stage);
            }
        } catch (Throwable error) { fail(error); }
    }
    private static void fail(Throwable error) {
        if (finished) return;
        results.add("FAIL C19 stage=" + stage + ": " + error);
        if (server != null) for (ServerPlayer player : server.getPlayerList().getPlayers()) { CompoundTag data = new CompoundTag(); data.putString("failure", error.toString()); PacketDistributor.sendToPlayer(player, new MultiplayerProtocol.Step(-1, data)); }
        finish(true);
    }
    private static void finish(boolean failure) {
        finished = true;
        if (families != null) families.close();
        try {
            Path report = Path.of(System.getProperty("ocfixture.multiplayerReport"));
            Files.createDirectories(report.getParent());
            List<String> lines = new ArrayList<>(List.of("status=" + (failure ? "FAIL" : "PASS"), "scenario=real-tcp-player-lifecycle", "ticks=" + ticks)); lines.addAll(results);
            Files.write(report, lines, StandardCharsets.UTF_8);
        } catch (Exception error) { throw new IllegalStateException(error); }
        if (server != null) server.halt(false);
    }
}
