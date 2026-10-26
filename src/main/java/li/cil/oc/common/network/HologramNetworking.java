package li.cil.oc.common.network;

import java.util.Objects;
import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.common.block.entity.HologramBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Ordered full, column, area, clear and projector-setting updates for tracking clients. */
public final class HologramNetworking {
    private HologramNetworking() {}
    public record Sync(ResourceLocation dimension, BlockPos position, long revision, boolean full, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OpenComputers.ID, "hologram_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = StreamCodec.of(
                (buffer, packet) -> { buffer.writeResourceLocation(packet.dimension); buffer.writeBlockPos(packet.position); buffer.writeVarLong(packet.revision); buffer.writeBoolean(packet.full); buffer.writeNbt(packet.data); },
                buffer -> new Sync(buffer.readResourceLocation(), buffer.readBlockPos(), buffer.readVarLong(), buffer.readBoolean(), Objects.requireNonNull(buffer.readNbt())));
        @Override public Type<Sync> type() { return TYPE; }
    }
    public record Request(BlockPos position) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OpenComputers.ID, "hologram_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of(
                (buffer, packet) -> buffer.writeBlockPos(packet.position), buffer -> new Request(buffer.readBlockPos()));
        @Override public Type<Request> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(Sync.TYPE, Sync.CODEC, (packet, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> Client.receive(packet));
        });
        registrar.playToServer(Request.TYPE, Request.CODEC, (packet, context) -> context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            double range = Math.max(64, Settings.get().hologramRenderDistance);
            if (!level.hasChunkAt(packet.position) || player.distanceToSqr(packet.position.getCenter()) > range * range) return;
            if (level.getBlockEntity(packet.position) instanceof HologramBlockEntity entity) {
                entity.flushPendingUpdates();
                PacketDistributor.sendToPlayer(player, new Sync(level.dimension().location(), entity.getBlockPos(), entity.revision(), true, entity.snapshot()));
            }
        }));
    }
    public static void broadcast(HologramBlockEntity entity, CompoundTag data) {
        if (entity.getLevel() instanceof ServerLevel level)
            PacketDistributor.sendToPlayersTrackingChunk(level, new net.minecraft.world.level.ChunkPos(entity.getBlockPos()),
                    new Sync(level.dimension().location(), entity.getBlockPos(), entity.revision(), false, data.copy()));
    }
    /** Common registration does not link Minecraft client classes on a dedicated server. */
    private static final class Client {
        private static void receive(Sync packet) { li.cil.oc.client.HologramClient.receive(packet); }
    }
}
