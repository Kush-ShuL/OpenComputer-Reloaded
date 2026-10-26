package li.cil.oc.server.component;

import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.AABB;
import java.util.*;

public final class UpgradeLeash extends UpgradeBase {
    private final Entity entity;private final Set<UUID> leashed=new HashSet<>();private boolean restore;
    public UpgradeLeash(EnvironmentHost host){super(host,"leash","Leash","FlockControl (FC-3LS)",false);entity=(Entity)host;}
    @Callback public Object[] leash(Context context,Arguments args){if(leashed.size()>=8)return new Object[]{null,"too many leashed entities"};var side=side(args,0);AABB bounds=new AABB(position()).minmax(new AABB(position().relative(side,2)));for(Mob mob:host.world().getEntitiesOfClass(Mob.class,bounds,mob->mob.isAlive()&&!mob.isLeashed()&&mob.canBeLeashed())){var event=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract(fakePlayer(),net.minecraft.world.InteractionHand.MAIN_HAND,mob);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);if(event.isCanceled())continue;mob.setLeashedTo(entity,true);leashed.add(mob.getUUID());changed();context.pause(.1);return new Object[]{true};}return new Object[]{null,"no unleashed entity"};}
    @Callback public Object[] unleash(Context context,Arguments args){unleashAll();return new Object[0];}
    private void unleashAll(){for(Mob mob:host.world().getEntitiesOfClass(Mob.class,new AABB(position()).inflate(5)))if(leashed.contains(mob.getUUID())&&mob.getLeashHolder()==entity)mob.dropLeash(true,false);leashed.clear();changed();}
    @Override public boolean canUpdate(){return true;}
    @Override public void update(){if(!restore)return;restore=false;Set<UUID> found=new HashSet<>();for(Mob mob:host.world().getEntitiesOfClass(Mob.class,new AABB(position()).inflate(5)))if(leashed.contains(mob.getUUID())){mob.setLeashedTo(entity,true);found.add(mob.getUUID());}leashed.retainAll(found);}
    @Override public void onDisconnect(Node disconnected){if(disconnected==node())unleashAll();}
    @Override public void load(CompoundTag tag){super.load(tag);leashed.clear();ListTag list=tag.getList("leashedEntities",Tag.TAG_STRING);for(int i=0;i<list.size();i++)try{leashed.add(UUID.fromString(list.getString(i)));}catch(IllegalArgumentException ignored){}restore=true;}
    @Override public void save(CompoundTag tag){super.save(tag);ListTag list=new ListTag();for(UUID id:leashed)list.add(StringTag.valueOf(id.toString()));tag.put("leashedEntities",list);}
}
