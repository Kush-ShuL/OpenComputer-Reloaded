package li.cil.oc.common.network;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import li.cil.oc.common.component.TextBuffer;
import li.cil.oc.server.portable.TabletHost;
import li.cil.oc.server.portable.TabletManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Tablet sessions are bound to a physical held stack, player, hand and dimension. */
public final class PortableNetworking {
    private record Session(UUID token,TabletHost host,ServerPlayer player,InteractionHand hand){}
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Map<TabletHost,Long> REVISIONS=new java.util.WeakHashMap<>();
    private PortableNetworking(){}
    public record TabletOpen(UUID token,ResourceLocation dimension,BlockPos position,String address,long revision,CompoundTag data) implements CustomPacketPayload {
        public static final Type<TabletOpen> TYPE=new Type<>(id("tablet_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf,TabletOpen> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.token);b.writeResourceLocation(p.dimension);b.writeBlockPos(p.position);b.writeUtf(p.address,64);b.writeVarLong(p.revision);b.writeNbt(p.data);},b->new TabletOpen(b.readUUID(),b.readResourceLocation(),b.readBlockPos(),b.readUtf(64),b.readVarLong(),java.util.Objects.requireNonNull(b.readNbt())));
        @Override public Type<TabletOpen> type(){return TYPE;}
    }
    public record TabletUpdate(UUID token,long revision,boolean full,CompoundTag data) implements CustomPacketPayload {
        public static final Type<TabletUpdate> TYPE=new Type<>(id("tablet_update"));
        public static final StreamCodec<RegistryFriendlyByteBuf,TabletUpdate> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.token);b.writeVarLong(p.revision);b.writeBoolean(p.full);b.writeNbt(p.data);},b->new TabletUpdate(b.readUUID(),b.readVarLong(),b.readBoolean(),java.util.Objects.requireNonNull(b.readNbt())));
        @Override public Type<TabletUpdate> type(){return TYPE;}
    }
    public record TabletRequest(UUID token) implements CustomPacketPayload {
        public static final Type<TabletRequest> TYPE=new Type<>(id("tablet_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,TabletRequest> CODEC=StreamCodec.of((b,p)->b.writeUUID(p.token),b->new TabletRequest(b.readUUID()));
        @Override public Type<TabletRequest> type(){return TYPE;}
    }
    public record TabletInput(UUID token,ScreenNetworking.InputType action,int character,int code,double x,double y,int value,String text) implements CustomPacketPayload {
        public static final Type<TabletInput> TYPE=new Type<>(id("tablet_input"));
        public static final StreamCodec<RegistryFriendlyByteBuf,TabletInput> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.token);b.writeEnum(p.action);b.writeVarInt(p.character);b.writeVarInt(p.code);b.writeDouble(p.x);b.writeDouble(p.y);b.writeVarInt(p.value);b.writeUtf(p.text,8192);},b->new TabletInput(b.readUUID(),b.readEnum(ScreenNetworking.InputType.class),b.readVarInt(),b.readVarInt(),b.readDouble(),b.readDouble(),b.readVarInt(),b.readUtf(8192)));
        @Override public Type<TabletInput> type(){return TYPE;}
    }
    private static ResourceLocation id(String name){return ResourceLocation.fromNamespaceAndPath("opencomputers",name);}
    public static void register(RegisterPayloadHandlersEvent event){var registrar=event.registrar("1");registrar.playToClient(TabletOpen.TYPE,TabletOpen.CODEC,(packet,context)->context.enqueueWork(()->li.cil.oc.client.PortableClient.open(packet)));registrar.playToClient(TabletUpdate.TYPE,TabletUpdate.CODEC,(packet,context)->context.enqueueWork(()->li.cil.oc.client.PortableClient.update(packet)));registrar.playToServer(TabletRequest.TYPE,TabletRequest.CODEC,(packet,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player){Session session=session(player,packet.token);if(session!=null)sendSnapshot(session);}}));registrar.playToServer(TabletInput.TYPE,TabletInput.CODEC,(packet,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)input(player,packet);}));}
    public static void registerEvents(){NeoForge.EVENT_BUS.addListener(PortableNetworking::tick);}
    public static void open(ServerPlayer player,TabletHost host){if(host.runtime.screen()==null){player.displayClientMessage(net.minecraft.network.chat.Component.translatable("oc:gui.Screen.NoScreen"),true);return;}InteractionHand hand=player.getMainHandItem()==host.stack()?InteractionHand.MAIN_HAND:player.getOffhandItem()==host.stack()?InteractionHand.OFF_HAND:null;if(hand==null||!host.stillValid(player))return;
        Session previous=SESSIONS.remove(player.getUUID());if(previous!=null)release(previous);host.runtime.screen().flushCommands();Session session=new Session(UUID.randomUUID(),host,player,hand);SESSIONS.put(player.getUUID(),session);PacketDistributor.sendToPlayer(player,new TabletOpen(session.token,player.level().dimension().location(),BlockPos.containing(host.xPosition(),host.yPosition(),host.zPosition()),host.id,REVISIONS.getOrDefault(host,0L),snapshot(host)));
    }
    private static boolean valid(Session session){return !session.player.isRemoved()&&!session.player.hasDisconnected()&&!session.player.isSpectator()&&session.player.level()==session.host.world()&&session.player.getItemInHand(session.hand)==session.host.stack()&&session.host.stillValid(session.player)&&session.host.runtime.screen()!=null;}
    private static Session session(ServerPlayer player,UUID token){Session session=SESSIONS.get(player.getUUID());if(session==null||!session.token.equals(token))return null;if(!valid(session)){closeSession(session,true);return null;}return session;}
    private static CompoundTag snapshot(TabletHost host){TextBuffer buffer=host.runtime.screen();if(!host.machine().isPaused())host.machine().pause(.05);CompoundTag tag=new CompoundTag();buffer.data().save(tag);ListTag pages=new ListTag();for(var device:buffer.videoDevices().values())for(var ram:device.videoBuffers().values()){CompoundTag page=new CompoundTag();ram.save(page);page.putString("owner",ram.owner);page.putInt("id",ram.id);pages.add(page);}tag.put("vram",pages);tag.putInt("viewportWidth",buffer.getViewportWidth());tag.putInt("viewportHeight",buffer.getViewportHeight());tag.putBoolean("on",buffer.getPowerState());tag.putBoolean("render",buffer.getPowerState()&&buffer.hasPower());tag.putBoolean("keyboard",!host.runtime.keyboards().isEmpty());return tag;}
    private static void sendSnapshot(Session session){session.host.runtime.screen().flushCommands();PacketDistributor.sendToPlayer(session.player,new TabletUpdate(session.token,REVISIONS.getOrDefault(session.host,0L),true,snapshot(session.host)));}
    public static void publish(TabletHost host){for(Session session:List.copyOf(SESSIONS.values()))if(session.host==host){if(valid(session))sendSnapshot(session);else closeSession(session,true);}}
    public static void publishCommands(TabletHost host,List<TextBuffer.Command> commands){if(commands.isEmpty()||host.closed())return;long revision=REVISIONS.merge(host,1L,Long::sum);CompoundTag data=ScreenNetworking.commands(commands);for(Session session:List.copyOf(SESSIONS.values()))if(session.host==host){if(valid(session))PacketDistributor.sendToPlayer(session.player,new TabletUpdate(session.token,revision,false,data));else closeSession(session,true);}}
    private static void input(ServerPlayer player,TabletInput packet){Session session=session(player,packet.token);if(session==null)return;if(packet.action==ScreenNetworking.InputType.CLOSE){closeSession(session,false);return;}TextBuffer buffer=session.host.runtime.screen();
        switch(packet.action){case KEY_DOWN,KEY_UP->{if(packet.character<0||packet.character>65535||packet.code<0||packet.code>255)return;if(packet.action==ScreenNetworking.InputType.KEY_DOWN)buffer.keyDown((char)packet.character,packet.code,player);else buffer.keyUp((char)packet.character,packet.code,player);}case CLIPBOARD->buffer.clipboard(packet.text,player);case TOUCH,DRAG,DROP,SCROLL->{if(!Double.isFinite(packet.x)||!Double.isFinite(packet.y)||packet.x<0||packet.y<0||packet.x>=buffer.getViewportWidth()||packet.y>=buffer.getViewportHeight())return;if(packet.action==ScreenNetworking.InputType.SCROLL){if(packet.value==1||packet.value==-1)buffer.mouseScroll(packet.x,packet.y,packet.value,player);}else if(packet.value>=0&&packet.value<2){if(packet.action==ScreenNetworking.InputType.TOUCH)buffer.mouseDown(packet.x,packet.y,packet.value,player);else if(packet.action==ScreenNetworking.InputType.DRAG)buffer.mouseDrag(packet.x,packet.y,packet.value,player);else buffer.mouseUp(packet.x,packet.y,packet.value,player);}}default->{}}
    }
    private static void release(Session session){for(var keyboard:session.host.runtime.keyboards())keyboard.releasePressedKeys(session.player);session.host.markChanged();}
    private static void closeSession(Session session,boolean notify){SESSIONS.remove(session.player.getUUID(),session);release(session);if(notify&&!session.player.isRemoved()&&!session.player.hasDisconnected()){CompoundTag tag=new CompoundTag();tag.putBoolean("closed",true);PacketDistributor.sendToPlayer(session.player,new TabletUpdate(session.token,0,true,tag));}}
    public static void close(TabletHost host){for(Session session:List.copyOf(SESSIONS.values()))if(session.host==host)closeSession(session,true);REVISIONS.remove(host);}
    private static void tick(ServerTickEvent.Post event){if(event.getServer().getTickCount()%20==0)for(Session session:List.copyOf(SESSIONS.values()))if(!valid(session))closeSession(session,true);}
}
