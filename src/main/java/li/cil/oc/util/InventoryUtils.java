package li.cil.oc.util;

import li.cil.oc.OpenComputers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.*;
import net.neoforged.neoforge.items.wrapper.*;
import java.util.*;
import java.util.function.*;

/** The original OC inventory operations, adapted to sided NeoForge handlers. */
public final class InventoryUtils {
    private InventoryUtils(){}
    public sealed interface InventorySource permits BlockInventorySource,EntityInventorySource {Direction side();IItemHandler inventory();}
    public record BlockInventorySource(BlockPos position,Direction side,IItemHandler inventory) implements InventorySource{}
    public record EntityInventorySource(Entity entity,Direction side,IItemHandler inventory) implements InventorySource{}
    public static IItemHandlerModifiable asItemHandler(Container inventory,Direction side){return inventory instanceof WorldlyContainer sided&&side!=null?new SidedInvWrapper(sided,side):new InvWrapper(inventory);}
    public static IItemHandlerModifiable asItemHandler(Container inventory){return asItemHandler(inventory,null);}
    public static boolean haveSameItemType(ItemStack a,ItemStack b,boolean checkComponents){return !a.isEmpty()&&!b.isEmpty()&&(checkComponents?ItemStack.isSameItemSameComponents(a,b):ItemStack.isSameItem(a,b));}
    public static boolean haveSameItemType(ItemStack a,ItemStack b){return haveSameItemType(a,b,false);}
    public static Optional<InventorySource> inventorySourceAt(Level level,BlockPos pos,Direction side){return inventorySourceAt(level,pos,side,true);}
    public static Optional<InventorySource> inventorySourceAt(Level level,BlockPos pos,Direction side,boolean includeEntities){
        if(level==null||!level.hasChunkAt(pos))return Optional.empty();
        IItemHandler handler=level.getCapability(Capabilities.ItemHandler.BLOCK,pos,side);
        if(handler==null&&level.getBlockEntity(pos) instanceof Container container)handler=asItemHandler(container,side);
        if(handler!=null)return Optional.of(new BlockInventorySource(pos.immutable(),side,handler));
        if(includeEntities)for(Entity entity:level.getEntities((Entity)null,new AABB(pos),Entity::isAlive)){
            handler=entity.getCapability(Capabilities.ItemHandler.ENTITY_AUTOMATION,side);
            if(handler!=null)return Optional.of(new EntityInventorySource(entity,side,handler));
        }
        return Optional.empty();
    }
    public static Optional<IItemHandler> inventoryAt(Level level,BlockPos pos,Direction side){return inventorySourceAt(level,pos,side).map(InventorySource::inventory);}
    public static Optional<IItemHandler> inventoryAt(Level level,BlockPos pos,Direction side,boolean entities){return inventorySourceAt(level,pos,side,entities).map(InventorySource::inventory);}
    public static Optional<InventorySource> anyInventorySourceAt(Level level,BlockPos pos){Optional<InventorySource> found=inventorySourceAt(level,pos,null);if(found.isPresent())return found;for(Direction side:Direction.values()){found=inventorySourceAt(level,pos,side);if(found.isPresent())return found;}return Optional.empty();}
    public static Optional<IItemHandler> anyInventoryAt(Level level,BlockPos pos){return anyInventorySourceAt(level,pos).map(InventorySource::inventory);}
    /** Simulation also shrinks the supplied working stack, exactly like upstream. */
    public static boolean insertIntoInventorySlot(ItemStack stack,IItemHandler inventory,int slot,int limit,boolean simulate){
        if(stack.isEmpty()||limit<=0||slot<0||slot>=inventory.getSlots())return false;
        int amount=Math.min(stack.getCount(),limit);ItemStack remainder=inventory.insertItem(slot,stack.copyWithCount(amount),simulate);int inserted=Math.max(0,amount-remainder.getCount());stack.shrink(inserted);return inserted>0;
    }
    public static boolean insertIntoInventorySlot(ItemStack stack,IItemHandler inventory,int slot){return insertIntoInventorySlot(stack,inventory,slot,64,false);}
    public static boolean insertIntoInventory(ItemStack stack,IItemHandler inventory,int limit,boolean simulate){return insertIntoInventory(stack,inventory,limit,simulate,null);}
    public static boolean insertIntoInventory(ItemStack stack,IItemHandler inventory,int limit,boolean simulate,Iterable<Integer> slots){
        if(stack.isEmpty()||limit<=0)return false;int before=stack.getCount(),remaining=Math.min(before,limit);
        if(slots==null){for(int slot=0;slot<inventory.getSlots()&&remaining>0;slot++){int previous=stack.getCount();insertIntoInventorySlot(stack,inventory,slot,remaining,simulate);remaining-=previous-stack.getCount();}}
        else for(int slot:slots){int previous=stack.getCount();insertIntoInventorySlot(stack,inventory,slot,remaining,simulate);remaining-=previous-stack.getCount();if(remaining<=0)break;}
        return stack.getCount()<before;
    }
    public static boolean insertIntoInventory(ItemStack stack,IItemHandler inventory){return insertIntoInventory(stack,inventory,64,false);}
    public static int extractFromInventorySlot(BiConsumer<ItemStack,Boolean> consumer,IItemHandler inventory,int slot,int limit){
        if(limit<=0||slot<0||slot>=inventory.getSlots())return 0;ItemStack simulated=inventory.extractItem(slot,limit,true);if(simulated.isEmpty())return 0;
        ItemStack working=simulated.copy();consumer.accept(working,true);int count=Math.max(0,simulated.getCount()-working.getCount());if(count==0)return 0;
        ItemStack extracted=inventory.extractItem(slot,count,false);int actual=extracted.getCount();if(actual!=count)OpenComputers.LOGGER.warn("Item handler {} returned a different result during extraction: simulated {}, extracted {}",inventory,count,actual);
        if(!extracted.isEmpty()){consumer.accept(extracted,false);if(!extracted.isEmpty()){
            // A foreign handler may change between simulation and execution. Return any
            // rejected remainder instead of discarding the extracted item stack.
            ItemStack remainder=inventory.insertItem(slot,extracted,false);if(!remainder.isEmpty())insertIntoInventory(remainder,inventory,remainder.getCount(),false);if(!remainder.isEmpty())OpenComputers.LOGGER.error("Item handler {} rejected extraction rollback of {}",inventory,remainder);
            actual-=extracted.getCount();
        }}return Math.max(0,actual);
    }
    public static int extractAnyFromInventory(BiConsumer<ItemStack,Boolean> consumer,IItemHandler inventory,int limit){for(int slot=0;slot<inventory.getSlots();slot++){int result=extractFromInventorySlot(consumer,inventory,slot,limit);if(result>0)return result;}return 0;}
    /** Returns the part of the requested stack which could not be extracted. */
    public static ItemStack extractFromInventory(ItemStack requested,IItemHandler inventory,boolean simulate,boolean exact){
        ItemStack remaining=requested.copy();for(int slot=0;slot<inventory.getSlots()&&!remaining.isEmpty();slot++){
            ItemStack available=inventory.extractItem(slot,remaining.getCount(),true);if(available.isEmpty()||!haveSameItemType(remaining,available,exact))continue;
            ItemStack taken=simulate?available:inventory.extractItem(slot,Math.min(remaining.getCount(),available.getCount()),false);remaining.shrink(taken.getCount());
        }return remaining;
    }
    public static int transferBetweenInventories(IItemHandler source,IItemHandler sink,int limit){if(source==sink)return 0;return extractAnyFromInventory((stack,simulate)->insertIntoInventory(stack,sink,limit,simulate),source,limit);}
    public static int transferBetweenInventoriesSlots(IItemHandler source,int sourceSlot,IItemHandler sink,Integer sinkSlot,int limit){if(source==sink&&sinkSlot!=null&&sourceSlot==sinkSlot)return 0;return extractFromInventorySlot((stack,simulate)->{if(sinkSlot==null)insertIntoInventory(stack,sink,limit,simulate);else insertIntoInventorySlot(stack,sink,sinkSlot,limit,simulate);},source,sourceSlot,limit);}
    public static boolean insertIntoInventoryAt(ItemStack stack,Level level,BlockPos pos,Direction side,int limit,boolean simulate){return inventoryAt(level,pos,side).map(inventory->insertIntoInventory(stack,inventory,limit,simulate)).orElse(false);}
    public static IntSupplier getExtractorFromInventoryAt(BiConsumer<ItemStack,Boolean> consumer,Level level,BlockPos pos,Direction side,int limit){return inventoryAt(level,pos,side).<IntSupplier>map(inventory->()->extractAnyFromInventory(consumer,inventory,limit)).orElse(null);}
    public static IntSupplier getTransferBetweenInventoriesAt(Level level,BlockPos source,Direction sourceSide,BlockPos sink,Direction sinkSide,int limit){IItemHandler a=inventoryAt(level,source,sourceSide).orElse(null),b=inventoryAt(level,sink,sinkSide).orElse(null);return a==null||b==null?null:()->transferBetweenInventories(a,b,limit);}
    public static IntSupplier getTransferBetweenInventoriesSlotsAt(Level level,BlockPos source,Direction sourceSide,int sourceSlot,BlockPos sink,Direction sinkSide,Integer sinkSlot,int limit){IItemHandler a=inventoryAt(level,source,sourceSide).orElse(null),b=inventoryAt(level,sink,sinkSide).orElse(null);return a==null||b==null?null:()->transferBetweenInventoriesSlots(a,sourceSlot,b,sinkSlot,limit);}
    public static boolean dropSlot(Level level,BlockPos pos,Container inventory,int slot,int count,Direction direction){ItemStack stack=inventory.removeItem(slot,count);if(stack.isEmpty())return false;spawnStackInWorld(level,pos,stack,direction);return true;}
    public static void dropAllSlots(Level level,BlockPos pos,Container inventory){for(int slot=0;slot<inventory.getContainerSize();slot++){ItemStack stack=inventory.removeItemNoUpdate(slot);if(!stack.isEmpty())spawnStackInWorld(level,pos,stack,null);}inventory.setChanged();}
    public static void addToPlayerInventory(ItemStack stack,Player player,boolean spawnInWorld){if(stack.isEmpty())return;player.getInventory().add(stack);player.getInventory().setChanged();player.containerMenu.broadcastChanges();if(!stack.isEmpty()&&spawnInWorld)player.drop(stack,false);}
    public static ItemEntity spawnStackInWorld(Level level,BlockPos pos,ItemStack stack,Direction direction){return spawnStackInWorld(level,pos,stack,direction,entity->true);}
    public static ItemEntity spawnStackInWorld(Level level,BlockPos pos,ItemStack stack,Direction direction,Predicate<ItemEntity> validator){
        if(level==null||level.isClientSide||stack.isEmpty())return null;var rng=level.random;int dx=direction==null?0:direction.getStepX(),dy=direction==null?0:direction.getStepY(),dz=direction==null?0:direction.getStepZ();
        ItemEntity entity=new ItemEntity(level,pos.getX()+.5+.1*(rng.nextDouble()-.5)+dx*.65,pos.getY()+.5+.1*(rng.nextDouble()-.5)+dy*.75+(dx+dz)*.25,pos.getZ()+.5+.1*(rng.nextDouble()-.5)+dz*.65,stack.copy());
        entity.setDeltaMovement(.0125*(rng.nextDouble()-.5)+dx*.03,.0125*(rng.nextDouble()-.5)+dy*.08+(dx+dz)*.03,.0125*(rng.nextDouble()-.5)+dz*.03);
        if(!validator.test(entity))return null;entity.setPickUpDelay(15);return level.addFreshEntity(entity)?entity:null;
    }
}
