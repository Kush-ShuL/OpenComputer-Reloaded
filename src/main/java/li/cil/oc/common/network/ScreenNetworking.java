package li.cil.oc.common.network;

import li.cil.oc.OpenComputers;
import li.cil.oc.api.internal.TextBuffer.ColorDepth;
import li.cil.oc.client.ScreenClient;
import li.cil.oc.common.block.entity.ScreenBlockEntity;
import li.cil.oc.common.component.TextBuffer;
import li.cil.oc.util.PackedColor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Screen snapshots, ordered text/VRAM deltas and authenticated GUI input. */
@net.neoforged.fml.common.EventBusSubscriber(modid = OpenComputers.ID)
public final class ScreenNetworking {
    private ScreenNetworking() {}
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final int MAX_TEXT = 8192;
    public enum InputType { KEY_DOWN, KEY_UP, CLIPBOARD, TOUCH, DRAG, DROP, SCROLL, COPY_LINE, CLOSE }
    private static final class Session {
        final UUID token;
        final BlockPos origin, anchor;
        final ServerLevel level;
        long lastTick, rateTick;
        int actions;
        Session(UUID token, ScreenBlockEntity screen, BlockPos anchor, long tick) {
            this.token = token; this.origin = screen.getBlockPos(); this.anchor = anchor.immutable(); this.level = (ServerLevel) screen.getLevel(); lastTick = tick;
        }
    }
    public record Sync(BlockPos position, long revision, boolean full, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OpenComputers.ID, "screen_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = StreamCodec.of(
                (out, value) -> { out.writeBlockPos(value.position); out.writeVarLong(value.revision); out.writeBoolean(value.full); out.writeNbt(value.data); },
                in -> new Sync(in.readBlockPos(), in.readVarLong(), in.readBoolean(), java.util.Objects.requireNonNull(in.readNbt())));
        @Override public Type<Sync> type() { return TYPE; }
    }
    public record Request(BlockPos position) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OpenComputers.ID, "screen_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of((out, value) -> out.writeBlockPos(value.position), in -> new Request(in.readBlockPos()));
        @Override public Type<Request> type() { return TYPE; }
    }
    public record Open(BlockPos position, UUID session, long revision, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OpenComputers.ID, "screen_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.of(
                (out, value) -> { out.writeBlockPos(value.position); out.writeUUID(value.session); out.writeVarLong(value.revision); out.writeNbt(value.data); },
                in -> new Open(in.readBlockPos(), in.readUUID(), in.readVarLong(), java.util.Objects.requireNonNull(in.readNbt())));
        @Override public Type<Open> type() { return TYPE; }
    }
    public record Input(BlockPos position, UUID session, InputType action, int character, int code, double x, double y, int value, String text) implements CustomPacketPayload {
        public static final Type<Input> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OpenComputers.ID, "screen_input"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Input> CODEC = StreamCodec.of(
                (out, p) -> { out.writeBlockPos(p.position); out.writeUUID(p.session); out.writeEnum(p.action); out.writeVarInt(p.character); out.writeVarInt(p.code); out.writeDouble(p.x); out.writeDouble(p.y); out.writeVarInt(p.value); out.writeUtf(p.text, MAX_TEXT); },
                in -> new Input(in.readBlockPos(), in.readUUID(), in.readEnum(InputType.class), in.readVarInt(), in.readVarInt(), in.readDouble(), in.readDouble(), in.readVarInt(), in.readUtf(MAX_TEXT)));
        @Override public Type<Input> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(Sync.TYPE, Sync.CODEC, (packet, context) -> context.enqueueWork(() -> ScreenClient.receive(packet)));
        registrar.playToClient(Open.TYPE, Open.CODEC, (packet, context) -> context.enqueueWork(() -> ScreenClient.open(packet)));
        registrar.playToServer(Request.TYPE, Request.CODEC, (packet, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && player.serverLevel().hasChunkAt(packet.position) &&
                    player.distanceToSqr(packet.position.getX() + 0.5, packet.position.getY() + 0.5, packet.position.getZ() + 0.5) <= 256 * 256 &&
                    player.level().getBlockEntity(packet.position) instanceof ScreenBlockEntity screen) sendSnapshot(player, screen.origin());
        }));
        registrar.playToServer(Input.TYPE, Input.CODEC, (packet, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) handleInput(player, packet);
        }));
    }
    public static void open(ServerPlayer player, ScreenBlockEntity clicked) { open(player, clicked, clicked.getBlockPos()); }
    public static void open(ServerPlayer player, ScreenBlockEntity clicked, BlockPos interactionAnchor) {
        ScreenBlockEntity screen = clicked.origin();
        if (player.isSpectator() || !canReach(player, interactionAnchor)) return;
        screen.flushPendingCommands();
        UUID token = UUID.randomUUID();
        SESSIONS.put(player.getUUID(), new Session(token, screen, interactionAnchor, player.level().getGameTime()));
        PacketDistributor.sendToPlayer(player, new Open(screen.getBlockPos(), token, screen.revision(), screen.snapshot()));
    }
    @net.neoforged.bus.api.SubscribeEvent
    public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Session session = SESSIONS.get(player.getUUID());
            if (session != null && session.level.hasChunkAt(session.origin) && session.level.getBlockEntity(session.origin) instanceof ScreenBlockEntity screen) screen.releaseKeys(player);
            forget(player);
        }
    }
    public static void forget(ServerPlayer player) { SESSIONS.remove(player.getUUID()); }
    public static void sendSnapshot(ServerPlayer player, ScreenBlockEntity screen) {
        screen.flushPendingCommands();
        PacketDistributor.sendToPlayer(player, new Sync(screen.getBlockPos(), screen.revision(), true, screen.snapshot()));
    }
    public static void broadcast(ScreenBlockEntity screen, boolean full, CompoundTag tag) {
        if (screen.getLevel() instanceof ServerLevel level)
            li.cil.oc.server.PacketSender.sendToPlayersTrackingBlock(screen, new Sync(screen.getBlockPos(), screen.revision(), full, tag));
    }
    private static boolean canReach(ServerPlayer player, BlockPos pos) {
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64 || !player.level().mayInteract(player, pos)) return false;
        var hit = player.level().clip(new ClipContext(player.getEyePosition(), net.minecraft.world.phys.Vec3.atCenterOf(pos), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }
    private static boolean sameScreenAnchor(ServerPlayer player, BlockPos pos, ScreenBlockEntity origin) {
        var blockEntity = player.level().getBlockEntity(pos);
        if (blockEntity instanceof ScreenBlockEntity screen) return screen.origin() == origin;
        if (blockEntity instanceof li.cil.oc.common.block.entity.KeyboardBlockEntity keyboard) return keyboard.connectedScreen() != null && keyboard.connectedScreen().origin() == origin;
        return false;
    }
    private static void handleInput(ServerPlayer player, Input input) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.token.equals(input.session) || !session.origin.equals(input.position)) return;
        if (input.action == InputType.CLOSE) {
            if (player.level().hasChunkAt(session.origin) && player.level().getBlockEntity(session.origin) instanceof ScreenBlockEntity screen) screen.releaseKeys(player);
            SESSIONS.remove(player.getUUID()); return;
        }
        long tick = player.level().getGameTime();
        if (player.isSpectator() || player.serverLevel() != session.level ||
                !player.level().hasChunkAt(session.anchor) || !canReach(player, session.anchor) ||
                !(player.level().getBlockEntity(session.origin) instanceof ScreenBlockEntity entity) || !entity.isOrigin() ||
                !sameScreenAnchor(player, session.anchor, entity)) {
            SESSIONS.remove(player.getUUID()); return;
        }
        if (session.rateTick != tick) { session.rateTick = tick; session.actions = 0; }
        if (++session.actions > 128) return;
        session.lastTick = tick;
        TextBuffer buffer = entity.buffer();
        switch (input.action) {
            case KEY_DOWN, KEY_UP -> {
                if (!entity.hasKeyboard() || input.character < 0 || input.character > Character.MAX_VALUE || input.code < 0 || input.code > 255) return;
                if (input.action == InputType.KEY_DOWN) buffer.keyDown((char) input.character, input.code, player);
                else buffer.keyUp((char) input.character, input.code, player);
            }
            case CLIPBOARD -> { if (entity.hasKeyboard()) buffer.clipboard(input.text, player); }
            case COPY_LINE -> { if (input.value >= 0 && input.value < buffer.getViewportHeight()) buffer.copyToAnalyzer(input.value, player); }
            case TOUCH, DRAG, DROP, SCROLL -> {
                if (!Double.isFinite(input.x) || !Double.isFinite(input.y) || input.x < 0 || input.y < 0 || input.x >= buffer.getViewportWidth() || input.y >= buffer.getViewportHeight()) return;
                if (input.action != InputType.SCROLL && (input.value < 0 || input.value > 2)) return;
                switch (input.action) {
                    case TOUCH -> buffer.mouseDown(input.x, input.y, input.value, player);
                    case DRAG -> buffer.mouseDrag(input.x, input.y, input.value, player);
                    case DROP -> buffer.mouseUp(input.x, input.y, input.value, player);
                    case SCROLL -> buffer.mouseScroll(input.x, input.y, Math.clamp(input.value, -1, 1), player);
                    default -> throw new IllegalStateException();
                }
            }
            default -> { }
        }
    }
    public static CompoundTag commands(java.util.List<TextBuffer.Command> commands) {
        CompoundTag tag = new CompoundTag(); ListTag list = new ListTag();
        for (TextBuffer.Command command : commands) {
            CompoundTag entry = new CompoundTag(); entry.putString("type", command.type()); ListTag data = new ListTag();
            for (Object value : command.payload()) data.add(encodeValue(value));
            entry.put("data", data); list.add(entry);
        }
        tag.put("commands", list); return tag;
    }
    private static CompoundTag encodeValue(Object value) {
        CompoundTag tag = new CompoundTag();
        if (value instanceof Boolean flag) { tag.putString("k", "b"); tag.putBoolean("v", flag); }
        else if (value instanceof Integer number) { tag.putString("k", "i"); tag.putInt("v", number); }
        else if (value instanceof Double number) { tag.putString("k", "d"); tag.putDouble("v", number); }
        else if (value instanceof String string) { tag.putString("k", "s"); tag.putString("v", string); }
        else if (value instanceof CompoundTag compound) { tag.putString("k", "n"); tag.put("v", compound.copy()); }
        else if (value instanceof ColorDepth depth) { tag.putString("k", "depth"); tag.putInt("v", depth.ordinal()); }
        else if (value instanceof PackedColor.Color color) { tag.putString("k", "color"); tag.putInt("v", color.value()); tag.putBoolean("p", color.isPalette()); }
        else if (value instanceof int[][] rows) { tag.putString("k", "rows"); ListTag list = new ListTag(); for (int[] row : rows) list.add(new net.minecraft.nbt.IntArrayTag(row)); tag.put("v", list); }
        else throw new IllegalArgumentException("Unsupported screen command value: " + value);
        return tag;
    }
    public static Object[] commandValues(CompoundTag command) {
        ListTag values = command.getList("data", Tag.TAG_COMPOUND); Object[] result = new Object[values.size()];
        for (int i = 0; i < values.size(); i++) {
            CompoundTag value = values.getCompound(i);
            result[i] = switch (value.getString("k")) {
                case "b" -> value.getBoolean("v"); case "i" -> value.getInt("v"); case "d" -> value.getDouble("v");
                case "s" -> value.getString("v"); case "n" -> value.getCompound("v");
                case "depth" -> ColorDepth.values()[Math.clamp(value.getInt("v"), 0, ColorDepth.values().length - 1)];
                case "color" -> new PackedColor.Color(value.getInt("v"), value.getBoolean("p"));
                case "rows" -> { ListTag list = value.getList("v", Tag.TAG_INT_ARRAY); int[][] rows = new int[list.size()][]; for (int y = 0; y < rows.length; y++) rows[y] = list.getIntArray(y); yield rows; }
                default -> throw new IllegalArgumentException("Invalid screen command encoding");
            };
        }
        return result;
    }
}
