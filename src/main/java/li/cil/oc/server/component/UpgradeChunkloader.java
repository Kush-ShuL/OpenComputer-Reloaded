package li.cil.oc.server.component;

import li.cil.oc.Settings;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.*;
import net.neoforged.neoforge.event.level.LevelEvent;
import java.util.*;

/** Real NeoForge persistent ticking tickets, reclaimed by the upgrade identity after loading. */
public final class UpgradeChunkloader extends UpgradeBase {
    private static final Map<ServerLevel,Map<UUID,Set<ChunkPos>>> RESTORED=new WeakHashMap<>();
    public static final TicketController TICKETS=new TicketController(ResourceLocation.fromNamespaceAndPath("opencomputers","chunkloader"),(level,helper)->{Map<UUID,Set<ChunkPos>> owners=new HashMap<>();helper.getEntityTickets().forEach((id,tickets)->{Set<ChunkPos> chunks=new HashSet<>();tickets.ticking().forEach((long packed)->chunks.add(new ChunkPos(packed)));owners.put(id,chunks);});RESTORED.put(level,owners);});
    private UUID identity=UUID.randomUUID();private final Set<ChunkPos> chunks=new HashSet<>();private boolean active;
    public UpgradeChunkloader(EnvironmentHost host){super(host,"chunkloader","World stabilizer","Realizer9001-CL",true);}
    public static void registerTickets(RegisterTicketControllersEvent event){event.register(TICKETS);}
    public static void saveWorld(LevelEvent.Save event){if(!(event.getLevel() instanceof ServerLevel level))return;Map<UUID,Set<ChunkPos>> owners=RESTORED.remove(level);if(owners!=null)owners.forEach((id,chunks)->chunks.forEach(chunk->TICKETS.forceChunk(level,id,chunk.x,chunk.z,false,true)));}
    private boolean allowed(){String dimension=host.world().dimension().location().toString();var config=Settings.get().config;var white=config.getAnyRefList("chunkloader.dimWhitelist");var black=config.getAnyRefList("chunkloader.dimBlacklist");return (white.isEmpty()||white.stream().anyMatch(value->dimension.equals(dimension(value))))&&black.stream().noneMatch(value->dimension.equals(dimension(value)));}
    private static String dimension(Object value){return switch(String.valueOf(value)){case "0"->"minecraft:overworld";case "-1"->"minecraft:the_nether";case "1"->"minecraft:the_end";default->String.valueOf(value);};}
    private void release(){if(host.world() instanceof ServerLevel level)for(ChunkPos chunk:chunks)TICKETS.forceChunk(level,identity,chunk.x,chunk.z,false,true);chunks.clear();active=false;changed();}
    public void updateLoadedChunks(){if(!active||!(host.world() instanceof ServerLevel level))return;ChunkPos center=new ChunkPos(position());Set<ChunkPos> wanted=new HashSet<>();for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)wanted.add(new ChunkPos(center.x+x,center.z+z));for(ChunkPos chunk:wanted)if(!chunks.contains(chunk))TICKETS.forceChunk(level,identity,chunk.x,chunk.z,true,true);for(ChunkPos chunk:new HashSet<>(chunks))if(!wanted.contains(chunk))TICKETS.forceChunk(level,identity,chunk.x,chunk.z,false,true);chunks.clear();chunks.addAll(wanted);}
    private boolean setActive(boolean enabled,boolean fail){if(enabled&&!active){if(!allowed()){if(fail)throw new IllegalArgumentException("this dimension is blacklisted");return false;}active=true;updateLoadedChunks();changed();return true;}if(!enabled&&active){release();return true;}return false;}
    @Callback public Object[] isActive(Context context,Arguments args){return new Object[]{active};}
    @Callback public Object[] setActive(Context context,Arguments args){return new Object[]{setActive(args.checkBoolean(0),true)};}
    @Override public boolean canUpdate(){return true;}
    @Override public void update(){if(active&&host.world().getGameTime()%Settings.get().tickFrequency==0){if(!connector().tryChangeBuffer(-Settings.get().chunkloaderCost*Settings.get().tickFrequency))release();else updateLoadedChunks();}}
    @Override public void onConnect(Node node){if(node!=node())return;if(host.world() instanceof ServerLevel level){Map<UUID,Set<ChunkPos>> restored=RESTORED.get(level);Set<ChunkPos> previous=restored==null?null:restored.remove(identity);if(previous!=null){chunks.addAll(previous);active=true;if(!allowed())release();else updateLoadedChunks();return;}}if(host instanceof MachineHost machine&&machine.machine().isRunning())setActive(true,false);}
    @Override public void onDisconnect(Node node){if(node==node())release();}
    @Override public void onMessage(Message message){if(message.name().equals("computer.stopped"))setActive(false,false);else if(message.name().equals("computer.started"))setActive(true,false);}
    @Override public void load(CompoundTag tag){super.load(tag);if(tag.hasUUID("ticketIdentity"))identity=tag.getUUID("ticketIdentity");}
    @Override public void save(CompoundTag tag){super.save(tag);tag.putUUID("ticketIdentity",identity);}
}
