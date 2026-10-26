package li.cil.oc.common.network;

import li.cil.oc.Settings;
import li.cil.oc.common.block.entity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class NetworkBlockNetworking {
    private NetworkBlockNetworking(){}
    private static ResourceLocation id(String name){return ResourceLocation.fromNamespaceAndPath("opencomputers",name);}
    public record RelayActivity(ResourceLocation dimension,BlockPos pos) implements CustomPacketPayload{
        public static final Type<RelayActivity> TYPE=new Type<>(id("relay_activity"));
        public static final StreamCodec<RegistryFriendlyByteBuf,RelayActivity> CODEC=StreamCodec.of((buffer,packet)->{buffer.writeResourceLocation(packet.dimension);buffer.writeBlockPos(packet.pos);},buffer->new RelayActivity(buffer.readResourceLocation(),buffer.readBlockPos()));
        @Override public Type<RelayActivity> type(){return TYPE;}
    }
    public record WaypointOpen(ResourceLocation dimension,BlockPos pos,String label) implements CustomPacketPayload{
        public static final Type<WaypointOpen> TYPE=new Type<>(id("waypoint_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf,WaypointOpen> CODEC=StreamCodec.of((buffer,packet)->{buffer.writeResourceLocation(packet.dimension);buffer.writeBlockPos(packet.pos);buffer.writeUtf(packet.label,32);},buffer->new WaypointOpen(buffer.readResourceLocation(),buffer.readBlockPos(),buffer.readUtf(32)));
        @Override public Type<WaypointOpen> type(){return TYPE;}
    }
    public record WaypointLabel(ResourceLocation dimension,BlockPos pos,String label) implements CustomPacketPayload{
        public static final Type<WaypointLabel> TYPE=new Type<>(id("waypoint_label"));
        public static final StreamCodec<RegistryFriendlyByteBuf,WaypointLabel> CODEC=StreamCodec.of((buffer,packet)->{buffer.writeResourceLocation(packet.dimension);buffer.writeBlockPos(packet.pos);buffer.writeUtf(packet.label,32);},buffer->new WaypointLabel(buffer.readResourceLocation(),buffer.readBlockPos(),buffer.readUtf(32)));
        @Override public Type<WaypointLabel> type(){return TYPE;}
    }
    public static void relayActivity(RelayBlockEntity relay){if(relay.getLevel() instanceof ServerLevel level)PacketDistributor.sendToPlayersNear(level,null,relay.xPosition(),relay.yPosition(),relay.zPosition(),Settings.get().maxNetworkClientEffectPacketDistance/2d,new RelayActivity(level.dimension().location(),relay.getBlockPos()));}
    public static void openWaypoint(ServerPlayer player,WaypointBlockEntity waypoint){PacketDistributor.sendToPlayer(player,new WaypointOpen(player.level().dimension().location(),waypoint.getBlockPos(),waypoint.label()));}
    public static void register(RegisterPayloadHandlersEvent event){var registrar=event.registrar("1");registrar.playToClient(RelayActivity.TYPE,RelayActivity.CODEC,(packet,context)->context.enqueueWork(()->li.cil.oc.client.NetworkBlockClient.relayActivity(packet)));registrar.playToClient(WaypointOpen.TYPE,WaypointOpen.CODEC,(packet,context)->context.enqueueWork(()->li.cil.oc.client.NetworkBlockClient.openWaypoint(packet)));registrar.playToServer(WaypointLabel.TYPE,WaypointLabel.CODEC,(packet,context)->context.enqueueWork(()->{
        if(!(context.player() instanceof ServerPlayer player)||player.isSpectator()||!player.level().dimension().location().equals(packet.dimension)||!player.level().hasChunkAt(packet.pos)||player.distanceToSqr(packet.pos.getX()+.5,packet.pos.getY()+.5,packet.pos.getZ()+.5)>64||!player.mayInteract(player.level(),packet.pos))return;
        if(player.level().getBlockEntity(packet.pos) instanceof WaypointBlockEntity waypoint)waypoint.label(packet.label);
    }));}
}
