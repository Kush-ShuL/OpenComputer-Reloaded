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
    protected int selected(Arguments args,int index) { return args.count()>index&&args.checkAny(index)!=null?slot(args,index):agent.selectedSlot(); }
    protected ItemStack selectedStack() { return agent.selectedSlot()>=0&&agent.selectedSlot()<agent.mainInventory().getContainerSize()?agent.mainInventory().getItem(agent.selectedSlot()):ItemStack.EMPTY; }
    protected void delay(Context context,String action) { context.pause(agent.delay(action)*(agent.robot()?1:2));if(agent.equipmentInventory().getContainerSize()>0&&!agent.equipmentInventory().getItem(0).isEmpty())agent.animate(agent.delay(action),1); }
    @Callback public Object[] name(Context context,Arguments args){return new Object[]{agent.name()};}
    @Callback public Object[] inventorySize(Context context,Arguments args){return new Object[]{agent.mainInventory().getContainerSize()};}
    @Callback public Object[] select(Context context,Arguments args){if(args.count()>0&&args.checkAny(0)!=null)agent.setSelectedSlot(slot(args,0));return new Object[]{Math.max(0,agent.selectedSlot())+1};}
    @Callback(direct=true) public Object[] count(Context context,Arguments args){int slot=selected(args,0);return new Object[]{slot<0?0:agent.mainInventory().getItem(slot).getCount()};}
    @Callback(direct=true) public Object[] space(Context context,Arguments args){int slot=selected(args,0);ItemStack stack=slot<0?ItemStack.EMPTY:agent.mainInventory().getItem(slot);return new Object[]{stack.isEmpty()?64:Math.min(stack.getMaxStackSize(),agent.mainInventory().getMaxStackSize())-stack.getCount()};}
    @Callback public Object[] compareTo(Context context,Arguments args){ItemStack a=selectedStack(),b=agent.mainInventory().getItem(slot(args,0));return new Object[]{a.isEmpty()&&b.isEmpty()||InventoryUtils.haveSameItemType(a,b,args.optBoolean(1,false))};}
    @Callback public Object[] transferTo(Context context,Arguments args){
        int to=slot(args,0),count=Math.clamp(args.optInteger(1,64),0,64);
        if(to==agent.selectedSlot()||count==0)return new Object[]{true};
        ItemStack from=selectedStack(),target=agent.mainInventory().getItem(to);
        if(!from.isEmpty()&&!target.isEmpty()&&!InventoryUtils.haveSameItemType(from,target,true)){
            if(count<from.getCount())return new Object[]{false};
            agent.mainInventory().setItem(to,from);agent.mainInventory().setItem(agent.selectedSlot(),target);agent.inventoryChanged();return new Object[]{true};
        }
        int moved=InventoryUtils.transferBetweenInventoriesSlots(InventoryUtils.asItemHandler(agent.mainInventory()),agent.selectedSlot(),InventoryUtils.asItemHandler(agent.mainInventory()),to,count);agent.inventoryChanged();return new Object[]{moved>0};
    }
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
        Direction facing=side(args,0);int count=Math.clamp(args.optInteger(1,64),0,64);ItemStack stack=selectedStack();if(stack.isEmpty())return new Object[]{false};
        BlockPos position=agent.blockPosition().relative(facing);AgentPlayer player=agent.runtime().player();
        var target=InventoryUtils.inventoryAt(agent.world(),position,facing.getOpposite());
        if(target.isPresent()&&rightClickAllowed(player,interactionFace(position,facing.getOpposite()))){
            int moved=InventoryUtils.transferBetweenInventoriesSlots(InventoryUtils.asItemHandler(agent.mainInventory()),agent.selectedSlot(),target.get(),null,count);
            if(moved==0)return new Object[]{false,"inventory full"};
        }else{
            ItemStack dropped=agent.mainInventory().removeItem(agent.selectedSlot(),count);
            if(!dropped.isEmpty()&&InventoryUtils.spawnStackInWorld(agent.world(),agent.blockPosition(),dropped,facing,
                    entity->!NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.item.ItemTossEvent(entity,player)).isCanceled())==null)
                player.getInventory().add(dropped);
        }
        context.pause(Settings.get().dropDelay);agent.inventoryChanged();return new Object[]{true};
    }
    @Callback public Object[] suck(Context context,Arguments args){
        Direction facing=side(args,0);int limit=Math.clamp(args.optInteger(1,64),0,64),moved=0;BlockPos position=agent.blockPosition().relative(facing);AgentPlayer player=agent.runtime().player();
        var inventory=InventoryUtils.inventoryAt(agent.world(),position,facing.getOpposite());
        if(inventory.isPresent()&&rightClickAllowed(player,interactionFace(position,facing.getOpposite())))moved=InventoryUtils.transferBetweenInventories(inventory.get(),InventoryUtils.asItemHandler(agent.mainInventory()),limit);
        if(moved==0){
            List<ItemEntity> items=new ArrayList<>();
            if(!agent.robot())items.addAll(agent.world().getEntitiesOfClass(ItemEntity.class,new AABB(agent.blockPosition()),Entity::isAlive));
            items.addAll(agent.world().getEntitiesOfClass(ItemEntity.class,new AABB(position),Entity::isAlive));
            for(ItemEntity entity:items)if(!entity.hasPickUpDelay()){
                ItemStack stack=entity.getItem();int before=stack.getCount();
                if(agent.robot())entity.playerTouch(player);
                else if(player.getInventory().add(stack))agent.world().playSound(null,agent.xPosition(),agent.yPosition(),agent.zPosition(),net.minecraft.sounds.SoundEvents.ITEM_PICKUP,net.minecraft.sounds.SoundSource.NEUTRAL,.2f,((agent.world().random.nextFloat()-agent.world().random.nextFloat())*.7f+1)*2);
                moved=before-stack.getCount();if(stack.isEmpty())entity.discard();if(moved>0||entity.isRemoved()){if(moved==0)moved=before;break;}
            }
        }
        if(moved>0)context.pause(Settings.get().suckDelay);agent.inventoryChanged();return moved>0?new Object[]{moved}:new Object[]{false};
    }
    @Callback public Object[] swing(Context context,Arguments args){
        Direction facing=side(args,0);List<Direction> faces=new ArrayList<>();
        if(args.isInteger(1)){int face=args.checkInteger(1);if(face<0||face>5)throw new IllegalArgumentException("invalid side");Direction side=agent.toGlobal(Direction.from3DDataValue(face));if(side==facing.getOpposite())throw new IllegalArgumentException("unsupported side");faces.add(side);}
        else{faces.add(facing);for(Direction side:Direction.values())if(side!=facing&&side!=facing.getOpposite())faces.add(side);}
        boolean sneaky=args.isBoolean(2)&&args.checkBoolean(2);AgentPlayer player=agent.runtime().player();ServerLevel level=(ServerLevel)agent.world();BlockPos target=agent.blockPosition().relative(facing);
        try(var drops=li.cil.oc.server.agent.AgentActionDrops.begin(agent,new AABB(agent.blockPosition()).inflate(Settings.get().swingRange+2))){
            try{
                String reason=null;
                for(Direction face:faces){
                    orientForInteraction(player,facing,face,sneaky);
                    Vec3 center=new Vec3(agent.xPosition(),agent.yPosition(),agent.zPosition()),offset=Vec3.atLowerCornerOf(facing.getNormal());
                    // The configured range extends from the adjacent block's center, not the agent's eye.
                    Vec3 start=center.add(offset.scale(.50001)),end=center.add(offset.scale(1.01)).add(Vec3.atLowerCornerOf(face.getNormal()).scale(Settings.get().swingRange));
                    BlockHitResult obstruction=level.clip(new net.minecraft.world.level.ClipContext(start,end,net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
                    double reachSquared=obstruction.getType()==HitResult.Type.MISS?start.distanceToSqr(end):start.distanceToSqr(obstruction.getLocation());
                    var targets=level.getEntities(player,new AABB(start,end).inflate(.35),entity->entity!=agent&&entity.isAlive()&&entity.isPickable()&&!(entity instanceof ItemEntity)&&entity.getBoundingBox().inflate(entity.getPickRadius()).clip(start,end).filter(hit->start.distanceToSqr(hit)<=reachSquared).isPresent());
                    Entity hit=targets.stream().min(Comparator.comparingDouble(entity->start.distanceToSqr(entity.position()))).orElse(null);
                    if(hit==null&&obstruction.getType()==HitResult.Type.MISS)hit=level.getEntitiesOfClass(LivingEntity.class,new AABB(target),Entity::isAlive).stream().filter(entity->entity!=agent&&entity!=player).min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
                    Object[] result;
                    if(hit!=null)result=swingEntity(context,player,hit);
                    else if(obstruction.getType()==HitResult.Type.BLOCK)result=swingBlock(context,player,obstruction.getBlockPos(),drops);
                    else{
                        var state=level.getBlockState(target);
                        if(state.is(Blocks.FIRE)||state.is(Blocks.SOUL_FIRE)){level.removeBlock(target,false);delay(context,"swing");return new Object[]{true,"fire"};}
                        result=new Object[]{false,"air"};
                    }
                    if(Boolean.TRUE.equals(result[0]))return result;
                    if(reason==null)reason=(String)result[1];
                }
                if(Boolean.TRUE.equals(blockContent(facing)[0]))return swingBlock(context,player,target,drops);
                return new Object[]{false,reason};
            }finally{player.setShiftKeyDown(false);}
        }
    }
    private Object[] swingEntity(Context context,AgentPlayer player,Entity hit){
        if(hit instanceof Player&&!Settings.get().canAttackPlayers){delay(context,"swing");return new Object[]{true,"entity"};}
        int attempts=hit instanceof net.minecraft.world.entity.vehicle.AbstractMinecart?11:1;
        for(int i=0;i<attempts&&hit.isAlive();i++){
            var event=new RobotAttackEntityEvent.Pre(agent,hit);NeoForge.EVENT_BUS.post(event);if(event.isCanceled())continue;
            ItemStack before=player.getMainHandItem().copy();player.attack(hit);NeoForge.EVENT_BUS.post(new RobotAttackEntityEvent.Post(agent,hit));toolUsed(before,player.getMainHandItem());
        }
        delay(context,"swing");return new Object[]{true,"entity"};
    }
    private Object[] swingBlock(Context context,AgentPlayer player,BlockPos target,li.cil.oc.server.agent.AgentActionDrops drops){
        ServerLevel level=(ServerLevel)agent.world();if(!level.hasChunkAt(target)||!level.mayInteract(player,target))return new Object[]{false,"block"};var state=level.getBlockState(target);
        if(state.isAir())return new Object[]{false,"air"};if(!state.canHarvestBlock(level,target,player))return new Object[]{false,"block"};
        boolean cobweb=state.is(Blocks.COBWEB)&&Settings.get().screwCobwebs;float progress=state.getDestroyProgress(player,level,target);if(progress<=0&&!cobweb)return new Object[]{false,"block"};
        double breakTime=(cobweb?Settings.get().swingDelay:1d/progress/20)*Settings.get().harvestRatio;
        var event=new RobotBreakBlockEvent.Pre(agent,level,target,breakTime);NeoForge.EVENT_BUS.post(event);if(event.isCanceled())return new Object[]{false,"block"};
        int experience=drops.experience();ItemStack before=player.getMainHandItem().copy();boolean result=player.gameMode.destroyBlock(target);toolUsed(before,player.getMainHandItem());
        if(result){NeoForge.EVENT_BUS.post(new RobotBreakBlockEvent.Post(agent,drops.experience()-experience));double duration=Math.max(.05,event.getBreakTime());context.pause(duration*(agent.robot()?1:2));if(agent.equipmentInventory().getContainerSize()>0&&!agent.equipmentInventory().getItem(0).isEmpty())agent.animate(duration,1);}
        return new Object[]{result,"block"};
    }
    private void toolUsed(ItemStack before,ItemStack after){var event=new RobotUsedToolEvent.ComputeDamageRate(agent,before,after,Settings.get().itemDamageRate);NeoForge.EVENT_BUS.post(event);if(event.getDamageRate()<1)NeoForge.EVENT_BUS.post(new RobotUsedToolEvent.ApplyDamageRate(agent,before,after,event.getDamageRate()));agent.inventoryChanged();}
    @Callback(doc="function(side:number[, face:number=side[, sneaky:boolean=false[, duration:number=0]]]):boolean, string -- Perform a 'right click' towards the specified side. The `face' allows a more precise click calibration, and is relative to the targeted blockspace.")
    public Object[] use(Context context,Arguments args){return interact(context,args,false);}
    @Callback(doc="function(side:number[, face:number=side[, sneaky:boolean=false]]):boolean -- Place a block towards the specified side. The `face' allows a more precise click calibration, and is relative to the targeted blockspace.")
    public Object[] place(Context context,Arguments args){return interact(context,args,true);}

    private Object[] interact(Context context,Arguments args,boolean place){
        int value=args.checkInteger(0);
        if(value<0||value>5)throw new IllegalArgumentException("invalid side");
        if(agent.robot()&&value!=0&&value!=1&&value!=3)throw new IllegalArgumentException("unsupported side");
        Direction facing=agent.robot()?agent.toGlobal(Direction.from3DDataValue(value)):Direction.from3DDataValue(value);
        List<Direction> sides=new ArrayList<>();
        if(args.isInteger(1)){
            int face=args.checkInteger(1);
            if(face<0||face>5)throw new IllegalArgumentException("invalid side");
            Direction side=agent.toGlobal(Direction.from3DDataValue(face));
            if(side==facing.getOpposite())throw new IllegalArgumentException("unsupported side");
            sides.add(side);
        }else{
            sides.add(facing);
            for(Direction side:Direction.values())if(side!=facing&&side!=facing.getOpposite())sides.add(side);
        }
        boolean sneaky=args.isBoolean(2)&&args.checkBoolean(2);
        double duration=args.isDouble(3)?args.checkDouble(3):0;
        AgentPlayer player=agent.runtime().player();
        ServerLevel level=(ServerLevel)agent.world();
        BlockPos target=agent.blockPosition().relative(facing);
        if(!level.hasChunkAt(target)||!level.mayInteract(player,target))return new Object[]{false};
        boolean previous=player.usingSelectedMaterial;
        int previousSlot=player.materialSlot;
        player.usingSelectedMaterial=false;
        try(var drops=li.cil.oc.server.agent.AgentActionDrops.begin(agent,new AABB(agent.blockPosition()).inflate(Settings.get().useAndPlaceRange+2))){
            if(place&&selectedStack().isEmpty())return new Object[]{null,"nothing selected"};
            for(Direction side:sides){
                orientForInteraction(player,facing,side,sneaky);
                HitResult hit=pickForInteraction(level,player,facing,side);
                if(place){
                    boolean success=false;
                    if(hit.getType()==HitResult.Type.BLOCK)
                        success=placeFromInventory(player,(BlockHitResult)hit,false);
                    else if(hit.getType()==HitResult.Type.MISS&&canPlaceInAir()
                            &&closestInteractionEntity(level,player,facing)==null&&level.getBlockState(target).canBeReplaced())
                        success=placeFromInventory(player,interactionFace(target,facing),false);
                    if(success){delay(context,"place");return new Object[]{true};}
                    continue;
                }
                ItemStack before=player.getMainHandItem().copy();
                boolean usedEquipment=false;
                String action=null;
                try{
                    if(hit instanceof EntityHitResult entityHit){
                        usedEquipment=true;
                        Entity entity=entityHit.getEntity();
                        if(isEquippedItemAllowed(player)&&player.interactOn(entity,InteractionHand.MAIN_HAND).consumesAction()){
                            action="item_interacted";
                        }
                    }
                    if(action==null&&hit.getType()==HitResult.Type.BLOCK){
                        usedEquipment=true;
                        action=activateEquipped(context,player,(BlockHitResult)hit,facing,duration);
                    }else if(action==null){
                        if(canPlaceInAir()){
                            // The original use callback tries main inventory slot zero before the tool.
                            if(placeFromInventory(player,interactionFace(agent.blockPosition(),facing),true))action="item_placed";
                            else{
                                usedEquipment=true;
                                BlockPos itemPos=target.relative(side);
                                action=activateEquipped(context,player,interactionFace(itemPos,side.getOpposite()),facing,duration);
                            }
                        }
                        if(action==null){
                            usedEquipment=true;
                            if(useEquippedItem(context,player,facing,duration))action="item_used";
                        }
                    }
                }finally{if(usedEquipment)toolUsed(before,player.getMainHandItem());}
                if(action!=null){delay(context,"use");return new Object[]{true,action};}
            }
            return new Object[]{false};
        }finally{
            player.usingSelectedMaterial=previous;
            player.materialSlot=previousSlot;
            player.setShiftKeyDown(false);
        }
    }

    private void orientForInteraction(AgentPlayer player,Direction facing,Direction side,boolean sneaky){
        player.orient(facing,sneaky);
        Vec3 direction=Vec3.atLowerCornerOf(facing.getNormal()).add(Vec3.atLowerCornerOf(side.getNormal())).normalize();
        player.setYRot((float)Math.toDegrees(-Math.atan2(direction.x,direction.z)));
        player.setXRot((float)(Math.toDegrees(-Math.atan2(direction.y,Math.sqrt(direction.x*direction.x+direction.z*direction.z)))*.99));
    }

    private Entity closestInteractionEntity(ServerLevel level,AgentPlayer player,Direction facing){
        Vec3 center=new Vec3(agent.xPosition(),agent.yPosition(),agent.zPosition());
        return level.getEntities(player,new AABB(agent.blockPosition().relative(facing)),entity->entity!=agent&&!entity.isRemoved())
                .stream().min(Comparator.comparingDouble(entity->entity.position().distanceToSqr(center))).orElse(null);
    }

    private HitResult pickForInteraction(ServerLevel level,AgentPlayer player,Direction facing,Direction side){
        Vec3 center=new Vec3(agent.xPosition(),agent.yPosition(),agent.zPosition());
        Vec3 offset=Vec3.atLowerCornerOf(facing.getNormal());
        // Start just outside the host so its own block is never a placement reference.
        Vec3 origin=center.add(offset.scale(.50001));
        Vec3 end=center.add(offset.scale(1.01)).add(Vec3.atLowerCornerOf(side.getNormal()).scale(Settings.get().useAndPlaceRange));
        BlockHitResult hit=level.clip(new net.minecraft.world.level.ClipContext(origin,end,
                net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
        Entity entity=closestInteractionEntity(level,player,facing);
        if((entity instanceof LivingEntity||entity instanceof net.minecraft.world.entity.vehicle.AbstractMinecart
                ||entity instanceof li.cil.oc.api.internal.Drone)
                &&(hit.getType()==HitResult.Type.MISS||center.distanceToSqr(hit.getLocation())>center.distanceToSqr(entity.position())))
            return new EntityHitResult(entity);
        return hit;
    }

    private boolean canPlaceInAir(){
        var event=new RobotPlaceInAirEvent(agent);
        NeoForge.EVENT_BUS.post(event);
        return event.isAllowed();
    }

    private static BlockHitResult interactionFace(BlockPos pos,Direction face){
        return new BlockHitResult(Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5)),face,pos,false);
    }

    private boolean rightClickAllowed(AgentPlayer player,BlockHitResult hit){
        var level=agent.world();
        if(!level.hasChunkAt(hit.getBlockPos())||!level.mayInteract(player,hit.getBlockPos()))return false;
        var event=net.neoforged.neoforge.common.CommonHooks.onRightClickBlock(player,InteractionHand.MAIN_HAND,hit.getBlockPos(),hit);
        return !event.isCanceled()&&!event.getUseBlock().isFalse()&&!event.getUseItem().isFalse();
    }

    private boolean placeFromInventory(AgentPlayer player,BlockHitResult hit,boolean firstSlot){
        boolean previous=player.usingSelectedMaterial;
        int previousSlot=player.materialSlot;
        player.usingSelectedMaterial=true;
        if(firstSlot)player.materialSlot=0;
        try{return rightClickAllowed(player,hit)&&placeHeldItem(player,hit);}
        finally{player.usingSelectedMaterial=previous;player.materialSlot=previousSlot;agent.inventoryChanged();}
    }

    private boolean placeHeldItem(AgentPlayer player,BlockHitResult hit){
        ItemStack stack=player.getMainHandItem();
        if(stack.isEmpty())return false;
        var event=new RobotPlaceBlockEvent.Pre(agent,stack,agent.world(),hit.getBlockPos());
        NeoForge.EVENT_BUS.post(event);
        if(event.isCanceled())return false;
        InteractionResult result=stack.useOn(new net.minecraft.world.item.context.UseOnContext(player,InteractionHand.MAIN_HAND,hit));
        if(!result.consumesAction())return false;
        NeoForge.EVENT_BUS.post(new RobotPlaceBlockEvent.Post(agent,stack,agent.world(),hit.getBlockPos()));
        return true;
    }

    private boolean isEquippedItemAllowed(AgentPlayer player){
        ItemStack stack=player.getMainHandItem();
        return stack.isEmpty()||(stack.getItem()!=net.minecraft.world.item.Items.LEAD
                &&(Settings.get().allowUseItemsWithDuration||stack.getUseDuration(player)<=0));
    }

    private String activateEquipped(Context context,AgentPlayer player,BlockHitResult hit,Direction facing,double duration){
        if(!rightClickAllowed(player,hit))return null;
        var level=agent.world();
        ItemStack stack=player.getMainHandItem();
        if(!stack.isEmpty()&&stack.onItemUseFirst(new net.minecraft.world.item.context.UseOnContext(player,InteractionHand.MAIN_HAND,hit)).consumesAction())
            return "item_used";
        var state=level.getBlockState(hit.getBlockPos());
        if(!state.isAir()&&Settings.get().allowActivateBlocks
                &&(!player.isShiftKeyDown()||stack.isEmpty()||stack.doesSneakBypassUse(level,hit.getBlockPos(),player))){
            ItemInteractionResult result=state.useItemOn(stack,level,player,InteractionHand.MAIN_HAND,hit);
            if(result.consumesAction())return "block_activated";
            if(result==ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION&&state.useWithoutItem(level,player,hit).consumesAction())
                return "block_activated";
        }
        if(duration<=Double.MIN_VALUE&&isEquippedItemAllowed(player)&&placeHeldItem(player,hit))return "item_placed";
        return useEquippedItem(context,player,facing,duration)?"item_used":null;
    }

    private boolean useEquippedItem(Context context,AgentPlayer player,Direction facing,double duration){
        if(!isEquippedItemAllowed(player)||player.getMainHandItem().isEmpty())return false;
        ItemStack before=player.getMainHandItem().copy();
        Vec3 previous=player.position();
        player.setPos(previous.x+facing.getStepX()*.5,previous.y,previous.z+facing.getStepZ()*.5);
        try{
            InteractionResult result=player.gameMode.useItem(player,agent.world(),player.getMainHandItem(),InteractionHand.MAIN_HAND);
            int ticks=Math.max(0,Math.min(before.getUseDuration(player),(int)(duration*20)));
            if(Settings.get().allowUseItemsWithDuration){
                for(int tick=0;tick<ticks&&player.isUsingItem();tick++)
                    player.getUseItem().getItem().onUseTick(agent.world(),player,player.getUseItem(),player.getUseItemRemainingTicks()-tick);
                if(player.isUsingItem())player.releaseUsingItem();
                if(ticks>0)context.pause(ticks/20d);
            }
            return result.consumesAction()&&!ItemStack.matches(before,player.getMainHandItem());
        }finally{player.setPos(previous.x,previous.y,previous.z);}
    }
    @Callback public Object[] tankCount(Context context, Arguments args) { return new Object[]{agent.tank().tankCount()}; }
    private int checkTank(Arguments args, int index) {
        int tank = args.checkInteger(index) - 1;
        if (tank < 0 || tank >= agent.tank().tankCount()) throw new IllegalArgumentException("invalid tank index");
        return tank;
    }
    private int selectedTank(Arguments args, int index) {
        return args.count() > index && args.checkAny(index) != null ? checkTank(args, index) : agent.selectedTank();
    }
    private IFluidTank getTank(int index) {
        return index < 0 || index >= agent.tank().tankCount() ? null : agent.tank().getFluidTank(index);
    }
    private static FluidStack fluid(IFluidTank tank) { return tank == null ? FluidStack.EMPTY : tank.getFluid(); }
    @Callback public Object[] selectTank(Context context, Arguments args) {
        if (args.count() > 0 && args.checkAny(0) != null) agent.setSelectedTank(checkTank(args, 0));
        return new Object[]{agent.selectedTank() + 1};
    }
    @Callback(direct = true) public Object[] tankLevel(Context context, Arguments args) {
        return new Object[]{fluid(getTank(selectedTank(args, 0))).getAmount()};
    }
    @Callback(direct = true) public Object[] tankSpace(Context context, Arguments args) {
        var tank = getTank(selectedTank(args, 0));
        return new Object[]{tank == null ? 0 : tank.getCapacity() - tank.getFluidAmount()};
    }
    @Callback public Object[] compareFluidTo(Context context, Arguments args) {
        return new Object[]{FluidStack.isSameFluidSameComponents(fluid(getTank(checkTank(args, 0))), fluid(getTank(agent.selectedTank())))};
    }
    @Callback public Object[] transferFluidTo(Context context, Arguments args) {
        int index = checkTank(args, 0), count = Math.max(0, args.optInteger(1, 1000));
        if (index == agent.selectedTank() || count == 0) return new Object[]{true};
        var from = getTank(agent.selectedTank()); var to = getTank(index);
        if (from == null || to == null) return new Object[]{null, "invalid index"};
        int moved = FluidUtils.transferBetweenFluidHandlers(new SelectedTank(from), new SelectedTank(to), count);
        if (moved > 0) { agent.markChanged(); return new Object[]{true}; }
        // The original exchanges two incompatible fluids when the complete contents fit.
        if (count >= from.getFluidAmount() && to.getCapacity() >= from.getFluidAmount()
                && from.getCapacity() >= to.getFluidAmount()) {
            FluidStack previous = to.drain(to.getFluidAmount(), IFluidHandler.FluidAction.EXECUTE);
            to.fill(from.drain(from.getFluidAmount(), IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
            from.fill(previous, IFluidHandler.FluidAction.EXECUTE);
            agent.markChanged(); return new Object[]{true};
        }
        return new Object[]{null, "incompatible or no fluid"};
    }
    @Callback public Object[] drain(Context context, Arguments args) { return fluidTransfer(args, true); }
    @Callback public Object[] fill(Context context, Arguments args) { return fluidTransfer(args, false); }
    private Object[] fluidTransfer(Arguments args, boolean drain) {
        Direction facing = side(args, 0);
        var tank = getTank(agent.selectedTank());
        if (tank == null) return new Object[]{null, "no tank selected"};
        int count = Math.max(0, args.optInteger(1, 1000));
        int amount = Math.min(count, drain ? tank.getCapacity() - tank.getFluidAmount() : tank.getFluidAmount());
        if (count > 0 && amount <= 0) return new Object[]{null, drain ? "tank is full" : "tank is empty"};
        var other = FluidUtils.fluidHandlerAt(agent.world(), agent.blockPosition().relative(facing), facing.getOpposite()).orElse(null);
        if (other == null) return new Object[]{null, drain ? "incompatible or no fluid" : "no space"};
        boolean hasFluid = !tank.getFluid().isEmpty();
        if (!drain && !hasFluid) return new Object[]{null, "tank is empty"};
        if (amount == 0) return new Object[]{hasFluid, 0};
        var own = new SelectedTank(tank);
        int moved = drain && hasFluid ? drainMatching(other, tank, amount)
                          : drain ? FluidUtils.transferBetweenFluidHandlers(other, own, amount)
                          : FluidUtils.transferBetweenFluidHandlers(own, other, amount);
        if (moved > 0) { agent.markChanged(); return new Object[]{true, moved}; }
        if (drain && !hasFluid) return new Object[]{false, 0};
        return new Object[]{null, "incompatible or no fluid"};
    }
    private static int drainMatching(IFluidHandler source, IFluidTank sink, int amount) {
        FluidStack available = source.drain(sink.getFluid().copyWithAmount(amount), IFluidHandler.FluidAction.SIMULATE);
        int accepted = sink.fill(available, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) return 0;
        FluidStack actual = source.drain(available.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
        int moved = sink.fill(actual, IFluidHandler.FluidAction.EXECUTE);
        if (moved < actual.getAmount()) source.fill(actual.copyWithAmount(actual.getAmount() - moved), IFluidHandler.FluidAction.EXECUTE);
        return moved;
    }
    @Callback public Object[] compareFluid(Context context, Arguments args) {
        Direction facing = side(args, 0);
        FluidStack selected = fluid(getTank(agent.selectedTank()));
        if (selected.isEmpty()) return new Object[]{false};
        var other = FluidUtils.fluidHandlerAt(agent.world(), agent.blockPosition().relative(facing), facing.getOpposite()).orElse(null);
        if (other == null) return new Object[]{false};
        if (args.count() > 1) {
            int index = args.checkInteger(1) - 1;
            if (index < 0 || index >= other.getTanks()) throw new IllegalArgumentException("invalid tank index");
            return new Object[]{FluidStack.isSameFluidSameComponents(selected, other.getFluidInTank(index))};
        }
        for (int index = 0; index < other.getTanks(); index++)
            if (FluidStack.isSameFluidSameComponents(selected, other.getFluidInTank(index))) return new Object[]{true};
        return new Object[]{false};
    }
    private record SelectedTank(IFluidTank tank) implements IFluidHandler {
        public int getTanks(){return 1;}public FluidStack getFluidInTank(int index){return index==0?tank.getFluid():FluidStack.EMPTY;}public int getTankCapacity(int index){return tank.getCapacity();}public boolean isFluidValid(int index,FluidStack stack){return index==0&&tank.isFluidValid(stack);}public int fill(FluidStack stack,FluidAction action){return tank.fill(stack,action);}public FluidStack drain(FluidStack stack,FluidAction action){return tank.drain(stack,action);}public FluidStack drain(int count,FluidAction action){return tank.drain(count,action);}
    }
}
