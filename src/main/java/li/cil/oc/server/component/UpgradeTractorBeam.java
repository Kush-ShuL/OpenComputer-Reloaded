package li.cil.oc.server.component;

import li.cil.oc.Settings;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.internal.*;
import li.cil.oc.util.InventoryUtils;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import java.util.*;

public final class UpgradeTractorBeam extends UpgradeBase {
    public UpgradeTractorBeam(EnvironmentHost host){super(host,"tractor_beam","Tractor beam","Item Magnet",false);}
    @Callback public Object[] suck(Context context,Arguments args){var entities=host.world().getEntitiesOfClass(ItemEntity.class,new AABB(position()).inflate(3),entity->entity.isAlive()&&!entity.hasPickUpDelay());Collections.shuffle(entities);for(ItemEntity entity:entities){int before=entity.getItem().getCount();if(host instanceof li.cil.oc.api.internal.Drone&&host instanceof Agent agent){ItemStack stack=entity.getItem();List<Integer> slots=new ArrayList<>();if(agent.selectedSlot()>=0)slots.add(agent.selectedSlot());for(int i=0;i<agent.mainInventory().getContainerSize();i++)if(i!=agent.selectedSlot())slots.add(i);InventoryUtils.insertIntoInventory(stack,InventoryUtils.asItemHandler(agent.mainInventory()),stack.getCount(),false,slots);if(stack.isEmpty())entity.discard();}else entity.playerTouch(fakePlayer());if(!entity.isAlive()||entity.getItem().getCount()<before){host.world().levelEvent(2003,entity.blockPosition(),0);context.pause(Settings.get().config.getDouble("robot.delays.suck"));changed();return new Object[]{true};}}return new Object[]{false};}
}
