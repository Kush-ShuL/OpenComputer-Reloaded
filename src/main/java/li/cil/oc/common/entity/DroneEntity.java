package li.cil.oc.common.entity;

import li.cil.oc.Settings;
import li.cil.oc.api.Driver;
import li.cil.oc.api.internal.MultiTank;
import li.cil.oc.api.network.*;
import li.cil.oc.common.item.data.DroneData;
import li.cil.oc.common.menu.AgentMenu;
import li.cil.oc.server.agent.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Original free-flying machine, with quarter-block targets and bounded acceleration. */
public final class DroneEntity extends Entity implements li.cil.oc.api.internal.Drone,AgentHost,MenuProvider,Analyzable {
    private static final EntityDataAccessor<Boolean> RUNNING=SynchedEntityData.defineId(DroneEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> TX=SynchedEntityData.defineId(DroneEntity.class,EntityDataSerializers.FLOAT),TY=SynchedEntityData.defineId(DroneEntity.class,EntityDataSerializers.FLOAT),TZ=SynchedEntityData.defineId(DroneEntity.class,EntityDataSerializers.FLOAT),ACCELERATION=SynchedEntityData.defineId(DroneEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIGHT=SynchedEntityData.defineId(DroneEntity.class,EntityDataSerializers.INT),ENERGY=SynchedEntityData.defineId(DroneEntity.class,EntityDataSerializers.INT),MAX_ENERGY=SynchedEntityData.defineId(DroneEntity.class,EntityDataSerializers.INT),CAPACITY=SynchedEntityData.defineId(DroneEntity.class,EntityDataSerializers.INT),SELECTED=SynchedEntityData.defineId(DroneEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> STATUS=SynchedEntityData.defineId(DroneEntity.class,EntityDataSerializers.STRING);
    public final DroneData info=new DroneData();
    private final AgentRuntime runtime=new AgentRuntime(this);
    public final AgentStorage inventory=new AgentStorage(8,()->entityData.get(CAPACITY),this::inventoryChanged);
    private final Container equipment=new SimpleContainer(0);
    private String owner=Settings.get().fakePlayerName;
    private UUID ownerId=UUID.fromString(Settings.get().fakePlayerUuid);
    private int selectedTank;
    private boolean loaded;
    public float bodyAngle;
    public final float[][] flapAngles=new float[4][2],targetFlapAngles=new float[4][2];
    private int nextFlapChange,nextAngularChange;
    private float angularVelocity;
    public DroneEntity(EntityType<? extends DroneEntity> type,Level level){super(type,level);blocksBuilding=true;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){builder.define(RUNNING,false);builder.define(TX,0f);builder.define(TY,0f);builder.define(TZ,0f);builder.define(ACCELERATION,0f);builder.define(LIGHT,0x66DD55);builder.define(ENERGY,0);builder.define(MAX_ENERGY,100);builder.define(CAPACITY,0);builder.define(SELECTED,0);builder.define(STATUS,"");}
    public void initializeAfterPlacement(ItemStack stack,Player player,Vec3 position){setPos(position);info.load(stack,level().registryAccess());owner=player instanceof AgentPlayer agent?agent.agent().ownerName():player.getGameProfile().getName();ownerId=player instanceof AgentPlayer agent?agent.agent().ownerUUID():AgentPlayer.determineUUID(player.getUUID());updateCapacity();runtime.initialize();if(runtime.control()!=null)runtime.control().node().changeBuffer(info.storedEnergy-runtime.control().node().localBuffer());setTarget(position);loaded=true;}
    public void preparePowerUp(){setTarget(new Vec3(Math.floor(getX())+.5,Math.round(getY())+.5,Math.floor(getZ())+.5));setAcceleration(.1f);runtime.initialize();}
    public boolean running(){return entityData.get(RUNNING);}
    public int energy(){return entityData.get(ENERGY);}
    public int energyCapacity(){return entityData.get(MAX_ENERGY);}
    public int inventoryCapacity(){return entityData.get(CAPACITY);}
    private void updateCapacity(){int capacity=0;for(ItemStack stack:info.components){var driver=Driver.driverFor(stack,getClass());if(driver instanceof li.cil.oc.api.driver.item.Inventory inventory)capacity+=inventory.inventoryCapacity(stack);}entityData.set(CAPACITY,Math.min(8,capacity));}
    public String statusText(){return entityData.get(STATUS);}
    public void setStatusText(String text){entityData.set(STATUS,text.lines().limit(2).map(line->line.substring(0,Math.min(10,line.length()))).reduce((a,b)->a+"\n"+b).orElse(""));}
    public int lightColor(){return entityData.get(LIGHT);}
    public void setLightColor(int color){entityData.set(LIGHT,color);}
    public float acceleration(){return entityData.get(ACCELERATION);}
    public void setAcceleration(float value){entityData.set(ACCELERATION,Math.max(0,Math.min(.1f,value)));}
    @Override public Vec3 getTarget(){return new Vec3(entityData.get(TX),entityData.get(TY),entityData.get(TZ));}
    @Override public void setTarget(Vec3 value){entityData.set(TX,Math.round((float)value.x*4)/4f);entityData.set(TY,Math.round((float)value.y*4)/4f);entityData.set(TZ,Math.round((float)value.z*4)/4f);}
    @Override public Vec3 getVelocity(){return getDeltaMovement();}
    @Override public void tick(){
        super.tick();
        if(!level().isClientSide){runtime.initialize();if(isInWaterOrBubble()||isInLava())machine().stop();runtime.tick();entityData.set(RUNNING,machine().isRunning());if(runtime.control()!=null){entityData.set(ENERGY,(int)Math.round(runtime.control().node().globalBuffer()));entityData.set(MAX_ENERGY,(int)runtime.control().node().globalBufferSize());}if(tier()==3&&runtime.control()!=null)runtime.control().node().changeBuffer(Double.POSITIVE_INFINITY);}
        else if(running()){
            if(--nextFlapChange<0){nextFlapChange=5+random.nextInt(10);for(int i=0;i<2;i++){int flap=random.nextInt(4);targetFlapAngles[flap][0]=(float)Math.toRadians(random.nextFloat()*4-2);targetFlapAngles[flap][1]=(float)Math.toRadians(random.nextFloat()*4-2);}}
            if(--nextAngularChange<0){if(angularVelocity!=0){angularVelocity=0;nextAngularChange=20;}else{angularVelocity=random.nextBoolean()?.1f:-.1f;nextAngularChange=100;}}
            for(int i=0;i<4;i++)for(int j=0;j<2;j++)flapAngles[i][j]=flapAngles[i][j]*.7f+targetFlapAngles[i][j]*.3f;bodyAngle+=angularVelocity;
        }
        Vec3 velocity=getDeltaMovement();
        if(running()){
            Vec3 offset=getTarget().subtract(position());double distance=offset.length();
            if(distance>0&&(distance>.005||velocity.lengthSqr()>.005)){Vec3 accelerated=velocity.add(offset.scale(Math.min(acceleration(),distance)/distance));velocity=new Vec3(Math.max(-.4,Math.min(.4,accelerated.x)),Math.max(-.4,Math.min(.4,accelerated.y)),Math.max(-.4,Math.min(.4,accelerated.z)));}
            else{velocity=Vec3.ZERO;setPos(getTarget());}
        }else velocity=velocity.add(0,-.05,0);
        setDeltaMovement(velocity);move(MoverType.SELF,velocity);
        double groundDrag=running()?.8:level().getBlockState(blockPosition().below()).getBlock().getFriction()*.8;
        setDeltaMovement(velocity.multiply(groundDrag,onGround()&&!running()?-.4:.8,groundDrag));
    }
    @Override public boolean isPickable(){return !isRemoved();}
    @Override public boolean isPushable(){return true;}
    @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float damage){if(source.getEntity()!=null&&running()){Vec3 direction=source.getEntity().getEyePosition().subtract(position()).normalize();if(!level().isClientSide){if(Settings.get().inputUsername)machine().signal("hit",direction.x,direction.z,direction.y,source.getEntity().getName().getString());else machine().signal("hit",direction.x,direction.z,direction.y);}setDeltaMovement(getDeltaMovement().subtract(direction).scale(.5));}return false;}
    @Override public InteractionResult interact(Player player,InteractionHand hand){if(isRemoved())return InteractionResult.PASS;if(!level().isClientSide&&canInteract(player)){if(player.isShiftKeyDown()){if(li.cil.oc.common.integration.WrenchTools.isWrench(player.getItemInHand(hand)))dropAndRemove();else if(!machine().isRunning()){preparePowerUp();machine().start();}}else player.openMenu(this,buffer->AgentMenu.writeOpen(this,buffer));}return InteractionResult.sidedSuccess(level().isClientSide);}
    public boolean canInteract(Player player){return !isRemoved()&&player.level()==level()&&player.distanceToSqr(this)<64&&(machine()==null||machine().canInteract(player.getGameProfile().getName()));}
    public void dropAndRemove(){if(level().isClientSide)return;runtime.close();info.storedEnergy=energy();var stack=info.createItemStack(level().registryAccess());spawnAtLocation(stack);for(int i=0;i<inventory.getContainerSize();i++){ItemStack held=inventory.removeItemNoUpdate(i);if(!held.isEmpty())spawnAtLocation(held);}discard();}
    @Override public void remove(RemovalReason reason){if(!level().isClientSide&&reason!=RemovalReason.CHANGED_DIMENSION)runtime.close();super.remove(reason);}
    @Override public Entity changeDimension(net.minecraft.world.level.portal.DimensionTransition transition){
        Vec3 offset=getTarget().subtract(position());
        Entity moved=super.changeDimension(transition);
        if(moved instanceof DroneEntity drone)drone.setTarget(drone.position().add(offset));
        // Entity copies the full machine state before removing its old incarnation.
        if(moved!=null&&moved!=this)runtime.close();
        return moved;
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag){info.load(tag.getCompound("info"),level().registryAccess());owner=tag.contains("owner")?tag.getString("owner"):owner;if(tag.hasUUID("ownerUuid"))ownerId=tag.getUUID("ownerUuid");updateCapacity();inventory.load(tag.getCompound("inventory"),level().registryAccess());CompoundTag runtimeTag=new CompoundTag();runtimeTag.put("oc:computer",tag.getCompound("machine"));runtimeTag.put("oc:robot",tag.getCompound("control"));runtime.load(runtimeTag);setTarget(new Vec3(tag.getFloat("targetX"),tag.getFloat("targetY"),tag.getFloat("targetZ")));setAcceleration(tag.getFloat("targetAcceleration"));setSelectedSlot(tag.getInt("selectedSlot"));selectedTank=tag.getInt("selectedTank");setStatusText(tag.getString("statusText"));setLightColor(tag.getInt("lightColor"));loaded=true;}
    @Override protected void addAdditionalSaveData(CompoundTag tag){if(level().isClientSide)return;CompoundTag runtimeTag=new CompoundTag();runtime.save(runtimeTag,level().registryAccess());info.storedEnergy=energy();CompoundTag infoTag=new CompoundTag(),inv=new CompoundTag();info.save(infoTag,level().registryAccess());inventory.save(inv,level().registryAccess());tag.put("info",infoTag);tag.put("inventory",inv);tag.put("machine",runtimeTag.getCompound("oc:computer"));tag.put("control",runtimeTag.getCompound("oc:robot"));tag.putFloat("targetX",(float)getTarget().x);tag.putFloat("targetY",(float)getTarget().y);tag.putFloat("targetZ",(float)getTarget().z);tag.putFloat("targetAcceleration",acceleration());tag.putInt("selectedSlot",selectedSlot());tag.putInt("selectedTank",selectedTank);tag.putString("statusText",statusText());tag.putInt("lightColor",lightColor());tag.putString("owner",owner);tag.putUUID("ownerUuid",ownerId);}
    @Override public AgentRuntime runtime(){return runtime;}
    @Override public boolean robot(){return false;}
    @Override public List<ComponentSlot> componentStacks(){List<ComponentSlot> result=new ArrayList<>();for(int i=0;i<info.components.length;i++)result.add(new ComponentSlot(i,info.components[i]));return result;}
    @Override public Iterable<ItemStack> internalComponents(){return Arrays.asList(info.components);}
    @Override public int componentSlot(String address){return runtime.componentSlot(address);}
    @Override public li.cil.oc.server.machine.Machine machine(){return runtime.machine();}
    @Override public Node node(){return machine()==null?null:machine().node();}
    @Override public void onConnect(Node node){}
    @Override public void onDisconnect(Node node){}
    @Override public void onMessage(Message message){}
    @Override public void onMachineConnect(Node node){runtime.connect();}
    @Override public void onMachineDisconnect(Node node){}
    @Override public Level world(){return level();}
    @Override public double xPosition(){return getX();}
    @Override public double yPosition(){return getY();}
    @Override public double zPosition(){return getZ();}
    @Override public void markChanged(){}
    @Override public void inventoryChanged(){if(machine()!=null)machine().signal("inventory_changed",selectedSlot()+1);}
    @Override public void synchronize(){}
    @Override public void animate(double seconds,int kind){}
    @Override public int tier(){return info.tier;}
    @Override public Direction facing(){return Direction.SOUTH;}
    @Override public Direction toGlobal(Direction side){return side;}
    @Override public Direction toLocal(Direction side){return side;}
    @Override public Container equipmentInventory(){return equipment;}
    @Override public Container mainInventory(){return inventory;}
    @Override public MultiTank tank(){return runtime;}
    @Override public int selectedSlot(){return entityData.get(SELECTED);}
    @Override public void setSelectedSlot(int slot){entityData.set(SELECTED,Math.max(0,Math.min(inventoryCapacity()-1,slot)));}
    @Override public int selectedTank(){return selectedTank;}
    @Override public void setSelectedTank(int tank){selectedTank=tank;}
    @Override public Player player(){return runtime.player().orient(Direction.SOUTH,false);}
    @Override public String name(){return info.name;}
    @Override public void setName(String name){info.name=name;}
    @Override public String ownerName(){return owner;}
    @Override public UUID ownerUUID(){return ownerId;}
    @Override public Node[] onAnalyze(Player player,Direction side,float x,float y,float z){return new Node[]{node()};}
    @Override public Component getDisplayName(){return Component.literal(info.name.isEmpty()?"Drone":info.name);}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player){return canInteract(player)?new AgentMenu(id,inventory,this):null;}
}
