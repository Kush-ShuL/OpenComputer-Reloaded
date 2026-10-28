package li.cil.oc.server;

import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.api.event.FileSystemAccessEvent;
import li.cil.oc.api.event.NetworkActivityEvent;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Node;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.*;

/** Position-scoped activity audio and computer speaker payloads. */
@EventBusSubscriber(modid=OpenComputers.ID,bus=EventBusSubscriber.Bus.MOD)
public final class PacketSender {
    private PacketSender() {}
    /** Original nearby-update range; a zero global cap leaves the view/explicit range intact. */
    private static double packetRange(ServerLevel level,double requested){double range=requested<0?(level.getServer().getPlayerList().getViewDistance()+1)*16d:requested;double maximum=Settings.get().maxNetworkClientPacketDistance;return maximum>0?Math.min(range,maximum):range;}
    public static void sendToPlayersTrackingBlock(BlockEntity entity,CustomPacketPayload payload){sendToPlayersTrackingBlock(entity,-1,payload);}
    public static void sendToPlayersTrackingBlock(BlockEntity entity,double requested,CustomPacketPayload payload){if(!(entity.getLevel() instanceof ServerLevel level))return;if(!level.getServer().isSameThread()){level.getServer().execute(()->sendToPlayersTrackingBlock(entity,requested,payload));return;}if(entity.isRemoved())return;double range=packetRange(level,requested),squared=range*range;var pos=entity.getBlockPos();for(ServerPlayer player:level.getChunkSource().chunkMap.getPlayers(new net.minecraft.world.level.ChunkPos(pos),false))if(player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=squared)PacketDistributor.sendToPlayer(player,payload);}
    public static void sendToPlayersNear(ServerLevel level,double x,double y,double z,double requested,CustomPacketPayload payload){if(!level.getServer().isSameThread()){level.getServer().execute(()->sendToPlayersNear(level,x,y,z,requested,payload));return;}PacketDistributor.sendToPlayersNear(level,null,x,y,z,packetRange(level,requested),payload);}
    public interface ActivityListener { void onComponentActivity(String address, boolean network); }
    public static void sendNetworkActivity(Node node,EnvironmentHost host){
        if(!(host.world() instanceof ServerLevel level))return;
        if(!level.getServer().isSameThread()){level.getServer().execute(()->sendNetworkActivity(node,host));return;}
        NetworkActivityEvent.Server event=host instanceof BlockEntity block?new NetworkActivityEvent.Server(block,node):new NetworkActivityEvent.Server(level,host.xPosition(),host.yPosition(),host.zPosition(),node);
        NeoForge.EVENT_BUS.post(event);
        // NetworkActivityEvent was not @Cancelable in the pinned public API.
        recordRackActivity(event.getTileEntity(),node,true);
        sendActivity(host,new Activity(event.getWorld().dimension().location(),net.minecraft.core.BlockPos.containing(event.getX(),event.getY(),event.getZ()),node.address()==null?"":node.address(),true,event.getTileEntity()!=null,event.getX(),event.getY(),event.getZ(),"",event.getData().copy()));
    }
    private static void sendActivity(EnvironmentHost host,Activity packet){
        if(host.world() instanceof ServerLevel level){double range=packet.network?Settings.get().maxNetworkClientEffectPacketDistance:Settings.get().maxNetworkClientSoundPacketDistance;if(host instanceof BlockEntity block)sendToPlayersTrackingBlock(block,range,packet);else sendToPlayersNear(level,host.xPosition(),host.yPosition(),host.zPosition(),range,packet);}
    }
    private static void recordRackActivity(BlockEntity tile,Node node,boolean network){
        if(!(tile instanceof li.cil.oc.api.internal.Rack rack))return;
        for(int slot=0;slot<rack.getContainerSize();slot++){
            var mountable=rack.getMountable(slot);
            if(mountable instanceof li.cil.oc.server.component.Server server&&server.componentSlot(node.address())>=0)server.onComponentActivity(node.address(),network);
            else if(!network&&mountable instanceof li.cil.oc.server.component.DiskDriveMountable disk&&disk.filesystemNode()==node){disk.recordAccess();rack.markChanged(slot);}
        }
    }
    public record Activity(ResourceLocation dimension,net.minecraft.core.BlockPos pos,String address,boolean network,boolean blockHosted,double x,double y,double z,String sound,CompoundTag data) implements CustomPacketPayload {
        public static final Type<Activity> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(OpenComputers.ID,"component_activity"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Activity> CODEC=StreamCodec.of((b,p)->{b.writeResourceLocation(p.dimension);b.writeBlockPos(p.pos);b.writeUtf(p.address,64);b.writeBoolean(p.network);b.writeBoolean(p.blockHosted);b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);b.writeUtf(p.sound);b.writeNbt(p.data);},b->new Activity(b.readResourceLocation(),b.readBlockPos(),b.readUtf(64),b.readBoolean(),b.readBoolean(),b.readDouble(),b.readDouble(),b.readDouble(),b.readUtf(),Objects.requireNonNull(b.readNbt())));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    private static final Map<Node,com.google.common.cache.Cache<String,Long>> LAST_ACTIVITY=new WeakHashMap<>();
    public static void sendFileSystemActivity(Node node,EnvironmentHost host,String sound){
        // A direct filesystem callback holds its volume lock; never inspect chunk/player state there.
        if(host.world() instanceof ServerLevel server&&!server.getServer().isSameThread()){server.getServer().execute(()->sendFileSystemActivity(node,host,sound));return;}
        if(!(host.world() instanceof ServerLevel level))return;
        long delay=Settings.get().diskActivitySoundDelay;if(delay<0)return;
        var timeouts=LAST_ACTIVITY.computeIfAbsent(node,ignored->com.google.common.cache.CacheBuilder.newBuilder().maximumSize(250).expireAfterWrite(delay,java.util.concurrent.TimeUnit.MILLISECONDS).build());
        Long timeout=timeouts.getIfPresent(sound);if(timeout!=null&&timeout>System.currentTimeMillis())return;
        FileSystemAccessEvent.Server event=host instanceof BlockEntity block?new FileSystemAccessEvent.Server(sound,block,node):new FileSystemAccessEvent.Server(sound,level,host.xPosition(),host.yPosition(),host.zPosition(),node);
        NeoForge.EVENT_BUS.post(event);if(event.isCanceled())return;
        recordRackActivity(event.getTileEntity(),node,false);
        timeouts.put(sound,System.currentTimeMillis()+delay);
        sendActivity(host,new Activity(event.getWorld().dimension().location(),net.minecraft.core.BlockPos.containing(event.getX(),event.getY(),event.getZ()),node.address()==null?"":node.address(),false,event.getTileEntity()!=null,event.getX(),event.getY(),event.getZ(),event.getSound(),event.getData().copy()));
    }
    public static void sendSound(Level world,double x,double y,double z,int frequency,int duration){
        send(world,x,y,z,new Speaker(world.dimension().location(),x,y,z,Math.max(20,Math.min(2000,frequency)),Math.max(0,Math.min(10000,duration)),""));
    }
    public static void sendSound(Level world,double x,double y,double z,String pattern){
        if(pattern==null||pattern.length()>64)throw new IllegalArgumentException("invalid beep pattern");
        send(world,x,y,z,new Speaker(world.dimension().location(),x,y,z,1000,0,pattern));
    }
    public static void playLocalSound(Level world,double x,double y,double z,String pattern){
        if(FMLEnvironment.dist==Dist.CLIENT&&world.isClientSide)ClientSpeaker.receive(new Speaker(world.dimension().location(),x,y,z,1000,0,pattern));
    }
    private static void send(Level world,double x,double y,double z,Speaker message){
        if(world instanceof ServerLevel level)sendToPlayersNear(level,x,y,z,Settings.get().maxNetworkClientSoundPacketDistance,message);
    }
    public record Speaker(ResourceLocation dimension,double x,double y,double z,int frequency,int duration,String pattern) implements CustomPacketPayload {
        public static final Type<Speaker> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(OpenComputers.ID,"speaker"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Speaker> CODEC=StreamCodec.of((buf,p)->{
            buf.writeResourceLocation(p.dimension);buf.writeDouble(p.x);buf.writeDouble(p.y);buf.writeDouble(p.z);buf.writeVarInt(p.frequency);buf.writeVarInt(p.duration);buf.writeUtf(p.pattern,64);
        },buf->new Speaker(buf.readResourceLocation(),buf.readDouble(),buf.readDouble(),buf.readDouble(),buf.readVarInt(),buf.readVarInt(),buf.readUtf(64)));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){
        event.registrar("1").playToClient(Activity.TYPE,Activity.CODEC,(payload,context)->{
            if(FMLEnvironment.dist==Dist.CLIENT)context.enqueueWork(()->ClientSpeaker.activity(payload));
        });
        event.registrar("1").playToClient(Speaker.TYPE,Speaker.CODEC,(payload,context)->{
            if(FMLEnvironment.dist==Dist.CLIENT)context.enqueueWork(()->ClientSpeaker.receive(payload));
        });
    }
    @OnlyIn(Dist.CLIENT) private static final class ClientSpeaker {
        private static final java.util.concurrent.ThreadPoolExecutor AUDIO=new java.util.concurrent.ThreadPoolExecutor(1,1,20,java.util.concurrent.TimeUnit.SECONDS,
            new java.util.concurrent.ArrayBlockingQueue<>(64),r->{Thread t=new Thread(r,"OpenComputers-Speaker");t.setDaemon(true);return t;},new java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy());
        static void activity(Activity packet){
            var level=net.minecraft.client.Minecraft.getInstance().level;
            if(level==null||!level.dimension().location().equals(packet.dimension))return;
            BlockEntity tile=level.getBlockEntity(packet.pos);if(packet.blockHosted&&tile==null)return;
            if(packet.network){
                NetworkActivityEvent.Client event=packet.blockHosted?new NetworkActivityEvent.Client(tile,packet.data):new NetworkActivityEvent.Client(level,packet.x,packet.y,packet.z,packet.data);
                NeoForge.EVENT_BUS.post(event);
            }else{
                FileSystemAccessEvent.Client event=packet.blockHosted?new FileSystemAccessEvent.Client(packet.sound,tile,packet.data):new FileSystemAccessEvent.Client(packet.sound,level,packet.x,packet.y,packet.z,packet.data);
                NeoForge.EVENT_BUS.post(event);if(event.isCanceled())return;
                ResourceLocation sound=ResourceLocation.parse(event.getSound().contains(":")?event.getSound():OpenComputers.ID+":"+event.getSound());
                event.getWorld().playLocalSound(event.getX(),event.getY(),event.getZ(),SoundEvent.createVariableRangeEvent(sound),SoundSource.BLOCKS,Settings.get().soundVolume,1,false);
            }
            if(tile instanceof ActivityListener listener)listener.onComponentActivity(packet.address,packet.network);
        }
        static void receive(Speaker packet){
            var client=net.minecraft.client.Minecraft.getInstance();
            if(client.level==null||client.player==null||!client.level.dimension().location().equals(packet.dimension))return;
            double distance=Math.sqrt(client.player.distanceToSqr(packet.x,packet.y,packet.z));
            double volume=Settings.get().soundVolume*client.options.getSoundSourceVolume(SoundSource.MASTER)*client.options.getSoundSourceVolume(SoundSource.BLOCKS)
                *Math.max(0,1-distance/Settings.get().beepRadius);
            if(volume<=0)return;
            AUDIO.execute(()->{
                if(packet.pattern.isEmpty())tone(packet.frequency,packet.duration,volume);
                else for(char symbol:packet.pattern.toCharArray()){
                    if(symbol=='.')tone(1000,100,volume);else if(symbol=='-')tone(1000,300,volume);else continue;
                    try{Thread.sleep(100);}catch(InterruptedException exception){Thread.currentThread().interrupt();return;}
                }
            });
        }
        private static void tone(int frequency,int duration,double volume){
            int rate=Math.max(8000,Math.min(96000,Settings.get().beepSampleRate));duration=Math.max(0,Math.min(10000,duration));
            byte[] samples=new byte[rate*duration/1000];
            for(int i=0;i<samples.length;i++)samples[i]=(byte)(Math.sin(2*Math.PI*Math.max(20,Math.min(2000,frequency))*i/rate)*Settings.get().beepAmplitude*Math.min(1,volume));
            javax.sound.sampled.AudioFormat format=new javax.sound.sampled.AudioFormat(rate,8,1,true,false);
            try(var line=javax.sound.sampled.AudioSystem.getSourceDataLine(format)){
                line.open(format);line.start();line.write(samples,0,samples.length);line.drain();
            }catch(Exception exception){OpenComputers.LOGGER.debug("Could not play computer speaker",exception);}
        }
    }
}
