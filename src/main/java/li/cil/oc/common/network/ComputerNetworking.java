package li.cil.oc.common.network;

import li.cil.oc.common.block.entity.CaseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid="opencomputers",bus=EventBusSubscriber.Bus.MOD)
public final class ComputerNetworking {
    private ComputerNetworking(){}
    public record Power(BlockPos pos,boolean start) implements CustomPacketPayload {
        public static final Type<Power> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("opencomputers","computer_power"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Power> CODEC=StreamCodec.of((b,p)->{b.writeBlockPos(p.pos);b.writeBoolean(p.start);},b->new Power(b.readBlockPos(),b.readBoolean()));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public static void setPower(BlockPos pos,boolean running){PacketDistributor.sendToServer(new Power(pos,running));}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(Power.TYPE,Power.CODEC,(packet,context)->context.enqueueWork(()->{
        var player=context.player();if(player.isSpectator()||!player.level().hasChunkAt(packet.pos)||player.distanceToSqr(packet.pos.getCenter())>64)return;
        if(player.level().getBlockEntity(packet.pos) instanceof CaseBlockEntity entity&&entity.stillValid(player)){if(packet.start)entity.machine().start();else entity.machine().stop();}
    }));}
}
