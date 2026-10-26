package li.cil.oc.server.component;

import li.cil.oc.Settings;
import li.cil.oc.api.internal.Agent;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.*;
import li.cil.oc.api.prefab.AbstractValue;
import li.cil.oc.util.InventoryUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.phys.*;
import java.util.*;

public final class UpgradeTrading extends UpgradeBase {
    public UpgradeTrading(EnvironmentHost host){super(host,"trading","Trading upgrade","Capitalism H.O. 1200T",false);}
    @Callback public Object[] getTrades(Context context,Arguments args){double range=Settings.get().tradingRange;List<Entity> merchants=host.world().getEntities((Entity)null,new AABB(position()).inflate(range),entity->entity instanceof Merchant&&entity.isAlive()&&entity.distanceToSqr(host.xPosition(),host.yPosition(),host.zPosition())<=range*range);merchants.sort(Comparator.comparing(Entity::getUUID));List<Trade> result=new ArrayList<>();for(int merchant=0;merchant<merchants.size();merchant++){Entity entity=merchants.get(merchant);Merchant trader=(Merchant)entity;for(int recipe=0;recipe<trader.getOffers().size();recipe++)result.add(new Trade(node().address(),entity.getUUID(),recipe,merchant+1));}return new Object[]{result.toArray()};}
    /** Persistent values resolve their originating upgrade through the caller's own network. */
    public static final class Trade extends AbstractValue {
        private String upgradeAddress="";private UUID merchantId;private int recipeIndex=-1,merchantIndex=-1;
        public Trade(){}
        private Trade(String address,UUID merchant,int recipe,int index){upgradeAddress=address;merchantId=merchant;recipeIndex=recipe;merchantIndex=index;}
        private UpgradeTrading upgrade(Context context){Node node=context.node().network()==null?null:context.node().network().node(upgradeAddress);return node!=null&&node.canBeReachedFrom(context.node())&&node.host() instanceof UpgradeTrading upgrade?upgrade:null;}
        private Merchant merchant(Context context){UpgradeTrading upgrade=upgrade(context);if(upgrade==null||merchantId==null||!(upgrade.host.world() instanceof ServerLevel level))return null;Entity entity=level.getEntity(merchantId);return entity instanceof Merchant merchant&&entity.isAlive()?merchant:null;}
        private MerchantOffer recipe(Context context){Merchant merchant=merchant(context);return merchant!=null&&recipeIndex>=0&&recipeIndex<merchant.getOffers().size()?merchant.getOffers().get(recipeIndex):null;}
        @Callback public Object[] getMerchantId(Context context,Arguments args){return new Object[]{merchantIndex};}
        @Callback public Object[] getInput(Context context,Arguments args){MerchantOffer offer=recipe(context);return offer==null?new Object[]{null,null}:new Object[]{offer.getCostA().copy(),offer.getCostB().isEmpty()?null:offer.getCostB().copy()};}
        @Callback public Object[] getOutput(Context context,Arguments args){MerchantOffer offer=recipe(context);return new Object[]{offer==null?null:offer.getResult().copy()};}
        @Callback public Object[] isEnabled(Context context,Arguments args){MerchantOffer offer=recipe(context);return new Object[]{offer!=null&&!offer.isOutOfStock()};}
        private boolean take(SimpleContainer inventory,ItemCost cost,int amount){int remaining=amount;for(int i=0;i<inventory.getContainerSize()&&remaining>0;i++){ItemStack stack=inventory.getItem(i);if(cost.test(stack)){int count=Math.min(remaining,stack.getCount());stack.shrink(count);remaining-=count;}}return remaining==0;}
        @Callback public Object[] trade(Context context,Arguments args){UpgradeTrading upgrade=upgrade(context);if(upgrade==null||!(upgrade.host instanceof Agent agent)||agent.mainInventory().getContainerSize()==0)return new Object[]{false,"trading requires an inventory upgrade to be installed"};Merchant merchant=merchant(context);MerchantOffer offer=recipe(context);if(!(merchant instanceof Entity entity)||offer==null)return new Object[]{false,"trade has become invalid"};if(entity.distanceToSqr(upgrade.host.xPosition(),upgrade.host.yPosition(),upgrade.host.zPosition())>Settings.get().tradingRange*Settings.get().tradingRange)return new Object[]{false,"out of range"};if(offer.isOutOfStock())return new Object[]{false,"trade is disabled"};var player=agent.player();if(merchant.getTradingPlayer()!=null&&merchant.getTradingPlayer()!=player)return new Object[]{false,"trader is busy"};var event=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract(player,net.minecraft.world.InteractionHand.MAIN_HAND,entity);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);if(event.isCanceled())return new Object[]{false,"not allowed"};
            SimpleContainer inventory=new SimpleContainer(agent.mainInventory().getContainerSize());for(int i=0;i<inventory.getContainerSize();i++)inventory.setItem(i,agent.mainInventory().getItem(i).copy());ItemStack room=offer.getResult().copy();InventoryUtils.insertIntoInventory(room,InventoryUtils.asItemHandler(inventory),room.getCount(),true);if(!room.isEmpty())return new Object[]{false,"not enough inventory space to trade"};if(!take(inventory,offer.getItemCostA(),offer.getCostA().getCount())||offer.getItemCostB().isPresent()&&!take(inventory,offer.getItemCostB().get(),offer.getCostB().getCount()))return new Object[]{false,"not enough items to trade"};ItemStack output=offer.assemble();InventoryUtils.insertIntoInventory(output,InventoryUtils.asItemHandler(inventory),output.getCount(),false);if(!output.isEmpty())return new Object[]{false,"not enough inventory space to trade"};for(int i=0;i<inventory.getContainerSize();i++)agent.mainInventory().setItem(i,inventory.getItem(i));var previous=merchant.getTradingPlayer();merchant.setTradingPlayer(player);try{merchant.notifyTrade(offer);merchant.overrideXp(merchant.getVillagerXp()+offer.getXp());}finally{merchant.setTradingPlayer(previous);}upgrade.changed();return new Object[]{true};}
        @Override public void load(CompoundTag tag){upgradeAddress=tag.getString("upgradeAddress");if(tag.hasUUID("merchant"))merchantId=tag.getUUID("merchant");recipeIndex=tag.getInt("recipeID");merchantIndex=tag.getInt("merchantID");}
        @Override public void save(CompoundTag tag){tag.putString("upgradeAddress",upgradeAddress);if(merchantId!=null)tag.putUUID("merchant",merchantId);tag.putInt("recipeID",recipeIndex);tag.putInt("merchantID",merchantIndex);}
    }
}
