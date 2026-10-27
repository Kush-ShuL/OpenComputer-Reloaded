package li.cil.oc.server.component;

import li.cil.oc.Settings;
import li.cil.oc.common.block.entity.RobotBlockEntity;
import li.cil.oc.server.agent.RobotController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
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
    @Callback public Object[] getTrades(Context context,Arguments args){double range=Settings.get().tradingRange;List<Entity> merchants=host.world().getEntities((Entity)null,new AABB(position()).inflate(range),entity->entity instanceof Merchant&&entity.isAlive()&&entity.distanceToSqr(host.xPosition(),host.yPosition(),host.zPosition())<=range*range);merchants.sort(Comparator.comparing(Entity::getUUID));List<Trade> result=new ArrayList<>();for(int merchant=0;merchant<merchants.size();merchant++){Entity entity=merchants.get(merchant);Merchant trader=(Merchant)entity;for(int recipe=0;recipe<trader.getOffers().size();recipe++)result.add(new Trade(host,node().address(),entity,recipe,merchant+1));}return new Object[]{result.toArray()};}
    /** Trade values retain their originating host, even after the upgrade is removed. */
    public static final class Trade extends AbstractValue {
        private String upgradeAddress="",hostAddress="",hostDimension="",merchantDimension="";
        private UUID merchantId,hostEntityId;
        private BlockPos hostPosition;
        private EnvironmentHost origin;
        private Entity merchantEntity;
        private int recipeIndex=-1,merchantIndex=-1;

        public Trade(){}
        private Trade(EnvironmentHost host,String address,Entity merchant,int recipe,int index){
            upgradeAddress=address;merchantId=merchant.getUUID();merchantEntity=merchant;
            merchantDimension=merchant.level().dimension().location().toString();
            recipeIndex=recipe;merchantIndex=index;rememberHost(host);
        }

        private static EnvironmentHost actualHost(EnvironmentHost host){
            // The movable block proxy is replaced while its internal controller survives.
            return host instanceof RobotBlockEntity robot?robot.controller():host;
        }
        private static boolean validHost(EnvironmentHost host){
            if(host==null||!(host.world() instanceof ServerLevel level))return false;
            if(host instanceof Entity entity)return !entity.isRemoved()&&entity.isAlive()&&level.getEntity(entity.getUUID())==entity;
            BlockEntity block=host instanceof RobotController robot?robot.proxy:host instanceof BlockEntity entity?entity:null;
            return block!=null&&!block.isRemoved()&&level.hasChunkAt(block.getBlockPos())&&level.getBlockEntity(block.getBlockPos())==block;
        }
        private void rememberHost(EnvironmentHost host){
            origin=actualHost(host);
            if(origin==null||origin.world()==null)return;
            hostDimension=origin.world().dimension().location().toString();
            hostPosition=BlockPos.containing(origin.xPosition(),origin.yPosition(),origin.zPosition());
            hostEntityId=origin instanceof Entity entity?entity.getUUID():null;
            if(origin instanceof MachineHost machine&&machine.machine()!=null&&machine.machine().node().address()!=null)hostAddress=machine.machine().node().address();
        }
        private boolean identifies(EnvironmentHost host){
            if(!validHost(host))return false;
            if(hostEntityId!=null&&(!(host instanceof Entity entity)||!hostEntityId.equals(entity.getUUID())))return false;
            if(!hostAddress.isEmpty())return host instanceof MachineHost machine&&machine.machine()!=null&&hostAddress.equals(machine.machine().node().address());
            return hostDimension.equals(host.world().dimension().location().toString())&&hostPosition!=null&&hostPosition.equals(BlockPos.containing(host.xPosition(),host.yPosition(),host.zPosition()));
        }
        private static ServerLevel level(Context context,String dimension){
            ResourceLocation id=ResourceLocation.tryParse(dimension);
            if(id==null)return null;
            MinecraftServer server=context instanceof Machine machine&&machine.host().world() instanceof ServerLevel world?world.getServer():ServerLifecycleHooks.getCurrentServer();
            return server==null?null:server.getLevel(ResourceKey.create(Registries.DIMENSION,id));
        }
        private EnvironmentHost host(Context context){
            if(validHost(origin))return origin;
            origin=null;
            // A resumed robot may already have moved away from its saved position.
            // Its machine identity still identifies the original host independently
            // of any component that happened to create this value.
            if(context instanceof Machine machine){
                EnvironmentHost candidate=actualHost(machine.host());
                if(identifies(candidate)){rememberHost(candidate);return origin;}
            }
            ServerLevel world=level(context,hostDimension);
            if(world!=null){
                EnvironmentHost candidate=null;
                if(hostEntityId!=null){Entity entity=world.getEntity(hostEntityId);if(entity instanceof EnvironmentHost environment)candidate=actualHost(environment);}
                else if(hostPosition!=null&&world.hasChunkAt(hostPosition)&&world.getBlockEntity(hostPosition) instanceof EnvironmentHost environment)candidate=actualHost(environment);
                if(identifies(candidate)){rememberHost(candidate);return origin;}
            }
            // Old preview saves only had the upgrade's address. Upgrade lookup is
            // a migration fallback, never the lifetime of a newly created Trade.
            if(hostDimension.isEmpty()&&hostAddress.isEmpty()&&context.node()!=null&&context.node().network()!=null){
                Node node=context.node().network().node(upgradeAddress);
                if(node!=null&&node.canBeReachedFrom(context.node())&&node.host() instanceof UpgradeTrading upgrade){
                    EnvironmentHost candidate=actualHost(upgrade.host);
                    if(validHost(candidate)){rememberHost(candidate);return origin;}
                }
            }
            // Do not force an unloaded chunk or discard its identity. A later
            // callback can resolve the same host once it is loaded again.
            return null;
        }
        private Merchant merchant(Context context){
            EnvironmentHost host=host(context);
            if(host==null||merchantId==null)return null;
            if(merchantEntity!=null&&!merchantEntity.isRemoved()&&merchantEntity.isAlive()&&merchantEntity instanceof Merchant merchant&&merchantEntity.level() instanceof ServerLevel world&&world.getEntity(merchantId)==merchantEntity)return merchant;
            merchantEntity=null;
            ServerLevel world=level(context,merchantDimension.isEmpty()?host.world().dimension().location().toString():merchantDimension);
            Entity entity=world==null?null:world.getEntity(merchantId);
            if(entity instanceof Merchant merchant&&entity.isAlive()&&!entity.isRemoved()){merchantEntity=entity;merchantDimension=world.dimension().location().toString();return merchant;}
            return null;
        }
        private MerchantOffer recipe(Context context){Merchant merchant=merchant(context);return merchant!=null&&recipeIndex>=0&&recipeIndex<merchant.getOffers().size()?merchant.getOffers().get(recipeIndex):null;}
        @Callback public Object[] getMerchantId(Context context,Arguments args){return new Object[]{merchantIndex};}
        @Callback public Object[] getInput(Context context,Arguments args){MerchantOffer offer=recipe(context);return offer==null?new Object[]{null,null}:new Object[]{offer.getCostA().copy(),offer.getCostB().isEmpty()?null:offer.getCostB().copy()};}
        @Callback public Object[] getOutput(Context context,Arguments args){MerchantOffer offer=recipe(context);return new Object[]{offer==null?null:offer.getResult().copy()};}
        @Callback public Object[] isEnabled(Context context,Arguments args){MerchantOffer offer=recipe(context);return new Object[]{offer!=null&&!offer.isOutOfStock()};}

        private static SimpleContainer copyInventory(Agent agent){
            SimpleContainer inventory=new SimpleContainer(agent.mainInventory().getContainerSize());
            for(int slot=0;slot<inventory.getContainerSize();slot++)inventory.setItem(slot,agent.mainInventory().getItem(slot).copy());
            return inventory;
        }
        private static boolean take(SimpleContainer inventory,ItemStack cost,boolean exact){
            int remaining=cost.getCount();
            for(int slot=0;slot<inventory.getContainerSize()&&remaining>0;slot++){
                ItemStack stack=inventory.getItem(slot);
                if(InventoryUtils.haveSameItemType(stack,cost,exact)){
                    int count=Math.min(remaining,stack.getCount());stack.shrink(count);remaining-=count;
                }
            }
            return remaining==0;
        }
        private static SimpleContainer payment(Agent agent,MerchantOffer offer,boolean exact){
            // Both inputs spend from one copy. A failed exact attempt must not
            // leave deductions behind when the original loose matching is tried.
            SimpleContainer inventory=copyInventory(agent);
            return take(inventory,offer.getCostA(),exact)&&take(inventory,offer.getCostB(),exact)?inventory:null;
        }
        @Callback public Object[] trade(Context context,Arguments args){
            EnvironmentHost host=host(context);
            if(!(host instanceof Agent agent)||agent.mainInventory().getContainerSize()==0)return new Object[]{false,"trading requires an inventory upgrade to be installed"};
            Merchant merchant=merchant(context);MerchantOffer offer=recipe(context);
            if(!(merchant instanceof Entity entity)||offer==null)return new Object[]{false,"trade has become invalid"};
            if(entity.level()!=host.world()||entity.distanceToSqr(host.xPosition(),host.yPosition(),host.zPosition())>Settings.get().tradingRange*Settings.get().tradingRange)return new Object[]{false,"out of range"};
            if(offer.isOutOfStock())return new Object[]{false,"trade is disabled"};
            var player=agent.player();
            if(merchant.getTradingPlayer()!=null&&merchant.getTradingPlayer()!=player)return new Object[]{false,"trader is busy"};
            var event=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract(player,net.minecraft.world.InteractionHand.MAIN_HAND,entity);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
            if(event.isCanceled())return new Object[]{false,"not allowed"};
            SimpleContainer inventory=copyInventory(agent);
            ItemStack room=offer.getResult().copy();
            InventoryUtils.insertIntoInventory(room,InventoryUtils.asItemHandler(inventory),room.getCount(),true);
            if(!room.isEmpty())return new Object[]{false,"not enough inventory space to trade"};
            inventory=payment(agent,offer,true);
            if(inventory==null)inventory=payment(agent,offer,false);
            if(inventory==null)return new Object[]{false,"not enough items to trade"};
            ItemStack output=offer.assemble();
            InventoryUtils.insertIntoInventory(output,InventoryUtils.asItemHandler(inventory),output.getCount(),false);
            if(!output.isEmpty())return new Object[]{false,"not enough inventory space to trade"};
            for(int slot=0;slot<inventory.getContainerSize();slot++)agent.mainInventory().setItem(slot,inventory.getItem(slot));
            var previous=merchant.getTradingPlayer();merchant.setTradingPlayer(player);
            try{merchant.notifyTrade(offer);merchant.overrideXp(merchant.getVillagerXp()+offer.getXp());}finally{merchant.setTradingPlayer(previous);}
            host.markChanged();return new Object[]{true};
        }
        @Override public void load(CompoundTag tag){
            origin=null;merchantEntity=null;upgradeAddress=tag.getString("upgradeAddress");
            hostAddress=tag.getString("hostAddress");hostDimension=tag.getString("hostDimension");merchantDimension=tag.getString("merchantDimension");
            hostPosition=tag.contains("hostPosition")?BlockPos.of(tag.getLong("hostPosition")):null;
            hostEntityId=tag.hasUUID("hostEntity")?tag.getUUID("hostEntity"):null;
            merchantId=tag.hasUUID("merchant")?tag.getUUID("merchant"):null;
            recipeIndex=tag.getInt("recipeID");merchantIndex=tag.getInt("merchantID");
        }
        @Override public void save(CompoundTag tag){
            if(validHost(origin))rememberHost(origin);
            tag.putString("upgradeAddress",upgradeAddress);tag.putString("hostAddress",hostAddress);tag.putString("hostDimension",hostDimension);tag.putString("merchantDimension",merchantDimension);
            if(hostPosition!=null)tag.putLong("hostPosition",hostPosition.asLong());
            if(hostEntityId!=null)tag.putUUID("hostEntity",hostEntityId);
            if(merchantId!=null)tag.putUUID("merchant",merchantId);
            tag.putInt("recipeID",recipeIndex);tag.putInt("merchantID",merchantIndex);
        }
    }
}
