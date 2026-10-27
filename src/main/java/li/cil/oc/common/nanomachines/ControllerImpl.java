package li.cil.oc.common.nanomachines;

import li.cil.oc.Settings;
import li.cil.oc.api.nanomachines.*;
import li.cil.oc.api.network.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ControllerImpl implements Controller,WirelessEndpoint {
    public final Player player;
    public final NeuralNetwork configuration;
    public String uuid=UUID.randomUUID().toString();
    private int responsePort,commandDelay;
    private Runnable queuedCommand;
    private double energy=Settings.get().bufferNanomachines*.25;
    private boolean hadPower=true,activeDirty=true,sentConfiguration;
    private final Set<Behavior> active=new LinkedHashSet<>();
    private ResourceKey<Level> previousDimension;
    public ControllerImpl(Player player){this.player=player;configuration=new NeuralNetwork(this);previousDimension=player.level().dimension();if(!world().isClientSide)li.cil.oc.api.Network.joinWirelessNetwork(this);}
    public Level world(){return player.level();}
    public int x(){return player.blockPosition().getX();}
    public int y(){return player.blockPosition().getY();}
    public int z(){return player.blockPosition().getZ();}
    public synchronized double getLocalBuffer(){return energy;}
    public double getLocalBufferSize(){return Settings.get().bufferNanomachines;}
    public synchronized double changeBuffer(double delta){if(world().isClientSide)return delta;if(delta<0&&(Settings.get().ignorePower||player.getAbilities().instabuild))return 0;double next=energy+delta;energy=Math.max(0,Math.min(next,getLocalBufferSize()));return next-energy;}
    public void setClientEnergy(double value){energy=Math.max(0,Math.min(value,getLocalBufferSize()));}
    public Controller reconfigure(){if(!world().isClientSide){synchronized(configuration){configuration.reconfigure();activeDirty=true;}if(player instanceof ServerPlayer server&&server.connection!=null){player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,100));player.addEffect(new MobEffectInstance(MobEffects.POISON,150));player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,200));changeBuffer(-Settings.get().nanomachineReconfigureCost);sentConfiguration=false;}}return this;}
    public int getTotalInputCount(){synchronized(configuration){return configuration.triggers.size();}}
    public int getSafeActiveInputs(){return Settings.get().nanomachinesSafeInputsActive;}
    public int getMaxActiveInputs(){return Settings.get().nanomachinesMaxInputsActive;}
    public boolean getInput(int index){synchronized(configuration){return configuration.triggers.get(index).active;}}
    public boolean setInput(int index,boolean value){if(world().isClientSide)return false;synchronized(configuration){if(value&&configuration.triggers.stream().filter(node->node.active).count()>=getMaxActiveInputs())return false;configuration.triggers.get(index).active=value;activeDirty=true;return true;}}
    public int getInputCount(Behavior behavior){return configuration.inputs(behavior);}
    public Iterable<Behavior> getActiveBehaviors(){synchronized(configuration){clean(DisableReason.InputChanged);return List.copyOf(active);}}
    private void clean(DisableReason reason){if(!activeDirty)return;Set<Behavior> next=new LinkedHashSet<>();for(var node:configuration.behaviors)if(node.isActive())next.add(node.behavior);Set<Behavior> added=new LinkedHashSet<>(next),removed=new LinkedHashSet<>(active);added.removeAll(active);removed.removeAll(next);active.clear();active.addAll(next);activeDirty=false;for(Behavior behavior:added)behavior.onEnable();for(Behavior behavior:removed)behavior.onDisable(reason);if(!world().isClientSide)NanomachineNetwork.sendInputs(this);}
    public void reset(){synchronized(configuration){for(var trigger:configuration.triggers)trigger.active=false;activeDirty=true;clean(DisableReason.Default);}}
    public void dispose(){reset();if(!world().isClientSide)li.cil.oc.api.Network.leaveWirelessNetwork(this,previousDimension);}
    public void debug(){if(!world().isClientSide){configuration.debug();activeDirty=true;sentConfiguration=false;}}
    public void print(){if(!world().isClientSide)configuration.print(player);}
    public void update(){
        if(!player.isAlive())return;boolean server=!world().isClientSide;
        if(server){if(commandDelay>0&&--commandDelay==0){Runnable command=queuedCommand;queuedCommand=null;if(command!=null)command.run();}if(!world().dimension().equals(previousDimension)){li.cil.oc.api.Network.leaveWirelessNetwork(this,previousDimension);li.cil.oc.api.Network.joinWirelessNetwork(this);previousDimension=world().dimension();}else li.cil.oc.api.Network.updateWirelessNetwork(this);}
        boolean hasPower=getLocalBuffer()>0||Settings.get().ignorePower;int inputs;
        synchronized(configuration){inputs=(int)configuration.triggers.stream().filter(trigger->trigger.active).count();if(hasPower!=hadPower){clean(DisableReason.InputChanged);if(!hasPower){for(Behavior behavior:List.copyOf(active))behavior.onDisable(DisableReason.OutOfEnergy);hasPower=getLocalBuffer()>0||Settings.get().ignorePower;}else for(Behavior behavior:List.copyOf(active))behavior.onEnable();}if(hasPower){clean(DisableReason.InputChanged);for(Behavior behavior:List.copyOf(active))behavior.update();}}
        if(hasPower){if(server){if(world().getGameTime()%Settings.get().tickFrequency==0){changeBuffer(-Settings.get().nanomachineCost*Settings.get().tickFrequency*(inputs+.5));NanomachineNetwork.sendPower(this);}int overload=inputs-getSafeActiveInputs();if(!player.getAbilities().instabuild&&overload>0&&world().getGameTime()%20==0)player.hurt(NanoDamage.overload(player),overload);}else if(Settings.get().enableNanomachinePfx){double intensity=(getLocalBuffer()/(getLocalBufferSize()+1d)+inputs/(getTotalInputCount()+1d))*.25;Nanomachines.spawnParticle(player,net.minecraft.core.particles.ParticleTypes.PORTAL,intensity);}}
        if(server){if(hadPower!=hasPower)NanomachineNetwork.sendPower(this);if(!sentConfiguration||world().getGameTime()%(20*60)==0){sentConfiguration=true;NanomachineNetwork.sendConfiguration(player);}}hadPower=hasPower;
    }
    public void receivePacket(Packet packet,WirelessEndpoint sender){
        if(getLocalBuffer()<=0||commandDelay>0||!player.isAlive()||packet.data().length<1)return;
        double dx=sender.x()+.5-player.getX(),dy=sender.y()+.5-player.getY(),dz=sender.z()+.5-player.getZ();double range=commandRange();if(Math.sqrt(dx*dx+dy*dy+dz*dz)>range)return;
        if(!(packet.data()[0] instanceof byte[] header)||!"nanomachines".equals(new String(header,StandardCharsets.UTF_8)))return;
        Object[] data=Arrays.stream(packet.data()).map(value->value instanceof byte[] bytes?new String(bytes,StandardCharsets.UTF_8):value).toArray();if(data.length<2||!(data[1] instanceof String command))return;int expected=switch(command){case "setResponsePort","getInput"->3;case "setInput"->4;default->2;};if(data.length!=expected)return;
        try{switch(command){
            case "setResponsePort"->{if(data.length==3&&data[2] instanceof Number port){responsePort=Math.max(0,Math.min(0xffff,port.intValue()));respond("port",responsePort);}}
            case "getPowerState"->respond("power",getLocalBuffer(),getLocalBufferSize());
            case "saveConfiguration"->saveConfiguration();
            case "getHealth"->respond("health",player.getHealth(),player.getMaxHealth());
            case "getHunger"->respond("hunger",player.getFoodData().getFoodLevel(),player.getFoodData().getSaturationLevel());
            case "getAge"->respond("age",player.getNoActionTime()/20);
            case "getName"->respond("name",player.getDisplayName().getString());
            case "getExperience"->respond("experience",player.experienceLevel);
            case "getTotalInputCount"->respond("totalInputCount",getTotalInputCount());
            case "getSafeActiveInputs"->respond("safeActiveInputs",getSafeActiveInputs());
            case "getMaxActiveInputs"->respond("maxActiveInputs",getMaxActiveInputs());
            case "getInput"->{if(data.length==3&&data[2] instanceof Number index)respond("input",index.intValue(),getInput(index.intValue()-1));}
            case "setInput"->{if(data.length==4&&data[2] instanceof Number index&&data[3] instanceof Boolean value){if(setInput(index.intValue()-1,value))respond("input",index.intValue(),getInput(index.intValue()-1));else respond("input","too many active inputs");}}
            case "getActiveEffects"->{List<String> names=new ArrayList<>();for(Behavior behavior:getActiveBehaviors()){String name=behavior.getNameHint();if(name!=null&&!name.isEmpty())names.add(name.replace(',','_').replace('"','_'));}respond("effects","{"+String.join(",",names)+"}");}
            default->{}
        }}catch(RuntimeException error){if(command.equals("saveConfiguration"))respond("saved",false,"error");else if(command.equals("getInput")||command.equals("setInput"))respond("input","error");else throw error;}
    }
    private void saveConfiguration(){Item item=BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(li.cil.oc.OpenComputers.ID,"nanomachines"));for(int slot=0;slot<player.getInventory().items.size();slot++){ItemStack stack=player.getInventory().getItem(slot);if(stack.getItem()==item&&new NanomachineData(stack).configuration==null){ItemStack configured=player.getInventory().removeItem(slot,1);new NanomachineData(this).save(configured);if(!player.getInventory().add(configured)&&!configured.isEmpty())player.drop(configured,false);respond("saved",true);return;}}respond("saved",false,"no nanomachines");}
    private double commandRange(){double configured=Settings.get().nanomachinesCommandRange;return configured*configured;}
    private void respond(Object...values){queuedCommand=()->{if(responsePort>0){double range=commandRange(),cost=Settings.get().wirelessCostPerRange[1]*range;if(changeBuffer(-cost)>-.1){Object[] data=new Object[values.length+1];data[0]="nanomachines";System.arraycopy(values,0,data,1,values.length);li.cil.oc.api.Network.sendWirelessPacket(this,range,li.cil.oc.api.Network.newPacket(uuid,null,responsePort,data));}}};commandDelay=(int)(Settings.get().nanomachinesCommandDelay*20);if(commandDelay<=0){Runnable command=queuedCommand;queuedCommand=null;command.run();}}
    public void save(CompoundTag nbt){synchronized(configuration){nbt.putString("uuid",uuid);nbt.putInt("port",responsePort);nbt.putDouble("energy",energy);CompoundTag graph=new CompoundTag();configuration.save(graph);nbt.put("configuration",graph);}}
    public void load(CompoundTag nbt){synchronized(configuration){String address=nbt.getString("uuid");if(!address.isEmpty())uuid=address;responsePort=Math.max(0,Math.min(0xffff,nbt.getInt("port")));energy=Math.max(0,Math.min(nbt.getDouble("energy"),getLocalBufferSize()));configuration.load(nbt.getCompound("configuration"));activeDirty=true;}}
    public void applyInputs(byte[] enabled){synchronized(configuration){for(int index=0;index<configuration.triggers.size();index++)configuration.triggers.get(index).active=index<enabled.length&&enabled[index]!=0;activeDirty=true;}}
    public byte[] inputStates(){synchronized(configuration){byte[] result=new byte[configuration.triggers.size()];for(int index=0;index<result.length;index++)result[index]=(byte)(configuration.triggers.get(index).active?1:0);return result;}}
}
