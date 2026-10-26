package li.cil.oc.common.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.*;
import java.util.function.Consumer;

/** Server-authoritative visibility, with no physical-client types referenced by this class. */
public final class PetNetworking {
    private static final Set<UUID> HIDDEN=new HashSet<>();
    private static final Set<UUID> INITIALIZED=new HashSet<>();
    private static volatile Consumer<Visibility> clientReceiver=packet->{};
    private PetNetworking(){}
    public record SetVisibility(boolean visible) implements CustomPacketPayload {
        public static final Type<SetVisibility> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("opencomputers","pet_visibility_set"));
        public static final StreamCodec<RegistryFriendlyByteBuf,SetVisibility> CODEC=StreamCodec.of((buffer,value)->buffer.writeBoolean(value.visible),buffer->new SetVisibility(buffer.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Visibility(boolean reset,Map<UUID,Boolean> players) implements CustomPacketPayload {
        public Visibility{players=Map.copyOf(players);}
        public static final Type<Visibility> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("opencomputers","pet_visibility"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Visibility> CODEC=StreamCodec.of((buffer,value)->{buffer.writeBoolean(value.reset);buffer.writeVarInt(value.players.size());value.players.forEach((id,visible)->{buffer.writeUUID(id);buffer.writeBoolean(visible);});},buffer->{boolean reset=buffer.readBoolean();int count=buffer.readVarInt();if(count<0||count>16384)throw new IllegalArgumentException("invalid pet visibility snapshot size");Map<UUID,Boolean> players=new HashMap<>();for(int i=0;i<count;i++)players.put(buffer.readUUID(),buffer.readBoolean());return new Visibility(reset,players);});
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public static void clientReceiver(Consumer<Visibility> receiver){clientReceiver=Objects.requireNonNull(receiver);}
    public static void register(IEventBus bus){bus.addListener(PetNetworking::payloads);NeoForge.EVENT_BUS.addListener(PetNetworking::logout);NeoForge.EVENT_BUS.addListener(PetNetworking::stop);}
    private static void payloads(RegisterPayloadHandlersEvent event){var registrar=event.registrar("1");registrar.playToClient(Visibility.TYPE,Visibility.CODEC,(packet,context)->context.enqueueWork(()->clientReceiver.accept(packet)));registrar.playToServer(SetVisibility.TYPE,SetVisibility.CODEC,(packet,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player){UUID id=player.getUUID();boolean initial=INITIALIZED.add(id);boolean changed=packet.visible?HIDDEN.remove(id):HIDDEN.add(id);if(initial){Map<UUID,Boolean> hidden=new HashMap<>();HIDDEN.forEach(uuid->hidden.put(uuid,false));PacketDistributor.sendToPlayer(player,new Visibility(true,hidden));}if(changed)PacketDistributor.sendToAllPlayers(new Visibility(false,Map.of(id,packet.visible)));}}));}
    private static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer player){INITIALIZED.remove(player.getUUID());if(HIDDEN.remove(player.getUUID()))PacketDistributor.sendToAllPlayers(new Visibility(false,Map.of(player.getUUID(),true)));}}
    private static void stop(ServerStoppedEvent event){HIDDEN.clear();INITIALIZED.clear();}
}
