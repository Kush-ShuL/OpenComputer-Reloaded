package li.cil.oc.server.component;

import li.cil.oc.Settings;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.event.*;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.*;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import li.cil.oc.server.agent.AgentHost;
import li.cil.oc.server.agent.AgentPlayer;
import li.cil.oc.util.InventoryUtils;
import li.cil.oc.util.FluidUtils;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import java.util.*;

/** Original shared Agent world, inventory and tank callbacks. World writes are synchronized callbacks. */
public abstract class AgentComponent extends AbstractManagedEnvironment implements DeviceInfo {
    public final AgentHost agent;
    protected AgentComponent(AgentHost agent, String name, double capacity) {
        this.agent = agent;
        setNode(Network.newNode(this, Visibility.Network).withComponent(name).withConnector(capacity).create());
    }
    @Override public ComponentConnector node() { return (ComponentConnector)super.node(); }
    @Override public Map<String,String> getDeviceInfo() { return Map.of("class","system","description",agent.robot()?"Robot":"Drone","vendor",li.cil.oc.Constants.DeviceInfo.DefaultVendor,"product",agent.robot()?"Caterpillar":"Overwatcher","capacity",Integer.toString(agent.mainInventory().getContainerSize())); }
    protected Direction side(Arguments args, int index) {
        int side=args.checkInteger(index);
        if(side<0||side>5||agent.robot()&&side!=0&&side!=1&&side!=3)throw new IllegalArgumentException("invalid side");
        return agent.toGlobal(Direction.from3DDataValue(side));
    }
    protected int slot(Arguments args,int index) { int slot=args.checkInteger(index)-1;if(slot<0||slot>=agent.mainInventory().getContainerSize())throw new IllegalArgumentException("invalid slot");return slot; }
    protected int selected(Arguments args,int index) { return args.count()>index?slot(args,index):agent.selectedSlot(); }
    protected ItemStack selectedStack() { return agent.selectedSlot()>=0&&agent.selectedSlot()<agent.mainInventory().getContainerSize()?agent.mainInventory().getItem(agent.selectedSlot()):ItemStack.EMPTY; }
    protected void delay(Context context,String action) { context.pause(agent.delay(action)*(agent.robot()?1:2));agent.animate(agent.delay(action),1); }
    @Callback public Object[] name(Context context,Arguments args){return new Object[]{agent.name()};}
    @Callback public Object[] inventorySize(Context context,Arguments args){return new Object[]{agent.mainInventory().getContainerSize()};}
    @Callback public Object[] select(Context context,Arguments args){if(args.count()>0)agent.setSelectedSlot(slot(args,0));return new Object[]{agent.selectedSlot()+1};}
    @Callback(direct=true) public Object[] count(Context context,Arguments args){int slot=selected(args,0);return new Object[]{slot<0?0:agent.mainInventory().getItem(slot).getCount()};}
    @Callback(direct=true) public Object[] space(Context context,Arguments args){int slot=selected(args,0);ItemStack stack=slot<0?ItemStack.EMPTY:agent.mainInventory().getItem(slot);return new Object[]{stack.isEmpty()?64:Math.min(stack.getMaxStackSize(),agent.mainInventory().getMaxStackSize())-stack.getCount()};}
    @Callback public Object[] compareTo(Context context,Arguments args){ItemStack a=selectedStack(),b=agent.mainInventory().getItem(slot(args,0));return new Object[]{a.isEmpty()&&b.isEmpty()||InventoryUtils.haveSameItemType(a,b,args.optBoolean(1,false))};}
    @Callback public Object[] transferTo(Context context,Arguments args){int to=slot(args,0);if(to==agent.selectedSlot())return new Object[]{true};int moved=InventoryUtils.transferBetweenInventoriesSlots(InventoryUtils.asItemHandler(agent.mainInventory()),agent.selectedSlot(),InventoryUtils.asItemHandler(agent.mainInventory()),to,Math.max(0,args.optInteger(1,64)));agent.inventoryChanged();return new Object[]{moved>0};}
    @Callback public Object[] detect(Context context,Arguments args){return blockContent(side(args,0));}
    public Object[] blockContent(Direction side){
        var level=agent.world();BlockPos pos=agent.blockPosition().relative(side);
        if(!level.hasChunkAt(pos))return new Object[]{true,"unloaded"};
        if(!level.getEntities((Entity)null,new AABB(pos),entity->entity instanceof LivingEntity||entity instanceof net.minecraft.world.entity.vehicle.AbstractMinecart).isEmpty())return new Object[]{true,"entity"};
        var state=level.getBlockState(pos);if(state.isAir())return new Object[]{false,"air"};
        if(!state.getFluidState().isEmpty()||state.canBeReplaced()){
            var event=new BlockEvent.BreakEvent(level,pos,state,agent.player());NeoForge.EVENT_BUS.post(event);
            return new Object[]{event.isCanceled(),!state.getFluidState().isEmpty()?"liquid":"replaceable"};
        }
        return new Object[]{true,state.getCollisionShape(level,pos).isEmpty()?"passable":"solid"};
    }
    @Callback public Object[] compare(Context context,Arguments args){var state=agent.world().getBlockState(agent.blockPosition().relative(side(args,0)));ItemStack stack=selectedStack();return new Object[]{stack.getItem() instanceof BlockItem item&&item.getBlock()==state.getBlock()};}
    @Callback public Object[] drop(Context context,Arguments args){
        Direction facing=side(args,0);ItemStack stack=selectedStack();if(stack.isEmpty())return new Object[]{false};int count=Math.max(0,Math.min(stack.getCount(),args.optInteger(1,64)));if(count==0)return new Object[]{false};
        var target=InventoryUtils.inventoryAt(agent.world(),agent.blockPosition().relative(facing),facing.getOpposite());
        int moved;
        if(target.isPresent())moved=InventoryUtils.transferBetweenInventoriesSlots(InventoryUtils.asItemHandler(agent.mainInventory()),agent.selectedSlot(),target.get(),null,count);
        else{ItemStack drop=agent.mainInventory().removeItem(agent.selectedSlot(),count);moved=drop.getCount();InventoryUtils.spawnStackInWorld(agent.world(),agent.blockPosition(),drop,facing);}
        if(moved>0)delay(context,"drop");agent.inventoryChanged();return new Object[]{moved>0};
    }
    @Callback public Object[] suck(Context context,Arguments args){
        Direction facing=side(args,0);int limit=Math.max(0,args.optInteger(1,64)),moved=0;var inventory=InventoryUtils.inventoryAt(agent.world(),agent.blockPosition().relative(facing),facing.getOpposite());
        if(inventory.isPresent())moved=InventoryUtils.transferBetweenInventories(inventory.get(),InventoryUtils.asItemHandler(agent.mainInventory()),limit);
        else for(ItemEntity entity:agent.world().getEntitiesOfClass(ItemEntity.class,new AABB(agent.blockPosition().relative(facing)).inflate(agent.robot()?0:.5),Entity::isAlive)){
            ItemStack stack=entity.getItem();int before=stack.getCount();InventoryUtils.insertIntoInventory(stack,InventoryUtils.asItemHandler(agent.mainInventory()),limit-moved,false);moved+=before-stack.getCount();if(stack.isEmpty())entity.discard();if(moved>=limit)break;
        }
        if(moved>0)delay(context,"suck");agent.inventoryChanged();return new Object[]{moved>0};
    }
    @Callback public Object[] swing(Context context,Arguments args){
        Direction facing=side(args,0);AgentPlayer player=agent.runtime().player().orient(facing,args.optBoolean(2,false));var level=(ServerLevel)agent.world();BlockPos target=agent.blockPosition().relative(facing);
        Vec3 start=player.getEyePosition(),end=start.add(Vec3.atLowerCornerOf(facing.getNormal()).scale(Settings.get().swingRange));
        var obstruction=level.clip(new net.minecraft.world.level.ClipContext(start,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
        double reachSquared=obstruction.getType()==HitResult.Type.MISS?start.distanceToSqr(end):start.distanceToSqr(obstruction.getLocation());
        var targets=level.getEntities(player,new AABB(start,end).inflate(.35),entity->entity!=agent&&entity.isAlive()&&entity.isPickable()&&!(entity instanceof ItemEntity)&&entity.getBoundingBox().inflate(entity.getPickRadius()).clip(start,end).filter(hit->start.distanceToSqr(hit)<=reachSquared).isPresent());
        if(!targets.isEmpty()){
            Entity hit=targets.stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElseThrow();
            if(hit instanceof Player&&!Settings.get().canAttackPlayers)return new Object[]{false,"entity"};
            var event=new RobotAttackEntityEvent.Pre(agent,hit);NeoForge.EVENT_BUS.post(event);if(event.isCanceled())return new Object[]{false,"entity"};
            Set<Integer> old=new HashSet<>();for(ItemEntity entity:level.getEntitiesOfClass(ItemEntity.class,hit.getBoundingBox().inflate(2)))old.add(entity.getId());
            ItemStack before=player.getMainHandItem().copy();player.attack(hit);NeoForge.EVENT_BUS.post(new RobotAttackEntityEvent.Post(agent,hit));toolUsed(before,player.getMainHandItem());delay(context,"swing");collectDrops(hit.blockPosition(),old);return new Object[]{true,"entity"};
        }
        if(!level.hasChunkAt(target)||!level.mayInteract(player,target))return new Object[]{false,"block"};var state=level.getBlockState(target);
        if(state.is(net.minecraft.world.level.block.Blocks.FIRE)||state.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)){level.removeBlock(target,false);delay(context,"swing");return new Object[]{true,"fire"};}
        if(state.isAir())return new Object[]{false,"air"};boolean cobwebOverride=state.is(net.minecraft.world.level.block.Blocks.COBWEB)&&Settings.get().screwCobwebs;float progress=state.getDestroyProgress(player,level,target);if(progress<=0&&!cobwebOverride)return new Object[]{false,"block"};
        double breakTime=cobwebOverride?Math.max(.05,Settings.get().swingDelay*Settings.get().harvestRatio):Math.max(agent.delay("swing"),1d/progress/20*Settings.get().harvestRatio);
        var event=new RobotBreakBlockEvent.Pre(agent,level,target,breakTime);NeoForge.EVENT_BUS.post(event);if(event.isCanceled())return new Object[]{false,"block"};
        Set<Integer> old=new HashSet<>(),oldExperience=new HashSet<>();for(ItemEntity entity:level.getEntitiesOfClass(ItemEntity.class,new AABB(target).inflate(2)))old.add(entity.getId());for(ExperienceOrb orb:level.getEntitiesOfClass(ExperienceOrb.class,new AABB(target).inflate(2)))oldExperience.add(orb.getId());
        ItemStack before=player.getMainHandItem().copy();boolean result=player.gameMode.destroyBlock(target);toolUsed(before,player.getMainHandItem());
        if(result){collectDrops(target,old);int experience=level.getEntitiesOfClass(ExperienceOrb.class,new AABB(target).inflate(2),orb->!oldExperience.contains(orb.getId())).stream().mapToInt(ExperienceOrb::getValue).sum();NeoForge.EVENT_BUS.post(new RobotBreakBlockEvent.Post(agent,experience));context.pause(event.getBreakTime());agent.animate(event.getBreakTime(),1);}
        player.setShiftKeyDown(false);return new Object[]{result,"block"};
    }
    private void collectDrops(BlockPos position,Set<Integer> existing){for(ItemEntity entity:agent.world().getEntitiesOfClass(ItemEntity.class,new AABB(position).inflate(2),entity->!existing.contains(entity.getId()))){ItemStack stack=entity.getItem();InventoryUtils.insertIntoInventory(stack,InventoryUtils.asItemHandler(agent.mainInventory()),stack.getCount(),false);if(stack.isEmpty())entity.discard();}agent.inventoryChanged();}
    private void toolUsed(ItemStack before,ItemStack after){var event=new RobotUsedToolEvent.ComputeDamageRate(agent,before,after,Settings.get().itemDamageRate);NeoForge.EVENT_BUS.post(event);if(event.getDamageRate()<1){if(!after.isEmpty()&&ItemStack.isSameItem(before,after)&&after.isDamageableItem()){int damage=Math.max(0,after.getDamageValue()-before.getDamageValue());int keep=(int)Math.floor(damage*event.getDamageRate()+agent.world().random.nextDouble());after.setDamageValue(before.getDamageValue()+keep);}NeoForge.EVENT_BUS.post(new RobotUsedToolEvent.ApplyDamageRate(agent,before,after,event.getDamageRate()));}agent.inventoryChanged();}
    @Callback public Object[] use(Context context,Arguments args){return interact(context,args,false);}
    @Callback public Object[] place(Context context,Arguments args){return interact(context,args,true);}
    private Object[] interact(Context context,Arguments args,boolean place){
        Direction facing=side(args,0);AgentPlayer player=agent.runtime().player().orient(facing,args.optBoolean(2,false));var level=(ServerLevel)agent.world();BlockPos target=agent.blockPosition().relative(facing);
        if(!level.hasChunkAt(target)||!level.mayInteract(player,target))return new Object[]{false};
        Direction face=args.count()>1&&args.isInteger(1)?agent.toGlobal(Direction.from3DDataValue(args.checkInteger(1))):facing.getOpposite();
        boolean previous=player.usingSelectedMaterial;player.usingSelectedMaterial=place;ItemStack before=player.getMainHandItem().copy();
        try{
            if(place&&before.isEmpty())return new Object[]{null,"nothing selected"};
            if(!place){
                Vec3 eye=player.getEyePosition(),end=eye.add(Vec3.atLowerCornerOf(facing.getNormal()).scale(Settings.get().useAndPlaceRange));
                var obstruction=level.clip(new net.minecraft.world.level.ClipContext(eye,end,net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
                double reach=obstruction.getType()==HitResult.Type.MISS?eye.distanceToSqr(end):eye.distanceToSqr(obstruction.getLocation());
                Entity entity=level.getEntities(player,new AABB(eye,end).inflate(.35),candidate->candidate!=agent&&candidate.isAlive()&&candidate.isPickable()&&!(candidate instanceof ItemEntity)&&candidate.getBoundingBox().inflate(candidate.getPickRadius()).clip(eye,end).filter(hit->eye.distanceToSqr(hit)<=reach).isPresent()).stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
                if(entity!=null){Set<Integer> old=new HashSet<>();for(ItemEntity item:level.getEntitiesOfClass(ItemEntity.class,entity.getBoundingBox().inflate(2)))old.add(item.getId());if(player.interactOn(entity,InteractionHand.MAIN_HAND).consumesAction()){collectDrops(entity.blockPosition(),old);toolUsed(before,player.getMainHandItem());delay(context,"use");return new Object[]{true,"item_interacted"};}}
            }
            BlockPos anchor=target;
            if(place&&level.getBlockState(target).canBeReplaced()){
                boolean reference=false;
                for(Direction direction:Direction.values()){BlockPos adjacent=target.relative(direction);if(!level.getBlockState(adjacent).canBeReplaced()){anchor=adjacent;face=direction.getOpposite();reference=true;break;}}
                if(!reference){var event=new RobotPlaceInAirEvent(agent);NeoForge.EVENT_BUS.post(event);if(!event.isAllowed())return new Object[]{false};}
            }
            var event=new RobotPlaceBlockEvent.Pre(agent,before,level,target);if(place){NeoForge.EVENT_BUS.post(event);if(event.isCanceled())return new Object[]{false};}
            var hit=new BlockHitResult(Vec3.atCenterOf(anchor).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5)),face,anchor,false);
            InteractionResult result=InteractionResult.PASS;
            if(place||Settings.get().allowActivateBlocks)result=player.gameMode.useItemOn(player,level,player.getMainHandItem(),InteractionHand.MAIN_HAND,hit);
            if(!result.consumesAction()&&!place&&!before.isEmpty())result=player.gameMode.useItem(player,level,player.getMainHandItem(),InteractionHand.MAIN_HAND);
            if(result.consumesAction()){
                if(place)NeoForge.EVENT_BUS.post(new RobotPlaceBlockEvent.Post(agent,before,level,target));
                if(!place&&args.count()>3&&args.isDouble(3)&&Settings.get().allowUseItemsWithDuration){int ticks=(int)Math.min(72000,Math.max(0,args.checkDouble(3)*20));for(int tick=0;tick<ticks&&player.isUsingItem();tick++)player.getUseItem().getItem().onUseTick(level,player,player.getUseItem(),player.getUseItemRemainingTicks()-tick);player.releaseUsingItem();context.pause(ticks/20d);}
                toolUsed(before,player.getMainHandItem());delay(context,place?"place":"use");return new Object[]{true,place?"item_placed":"item_used"};
            }
            return new Object[]{false};
        }finally{player.usingSelectedMaterial=previous;player.setShiftKeyDown(false);}
    }
    @Callback public Object[] tankCount(Context context,Arguments args){return new Object[]{agent.tank().tankCount()};}
    private int tank(Arguments args,int index){int tank=args.count()>index?args.checkInteger(index)-1:agent.selectedTank();if(tank<0||tank>=agent.tank().tankCount())throw new IllegalArgumentException("invalid tank");return tank;}
    @Callback public Object[] selectTank(Context context,Arguments args){if(args.count()>0)agent.setSelectedTank(tank(args,0));return new Object[]{agent.selectedTank()+1};}
    @Callback(direct=true) public Object[] tankLevel(Context context,Arguments args){return new Object[]{agent.tank().getFluidTank(tank(args,0)).getFluidAmount()};}
    @Callback(direct=true) public Object[] tankSpace(Context context,Arguments args){var tank=agent.tank().getFluidTank(tank(args,0));return new Object[]{tank.getCapacity()-tank.getFluidAmount()};}
    @Callback public Object[] compareFluidTo(Context context,Arguments args){return new Object[]{FluidStack.isSameFluidSameComponents(agent.tank().getFluidTank(tank(args,0)).getFluid(),agent.tank().getFluidTank(agent.selectedTank()).getFluid())};}
    @Callback public Object[] transferFluidTo(Context context,Arguments args){var from=agent.tank().getFluidTank(agent.selectedTank());var to=agent.tank().getFluidTank(tank(args,0));FluidStack available=from.drain(Math.max(0,args.optInteger(1,1000)),IFluidHandler.FluidAction.SIMULATE);int moved=to.fill(available,IFluidHandler.FluidAction.SIMULATE);if(moved>0)to.fill(from.drain(moved,IFluidHandler.FluidAction.EXECUTE),IFluidHandler.FluidAction.EXECUTE);return new Object[]{moved>0};}
    @Callback public Object[] drain(Context context,Arguments args){return fluidTransfer(context,args,true);}
    @Callback public Object[] fill(Context context,Arguments args){return fluidTransfer(context,args,false);}
    private Object[] fluidTransfer(Context context,Arguments args,boolean drain){Direction facing=side(args,0);if(agent.tank().tankCount()==0)return new Object[]{false,"no tank selected"};var own=new SelectedTank(agent.tank().getFluidTank(agent.selectedTank()));var other=FluidUtils.fluidHandlerAt(agent.world(),agent.blockPosition().relative(facing),facing.getOpposite()).orElse(null);if(other==null)return new Object[]{false,"no tank"};int moved=drain?FluidUtils.transferBetweenFluidHandlers(other,own,Math.max(0,args.optInteger(1,1000))):FluidUtils.transferBetweenFluidHandlers(own,other,Math.max(0,args.optInteger(1,1000)));if(moved>0)delay(context,drain?"drain":"fill");agent.markChanged();return new Object[]{moved>0,moved};}
    @Callback public Object[] compareFluid(Context context,Arguments args){Direction facing=side(args,0);var other=FluidUtils.fluidHandlerAt(agent.world(),agent.blockPosition().relative(facing),facing.getOpposite()).orElse(null);if(agent.tank().tankCount()==0||other==null)return new Object[]{false};int index=args.optInteger(1,1)-1;if(index<0||index>=other.getTanks())throw new IllegalArgumentException("invalid tank");return new Object[]{FluidStack.isSameFluidSameComponents(agent.tank().getFluidTank(agent.selectedTank()).getFluid(),other.getFluidInTank(index))};}
    private record SelectedTank(IFluidTank tank) implements IFluidHandler {
        public int getTanks(){return 1;}public FluidStack getFluidInTank(int index){return index==0?tank.getFluid():FluidStack.EMPTY;}public int getTankCapacity(int index){return tank.getCapacity();}public boolean isFluidValid(int index,FluidStack stack){return index==0&&tank.isFluidValid(stack);}public int fill(FluidStack stack,FluidAction action){return tank.fill(stack,action);}public FluidStack drain(FluidStack stack,FluidAction action){return tank.drain(stack,action);}public FluidStack drain(int count,FluidAction action){return tank.drain(count,action);}
    }
}
