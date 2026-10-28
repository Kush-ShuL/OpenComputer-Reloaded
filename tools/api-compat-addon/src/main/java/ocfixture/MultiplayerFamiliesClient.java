package ocfixture;

import static ocfixture.MultiplayerProtocol.*;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import li.cil.oc.api.Nanomachines;
import li.cil.oc.api.event.FileSystemAccessEvent;
import li.cil.oc.api.event.NetworkActivityEvent;
import li.cil.oc.api.internal.Robot;
import li.cil.oc.api.internal.TextBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/** Passive wire observations plus assertions against actual production client mirrors. */
final class MultiplayerFamiliesClient {
    private static final Map<String, AtomicInteger> packets = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<String> activity = new ConcurrentLinkedQueue<>();
    private static final Map<BlockPos, List<CompoundTag>> nativeUpdates = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<String> sounds = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger particles = new AtomicInteger();
    private static Channel observed;
    private static int stage = -1, ticks;
    private static boolean acted, released;
    private static Object originalRobot;
    private static UUID oldTablet, oldTerminal, oldScreen;
    private static BlockPos movedRobot;
    private MultiplayerFamiliesClient() {}
    static void register() {
        NeoForge.EVENT_BUS.addListener(MultiplayerFamiliesClient::fileActivity);
        NeoForge.EVENT_BUS.addListener(MultiplayerFamiliesClient::networkActivity);
        NeoForge.EVENT_BUS.addListener(MultiplayerFamiliesClient::sound);
    }
    private static void fileActivity(FileSystemAccessEvent.Client event) { String marker = event.getData().getString(MultiplayerFamilies.MARKER); if (!marker.isEmpty()) activity.add("filesystem:" + marker); }
    private static void networkActivity(NetworkActivityEvent.Client event) { String marker = event.getData().getString(MultiplayerFamilies.MARKER); if (!marker.isEmpty()) activity.add("network:" + marker); }
    private static void sound(PlaySoundEvent event) { if (event.getSound() != null) sounds.add(event.getSound().getLocation().toString()); }
    static void observe(Minecraft client) {
        if (client.getConnection() == null) return;
        Channel channel = (Channel) field(client.getConnection().getConnection(), "channel");
        if (channel == observed) return; observed = channel;
        channel.eventLoop().execute(() -> channel.pipeline().addBefore("packet_handler", "oc_tcp_passive_payload_observer", new ChannelInboundHandlerAdapter() {
            @Override public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
                if (message instanceof ClientboundCustomPayloadPacket packet) packets.computeIfAbsent(packet.payload().type().id().toString(), ignored -> new AtomicInteger()).incrementAndGet();
                if (message instanceof ClientboundBlockEntityDataPacket packet && packet.getTag() != null) { List<CompoundTag> history = nativeUpdates.computeIfAbsent(packet.getPos(), ignored -> new java.util.concurrent.CopyOnWriteArrayList<>()); history.add(packet.getTag().copy()); if (history.size() > 64) history.removeFirst(); }
                if (message instanceof ClientboundLevelParticlesPacket) particles.incrementAndGet();
                super.channelRead(context, message);
            }
        }));
    }
    static int packets(String path) { AtomicInteger value = packets.get("opencomputers:" + path); return value == null ? 0 : value.get(); }
    @SuppressWarnings({"unchecked", "rawtypes"}) static Object action(String owner, String name) { return Enum.valueOf((Class) type("li.cil.oc.common.network." + owner), name); }
    static boolean named(Minecraft client, String name) { return client.screen != null && client.screen.getClass().getSimpleName().equals(name); }
    static Object device(Minecraft client, BlockPos pos) { return client.level.getBlockEntity(pos); }
    static TextBuffer buffer(Minecraft client) { Object value = device(client, MultiplayerServerContract.SCREEN); return value == null ? null : (TextBuffer) call(value, "buffer"); }
    static boolean text(TextBuffer buffer, int x, int y, String text) {
        if (buffer == null || y >= buffer.getHeight() || x + text.codePointCount(0, text.length()) > buffer.getWidth()) return false;
        int column = x; for (int codePoint : text.codePoints().toArray()) if (buffer.getCodePoint(column++, y) != codePoint) return false; return true;
    }
    static boolean hasRam(TextBuffer buffer) {
        for (Object device : ((Map<?, ?>) call(buffer, "videoDevices")).values()) if (!((Map<?, ?>) call(device, "videoBuffers")).isEmpty()) return true;
        return false;
    }
    static boolean cachedText(TextBuffer buffer, String expected) {
        for (Object device : ((Map<?, ?>) call(buffer, "videoDevices")).values()) for (Object page : ((Map<?, ?>) call(device, "videoBuffers")).values()) if (page instanceof TextBuffer value && text(value, 0, 0, expected)) return true;
        return false;
    }
    static boolean hologram(Minecraft client, boolean cleared) {
        Object entity = device(client, MultiplayerFamilies.HOLOGRAM); if (entity == null) return false;
        Object hologram = call(entity, "hologram");
        if (cleared) return ((Number) call(hologram, "getColor", 0, 1, 2)).intValue() == 0 && ((Number) call(hologram, "getColor", 1, 3, 1)).intValue() == 0;
        CompoundTag snapshot = (CompoundTag) call(entity, "snapshot");
        return ((Number) call(hologram, "getColor", 0, 1, 2)).intValue() == 2 && ((Number) call(hologram, "getColor", 1, 3, 1)).intValue() == 1
            && snapshot.getDouble("oc:scale") == 1.25 && snapshot.getDouble("oc:offsetX") == .25 && snapshot.getDouble("oc:offsetZ") == -.5
            && snapshot.getFloat("oc:rotationAngle") == 30 && snapshot.getFloat("oc:rotationSpeed") == 45;
    }
    static CustomPacketPayload drive(boolean unmanaged, boolean lock) {
        try { return (CustomPacketPayload) ((Class<?>) type("li.cil.oc.common.item.ComponentItem$DriveSettingsPayload")).getConstructor(InteractionHand.class, boolean.class, boolean.class).newInstance(InteractionHand.MAIN_HAND, unmanaged, lock); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    static void terminal(String action, String text, double x, double y) {
        Minecraft client = Minecraft.getInstance(); UUID session = (UUID) field(client.screen, "session");
        PacketDistributor.sendToServer(payload("RackNetworking", "TerminalInput", session, action("ScreenNetworking$InputType", action), action.startsWith("KEY") ? 116 : 0, action.startsWith("KEY") ? 20 : 0, x, y, action.equals("SCROLL") ? 1 : 0, text));
    }
    static void physical(Minecraft client, BlockPos pos) { client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)); }
    static CompoundTag tick(Minecraft client, MultiplayerProtocol.Step step, boolean primary, List<String> results) throws Exception {
        if (stage != step.id()) { stage = step.id(); ticks = 0; acted = released = false; } ticks++;
        require(ticks <= 800, "TCP family client timeout: " + diagnosis(client));
        if (stage == 100) {
            if (client.screen != null) client.setScreen(null);
            for (BlockPos pos : MultiplayerFamilies.NATIVE.keySet()) if (device(client, pos) == null) return null;
            if (device(client, MultiplayerFamilies.HOLOGRAM) == null || device(client, MultiplayerFamilies.RACK) == null) return null;
            Object computer = device(client, MultiplayerServerContract.COMPUTER);
            if (!java.util.Arrays.asList((String[]) field(computer, "users")).contains(MultiplayerServerContract.ONE) || (Boolean) field(computer, "hasErrored")) return null;
            var state = client.level.getBlockState(MultiplayerServerContract.COMPUTER); var running = (net.minecraft.world.level.block.state.properties.BooleanProperty) state.getBlock().getStateDefinition().getProperty("running"); if (!state.getValue(running)) return null;
            return new CompoundTag();
        }
        if (stage == 101) {
            TextBuffer buffer = buffer(client);
            if (buffer == null || buffer.getWidth() != 32 || buffer.getHeight() != 10 || buffer.getViewportWidth() != 30 || buffer.getViewportHeight() != 8 || buffer.getMaximumWidth() != 80 || buffer.getColorDepth() != TextBuffer.ColorDepth.EightBit) return null;
            if (!text(buffer, 0, 0, "TCP delta") || !text(buffer, 4, 2, "FFF") || !text(buffer, 0, 4, "RAW") || !text(buffer, 0, 6, "VRAM") || buffer.getCodePoint(20, 3) != 'T' || !hasRam(buffer) || !hologram(client, false)) return null;
            require(buffer.getPaletteColor(1) == 0x123456, "Actual remote screen palette mismatch");
            require(buffer.getForegroundColor() == 1 && buffer.isForegroundFromPalette() && buffer.getBackgroundColor() == 0x112233 && buffer.getForegroundColor(0, 4) == 0xFF0000 && buffer.getBackgroundColor(0, 4) == 0x00FF00, "Actual remote screen global/raw colors mismatch");
            results.add("PASS actual TCP screen command batch/VRAM and hologram settings/voxels applied to this client's real world objects"); return new CompoundTag();
        }
        if (stage == 102) {
            TextBuffer buffer = buffer(client); if (buffer == null || buffer.isRenderingEnabled() || hasRam(buffer) || !hologram(client, true)) return null;
            return new CompoundTag();
        }
        if (stage == 103) {
            if (((Number) call(device(client, MultiplayerServerContract.COMPUTER), "getColor")).intValue() != 0x224466) return null;
            for (var entry : MultiplayerFamilies.NATIVE.entrySet()) {
                Object device = device(client, entry.getKey()); if (device == null) return null;
                if (entry.getValue().equals("adapter") && (Boolean) call(device, "isSideOpen", Direction.EAST)) return null;
                if (entry.getValue().equals("netsplitter") && !(Boolean) call(device, "isSideOpen", Direction.EAST)) return null;
                if (entry.getValue().equals("charger") && !(Boolean) field(device, "invertSignal")) return null;
                if (entry.getValue().equals("diskdrive") && ((net.minecraft.world.Container) device).isEmpty()) return null;
                if (entry.getValue().equals("powerdistributor") && ((Number) call(device, "globalBuffer")).doubleValue() <= 0) return null;
            }
            Player owner = client.level.getPlayerByUUID(step.data().getUUID("owner")); if (owner == null || !Nanomachines.hasController(owner)) return null;
            var controller = Nanomachines.getController(owner); if (controller.getLocalBuffer() <= 0 || controller.getTotalInputCount() > 0 && !controller.getInput(0)) return null;
            List<?> disks = (List<?>) call(type("li.cil.oc.common.integration.LootIntegration"), "clientDisks");
            if (disks.stream().noneMatch(value -> value instanceof ItemStack disk && MultiplayerFamilies.tag(disk).getString("oc:lootFactory").equals("opencomputers:tcp-program"))) return null;
            List<?> cycling = (List<?>) call(type("li.cil.oc.common.recipe.Recipes"), "cyclingDisks", client.level.registryAccess());
            require(cycling.stream().anyMatch(value -> value instanceof ItemStack disk && MultiplayerFamilies.tag(disk).getString("oc:lootFactory").equals("opencomputers:tcp-program")), "Actual remote cycling catalog omitted the received world disk");
            if (primary) {
                if (!client.player.getMainHandItem().is(MultiplayerFamilies.item("hdd3").getItem())) return null;
                if (!acted) { PacketDistributor.sendToServer(drive(true, true)); acted = true; return null; }
                CompoundTag data = MultiplayerFamilies.tag(client.player.getMainHandItem()); if (!data.getBoolean("oc:unmanaged") || data.getString("oc:lock").isEmpty()) return null;
                if (!Files.readString(Path.of("logs/latest.log")).contains("OC_TCP_ACTUAL_COMMAND_LOG")) return null;
            }
            results.add("PASS real remote native BE/HDD, nonempty loot catalog and actual nanomachine configuration/input/power" + (primary ? ", targeted actual command logger" : " tracking mirror")); return new CompoundTag();
        }
        if (stage == 104) {
            Object rack = device(client, MultiplayerFamilies.RACK); if (rack == null || (((Number) call(rack, "presenceMask")).intValue() & 0x111) != 0x111) return null;
            if (primary) {
                if (!named(client, "RackScreen")) { if (!released) { physical(client, MultiplayerFamilies.RACK); released = true; } return null; }
                if (!acted) { PacketDistributor.sendToServer(payload("RackNetworking", "Edit", MultiplayerFamilies.RACK, 0, 0, Direction.EAST.get3DDataValue(), 0, true)); PacketDistributor.sendToServer(payload("RackNetworking", "Edit", MultiplayerFamilies.RACK, 0, 0, 0, 1, true)); acted = true; return null; }
            }
            if (((Number) call(rack, "mapping", 0, 0)).intValue() != Direction.EAST.get3DDataValue() || !(Boolean) call(rack, "relayEnabled")) return null;
            return new CompoundTag();
        }
        if (stage == 105) {
            if (primary) { if (!named(client, "ServerScreen")) return null; if (!acted) { PacketDistributor.sendToServer(payload("RackNetworking", "Edit", MultiplayerFamilies.RACK, 0, 0, 0, 2, true)); acted = true; return null; } }
            Object rack = device(client, MultiplayerFamilies.RACK); if (rack == null || !((CompoundTag) call(rack, "getMountableData", 0)).getBoolean("isRunning")) return null;
            return new CompoundTag();
        }
        if (stage == 106) {
            if (!primary) return new CompoundTag();
            if (!named(client, "TerminalScreen")) { if (!acted && client.player.getMainHandItem().is(MultiplayerFamilies.item("terminal").getItem()) && !MultiplayerFamilies.tag(client.player.getMainHandItem()).getString("oc:key").isEmpty()) { client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND); acted = true; } return null; }
            oldTerminal = (UUID) field(client.screen, "session");
            if (!released) { terminal("KEY_DOWN", "", 0, 0); terminal("KEY_UP", "", 0, 0); terminal("CLIPBOARD", "TCP terminal input", 0, 0); terminal("TOUCH", "", 1, 1); terminal("DRAG", "", 2, 2); terminal("SCROLL", "", 2, 2); terminal("DROP", "", -1, -1); released = true; }
            TextBuffer screen = (TextBuffer) field(client.screen, "buffer");
            if (!text(screen, 0, 0, "TCP terminal initial") || !text(screen, 0, 2, "OLD!") || !cachedText(screen, "NEW!")) return null;
            return new CompoundTag();
        }
        if (stage == 107) { if (primary && (!named(client, "TerminalScreen") || !text((TextBuffer) field(client.screen, "buffer"), 0, 0, "TCP terminal delta"))) return null; return new CompoundTag(); }
        return later(client, step, primary, results);
    }
    static void agent(Minecraft client, String name, String text, double x, double y, int value) {
        require(client.screen instanceof AbstractContainerScreen<?>, "Actual Agent menu is absent");
        int menu = ((AbstractContainerScreen<?>) client.screen).getMenu().containerId;
        PacketDistributor.sendToServer(payload("AgentNetworking", "Input", menu, action("AgentNetworking$Action", name), name.startsWith("KEY") ? 114 : 0, name.startsWith("KEY") ? 19 : 0, x, y, value, text));
    }
    static void tablet(Minecraft client, UUID token, String name, String text, double x, double y) {
        PacketDistributor.sendToServer(payload("PortableNetworking", "TabletInput", token, action("ScreenNetworking$InputType", name), name.startsWith("KEY") ? 112 : 0, name.startsWith("KEY") ? 25 : 0, x, y, name.equals("SCROLL") ? 1 : 0, text));
    }
    static Object robot(Minecraft client, BlockPos pos) { Object proxy = device(client, pos); return proxy instanceof Robot ? call(proxy, "controller") : null; }
    private static CompoundTag later(Minecraft client, MultiplayerProtocol.Step step, boolean primary, List<String> results) throws Exception {
        switch (step.id()) {
            case 123 -> {
                if (primary) {
                    if (!acted) { PacketDistributor.sendToServer(drive(false, false)); acted = true; return null; }
                    CompoundTag data = MultiplayerFamilies.tag(client.player.getMainHandItem());
                    if (data.getBoolean("oc:unmanaged") || !data.getString("oc:lock").isEmpty() || !data.getCompound("oc:data").getCompound("node").getString("address").isEmpty()) return null;
                }
                return new CompoundTag();
            }
            case 108 -> {
                originalRobot = robot(client, MultiplayerFamilies.ROBOT); if (originalRobot == null || field(originalRobot, "clientScreen") == null) return null;
                if (device(client, MultiplayerFamilies.MICROCONTROLLER) == null) return null;
                return new CompoundTag();
            }
            case 109 -> {
                movedRobot = BlockPos.of(step.data().getLong("position")); Object mirror = robot(client, movedRobot); if (mirror == null) return null;
                require(mirror == originalRobot, "Actual remote Robot move replaced its stable controller");
                Robot robot = (Robot) mirror;
                if (!robot.name().equals("TCP robot") || robot.selectedSlot() < 1 || robot.selectedSlot() > 2 || robot.mainInventory().getItem(1).getCount() != 3 || !((Boolean) call(mirror, "canInteract", MultiplayerServerContract.TWO))) return null;
                if (((Number) field(field(mirror, "info"), "lightColor")).intValue() != 0x336699) return null;
                if (((Number) call(call(mirror, "redstoneState"), "output", Direction.UP)).intValue() != 15) return null;
                Object micro = device(client, MultiplayerFamilies.MICROCONTROLLER); if (micro == null || !(Boolean) field(micro, "clientRunning") || !java.util.Arrays.asList((String[]) field(micro, "users")).contains(MultiplayerServerContract.TWO)) return null;
                if (primary) {
                    if (!named(client, "AgentScreen")) { if (!released) { physical(client, movedRobot); released = true; } return null; }
                    if (!acted) { agent(client, "KEY_DOWN", "", 0, 0, 0); agent(client, "KEY_UP", "", 0, 0, 0); agent(client, "CLIPBOARD", "TCP robot input", 0, 0, 0); agent(client, "TOUCH", "", 1, 1, 0); agent(client, "DRAG", "", 2, 2, 0); agent(client, "MOUSE_SCROLL", "", 2, 2, 1); agent(client, "DROP", "", -1, -1, 0); agent(client, "SELECT", "", 0, 0, 2); agent(client, "REQUEST", "", 0, 0, 0); acted = true; return null; }
                    if (robot.selectedSlot() != 2) return null;
                }
                return new CompoundTag();
            }
            case 110 -> {
                Object mirror = robot(client, movedRobot); if (mirror == null || !(Boolean) field(mirror, "clientHasErrored") || (Boolean) call(mirror, "canInteract", MultiplayerServerContract.TWO)) return null;
                Object micro = device(client, MultiplayerFamilies.MICROCONTROLLER); if (micro == null || !(Boolean) field(micro, "clientErrored") || (Boolean) field(micro, "clientRunning") || java.util.Arrays.asList((String[]) field(micro, "users")).contains(MultiplayerServerContract.TWO)) return null;
                require(mirror == originalRobot, "Actual Robot state update replaced its stable controller");
                if (client.screen != null) client.screen.onClose(); return new CompoundTag();
            }
            case 111 -> {
                {
                    net.minecraft.world.entity.Entity drone = null; for (var entity : client.level.entitiesForRendering()) if (entity.getUUID().equals(step.data().getUUID("entity"))) { drone = entity; break; }
                    if (drone == null || !(drone instanceof li.cil.oc.api.internal.Drone) || !call(drone, "statusText").equals("TCP entity") || ((Number) call(drone, "lightColor")).intValue() != 0x225588) return null;
                    if (primary) {
                        if (!named(client, "AgentScreen")) { if (!released) { client.gameMode.interact(client.player, drone, InteractionHand.MAIN_HAND); released = true; } return null; }
                        if (!acted) { agent(client, "POWER", "", 0, 0, 0); acted = true; return null; }
                    }
                    if (!(Boolean) call(drone, "running")) return null;
                }
                return new CompoundTag();
            }
            case 112 -> {
                if (!primary) {
                    Player owner = client.level.players().stream().filter(value -> value.getGameProfile().getName().equals(MultiplayerServerContract.ONE)).findFirst().orElse(null);
                    if (owner == null || !MultiplayerFamilies.tag(owner.getMainHandItem()).getBoolean("oc:isRunning")) return null;
                    return new CompoundTag();
                }
                if (!named(client, "TabletScreen")) {
                    if (!client.player.getMainHandItem().is(MultiplayerFamilies.item("tablet").getItem()) || !MultiplayerFamilies.tag(client.player.getMainHandItem()).getBoolean("oc:isRunning")) return null;
                    if (!acted) { client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND); acted = true; return null; }
                    if (!released && ticks > 2) { client.gameMode.releaseUsingItem(client.player); released = true; }
                    return null;
                }
                oldTablet = (UUID) field(client.screen, "session");
                if (!text((TextBuffer) field(client.screen, "buffer"), 0, 0, "TCP actual native host")) return null;
                if (!text((TextBuffer) field(client.screen, "buffer"), 0, 2, "OLD!") || !cachedText((TextBuffer) field(client.screen, "buffer"), "NEW!")) return null;
                if (ticks < 5) return null;
                tablet(client, oldTablet, "KEY_DOWN", "", 0, 0); tablet(client, oldTablet, "KEY_UP", "", 0, 0); tablet(client, oldTablet, "CLIPBOARD", "TCP tablet input", 0, 0); tablet(client, oldTablet, "TOUCH", "", 1, 1); tablet(client, oldTablet, "DRAG", "", 2, 2); tablet(client, oldTablet, "SCROLL", "", 2, 2); tablet(client, oldTablet, "DROP", "", -1, -1);
                PacketDistributor.sendToServer(payload("PortableNetworking", "TabletRequest", oldTablet)); return new CompoundTag();
            }
            case 127 -> {
                Player owner = client.level.players().stream().filter(value -> value.getGameProfile().getName().equals(MultiplayerServerContract.ONE)).findFirst().orElse(null);
                if (owner == null || !MultiplayerFamilies.tag(owner.getMainHandItem()).getBoolean("oc:isRunning")) return null;
                return new CompoundTag();
            }
            case 113 -> { if (primary && (!named(client, "TabletScreen") || !text((TextBuffer) field(client.screen, "buffer"), 0, 0, "TCP tablet delta"))) return null; return new CompoundTag(); }
            case 114 -> {
                if (primary) { if (!acted) { client.player.getInventory().selected = 1; client.getConnection().send(new ServerboundSetCarriedItemPacket(1)); tablet(client, oldTablet, "CLIPBOARD", "TCP stale tablet", 0, 0); acted = true; } if (named(client, "TabletScreen")) return null; }
                return new CompoundTag();
            }
            case 115 -> {
                for (String marker : List.of("real-tcp-case", "real-tcp-agent")) if (!activity.contains("filesystem:" + marker) || !activity.contains("network:" + marker)) return null;
                if (packets("speaker") < 2 || packets("analyze") < (primary ? 1 : 0) || particles.get() == 0 || sounds.stream().noneMatch(value -> value.equals("opencomputers:floppy_insert"))) return null;
                Object relay = device(client, new BlockPos(3, 81, 13)), transposer = device(client, MultiplayerFamilies.TRANSPOSER);
                if (relay == null || transposer == null || ((Number) field(relay, "lastMessage")).longValue() == 0 || ((Number) field(transposer, "lastOperation")).longValue() == 0) return null;
                results.add("PASS actual production filesystem/network API events carry Case/Agent custom NBT into this remote client; targeted speaker/analyzer, native particle/sound and relay/transposer effects received normally"); return new CompoundTag();
            }
            case 130 -> {
                if (primary) {
                    if (!named(client, "WorkstationScreen")) { if (!released) { physical(client, MultiplayerFamilies.ASSEMBLER); released = true; } return null; }
                    if (!acted) { client.gameMode.handleInventoryButtonClick(((AbstractContainerScreen<?>) client.screen).getMenu().containerId, 0); acted = true; return null; }
                }
                Object assembler = device(client, MultiplayerFamilies.ASSEMBLER); if (assembler == null || !((CompoundTag) call(assembler, "getUpdateTag", client.level.registryAccess())).getBoolean("oc:assembling")) return null;
                return new CompoundTag();
            }
            case 131 -> {
                Object printer = device(client, MultiplayerFamilies.PRINTER); if (printer == null || !((Boolean) field(printer, "clientWorking"))) return null;
                Object model = call(printer, "preview"); if (((java.util.Collection<?>) field(model, "stateOff")).size() != 1 || !((java.util.Optional<?>) field(model, "label")).map(Object::toString).orElse("").equals("TCP print")) return null;
                boolean active = nativeUpdates.getOrDefault(MultiplayerFamilies.DISASSEMBLER, List.of()).stream().anyMatch(data -> data.getBoolean("oc:isActive")); if (!active) return null;
                Object screen = device(client, MultiplayerServerContract.SCREEN); if (screen == null || !(Boolean) call(screen, "invertTouchMode")) return null;
                results.add("PASS actual remote assembler menu/state, printer preview+working and disassembler native active-state updates; real screen touch inversion applied"); return new CompoundTag();
            }
            case 118 -> {
                if (!named(client, "ScreenGui")) return null; oldScreen = (UUID) field(client.screen, "session");
                if (!acted) {
                    call(client.screen, "send", action("ScreenNetworking$InputType", "KEY_DOWN"), 100, 32, 0d, 0d, 0, "");
                    if (primary) { call(client.screen, "send", action("ScreenNetworking$InputType", "TOUCH"), 0, 0, 1d, 1d, 0, ""); call(client.screen, "send", action("ScreenNetworking$InputType", "DRAG"), 0, 0, 2d, 2d, 0, ""); call(client.screen, "send", action("ScreenNetworking$InputType", "SCROLL"), 0, 0, 2d, 2d, 1, ""); call(client.screen, "send", action("ScreenNetworking$InputType", "DROP"), 0, 0, -1d, -1d, 0, ""); call(client.screen, "send", action("ScreenNetworking$InputType", "COPY_LINE"), 0, 0, 0d, 0d, 0, ""); }
                    PacketDistributor.sendToServer(payload("ScreenNetworking", "Request", MultiplayerServerContract.SCREEN)); acted = true; return null;
                }
                if (primary && !MultiplayerFamilies.tag(client.player.getMainHandItem()).getString("oc:clipboard").contains("TCP before tracking")) return null;
                return new CompoundTag();
            }
            case 124, 125 -> {
                int kind = step.id() == 124 ? 1 : 3;
                if (nativeUpdates.getOrDefault(MultiplayerFamilies.ROBOT, List.of()).stream().noneMatch(data -> data.getInt("oc:animationKind") == kind)) return null;
                require(robot(client, MultiplayerFamilies.ROBOT) == originalRobot, "Real native animation update replaced its Robot controller"); return new CompoundTag();
            }
            case 119 -> {
                if (!client.level.dimension().equals(net.minecraft.world.level.Level.NETHER) || device(client, MultiplayerServerContract.SCREEN) != null) return null;
                PacketDistributor.sendToServer(payload("ScreenNetworking", "Input", MultiplayerServerContract.SCREEN, oldScreen, action("ScreenNetworking$InputType", "CLIPBOARD"), 0, 0, 0d, 0d, 0, "TCP wrong dimension")); return new CompoundTag();
            }
            case 120 -> {
                if (!client.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return null;
                TextBuffer buffer = buffer(client); Object projector = device(client, MultiplayerFamilies.HOLOGRAM); Object robot = robot(client, movedRobot);
                if (!text(buffer, 0, 0, "TCP tracking full") || !text(buffer, 0, 6, "BACK") || buffer.getMaximumWidth() != 80 || buffer.getMaximumHeight() != 25 || projector == null || ((Number) call(call(projector, "hologram"), "getColor", 0, 1, 2)).intValue() != 3 || robot == null || !((Robot) robot).name().equals("TCP robot") || !(Boolean) field(robot, "clientHasErrored")) return null;
                return new CompoundTag();
            }
            case 126 -> {
                TextBuffer mirror = buffer(client); if (mirror == null) return null;
                int[] actual = java.util.stream.IntStream.range(5, 9).map(x -> mirror.getCodePoint(x, 6)).toArray(); int[] actualServer = step.data().getIntArray("serverRaster");
                String observation = "server=" + java.util.Arrays.toString(actualServer) + ", actual_client=" + java.util.Arrays.toString(actual) + ", client_vram=" + hasRam(mirror);
                if (!java.util.Arrays.equals(actualServer, actual)) { if (ticks == 40) results.add("OBS C19.clean-blit mismatch " + observation); return null; }
                results.add("PASS unchanged actual GPU page rendered at a new screen destination after remote dimension retracking; " + observation); return new CompoundTag();
            }
            case 128 -> {
                if (device(client, MultiplayerServerContract.KEYBOARD) != null || buffer(client) == null || hasRam(buffer(client))) return null;
                return new CompoundTag();
            }
            case 129 -> {
                Object screen = device(client, MultiplayerServerContract.SCREEN);
                if (device(client, MultiplayerServerContract.KEYBOARD) == null || screen == null) return null;
                if (primary) {
                    if (!named(client, "ScreenGui")) { if (!released) { physical(client, MultiplayerServerContract.KEYBOARD); released = true; } return null; }
                    if (!(Boolean) field(screen, "clientHasKeyboard")) return null;
                }
                return new CompoundTag();
            }
            case 132 -> {
                if (!text(buffer(client), 10, 6, "NEW2")) return null;
                results.add("PASS physical screen unlink cleared old page metadata; actual relink+non-reset GPU bind restored a clean-page copy to this client's real raster"); return new CompoundTag();
            }
            case 121 -> {
                TextBuffer buffer = buffer(client); Object projector = device(client, MultiplayerFamilies.HOLOGRAM);
                if (!text(buffer, 0, 0, "TCP delta after return") || !text(buffer, 0, 6, "NEW2") || !hasRam(buffer) || projector == null || ((Number) call(call(projector, "hologram"), "getColor", 0, 1, 2)).intValue() != 1) return null;
                results.add("PASS actual dimension/chunk departure and re-entry restore full screen/VRAM/hologram/Robot state, followed by continuous new deltas");
                results.add("WIRE production payload counts " + packets); return new CompoundTag();
            }
            default -> throw new AssertionError("Unknown TCP client family phase " + step.id());
        }
    }
    static String diagnosis(Minecraft client) {
        String screen = client.screen == null ? "null" : client.screen.getClass().getSimpleName(); TextBuffer buffer = client.level == null ? null : buffer(client);
        return "stage=" + stage + " ticks=" + ticks + " screen=" + screen + " dimension=" + (client.level == null ? "none" : client.level.dimension().location())
            + " buffer=" + (buffer == null ? "none" : buffer.getWidth() + "x" + buffer.getHeight() + " viewport=" + buffer.getViewportWidth() + "x" + buffer.getViewportHeight() + " render=" + buffer.isRenderingEnabled() + " ram=" + hasRam(buffer))
            + " activity=" + activity + " sounds=" + sounds + " particles=" + particles + " payloads=" + packets;
    }
}
