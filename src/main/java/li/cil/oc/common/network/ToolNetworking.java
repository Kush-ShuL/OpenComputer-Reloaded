package li.cil.oc.common.network;

import li.cil.oc.OpenComputers;
import li.cil.oc.common.block.entity.ScreenBlockEntity;
import li.cil.oc.common.item.AnalyzerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Original analyzer address response and validated screen-line clipboard requests. */
public final class ToolNetworking {
    private ToolNetworking() {}
    public record Analyze(String address) implements CustomPacketPayload {
        public static final Type<Analyze> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OpenComputers.ID, "analyze"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Analyze> CODEC = StreamCodec.of((out, value) -> out.writeUtf(value.address, 128), in -> new Analyze(in.readUtf(128)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record CopyScreenLine(BlockPos position, InteractionHand hand, int line) implements CustomPacketPayload {
        public static final Type<CopyScreenLine> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OpenComputers.ID, "analyzer_copy_line"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CopyScreenLine> CODEC = StreamCodec.of(
                (out, value) -> { out.writeBlockPos(value.position); out.writeEnum(value.hand); out.writeVarInt(value.line); },
                in -> new CopyScreenLine(in.readBlockPos(), in.readEnum(InteractionHand.class), in.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(Analyze.TYPE, Analyze.CODEC, (packet, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> li.cil.oc.client.AnalyzerClient.receive(packet));
        });
        registrar.playToServer(CopyScreenLine.TYPE, CopyScreenLine.CODEC, (packet, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) copyLine(player, packet);
        }));
    }
    public static void sendAnalyze(String address, ServerPlayer player) { PacketDistributor.sendToPlayer(player, new Analyze(address)); }
    private static void copyLine(ServerPlayer player, CopyScreenLine packet) {
        var level = player.serverLevel();
        if (player.isSpectator() || !player.isShiftKeyDown() || !(player.getItemInHand(packet.hand).getItem() instanceof AnalyzerItem)
                || !level.hasChunkAt(packet.position) || !level.mayInteract(player, packet.position)
                || player.distanceToSqr(Vec3.atCenterOf(packet.position)) > 64
                || !(level.getBlockEntity(packet.position) instanceof ScreenBlockEntity screen)) return;
        var hit = level.clip(new ClipContext(player.getEyePosition(), Vec3.atCenterOf(packet.position), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(packet.position) || hit.getDirection() != screen.facing()) return;
        AnalyzerItem.copyLine(screen, packet.line, player, packet.hand);
    }
}
