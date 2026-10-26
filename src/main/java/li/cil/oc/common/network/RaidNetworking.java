package li.cil.oc.common.network;

import li.cil.oc.common.block.entity.RaidBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.*;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Only presence and label are public; filesystem data and inventory stay server-owned. */
public final class RaidNetworking {
    private RaidNetworking(){}
    public record State(ResourceLocation dimension,BlockPos position,int presence,String label) implements CustomPacketPayload {
        public static final Type<State> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("opencomputers","raid_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of((buffer,packet)->{buffer.writeResourceLocation(packet.dimension);buffer.writeBlockPos(packet.position);buffer.writeByte(packet.presence);buffer.writeBoolean(packet.label!=null);if(packet.label!=null)buffer.writeUtf(packet.label,16);},buffer->new State(buffer.readResourceLocation(),buffer.readBlockPos(),buffer.readUnsignedByte(),buffer.readBoolean()?buffer.readUtf(16):null));
        @Override public Type<State> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToClient(State.TYPE,State.CODEC,(packet,context)->{if(FMLEnvironment.dist==Dist.CLIENT)context.enqueueWork(()->Client.apply(packet));});}
    public static void send(RaidBlockEntity raid){if(raid.getLevel() instanceof ServerLevel level){int mask=0;for(int slot=0;slot<3;slot++)if(raid.presence[slot])mask|=1<<slot;li.cil.oc.server.PacketSender.sendToPlayersTrackingBlock(raid,new State(level.dimension().location(),raid.getBlockPos(),mask,raid.label.getLabel()));}}
    @OnlyIn(Dist.CLIENT)private static final class Client{private static void apply(State packet){var level=net.minecraft.client.Minecraft.getInstance().level;if(level!=null&&level.dimension().location().equals(packet.dimension)&&level.getBlockEntity(packet.position) instanceof RaidBlockEntity raid){byte[] values=new byte[3];for(int slot=0;slot<3;slot++)values[slot]=(byte)((packet.presence>>slot)&1);raid.applyClient(values,packet.label);}}}
}
