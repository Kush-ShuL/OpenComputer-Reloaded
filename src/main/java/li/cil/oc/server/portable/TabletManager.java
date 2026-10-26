package li.cil.oc.server.portable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Server-tick expiry preserves running tablets while an integrated game is paused. */
public final class TabletManager {
    private static final Map<String,TabletHost> HOSTS=new HashMap<>();
    private TabletManager(){}
    public static void register(){NeoForge.EVENT_BUS.addListener(TabletManager::tick);NeoForge.EVENT_BUS.addListener(TabletManager::save);NeoForge.EVENT_BUS.addListener(TabletManager::unload);NeoForge.EVENT_BUS.addListener(TabletManager::stop);}
    public static String id(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getString("oc:tablet");}
    private static String assignId(ItemStack stack){String id=id(stack);if(id.isEmpty()){id=UUID.randomUUID().toString();var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();tag.putString("oc:tablet",id);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));}return id;}
    public static TabletHost get(ItemStack stack,Player holder){if(holder.level().isClientSide)throw new IllegalStateException("Tablet VMs live on the server");String id=assignId(stack);TabletHost host=HOSTS.get(id);
        if(host!=null&&host.stack()!=stack&&isInInventory(host.player(),host.stack())){var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();tag.remove("oc:tablet");stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));id=assignId(stack);host=null;}
        if(host!=null&&(host.world()!=holder.level()||host.player()!=holder)){ItemStack previous=host.stack();host.close(true);if(previous!=stack&&previous.has(DataComponents.CUSTOM_DATA))stack.set(DataComponents.CUSTOM_DATA,previous.get(DataComponents.CUSTOM_DATA));HOSTS.remove(id);host=null;}
        if(host==null){host=new TabletHost(id,stack,holder);HOSTS.put(id,host);}else host.touch(stack,holder);return host;
    }
    public static TabletHost peek(ItemStack stack){if(!li.cil.oc.util.SideTracker.isServer())return null;String id=id(stack);return id.isEmpty()?null:HOSTS.get(id);}
    public static TabletHost find(String id){return HOSTS.get(id);}
    public static boolean isInInventory(Player player,ItemStack stack){for(int slot=0;slot<player.getInventory().getContainerSize();slot++)if(player.getInventory().getItem(slot)==stack)return true;return false;}
    public static void invalidate(ItemStack stack){TabletHost host=HOSTS.remove(id(stack));if(host!=null)host.close(false);}
    private static void tick(ServerTickEvent.Post event){int now=event.getServer().getTickCount();HOSTS.values().removeIf(host->{if(host.player().getServer()!=event.getServer())return false;if(host.player().isRemoved()||(host.player() instanceof net.minecraft.server.level.ServerPlayer player&&player.hasDisconnected())||now-host.lastAccess>200){host.close(false);return true;}return false;});}
    private static void save(LevelEvent.Save event){if(event.getLevel() instanceof Level level&&!level.isClientSide)for(TabletHost host:HOSTS.values())if(host.world()==level)host.save(false);}
    private static void unload(LevelEvent.Unload event){if(event.getLevel().isClientSide())return;HOSTS.values().removeIf(host->{if(host.world()==event.getLevel()){host.close(true);return true;}return false;});}
    private static void stop(ServerStoppingEvent event){HOSTS.values().removeIf(host->{if(host.player().getServer()==event.getServer()){host.close(true);return true;}return false;});}
}
