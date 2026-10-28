package li.cil.oc.common.network;

import li.cil.oc.common.component.TextBuffer;
import li.cil.oc.common.menu.AgentMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Robot input is authorized against the currently open server menu, including after moves. */
public final class AgentNetworking {
    public enum Action{POWER,SCROLL,SELECT,REQUEST,KEY_DOWN,KEY_UP,CLIPBOARD,TOUCH,DRAG,DROP,MOUSE_SCROLL}
    public record Input(int menu,Action action,int character,int code,double x,double y,int value,String text)implements CustomPacketPayload{
        public static final Type<Input> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("opencomputers","agent_input"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Input> CODEC=StreamCodec.of((buffer,value)->{buffer.writeVarInt(value.menu);buffer.writeEnum(value.action);buffer.writeVarInt(value.character);buffer.writeVarInt(value.code);buffer.writeDouble(value.x);buffer.writeDouble(value.y);buffer.writeVarInt(value.value);buffer.writeUtf(value.text,8192);},buffer->new Input(buffer.readVarInt(),buffer.readEnum(Action.class),buffer.readVarInt(),buffer.readVarInt(),buffer.readDouble(),buffer.readDouble(),buffer.readVarInt(),buffer.readUtf(8192)));
        @Override public Type<? extends CustomPacketPayload>type(){return TYPE;}
    }
    private AgentNetworking(){}
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(Input.TYPE,Input.CODEC,(packet,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)handle(player,packet);}));}
    private static void handle(ServerPlayer player,Input packet){
        if(player.isSpectator()||!(player.containerMenu instanceof AgentMenu menu)||menu.containerId!=packet.menu||!menu.stillValid(player)||menu.host.machine()==null||!menu.host.machine().canInteract(player.getGameProfile().getName()))return;
        var host=menu.host;
        if(packet.action==Action.POWER){if(host.machine().isRunning())host.machine().stop();else{if(host instanceof li.cil.oc.common.entity.DroneEntity drone)drone.preparePowerUp();host.machine().start();}host.synchronize();return;}
        if(packet.action==Action.SCROLL){menu.scroll(packet.value);return;}
        if(packet.action==Action.SELECT){if(packet.value>=0&&packet.value<host.mainInventory().getContainerSize())host.setSelectedSlot(packet.value);return;}
        if(packet.action==Action.REQUEST){host.synchronize();return;}
        TextBuffer screen=host.runtime().screen();if(screen==null)return;
        if(packet.action==Action.KEY_DOWN||packet.action==Action.KEY_UP){if(packet.character<0||packet.character>65535||packet.code<0||packet.code>255)return;if(packet.action==Action.KEY_DOWN)screen.keyDown((char)packet.character,packet.code,player);else screen.keyUp((char)packet.character,packet.code,player);return;}
        if(packet.action==Action.CLIPBOARD){screen.clipboard(packet.text,player);return;}
        boolean outsideDrop=packet.action==Action.DROP&&packet.x==-1&&packet.y==-1;
        if(!Double.isFinite(packet.x)||!Double.isFinite(packet.y)||!outsideDrop&&(packet.x<0||packet.y<0||packet.x>=screen.getViewportWidth()||packet.y>=screen.getViewportHeight()))return;
        switch(packet.action){case TOUCH->{if(packet.value==0||packet.value==1)screen.mouseDown(packet.x,packet.y,packet.value,player);}case DRAG->{if(packet.value==0||packet.value==1)screen.mouseDrag(packet.x,packet.y,packet.value,player);}case DROP->{if(packet.value==0||packet.value==1)screen.mouseUp(packet.x,packet.y,packet.value,player);}case MOUSE_SCROLL->screen.mouseScroll(packet.x,packet.y,Integer.signum(packet.value),player);default->{}}
    }
    public static void writeScreen(TextBuffer screen,CompoundTag tag){if(screen==null)return;CompoundTag data=new CompoundTag();screen.data().save(data);tag.put("agentBuffer",data);tag.putInt("agentViewportWidth",screen.getViewportWidth());tag.putInt("agentViewportHeight",screen.getViewportHeight());tag.putBoolean("agentPower",screen.hasPower()&&screen.getPowerState());tag.putInt("agentMaxWidth",screen.getMaximumWidth());tag.putInt("agentMaxHeight",screen.getMaximumHeight());tag.putBoolean("agentKeyboard",!((li.cil.oc.server.agent.AgentHost)screen.host).runtime().keyboards().isEmpty());}
}
