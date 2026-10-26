package li.cil.oc.client;

import java.util.HashMap;
import java.util.Map;
import li.cil.oc.OpenComputers;
import li.cil.oc.client.renderer.HologramBlockEntityRenderer;
import li.cil.oc.client.renderer.HologramRenderTypes;
import li.cil.oc.common.block.entity.HologramBlockEntity;
import li.cil.oc.common.init.Holograms;
import li.cil.oc.common.network.HologramNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/** Snapshot retries and GPU-cache lifetime belong to the physical client. */
public final class HologramClient {
    private static final Map<BlockPos, Long> REQUESTS = new HashMap<>();
    private record Pending(HologramNetworking.Sync snapshot, HologramNetworking.Sync lastDelta) {}
    private static final Map<BlockPos, Pending> PENDING = new HashMap<>();
    private static Level previousLevel;
    private HologramClient() {}
    public static void initialize(IEventBus modBus) {
        HologramBlockEntity.setClientAccess(new HologramBlockEntity.ClientAccess() {
            @Override public void request(HologramBlockEntity entity) { HologramClient.request(entity); }
            @Override public void forget(HologramBlockEntity entity) { HologramClient.forget(entity); }
        });
        modBus.addListener(HologramClient::renderers);
        modBus.addListener(HologramClient::shaders);
        NeoForge.EVENT_BUS.addListener(HologramClient::tick);
        NeoForge.EVENT_BUS.addListener(HologramClient::logout);
    }
    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        for (var type : Holograms.HOLOGRAM_TYPES) event.registerBlockEntityRenderer(type.get(), HologramBlockEntityRenderer::new);
    }
    private static void shaders(RegisterShadersEvent event) {
        try {
            HologramRenderTypes.register(event);
            HologramBlockEntityRenderer.clearCache();
        } catch (java.io.IOException error) {
            throw new java.io.UncheckedIOException("Could not load the OpenComputers hologram shader", error);
        }
    }
    public static void request(HologramBlockEntity entity) {
        var client = Minecraft.getInstance();
        if (client.level == null || client.getConnection() == null || entity.getLevel() != client.level) return;
        long time = client.level.getGameTime();
        Long previous = REQUESTS.get(entity.getBlockPos());
        if (previous != null && time >= previous && time - previous < 20) return;
        REQUESTS.put(entity.getBlockPos(), time);
        PacketDistributor.sendToServer(new HologramNetworking.Request(entity.getBlockPos()));
    }
    public static void receive(HologramNetworking.Sync packet) {
        var level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().location().equals(packet.dimension())) return;
        if (!(level.getBlockEntity(packet.position()) instanceof HologramBlockEntity entity)) {
            var previous = PENDING.get(packet.position());
            // Retain a snapshot and the final delta. A missing interval triggers a fresh snapshot.
            if (previous != null || PENDING.size() < 256) PENDING.put(packet.position(), packet.full()
                    ? new Pending(packet, null) : new Pending(previous == null ? null : previous.snapshot, packet));
            return;
        }
        try {
            entity.applySync(packet.data(), packet.revision(), packet.full());
            if (packet.full()) REQUESTS.remove(packet.position());
        } catch (RuntimeException error) {
            OpenComputers.LOGGER.warn("Rejected hologram update at {}", packet.position(), error);
            request(entity);
        }
    }
    public static void forget(HologramBlockEntity entity) {
        REQUESTS.remove(entity.getBlockPos()); PENDING.remove(entity.getBlockPos());
        HologramBlockEntityRenderer.forget(entity);
    }
    private static void tick(ClientTickEvent.Post event) {
        var level = Minecraft.getInstance().level;
        if (level != previousLevel) { previousLevel = level; REQUESTS.clear(); PENDING.clear(); HologramBlockEntityRenderer.clearCache(); }
        HologramBlockEntityRenderer.cleanCache();
        if (level == null) return;
        for (BlockPos pos : PENDING.keySet().toArray(BlockPos[]::new)) {
            if (level.getBlockEntity(pos) instanceof HologramBlockEntity) {
                Pending pending = PENDING.remove(pos);
                if (pending.snapshot != null) receive(pending.snapshot);
                if (pending.lastDelta != null) receive(pending.lastDelta);
            }
        }
        // Retry a gap even when the server sends no further deltas afterward.
        for (BlockPos pos : REQUESTS.keySet().toArray(BlockPos[]::new))
            if (level.getBlockEntity(pos) instanceof HologramBlockEntity entity) request(entity);
    }
    private static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        REQUESTS.clear(); PENDING.clear(); previousLevel = null; HologramBlockEntityRenderer.clearCache();
    }
}
