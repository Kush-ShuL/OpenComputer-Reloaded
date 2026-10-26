package li.cil.oc.common.command;

import li.cil.oc.OpenComputers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The original ClientLog packet writes diagnostics to the addressed client's log, not to chat. */
public final class CommandNetworking {
    private CommandNetworking(){}
    public record ClientLog(String line)implements CustomPacketPayload{
        public static final Type<ClientLog> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(OpenComputers.ID,"command_log"));
        public static final StreamCodec<RegistryFriendlyByteBuf,ClientLog> CODEC=StreamCodec.of((buffer,packet)->buffer.writeUtf(packet.line,32767),buffer->new ClientLog(buffer.readUtf(32767)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToClient(ClientLog.TYPE,ClientLog.CODEC,(packet,context)->context.enqueueWork(()->OpenComputers.LOGGER.info("{}",packet.line)));}
    public static void clientLog(ServerPlayer player,String line){PacketDistributor.sendToPlayer(player,new ClientLog(line));}
}
