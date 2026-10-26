package li.cil.oc.server.component;

import li.cil.oc.Settings;
import li.cil.oc.api.internal.*;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.*;
import li.cil.oc.util.InventoryUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

public final class UpgradeGenerator extends UpgradeBase {
    private final Agent agent;private ItemStack queue=ItemStack.EMPTY;private int remainingTicks;
    public UpgradeGenerator(EnvironmentHost host){super(host,"generator","Generator","Portagen 2.0 (Rev. 3)",true);agent=(Agent)host;}
    private int burnTime(ItemStack stack){return stack.getBurnTime(RecipeType.SMELTING);}
    private void sync(){changed();if(agent instanceof li.cil.oc.api.internal.Robot robot)robot.synchronizeSlot(agent.componentSlot(node().address()));}
    @Callback public Object[] insert(Context context,Arguments args){
        if(agent.mainInventory().getContainerSize()==0)return new Object[]{null,"selected slot is empty"};ItemStack fuel=agent.mainInventory().getItem(agent.selectedSlot());if(fuel.isEmpty())return new Object[]{null,"selected slot is empty"};if(burnTime(fuel)<=0)return new Object[]{null,"selected slot does not contain fuel"};if(!queue.isEmpty()&&!ItemStack.isSameItemSameComponents(queue,fuel))return new Object[]{null,"different fuel type already queued"};int amount=Math.max(0,Math.min(args.optInteger(0,64),Math.min(fuel.getCount(),fuel.getMaxStackSize()-queue.getCount())));if(amount==0)return new Object[]{null,"queue is full"};
        ItemStack old=fuel.copy(),remainder=fuel.getCraftingRemainingItem();fuel.shrink(amount);agent.mainInventory().setItem(agent.selectedSlot(),fuel);
        if(!remainder.isEmpty()){remainder.setCount(amount);ItemStack check=remainder.copy();boolean accepted=InventoryUtils.insertIntoInventory(check,InventoryUtils.asItemHandler(agent.mainInventory()),amount,true);if(!accepted){agent.mainInventory().setItem(agent.selectedSlot(),old);return new Object[]{false,"no space in inventory for fuel containers"};}InventoryUtils.insertIntoInventory(remainder,InventoryUtils.asItemHandler(agent.mainInventory()),amount,false);if(!remainder.isEmpty())InventoryUtils.spawnStackInWorld(host.world(),position(),remainder,null);}
        if(queue.isEmpty())queue=old.copyWithCount(amount);else queue.grow(amount);sync();return new Object[]{true,amount};
    }
    @Callback public Object[] count(Context context,Arguments args){return queue.isEmpty()?new Object[]{0}:new Object[]{queue.getCount(),queue.getHoverName().getString()};}
    @Callback public Object[] remove(Context context,Arguments args){int count=Math.max(0,args.optInteger(0,Integer.MAX_VALUE));if(count==0)return new Object[]{true};if(queue.isEmpty())return new Object[]{false,"queue is empty"};ItemStack required=queue.getCraftingRemainingItem();ItemStack selected=agent.mainInventory().getContainerSize()>0?agent.mainInventory().getItem(agent.selectedSlot()):ItemStack.EMPTY;if(!required.isEmpty()&&!ItemStack.isSameItemSameComponents(required,selected))return new Object[]{false,"removing this fuel requires the appropriate container in the selected slot"};int amount=Math.min(count,queue.getCount());if(!required.isEmpty())amount=Math.min(amount,selected.getCount());ItemStack previous=selected.copy();if(!required.isEmpty())selected.shrink(amount);ItemStack removed=queue.copyWithCount(amount);InventoryUtils.insertIntoInventory(removed,InventoryUtils.asItemHandler(agent.mainInventory()),amount,false);int inserted=amount-removed.getCount();if(inserted==0){agent.mainInventory().setItem(agent.selectedSlot(),previous);return new Object[]{false,"no inventory space available for fuel"};}if(!required.isEmpty()&&inserted<amount){ItemStack containers=required.copyWithCount(amount-inserted);InventoryUtils.insertIntoInventory(containers,InventoryUtils.asItemHandler(agent.mainInventory()),containers.getCount(),false);if(!containers.isEmpty())InventoryUtils.spawnStackInWorld(host.world(),position(),containers,null);}queue.shrink(inserted);sync();return new Object[]{true,inserted};}
    @Override public boolean canUpdate(){return true;}
    @Override public void update(){if(remainingTicks<=0&&!queue.isEmpty()){remainingTicks=burnTime(queue);if(remainingTicks>0){queue.shrink(1);sync();}}if(remainingTicks>0){remainingTicks--;connector().changeBuffer(Settings.get().generatorEfficiency);if(remainingTicks==0&&queue.isEmpty())sync();}}
    @Override public void onDisconnect(Node node){if(node==node()){if(!queue.isEmpty())InventoryUtils.spawnStackInWorld(host.world(),position(),queue.copy(),null);queue=ItemStack.EMPTY;remainingTicks=0;}}
    @Override public void load(CompoundTag tag,HolderLookup.Provider registries){super.load(tag);queue=ItemStack.parseOptional(registries,tag.getCompound("inventory"));remainingTicks=Math.max(0,tag.getInt("remainingTicks"));}
    @Override public void save(CompoundTag tag,HolderLookup.Provider registries){super.save(tag);tag.put("inventory",queue.saveOptional(registries));tag.putInt("remainingTicks",remainingTicks);}
}
