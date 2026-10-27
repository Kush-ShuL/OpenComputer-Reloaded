package li.cil.oc.common.network;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import li.cil.oc.common.block.entity.RackBlockEntity;
import li.cil.oc.common.component.TerminalServer;
import li.cil.oc.common.component.TextBuffer;
import li.cil.oc.common.init.Racks;
import li.cil.oc.common.menu.RackMenu;
import li.cil.oc.common.menu.ServerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Menu-scoped rack edits and key/range/dimension-bound remote terminal sessions. */
@EventBusSubscriber(modid="opencomputers")
public final class RackNetworking {
    private RackNetworking(){}
    private record TerminalKey(ServerLevel level,String address){}
    private record Session(UUID token,TerminalServer terminal,InteractionHand hand,ServerPlayer player){}
    private static final Map<TerminalKey,TerminalServer> TERMINALS=new HashMap<>();
    private static final Map<TerminalServer,Long> REVISIONS=new java.util.WeakHashMap<>();
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    public record Edit(BlockPos position,int slot,int connection,int side,int operation,boolean enabled) implements CustomPacketPayload {
        public static final Type<Edit> TYPE=new Type<>(id("rack_edit"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Edit> CODEC=StreamCodec.of((b,p)->{b.writeBlockPos(p.position);b.writeVarInt(p.slot);b.writeVarInt(p.connection);b.writeVarInt(p.side);b.writeVarInt(p.operation);b.writeBoolean(p.enabled);},b->new Edit(b.readBlockPos(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean()));
        @Override public Type<Edit> type(){return TYPE;}
    }
    public record TerminalOpen(UUID token,ResourceLocation dimension,BlockPos position,int slot,String address,long revision,CompoundTag data) implements CustomPacketPayload {
        public static final Type<TerminalOpen> TYPE=new Type<>(id("terminal_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf,TerminalOpen> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.token);b.writeResourceLocation(p.dimension);b.writeBlockPos(p.position);b.writeVarInt(p.slot);b.writeUtf(p.address,64);b.writeVarLong(p.revision);b.writeNbt(p.data);},b->new TerminalOpen(b.readUUID(),b.readResourceLocation(),b.readBlockPos(),b.readVarInt(),b.readUtf(64),b.readVarLong(),java.util.Objects.requireNonNull(b.readNbt())));
        @Override public Type<TerminalOpen> type(){return TYPE;}
    }
    public record TerminalUpdate(UUID token,long revision,boolean full,CompoundTag data) implements CustomPacketPayload {
        public static final Type<TerminalUpdate> TYPE=new Type<>(id("terminal_update"));
        public static final StreamCodec<RegistryFriendlyByteBuf,TerminalUpdate> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.token);b.writeVarLong(p.revision);b.writeBoolean(p.full);b.writeNbt(p.data);},b->new TerminalUpdate(b.readUUID(),b.readVarLong(),b.readBoolean(),java.util.Objects.requireNonNull(b.readNbt())));
        @Override public Type<TerminalUpdate> type(){return TYPE;}
    }
    public record TerminalRequest(UUID token) implements CustomPacketPayload {
        public static final Type<TerminalRequest> TYPE=new Type<>(id("terminal_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,TerminalRequest> CODEC=StreamCodec.of((b,p)->b.writeUUID(p.token),b->new TerminalRequest(b.readUUID()));
        @Override public Type<TerminalRequest> type(){return TYPE;}
    }
    public record TerminalInput(UUID token,ScreenNetworking.InputType action,int character,int code,double x,double y,int value,String text) implements CustomPacketPayload {
        public static final Type<TerminalInput> TYPE=new Type<>(id("terminal_input"));
        public static final StreamCodec<RegistryFriendlyByteBuf,TerminalInput> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.token);b.writeEnum(p.action);b.writeVarInt(p.character);b.writeVarInt(p.code);b.writeDouble(p.x);b.writeDouble(p.y);b.writeVarInt(p.value);b.writeUtf(p.text,8192);},b->new TerminalInput(b.readUUID(),b.readEnum(ScreenNetworking.InputType.class),b.readVarInt(),b.readVarInt(),b.readDouble(),b.readDouble(),b.readVarInt(),b.readUtf(8192)));
        @Override public Type<TerminalInput> type(){return TYPE;}
    }
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("opencomputers",path);}
    public static void register(RegisterPayloadHandlersEvent event){var registrar=event.registrar("1");
        registrar.playToServer(Edit.TYPE,Edit.CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)edit(player,p);}));
        registrar.playToClient(TerminalOpen.TYPE,TerminalOpen.CODEC,(p,c)->c.enqueueWork(()->li.cil.oc.client.RackClient.open(p)));
        registrar.playToClient(TerminalUpdate.TYPE,TerminalUpdate.CODEC,(p,c)->c.enqueueWork(()->li.cil.oc.client.RackClient.update(p)));
        registrar.playToServer(TerminalRequest.TYPE,TerminalRequest.CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player){Session session=session(player,p.token);if(session!=null)sendSnapshot(session);}}));
        registrar.playToServer(TerminalInput.TYPE,TerminalInput.CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)input(player,p);}));
    }
    private static void edit(ServerPlayer player,Edit packet){
        if(player.isSpectator()||!player.level().hasChunkAt(packet.position)||!(player.level().getBlockEntity(packet.position) instanceof RackBlockEntity rack)||!rack.stillValid(player))return;
        if(packet.operation==2){if(!(player.containerMenu instanceof ServerMenu menu)||!menu.mounted||!menu.position.equals(packet.position)||menu.rackSlot!=packet.slot)return;var mountable=rack.getMountable(packet.slot);if(mountable instanceof li.cil.oc.server.component.Server server&&server.stillValid(player)){if(packet.enabled)server.machine().start();else server.machine().stop();rack.markChanged(packet.slot);}return;}
        if(!(player.containerMenu instanceof RackMenu menu)||!menu.position.equals(packet.position))return;
        if(packet.operation==0)rack.connect(packet.slot,packet.connection,packet.side);
        else if(packet.operation==1)rack.setRelayEnabled(packet.enabled);
    }
    public static void registerTerminal(TerminalServer terminal){if(terminal.world() instanceof ServerLevel level){if(!level.getServer().isSameThread()){level.getServer().execute(()->registerTerminal(terminal));return;}if(terminal.node()!=null&&terminal.node().address()!=null)TERMINALS.put(new TerminalKey(level,terminal.node().address()),terminal);}}
    public static void unregisterTerminal(TerminalServer terminal){if(terminal.world() instanceof ServerLevel level&&!level.getServer().isSameThread()){level.getServer().execute(()->unregisterTerminal(terminal));return;}TERMINALS.values().removeIf(value->value==terminal);REVISIONS.remove(terminal);for(Session session:List.copyOf(SESSIONS.values()))if(session.terminal==terminal)close(session,true);}
    public static void openTerminal(ServerPlayer player,InteractionHand hand){
        ItemStack held=player.getItemInHand(hand);if(hand!=InteractionHand.MAIN_HAND||!held.is(Racks.TERMINAL.get()))return;CompoundTag data=held.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();TerminalServer terminal=TERMINALS.get(new TerminalKey(player.serverLevel(),data.getString("oc:server")));
        if(terminal==null||!inRange(player,terminal)){player.displayClientMessage(Component.translatable("oc:gui.Terminal.OutOfRange"),false);return;}
        if(!valid(player,terminal,hand)){player.displayClientMessage(Component.translatable("oc:gui.Terminal.InvalidKey"),false);return;}
        Session previous=SESSIONS.remove(player.getUUID());if(previous!=null)release(previous);((TextBuffer)terminal.buffer()).flushCommands();
        Session session=new Session(UUID.randomUUID(),terminal,hand,player);SESSIONS.put(player.getUUID(),session);
        BlockPos pos=BlockPos.containing(terminal.xPosition(),terminal.yPosition(),terminal.zPosition());PacketDistributor.sendToPlayer(player,new TerminalOpen(session.token,player.level().dimension().location(),pos,terminal.slot,terminal.node().address(),REVISIONS.getOrDefault(terminal,0L),snapshot(terminal)));
    }
    private static boolean valid(ServerPlayer player,TerminalServer terminal,InteractionHand hand){
        if(hand!=InteractionHand.MAIN_HAND||!inRange(player,terminal))return false;
        ItemStack held=player.getItemInHand(hand);if(!held.is(Racks.TERMINAL.get()))return false;CompoundTag tag=held.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        return terminal.node().address()!=null&&terminal.node().address().equals(tag.getString("oc:server"))&&terminal.keys.contains(tag.getString("oc:key"));
    }
    private static boolean inRange(ServerPlayer player,TerminalServer terminal){return player.isAlive()&&!player.isRemoved()&&terminal.world()==player.level()&&terminal.rack.getMountable(terminal.slot)==terminal&&terminal.node()!=null&&terminal.node().network()!=null&&player.distanceToSqr(terminal.xPosition(),terminal.yPosition(),terminal.zPosition())<terminal.range*terminal.range;}
    private static Session session(ServerPlayer player,UUID token){Session value=SESSIONS.get(player.getUUID());if(value==null||!value.token.equals(token))return null;if(!valid(player,value.terminal,value.hand)){close(value,true);return null;}return value;}
    private static CompoundTag snapshot(TerminalServer terminal){TextBuffer buffer=(TextBuffer)terminal.buffer();if(buffer.node()!=null&&buffer.node().network()!=null)for(var node:buffer.node().network().nodes())if(node.host() instanceof li.cil.oc.api.machine.Machine machine&&!machine.isPaused())machine.pause(.05);CompoundTag tag=new CompoundTag();buffer.data().save(tag);net.minecraft.nbt.ListTag pages=new net.minecraft.nbt.ListTag();for(var device:buffer.videoDevices().values())for(var ram:device.videoBuffers().values()){CompoundTag page=new CompoundTag();ram.save(page);page.putString("owner",ram.owner);page.putInt("id",ram.id);pages.add(page);}tag.put("vram",pages);tag.putInt("viewportWidth",buffer.getViewportWidth());tag.putInt("viewportHeight",buffer.getViewportHeight());tag.putBoolean("on",buffer.getPowerState());tag.putBoolean("render",buffer.getPowerState()&&buffer.hasPower());tag.putString("address",buffer.nodeAddress()==null?"":buffer.nodeAddress());return tag;}
    private static void sendSnapshot(Session session){((TextBuffer)session.terminal.buffer()).flushCommands();PacketDistributor.sendToPlayer(session.player,new TerminalUpdate(session.token,REVISIONS.getOrDefault(session.terminal,0L),true,snapshot(session.terminal)));}
    public static void publishTerminal(TerminalServer terminal,List<TextBuffer.Command> commands){if(commands.isEmpty())return;long revision=REVISIONS.merge(terminal,1L,Long::sum);CompoundTag data=ScreenNetworking.commands(commands);for(Session session:List.copyOf(SESSIONS.values()))if(session.terminal==terminal){if(valid(session.player,terminal,session.hand))PacketDistributor.sendToPlayer(session.player,new TerminalUpdate(session.token,revision,false,data));else close(session,true);}}
    private static void input(ServerPlayer player,TerminalInput packet){
        Session session=session(player,packet.token);if(session==null)return;if(packet.action==ScreenNetworking.InputType.CLOSE){close(session,false);return;}
        TextBuffer buffer=(TextBuffer)session.terminal.buffer();
        switch(packet.action){
            case KEY_DOWN,KEY_UP->{if(packet.character<0||packet.character>65535||packet.code<0||packet.code>255)return;if(packet.action==ScreenNetworking.InputType.KEY_DOWN)buffer.keyDown((char)packet.character,packet.code,player);else buffer.keyUp((char)packet.character,packet.code,player);}
            case CLIPBOARD->buffer.clipboard(packet.text,player);
            case TOUCH,DRAG,DROP,SCROLL->{boolean outsideDrop=packet.action==ScreenNetworking.InputType.DROP&&packet.x==-1&&packet.y==-1;if(!outsideDrop&&(!Double.isFinite(packet.x)||!Double.isFinite(packet.y)||packet.x<0||packet.y<0||packet.x>=buffer.getViewportWidth()||packet.y>=buffer.getViewportHeight()))return;if(packet.action==ScreenNetworking.InputType.SCROLL){if(packet.value!=1&&packet.value!=-1)return;buffer.mouseScroll(packet.x,packet.y,packet.value,player);}else if(packet.value>=0&&packet.value<2){if(packet.action==ScreenNetworking.InputType.TOUCH)buffer.mouseDown(packet.x,packet.y,packet.value,player);else if(packet.action==ScreenNetworking.InputType.DRAG)buffer.mouseDrag(packet.x,packet.y,packet.value,player);else buffer.mouseUp(packet.x,packet.y,packet.value,player);}}
            default->{}
        }
    }
    private static void release(Session session){if(session.terminal.keyboard() instanceof li.cil.oc.server.component.Keyboard keyboard)keyboard.releasePressedKeys(session.player);}
    private static void close(Session session,boolean notify){SESSIONS.remove(session.player.getUUID(),session);release(session);if(notify&&!session.player.isRemoved()){CompoundTag closed=new CompoundTag();closed.putBoolean("closed",true);PacketDistributor.sendToPlayer(session.player,new TerminalUpdate(session.token,0,true,closed));}}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){if(event.getServer().getTickCount()%20==0)for(Session session:List.copyOf(SESSIONS.values()))if(!valid(session.player,session.terminal,session.hand))close(session,true);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){SESSIONS.clear();TERMINALS.clear();REVISIONS.clear();}
}
