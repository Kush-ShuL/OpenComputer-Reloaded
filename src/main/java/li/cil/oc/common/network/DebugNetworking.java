package li.cil.oc.common.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class DebugNetworking {
    private DebugNetworking(){}
    public record Clipboard(String text) implements CustomPacketPayload {
        public static final Type<Clipboard> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("opencomputers","debug_clipboard"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Clipboard> CODEC=StreamCodec.of((buffer,value)->buffer.writeUtf(value.text,32767),buffer->new Clipboard(buffer.readUtf(32767)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToClient(Clipboard.TYPE,Clipboard.CODEC,(packet,context)->{if(net.neoforged.fml.loading.FMLEnvironment.dist==net.neoforged.api.distmarker.Dist.CLIENT)context.enqueueWork(()->li.cil.oc.client.DebugClient.clipboard(packet.text));});}
    public static void clipboard(ServerPlayer player,String text){if(text.length()>32767)throw new IllegalArgumentException("clipboard text is too long");PacketDistributor.sendToPlayer(player,new Clipboard(text));}
}
