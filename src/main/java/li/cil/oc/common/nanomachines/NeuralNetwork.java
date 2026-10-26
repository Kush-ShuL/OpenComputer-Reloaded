package li.cil.oc.common.nanomachines;

import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.api.Persistable;
import li.cil.oc.api.nanomachines.*;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import java.util.*;

/** The original two-layer randomized trigger/connector/behavior network. */
public final class NeuralNetwork implements Persistable {
    final ControllerImpl controller;
    public final List<TriggerNeuron> triggers=new ArrayList<>();
    public final List<ConnectorNeuron> connectors=new ArrayList<>();
    public final List<BehaviorNeuron> behaviors=new ArrayList<>();
    private final Map<Behavior,BehaviorNeuron> behaviorMap=new LinkedHashMap<>();
    NeuralNetwork(ControllerImpl controller){this.controller=controller;}
    public synchronized int inputs(Behavior behavior){BehaviorNeuron node=behaviorMap.get(behavior);return node==null?0:(int)node.inputs.stream().filter(Neuron::isActive).count();}
    private void rebuildBehaviors(){behaviors.clear();for(BehaviorProvider provider:Nanomachines.INSTANCE.getProviders()){Iterable<Behavior> provided=provider.createBehaviors(controller.player);if(provided!=null)for(Behavior behavior:provided)if(behavior!=null)behaviors.add(new BehaviorNeuron(provider,behavior));}}
    public synchronized void reconfigure(){
        rebuildBehaviors();int triggerCount=(int)Math.ceil(behaviors.size()*Settings.get().nanomachineTriggerQuota),connectorCount=(int)Math.ceil(behaviors.size()*Settings.get().nanomachineConnectorQuota);
        while(triggers.size()>triggerCount)triggers.removeLast();for(TriggerNeuron trigger:triggers)trigger.active=false;while(triggers.size()<triggerCount)triggers.add(new TriggerNeuron());
        while(connectors.size()>connectorCount)connectors.removeLast();for(ConnectorNeuron connector:connectors)connector.inputs.clear();while(connectors.size()<connectorCount)connectors.add(new ConnectorNeuron());
        Random random=new Random(controller.player.level().random.nextInt());List<Neuron> sources=new ArrayList<>();for(int index=0;index<Settings.get().nanomachineMaxOutputs;index++)sources.addAll(triggers);connect(connectors,sources,random);for(int index=0;index<Settings.get().nanomachineMaxOutputs;index++)sources.addAll(connectors);connect(behaviors,sources,random);
        Set<ConnectorNeuron> dead=new HashSet<>();for(ConnectorNeuron connector:connectors)if(connector.inputs.isEmpty())dead.add(connector);connectors.removeAll(dead);for(BehaviorNeuron behavior:behaviors)behavior.inputs.removeAll(dead);behaviors.removeIf(behavior->behavior.inputs.isEmpty());indexBehaviors();
    }
    private static void connect(List<? extends ConnectorNeuron> sinks,List<Neuron> sources,Random random){List<ConnectorNeuron> shuffled=new ArrayList<>(sinks);Collections.shuffle(shuffled,random);for(ConnectorNeuron sink:shuffled){if(sources.isEmpty())break;Set<Neuron> blacklist=new HashSet<>();int count=1+random.nextInt(Settings.get().nanomachineMaxInputs);for(int n=0;n<count&&!sources.isEmpty();n++){int base=random.nextInt(sources.size()),selected=-1;for(int index=0;index<sources.size();index++){int slot=(base+index)%sources.size();if(!blacklist.contains(sources.get(slot))){selected=slot;break;}}if(selected>=0){Neuron source=sources.remove(selected);blacklist.add(source);sink.inputs.add(source);}}}}
    private void indexBehaviors(){behaviorMap.clear();for(BehaviorNeuron behavior:behaviors)behaviorMap.put(behavior.behavior,behavior);}
    public synchronized void debug(){debugLog("Creating debug configuration for nanomachines in player "+controller.player.getDisplayName().getString()+".");rebuildBehaviors();connectors.clear();triggers.clear();for(int index=0;index<behaviors.size();index++){TriggerNeuron trigger=new TriggerNeuron();triggers.add(trigger);BehaviorNeuron behavior=behaviors.get(index);behavior.inputs.add(trigger);debugLog(index+" -> "+behavior.behavior.getNameHint()+" ("+behavior.behavior.getClass()+")");}indexBehaviors();}
    private void debugLog(String line){if(controller.player instanceof net.minecraft.server.level.ServerPlayer player)li.cil.oc.common.command.CommandNetworking.clientLog(player,line);else OpenComputers.LOGGER.info("{}",line);}
    public synchronized void print(Player player){for(BehaviorNeuron behavior:behaviors){String name=behavior.behavior.getNameHint();StringBuilder text=new StringBuilder();colored(text,name==null?behavior.behavior.getClass().getSimpleName():name,behavior.isActive());text.append(" <- (");boolean first=true;for(Neuron input:behavior.inputs){if(!first)text.append(", ");first=false;if(input instanceof TriggerNeuron trigger)colored(text,triggers.indexOf(trigger)+1,trigger.isActive());else if(input instanceof ConnectorNeuron connector){text.append('(');boolean initial=true;for(Neuron trigger:connector.inputs){if(!initial)text.append(", ");initial=false;colored(text,triggers.indexOf(trigger)+1,trigger.isActive());}text.append(')');}}text.append(')');player.sendSystemMessage(Component.literal(text.toString()));}}
    private static void colored(StringBuilder text,Object value,boolean active){text.append(active?ChatFormatting.GREEN:ChatFormatting.RED).append(value).append(ChatFormatting.RESET);}
    public synchronized void save(CompoundTag nbt){save(nbt,false);}
    public synchronized void save(CompoundTag nbt,boolean forItem){ListTag input=new ListTag();for(TriggerNeuron trigger:triggers){CompoundTag tag=new CompoundTag();tag.putBoolean("isActive",trigger.active&&!forItem);input.add(tag);}nbt.put("triggers",input);ListTag middle=new ListTag();for(ConnectorNeuron connector:connectors){CompoundTag tag=new CompoundTag();tag.putIntArray("triggerInputs",connector.inputs.stream().mapToInt(triggers::indexOf).filter(index->index>=0).toArray());middle.add(tag);}nbt.put("connectors",middle);ListTag output=new ListTag();for(BehaviorNeuron behavior:behaviors){CompoundTag tag=new CompoundTag();tag.putIntArray("triggerInputs",behavior.inputs.stream().mapToInt(triggers::indexOf).filter(index->index>=0).toArray());tag.putIntArray("connectorInputs",behavior.inputs.stream().mapToInt(connectors::indexOf).filter(index->index>=0).toArray());tag.put("behavior",behavior.provider.writeToNBT(behavior.behavior));output.add(tag);}nbt.put("behaviors",output);}
    public synchronized void load(CompoundTag nbt){triggers.clear();for(Tag raw:nbt.getList("triggers",Tag.TAG_COMPOUND)){TriggerNeuron trigger=new TriggerNeuron();trigger.active=((CompoundTag)raw).getBoolean("isActive");triggers.add(trigger);}connectors.clear();for(Tag raw:nbt.getList("connectors",Tag.TAG_COMPOUND)){ConnectorNeuron connector=new ConnectorNeuron();for(int index:((CompoundTag)raw).getIntArray("triggerInputs"))if(index>=0&&index<triggers.size())connector.inputs.add(triggers.get(index));connectors.add(connector);}behaviors.clear();for(Tag raw:nbt.getList("behaviors",Tag.TAG_COMPOUND)){CompoundTag tag=(CompoundTag)raw;for(BehaviorProvider provider:Nanomachines.INSTANCE.getProviders()){Behavior behavior;try{behavior=provider.readFromNBT(controller.player,tag.getCompound("behavior"));}catch(RuntimeException error){OpenComputers.LOGGER.warn("Failed restoring nanomachine behavior",error);continue;}if(behavior!=null){BehaviorNeuron node=new BehaviorNeuron(provider,behavior);for(int index:tag.getIntArray("triggerInputs"))if(index>=0&&index<triggers.size())node.inputs.add(triggers.get(index));for(int index:tag.getIntArray("connectorInputs"))if(index>=0&&index<connectors.size())node.inputs.add(connectors.get(index));behaviors.add(node);break;}}}indexBehaviors();}
    public interface Neuron{boolean isActive();}
    public static final class TriggerNeuron implements Neuron{public boolean active;public boolean isActive(){return active;}}
    public static class ConnectorNeuron implements Neuron{final List<Neuron> inputs=new ArrayList<>();public boolean isActive(){return inputs.stream().allMatch(Neuron::isActive);}}
    public static final class BehaviorNeuron extends ConnectorNeuron{public final BehaviorProvider provider;public final Behavior behavior;BehaviorNeuron(BehaviorProvider provider,Behavior behavior){this.provider=provider;this.behavior=behavior;}}
}
