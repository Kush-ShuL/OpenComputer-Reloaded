package li.cil.oc.server;

import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Node;
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
    public static void sendToPlayersTrackingBlock(BlockEntity entity,double requested,CustomPacketPayload payload){if(!(entity.getLevel() instanceof ServerLevel level))return;double range=packetRange(level,requested),squared=range*range;var pos=entity.getBlockPos();for(ServerPlayer player:level.getChunkSource().chunkMap.getPlayers(new net.minecraft.world.level.ChunkPos(pos),false))if(player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=squared)PacketDistributor.sendToPlayer(player,payload);}
    public static void sendToPlayersNear(ServerLevel level,double x,double y,double z,double requested,CustomPacketPayload payload){PacketDistributor.sendToPlayersNear(level,null,x,y,z,packetRange(level,requested),payload);}
    public interface ActivityListener { void onComponentActivity(String address, boolean network); }
    public static void sendNetworkActivity(Node node,EnvironmentHost host){ activity(node,host,true); }
    private static void activity(Node node,EnvironmentHost host,boolean network){
        if(host.world() instanceof ServerLevel level){double range=network?Settings.get().maxNetworkClientEffectPacketDistance:Settings.get().maxNetworkClientSoundPacketDistance;var packet=new Activity(level.dimension().location(),net.minecraft.core.BlockPos.containing(host.xPosition(),host.yPosition(),host.zPosition()),node.address()==null?"":node.address(),network);if(host instanceof BlockEntity block)sendToPlayersTrackingBlock(block,range,packet);else sendToPlayersNear(level,host.xPosition(),host.yPosition(),host.zPosition(),range,packet);}
    }
    public record Activity(ResourceLocation dimension,net.minecraft.core.BlockPos pos,String address,boolean network) implements CustomPacketPayload {
        public static final Type<Activity> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(OpenComputers.ID,"component_activity"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Activity> CODEC=StreamCodec.of((b,p)->{b.writeResourceLocation(p.dimension);b.writeBlockPos(p.pos);b.writeUtf(p.address,64);b.writeBoolean(p.network);},b->new Activity(b.readResourceLocation(),b.readBlockPos(),b.readUtf(64),b.readBoolean()));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    private static final Map<Node,Long> LAST_ACTIVITY=Collections.synchronizedMap(new WeakHashMap<>());
    public static void sendFileSystemActivity(Node node,EnvironmentHost host,String sound){
        activity(node,host,false);
        if(!(host.world() instanceof ServerLevel level)||Settings.get().soundVolume<=0)return;
        long now=System.currentTimeMillis();Long previous=LAST_ACTIVITY.get(node);
        if(previous!=null&&now-previous<Math.max(0,Settings.get().diskActivitySoundDelay))return;
        LAST_ACTIVITY.put(node,now);
        ResourceLocation id=ResourceLocation.tryParse(sound.contains(":")?sound:OpenComputers.ID+":"+sound);
        if(id!=null){double range=packetRange(level,Settings.get().maxNetworkClientSoundPacketDistance),squared=range*range;var packet=new net.minecraft.network.protocol.game.ClientboundSoundPacket(net.minecraft.core.Holder.direct(SoundEvent.createVariableRangeEvent(id)),SoundSource.BLOCKS,host.xPosition(),host.yPosition(),host.zPosition(),Settings.get().soundVolume,1,java.util.concurrent.ThreadLocalRandom.current().nextLong());Iterable<ServerPlayer> players=host instanceof BlockEntity block?level.getChunkSource().chunkMap.getPlayers(new net.minecraft.world.level.ChunkPos(block.getBlockPos()),false):level.players();for(ServerPlayer player:players)if(player.distanceToSqr(host.xPosition(),host.yPosition(),host.zPosition())<=squared)player.connection.send(packet);}
    }
    public static void sendSound(Level world,double x,double y,double z,int frequency,int duration){
        send(world,x,y,z,new Speaker(world.dimension().location(),x,y,z,Math.max(20,Math.min(2000,frequency)),Math.max(0,Math.min(10000,duration)),""));
    }
    public static void sendSound(Level world,double x,double y,double z,String pattern){
        if(pattern==null||pattern.length()>64)throw new IllegalArgumentException("invalid beep pattern");
        send(world,x,y,z,new Speaker(world.dimension().location(),x,y,z,1000,0,pattern));
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
            if(level!=null&&level.dimension().location().equals(packet.dimension)&&level.getBlockEntity(packet.pos) instanceof ActivityListener listener)listener.onComponentActivity(packet.address,packet.network);
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
