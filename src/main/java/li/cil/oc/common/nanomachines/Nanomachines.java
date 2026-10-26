package li.cil.oc.common.nanomachines;

import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.api.detail.NanomachinesAPI;
import li.cil.oc.api.nanomachines.*;
import li.cil.oc.common.nanomachines.provider.*;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Provider registry, per-player lifetime and original .ocnm player-state persistence. */
public final class Nanomachines implements NanomachinesAPI {
    public static final Nanomachines INSTANCE=new Nanomachines();
    private static final Set<Player> PENDING_LOGOUT=Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<BehaviorProvider> providers=new LinkedHashSet<>();
    private final Map<Player,ControllerImpl> server=new WeakHashMap<>(),client=new WeakHashMap<>();
    private Nanomachines(){}
    public void addProvider(BehaviorProvider provider){providers.add(Objects.requireNonNull(provider));}
    public Iterable<BehaviorProvider> getProviders(){return List.copyOf(providers);}
    public static void registerBuiltins(){for(BehaviorProvider provider:List.of(PotionProvider.INSTANCE,ParticleProvider.INSTANCE,HungryProvider.INSTANCE,MagnetProvider.INSTANCE,DisintegrationProvider.INSTANCE))INSTANCE.addProvider(provider);}
    private Map<Player,ControllerImpl> controllers(Player player){return player.level().isClientSide?client:server;}
    static CompoundTag persisted(Player player){CompoundTag data=player.getPersistentData();if(!data.contains(Player.PERSISTED_NBT_TAG))data.put(Player.PERSISTED_NBT_TAG,new CompoundTag());return data.getCompound(Player.PERSISTED_NBT_TAG);}
    public boolean hasController(Player player){return player!=null&&persisted(player).getBoolean(Settings.namespace+"hasNanomachines");}
    public Controller getController(Player player){if(!hasController(player))return null;return controllers(player).computeIfAbsent(player,ControllerImpl::new);}
    public Controller installController(Player player){persisted(player).putBoolean(Settings.namespace+"hasNanomachines",true);return getController(player);}
    public void uninstallController(Player player){ControllerImpl controller=controllers(player).remove(player);persisted(player).remove(Settings.namespace+"hasNanomachines");if(controller!=null)controller.dispose();if(!player.level().isClientSide)NanomachineNetwork.sendConfiguration(player);}
    static void clientConfiguration(Player player,boolean installed,CompoundTag tag){if(!installed){INSTANCE.uninstallController(player);return;}ControllerImpl controller=(ControllerImpl)INSTANCE.installController(player);controller.load(tag);}
    public static void spawnParticle(Player player,ParticleOptions particle,double chance){var random=player.level().random;if(chance<1&&random.nextDouble()>=chance)return;var bounds=player.getBoundingBox();double x=bounds.minX+(bounds.maxX-bounds.minX)*random.nextDouble()*1.5,y=bounds.minY+(bounds.maxY-bounds.minY)*random.nextDouble()*.5,z=bounds.minZ+(bounds.maxZ-bounds.minZ)*random.nextDouble()*1.5;player.level().addParticle(particle,x,y,z,0,0,0);}
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event){if(INSTANCE.getController(event.getEntity()) instanceof ControllerImpl controller)controller.update();}
    @SubscribeEvent public static void save(PlayerEvent.SaveToFile event){if(INSTANCE.getController(event.getEntity()) instanceof ControllerImpl controller)try{CompoundTag nbt=new CompoundTag();controller.save(nbt);NbtIo.writeCompressed(nbt,event.getPlayerFile("ocnm").toPath());}catch(IOException error){OpenComputers.LOGGER.warn("Error saving nanomachines for {}",event.getEntity().getUUID(),error);}finally{if(PENDING_LOGOUT.remove(event.getEntity())){INSTANCE.uninstallController(event.getEntity());}}}
    @SubscribeEvent public static void load(PlayerEvent.LoadFromFile event){Path file=event.getPlayerFile("ocnm").toPath();if(Files.isRegularFile(file)&&INSTANCE.getController(event.getEntity()) instanceof ControllerImpl controller)try{controller.load(NbtIo.readCompressed(file,NbtAccounter.create(16L*1024*1024)));}catch(IOException error){OpenComputers.LOGGER.warn("Error loading nanomachines for {}",event.getEntity().getUUID(),error);}}
    @SubscribeEvent public static void clonePlayer(PlayerEvent.Clone event){Player old=event.getOriginal(),replacement=event.getEntity();if(!INSTANCE.hasController(old))return;CompoundTag state=new CompoundTag();if(INSTANCE.getController(old) instanceof ControllerImpl controller){controller.save(state);controller.dispose();INSTANCE.controllers(old).remove(old);}persisted(replacement).putBoolean(Settings.namespace+"hasNanomachines",true);ControllerImpl next=(ControllerImpl)INSTANCE.getController(replacement);next.load(state);next.reset();if(event.isWasDeath())next.changeBuffer(-next.getLocalBuffer());}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event){if(INSTANCE.getController(event.getEntity()) instanceof ControllerImpl controller)controller.changeBuffer(-controller.getLocalBuffer());}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){PENDING_LOGOUT.add(event.getEntity());}
    @SubscribeEvent public static void unload(LevelEvent.Unload event){for(Map<Player,ControllerImpl> cache:List.of(INSTANCE.server,INSTANCE.client)){Iterator<Map.Entry<Player,ControllerImpl>> iterator=cache.entrySet().iterator();while(iterator.hasNext()){var entry=iterator.next();if(entry.getKey().level()==event.getLevel()){entry.getValue().dispose();iterator.remove();}}}}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){for(ControllerImpl controller:List.copyOf(INSTANCE.server.values()))controller.dispose();INSTANCE.server.clear();PENDING_LOGOUT.clear();}
}
