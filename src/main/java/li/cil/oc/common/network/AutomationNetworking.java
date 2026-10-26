package li.cil.oc.common.network;

import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.common.block.entity.TransposerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.api.distmarker.*;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The original nearby-player transfer pulse; no server-bound control messages. */
public final class AutomationNetworking {
    private AutomationNetworking(){}
    public record TransposerActivity(ResourceLocation dimension,BlockPos pos)implements CustomPacketPayload {
        public static final Type<TransposerActivity> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(OpenComputers.ID,"transposer_activity"));
        public static final StreamCodec<RegistryFriendlyByteBuf,TransposerActivity> CODEC=StreamCodec.of((buffer,packet)->{buffer.writeResourceLocation(packet.dimension);buffer.writeBlockPos(packet.pos);},buffer->new TransposerActivity(buffer.readResourceLocation(),buffer.readBlockPos()));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public static void sendTransposerActivity(TransposerBlockEntity entity){if(entity.getLevel() instanceof ServerLevel level)PacketDistributor.sendToPlayersNear(level,null,entity.xPosition(),entity.yPosition(),entity.zPosition(),Settings.get().maxNetworkClientEffectPacketDistance/2d,new TransposerActivity(level.dimension().location(),entity.getBlockPos()));}
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToClient(TransposerActivity.TYPE,TransposerActivity.CODEC,(packet,context)->{if(FMLEnvironment.dist==Dist.CLIENT)context.enqueueWork(()->Client.receive(packet));});}
    @OnlyIn(Dist.CLIENT) private static final class Client {
        static void receive(TransposerActivity packet){var level=net.minecraft.client.Minecraft.getInstance().level;if(level!=null&&level.dimension().location().equals(packet.dimension)&&level.getBlockEntity(packet.pos) instanceof TransposerBlockEntity entity)entity.lastOperation=System.currentTimeMillis();}
    }
}
