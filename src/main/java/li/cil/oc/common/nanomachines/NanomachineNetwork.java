package li.cil.oc.common.nanomachines;

import li.cil.oc.OpenComputers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Player-scoped full configuration, trigger and power synchronization. */
@EventBusSubscriber(modid=OpenComputers.ID,bus=EventBusSubscriber.Bus.MOD)
public final class NanomachineNetwork {
    public record State(int entityId,int mode,boolean installed,double energy,CompoundTag configuration,byte[] inputs)implements CustomPacketPayload{
        static final Type<State> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(OpenComputers.ID,"nanomachines"));
        static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of((buffer,message)->{buffer.writeVarInt(message.entityId);buffer.writeByte(message.mode);buffer.writeBoolean(message.installed);buffer.writeDouble(message.energy);if(message.mode==0)buffer.writeNbt(message.configuration);if(message.mode==1)buffer.writeByteArray(message.inputs);},buffer->{int id=buffer.readVarInt(),mode=buffer.readUnsignedByte();boolean installed=buffer.readBoolean();double energy=buffer.readDouble();CompoundTag configuration=mode==0?buffer.readNbt():new CompoundTag();byte[] inputs=mode==1?buffer.readByteArray(65536):new byte[0];if(mode>2)throw new IllegalArgumentException("unknown nanomachine state message");return new State(id,mode,installed,energy,configuration==null?new CompoundTag():configuration,inputs);});
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public static void sendConfiguration(Player player){send(player,0);}
    public static void sendInputs(ControllerImpl controller){send(controller.player,1);}
    public static void sendPower(ControllerImpl controller){send(controller.player,2);}
    private static void send(Player player,int mode){if(!(player instanceof ServerPlayer server))return;ControllerImpl controller=(ControllerImpl)Nanomachines.INSTANCE.getController(player);CompoundTag configuration=new CompoundTag();if(mode==0&&controller!=null)controller.save(configuration);PacketDistributor.sendToPlayersTrackingEntityAndSelf(server,new State(player.getId(),mode,controller!=null,controller==null?0:controller.getLocalBuffer(),configuration,mode==1&&controller!=null?controller.inputStates():new byte[0]));}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToClient(State.TYPE,State.CODEC,(message,context)->{if(FMLEnvironment.dist==Dist.CLIENT)context.enqueueWork(()->Client.receive(message));});}
    @OnlyIn(Dist.CLIENT) private static final class Client{
        static void receive(State message){var level=net.minecraft.client.Minecraft.getInstance().level;if(level==null||!(level.getEntity(message.entityId) instanceof Player player))return;if(!message.installed){Nanomachines.INSTANCE.uninstallController(player);return;}if(message.mode==0)Nanomachines.clientConfiguration(player,true,message.configuration);ControllerImpl controller=(ControllerImpl)Nanomachines.INSTANCE.installController(player);controller.setClientEnergy(message.energy);if(message.mode==1)controller.applyInputs(message.inputs);}
    }
}
