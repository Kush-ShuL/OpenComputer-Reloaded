package li.cil.oc.server.machine;

import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.driver.item.CallBudget;
import li.cil.oc.api.driver.item.Processor;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.*;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import li.cil.oc.common.SaveHandler;
import li.cil.oc.server.PacketSender;
import li.cil.oc.server.driver.Registry;
import li.cil.oc.util.SideTracker;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import java.util.*;
import java.util.concurrent.*;

/** The original OC computer scheduler and state machine, rewritten in Java. */
public final class Machine extends AbstractManagedEnvironment implements li.cil.oc.api.machine.Machine,Runnable,DeviceInfo,li.cil.oc.server.network.Network.ComponentListener {
    // Ordinals are part of the original saved state format.
    public enum State {Stopped,Starting,Restarting,Stopping,Paused,SynchronizedCall,SynchronizedReturn,Yielded,Sleeping,Running}
    public final MachineHost host;
    private final Object stateLock=new Object();
    private final ArrayDeque<State> states=new ArrayDeque<>();
    private final Map<String,String> components=Collections.synchronizedMap(new LinkedHashMap<>());
    private final Set<Component> added=Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<String> users=new LinkedHashSet<>();
    private final ArrayDeque<QueuedSignal> signals=new ArrayDeque<>();
    private final ComponentConnector connector;
    private final ManagedEnvironment tmp;
    private Architecture architecture;
    private int maxComponents;
    private boolean hasMemory,inSynchronizedCall;
    private volatile double callBudget;
    private double maxCallBudget=1,cost=Settings.get().computerCost*Settings.get().tickFrequency;
    private long worldTime,uptime,cpuTotal,cpuStart;
    private int remainIdle,remainingPause;
    private String error;
    private static volatile ScheduledExecutorService workers;
    private static synchronized ScheduledExecutorService workers(){
        if(workers==null||workers.isShutdown())workers=Executors.newScheduledThreadPool(Settings.get().threads,r->li.cil.oc.util.ThreadPoolFactory.daemon(SideTracker.serverTask(r),"OpenComputers-Computer"));return workers;
    }
    public static synchronized void shutdownWorkers(){if(workers!=null){workers.shutdownNow();workers=null;}}
    public Machine(MachineHost host){
        this.host=Objects.requireNonNull(host);states.push(State.Stopped);
        connector=Network.newNode(this,Visibility.Network).withComponent("computer",Visibility.Neighbors).withConnector(Settings.get().bufferComputer).create();setNode(connector);
        tmp=Settings.get().tmpSize>0?li.cil.oc.api.FileSystem.asManagedEnvironment(li.cil.oc.api.FileSystem.fromMemory(Settings.get().tmpSize*1024L),"tmpfs",null,null,5):null;
    }
    @Override public ComponentConnector node(){return connector;}
    @Override public MachineHost host(){return host;}
    @Override public Architecture architecture(){return architecture;}
    @Override public synchronized void onHostChanged(){
        maxComponents=0;double budgets=0;int budgetCount=0;Class<? extends Architecture> type=null;
        for(ItemStack stack:host.internalComponents()){
            var driver=Driver.driverFor(stack,host.getClass());
            if(driver instanceof Processor processor){maxComponents+=processor.supportedComponents(stack);if(type==null&&"cpu".equals(driver.slot(stack)))type=processor.architecture(stack);}
            if(driver instanceof CallBudget budget){budgets+=budget.getCallBudget(stack);budgetCount++;}
        }
        maxCallBudget=budgetCount==0?1:budgets/budgetCount;
        if(type==null){if(architecture!=null)architecture.close();architecture=null;}
        else if(architecture==null||architecture.getClass()!=type){
            if(architecture!=null)architecture.close();
            try{architecture=type.getConstructor(li.cil.oc.api.machine.Machine.class).newInstance(this);if(node().network()!=null)architecture.onConnect();}
            catch(ReflectiveOperationException exception){architecture=null;OpenComputers.LOGGER.error("Could not instantiate architecture {}",type.getName(),exception);}
        }
        hasMemory=architecture!=null&&architecture.recomputeMemory(host.internalComponents());
    }
    @Override public Map<String,String> components(){return components;}
    @Override public int componentCount(){synchronized(stateLock){double total=0;synchronized(components){for(String name:components.values())total+=name.equals("filesystem")?.25:1;}for(Component c:added)total+=c.name().equals("filesystem")?.25:1;return (int)(total-1);}}
    @Override public int maxComponents(){return maxComponents;}
    @Override public String tmpAddress(){return tmp==null?null:tmp.node().address();}
    @Override public String lastError(){return error;}
    @Override public long worldTime(){return worldTime;}
    @Override public double upTime(){if(uptime<0)uptime+=worldTime;return uptime/20d;}
    @Override public double cpuTime(){return (cpuTotal+(cpuStart==0?0:System.nanoTime()-cpuStart))/1e9;}
    @Override public void setCostPerTick(double cost){this.cost=cost*Settings.get().tickFrequency;}
    @Override public double getCostPerTick(){return cost/Settings.get().tickFrequency;}
    @Override public Map<String,String> getDeviceInfo(){return host instanceof DeviceInfo info?info.getDeviceInfo():null;}
    @Override public String[] users(){synchronized(users){return users.toArray(String[]::new);}}
    @Override public boolean canInteract(String name){
        if(!Settings.get().canComputersBeOwned)return true;
        synchronized(users){if(users.isEmpty()||users.contains(name))return true;}
        var server=ServerLifecycleHooks.getCurrentServer();if(server==null)return false;if(server.isSingleplayer())return true;
        var player=server.getPlayerList().getPlayerByName(name);return player!=null&&server.getPlayerList().isOp(player.getGameProfile());
    }
    @Override public void addUser(String name)throws Exception{
        synchronized(users){if(users.size()>=Settings.get().maxUsers)throw new Exception("too many users");if(users.contains(name))throw new Exception("user exists");if(name.length()>Settings.get().maxUsernameLength)throw new Exception("username too long");
            var server=ServerLifecycleHooks.getCurrentServer();if(server==null||server.getPlayerList().getPlayerByName(name)==null)throw new Exception("player must be online");users.add(name);}
        host.markChanged();
    }
    @Override public boolean removeUser(String name){synchronized(users){boolean changed=users.remove(name);if(changed)host.markChanged();return changed;}}
    private State state(){synchronized(stateLock){return states.peek();}}
    public boolean isExecuting(){synchronized(stateLock){return states.contains(State.Running);}}
    @Override public boolean isRunning(){State s=state();return s!=State.Stopped&&s!=State.Stopping;}
    @Override public boolean isPaused(){return state()==State.Paused&&remainingPause>0;}
    @Override public boolean start(){
        if(ArchitectureCallbackContext.isCallbackFor(this))synchronized(stateLock){
            // A callback belongs to an already active VM. It may request a
            // resume/restart, but cannot initialize a new VM inside itself.
            if(state()==State.Paused&&remainingPause>0){remainingPause=0;host.markChanged();return true;}
            if(state()==State.Stopping){switchTo(State.Restarting);return true;}
            return false;
        }
        synchronized(this){return startLocked();}
    }
    private boolean startLocked(){synchronized(stateLock){
        switch(states.peek()){
            case Stopped->{
                if(node().network()==null)return false;onHostChanged();processAdded();verifyComponents();
                if(!Settings.get().ignorePower&&node().globalBuffer()<cost){crash("gui.Error.NoEnergy");return false;}
                if(architecture==null||maxComponents==0){beep("-");crash("gui.Error.NoCPU");return false;}
                if(componentCount()>maxComponents){beep("-..");crash("gui.Error.ComponentOverflow");return false;}
                if(!hasMemory){beep("-.");crash("gui.Error.NoRAM");return false;}
                if(!initialize()){beep("--");return false;}
                switchTo(State.Starting);uptime=0;node().sendToReachable("computer.started");return true;
            }
            case Paused->{if(remainingPause<=0)return false;remainingPause=0;host.markChanged();return true;}
            case Stopping->{switchTo(State.Restarting);return true;}
            default->{return false;}
        }
    }}
    @Override public boolean pause(double seconds){
        int ticks=Math.max(0,(int)(seconds*20));
        // LuaJ runs direct callbacks on a separate Java coroutine thread while
        // the executor owns this monitor and waits for that coroutine. Such a
        // callback only requests a pause after its current task, as Context
        // promises; it must not wait for the executor's monitor.
        if(ArchitectureCallbackContext.isCallbackFor(this))synchronized(stateLock){return pauseForTicks(ticks);}
        synchronized(stateLock){if(state()==State.Stopping||state()==State.Stopped||state()==State.Paused&&ticks<=remainingPause)return false;}
        synchronized(this){synchronized(stateLock){return pauseForTicks(ticks);}}
    }
    private boolean pauseForTicks(int ticks){if(state()==State.Stopping||state()==State.Stopped||state()==State.Paused&&ticks<=remainingPause)return false;if(state()!=State.Paused)states.push(State.Paused);remainingPause=ticks;host.markChanged();return true;}
    @Override public boolean stop(){synchronized(stateLock){if(state()==State.Stopped||state()==State.Stopping)return false;states.push(State.Stopping);host.markChanged();return true;}}
    @Override public boolean crash(String message){error=message;synchronized(stateLock){boolean result=stop();if(state()==State.Stopping){states.clear();states.push(State.Stopping);}return result;}}
    @Override public void consumeCallBudget(double value){if(architecture!=null&&architecture.isInitialized()&&!inSynchronizedCall){value=Math.max(0,value);if(value>callBudget)Machine.<RuntimeException>throwUnchecked(new LimitReachedException());callBudget-=value;}}
    @SuppressWarnings("unchecked") private static <T extends Throwable> void throwUnchecked(Throwable exception) throws T { throw (T) exception; }
    @Override public void beep(short frequency,short duration){PacketSender.sendSound(host.world(),host.xPosition(),host.yPosition(),host.zPosition(),frequency,duration);}
    @Override public void beep(String pattern){PacketSender.sendSound(host.world(),host.xPosition(),host.yPosition(),host.zPosition(),pattern);}
    private Object signalArg(Object value){
        if(value==null||value instanceof Boolean||value instanceof String||value instanceof byte[]||value instanceof CompoundTag)return value;
        if(value instanceof Character c)return (int)c;if(value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long)return value;
        if(value instanceof Number n)return n.doubleValue();
        if(value instanceof Map<?,?> map){Map<Object,Object> copy=new LinkedHashMap<>();map.forEach((k,v)->{Object key=signalArg(k),entry=signalArg(v);if(key!=null&&entry!=null)copy.put(key,entry);});return copy;}
        OpenComputers.LOGGER.warn("Unsupported signal argument {}",value.getClass().getName());return null;
    }
    @Override public boolean signal(String name,Object... args){
        synchronized(stateLock){if(state()==State.Stopped||state()==State.Stopping)return false;synchronized(signals){if(signals.size()>=Settings.get().maxSignalQueueSize)return false;Object[] copy=args==null?new Object[0]:Arrays.stream(args).map(this::signalArg).toArray();signals.add(new QueuedSignal(name,copy));}}
        if(architecture!=null)architecture.onSignal();return true;
    }
    @Override public Signal popSignal(){synchronized(signals){QueuedSignal signal=signals.poll();return signal==null?null:new QueuedSignal(signal.name,Registry.convert(signal.args,host.world()==null?null:host.world().registryAccess()));}}
    private record QueuedSignal(String name,Object[] args) implements Signal {}
    @Override public Map<String,Callback> methods(Object value){Map<String,Callback> result=new LinkedHashMap<>();for(String name:Callbacks.methods(value))result.put(name,Callbacks.annotation(value,name));return result;}
    @Override public Object[] invoke(String address,String method,Object[] args)throws Exception{
        if(node().network()==null)throw new LimitReachedException();Node target=node().network().node(address);
        if(!(target instanceof Component component)||!(target==node()||component.canBeSeenFrom(node())))throw new IllegalArgumentException("no such component");
        Callback annotation=Callbacks.annotation(component.host(),method);if(annotation==null)throw new NoSuchMethodException(method);if(annotation.direct())consumeCallBudget(1d/annotation.limit());return component.invoke(method,this,args);
    }
    @Override public Object[] invoke(Value value,String method,Object[] args)throws Exception{Callback a=Callbacks.annotation(value,method);if(a==null)throw new NoSuchMethodException(method);if(a.direct())consumeCallBudget(1d/a.limit());return Registry.convert(Callbacks.invoke(value,method,this,args),host.world()==null?null:host.world().registryAccess());}
    @Callback public Object[] start(Context context,Arguments args){return new Object[]{!isPaused()&&start()};}
    @Callback public Object[] stop(Context context,Arguments args){return new Object[]{stop()};}
    @Callback(direct=true) public Object[] isRunning(Context context,Arguments args){return new Object[]{isRunning()};}
    @Callback public Object[] getProgramLocations(Context context,Arguments args){return new Object[]{ProgramLocations.getMappings(li.cil.oc.api.Machine.getArchitectureName(architecture.getClass()))};}
    @Callback public Object[] beep(Context context,Arguments args){
        if(args.count()==1&&args.isString(0))beep(args.checkString(0));
        else{
            int frequency=args.optInteger(0,440);
            if(frequency<20||frequency>2000)throw new IllegalArgumentException("invalid frequency, must be in [20, 2000]");
            int milliseconds=Math.max(50,Math.min(5000,(int)(args.optDouble(1,.1)*1000)));
            context.pause(milliseconds/1000d);beep((short)frequency,(short)milliseconds);
        }
        return null;
    }
    @Callback public Object[] getDeviceInfo(Context context,Arguments args){
        context.pause(1);
        Map<String,Object> devices=new LinkedHashMap<>();
        if(node().network()!=null)for(Node n:node().network().nodes()){
            boolean visible=n instanceof Component component?n==node()||component.canBeSeenFrom(node()):n.canBeReachedFrom(node());
            if(visible&&n.host() instanceof DeviceInfo info){Map<String,String> data=info.getDeviceInfo();if(data!=null)devices.put(n.address(),data);}
        }
        return new Object[]{devices};
    }
    @Override public boolean canUpdate(){return true;}
    @Override public void update(){
        if(state()==State.Stopped)return;processAdded();if(componentCount()>maxComponents){beep("-..");crash("gui.Error.ComponentOverflow");}
        if(host.world()==null)return;worldTime=host.world().getDayTime();uptime++;if(remainIdle>0)remainIdle--;callBudget=maxCallBudget;
        if(host.world().getGameTime()%Settings.get().tickFrequency==0){State s=state();if(s!=State.Paused&&s!=State.Restarting&&s!=State.Stopping&&s!=State.Stopped){double charge=cost;if(s==State.Sleeping&&remainIdle>0&&signals.isEmpty())charge*=Settings.get().sleepCostFactor;if(!node().tryChangeBuffer(-charge))crash("gui.Error.NoEnergy");}}
        switch(state()){
            case Starting->{verifyComponents();switchTo(State.Yielded);}
            case Restarting->{synchronized(this){close();if(Settings.get().eraseTmpOnReboot&&tmp!=null){tmp.node().remove();node().connect(tmp.node());}node().sendToReachable("computer.stopped");start();}}
            case Sleeping->{if(remainIdle<=0||!signals.isEmpty())switchTo(State.Yielded);}
            case Paused->{if(remainingPause>0)remainingPause--;else{synchronized(stateLock){verifyComponents();states.pop();switchTo(states.peek());}}}
            case SynchronizedCall->{
                switchTo(State.Running);try{inSynchronizedCall=true;architecture.runSynchronized();synchronized(stateLock){if(state()==State.Running)switchTo(State.SynchronizedReturn);else if(state()==State.Paused){states.pop();states.pop();states.push(State.SynchronizedReturn);states.push(State.Paused);}else if(state()==State.Stopping){states.clear();states.push(State.Stopping);}}}
                catch(Throwable exception){OpenComputers.LOGGER.error("Synchronized architecture failure",exception);crash(exception instanceof OutOfMemoryError?"gui.Error.OutOfMemory":"gui.Error.InternalError");}finally{inSynchronizedCall=false;}
            }
            default->{}
        }
        if(state()==State.Stopping)tryClose();
    }
    private State switchTo(State next){synchronized(stateLock){State previous=states.isEmpty()?null:states.pop();if(next==State.Stopping||next==State.Restarting)states.clear();states.push(next);if(next==State.Yielded||next==State.SynchronizedReturn){remainIdle=0;workers().schedule(this,Settings.get().executionDelay,TimeUnit.MILLISECONDS);}host.markChanged();return previous;}}
    private boolean initialize(){onHostChanged();if(architecture==null)return false;error=null;synchronized(signals){signals.clear();}if(node().network()!=null&&tmp!=null)node().connect(tmp.node());try{return architecture.initialize();}catch(Throwable exception){OpenComputers.LOGGER.error("Could not initialize computer",exception);close();return false;}}
    private synchronized void close(){synchronized(stateLock){if(states.size()==1&&states.peek()==State.Stopped)return;states.clear();states.push(State.Stopped);if(architecture!=null)architecture.close();synchronized(signals){signals.clear();}uptime=cpuTotal=cpuStart=0;remainIdle=0;}host.markChanged();}
    public synchronized boolean tryClose(){if(isExecuting())return false;close();if(tmp!=null){tmp.node().remove();if(node().network()!=null)node().connect(tmp.node());}if(node().network()!=null)node().sendToReachable("computer.stopped");return true;}
    @Override public synchronized void run(){
        boolean sync;synchronized(stateLock){State s=state();if(s!=State.Yielded&&s!=State.SynchronizedReturn)return;if(SideTracker.isGamePaused()){states.push(State.Paused);return;}sync=switchTo(State.Running)==State.SynchronizedReturn;}
        cpuStart=System.nanoTime();try{
            ExecutionResult result=architecture.runThreaded(sync);
            synchronized(stateLock){State top=state();boolean paused=top==State.Paused;if(top==State.Running||paused){if(paused){states.pop();states.pop();}
                if(result instanceof ExecutionResult.Sleep sleep){remainIdle=sleep.ticks;if(paused)states.push(State.Sleeping);else switchTo(signals.isEmpty()&&sleep.ticks>0?State.Sleeping:State.Yielded);}
                else if(result instanceof ExecutionResult.SynchronizedCall){if(paused)states.push(State.SynchronizedCall);else switchTo(State.SynchronizedCall);}
                else if(result instanceof ExecutionResult.Shutdown shutdown){State next=shutdown.reboot?State.Restarting:State.Stopping;if(paused)states.push(next);else switchTo(next);}
                else if(result instanceof ExecutionResult.Error failure)crash(Objects.requireNonNullElse(failure.message,"unknown error"));
                if(paused)states.push(State.Paused);
            }else if(top==State.Stopping){states.clear();states.push(State.Stopping);}}
        }catch(Throwable exception){OpenComputers.LOGGER.error("Architecture execution failure",exception);crash("gui.Error.InternalError");}finally{cpuTotal+=System.nanoTime()-cpuStart;cpuStart=0;}
    }
    @Override public void onMessage(Message message){
        Object[] data=message.data();if(message.name().equals("computer.signal")&&data.length>0&&data[0] instanceof String name){Object[] args=new Object[data.length];args[0]=message.source().address();System.arraycopy(data,1,args,1,data.length-1);signal(name,args);}
        else if(message.name().equals("computer.checked_signal")&&data.length>1&&data[0] instanceof Player player&&data[1] instanceof String name&&canInteract(player.getGameProfile().getName())){Object[] args=new Object[data.length-1];args[0]=message.source().address();System.arraycopy(data,2,args,1,data.length-2);signal(name,args);}
        else if(message.name().equals("computer.start")&&!isPaused())start();else if(message.name().equals("computer.stop"))stop();
    }
    @Override public void onConnect(Node node){if(node==node()){components.put(node.address(),node().name());if(tmp!=null)node.connect(tmp.node());if(architecture!=null)architecture.onConnect();}else if(node instanceof Component component)addComponent(component);host.onMachineConnect(node);}
    @Override public void onDisconnect(Node node){if(node==node()){close();if(tmp!=null)tmp.node().remove();}else if(node instanceof Component component)removeComponent(component);host.onMachineDisconnect(node);}
    @Override public void addComponent(Component component){synchronized(stateLock){if(!components.containsKey(component.address()))added.add(component);}}
    @Override public void removeComponent(Component component){synchronized(stateLock){if(components.remove(component.address())!=null)signal("component_removed",component.address(),component.name());added.remove(component);}}
    private void processAdded(){synchronized(stateLock){for(Component c:added)if(c.canBeSeenFrom(node())){components.put(c.address(),c.name());if(architecture!=null&&architecture.isInitialized())signal("component_added",c.address(),c.name());}added.clear();}}
    private void verifyComponents(){if(node().network()==null)return;synchronized(components){for(var it=components.entrySet().iterator();it.hasNext();){var entry=it.next();Node found=node().network().node(entry.getKey());if(!(found instanceof Component c)||!c.name().equals(entry.getValue())){signal("component_removed",entry.getKey(),entry.getValue());it.remove();}}}}
    @Override public synchronized void load(CompoundTag tag){synchronized(stateLock){
        close();super.load(tag);states.clear();int[] saved=tag.getIntArray("state");for(int i=saved.length-1;i>=0;i--)if(saved[i]>=0&&saved[i]<State.values().length)states.push(State.values()[saved[i]]);if(states.isEmpty())states.push(State.Stopped);
        synchronized(users){users.clear();for(Tag value:tag.getList("users",Tag.TAG_STRING))users.add(value.getAsString());}error=tag.contains("message")?tag.getString("message"):null;
        components.clear();for(Tag value:tag.getList("components",Tag.TAG_COMPOUND)){CompoundTag c=(CompoundTag)value;components.put(c.getString("address"),c.getString("name"));}
        if(tmp!=null){CompoundTag data=tag.contains("tmp")?tag.getCompound("tmp"):SaveHandler.loadNBT(tag,node().address()+"_tmp");tmp.load(data,host.world().registryAccess());}
        if(isRunning()&&initialize())try{architecture.load(tag,host.world().registryAccess());
            for(Tag value:tag.getList("signals",Tag.TAG_COMPOUND)){CompoundTag s=(CompoundTag)value;CompoundTag args=s.getCompound("args");Object[] values=new Object[args.getInt("length")];for(int i=0;i<values.length;i++)values[i]=readSignalValue(args.get("arg"+i));signals.add(new QueuedSignal(s.getString("name"),values));}
            uptime=tag.getLong("uptime");cpuTotal=tag.getLong("cpuTime");remainingPause=tag.getInt("remainingPause");if(state()!=State.Restarting)pause(Settings.get().startupDelay);
        }catch(Throwable exception){OpenComputers.LOGGER.error("Could not restore computer at {}",host.machinePosition(),exception);close();}else{onHostChanged();close();}
    }}
    @Override public synchronized void save(CompoundTag tag){synchronized(stateLock){
        if(isExecuting()||SaveHandler.isSavingForClients())return;pause(.05);super.save(tag);processAdded();tag.putIntArray("state",states.stream().mapToInt(Enum::ordinal).toArray());
        ListTag userTags=new ListTag();synchronized(users){for(String user:users)userTags.add(StringTag.valueOf(user));}tag.put("users",userTags);if(error!=null)tag.putString("message",error);
        ListTag componentTags=new ListTag();synchronized(components){components.forEach((address,name)->{CompoundTag c=new CompoundTag();c.putString("address",address);c.putString("name",name);componentTags.add(c);});}tag.put("components",componentTags);
        if(tmp!=null)SaveHandler.scheduleSave(host,tag,node().address()+"_tmp",data->tmp.save(data,host.world().registryAccess()));
        if(state()!=State.Stopped&&architecture!=null){architecture.save(tag,host.world().registryAccess());ListTag signalTags=new ListTag();synchronized(signals){for(QueuedSignal signal:signals){CompoundTag s=new CompoundTag(),args=new CompoundTag();s.putString("name",signal.name);args.putInt("length",signal.args.length);for(int i=0;i<signal.args.length;i++)args.put("arg"+i,writeSignalValue(signal.args[i]));s.put("args",args);signalTags.add(s);}}tag.put("signals",signalTags);tag.putLong("uptime",uptime);tag.putLong("cpuTime",cpuTotal);tag.putInt("remainingPause",remainingPause);}
    }}
    private static Tag writeSignalValue(Object value){
        if(value==null)return ByteTag.valueOf((byte)-1);if(value instanceof Boolean b)return ByteTag.valueOf(b);if(value instanceof Float||value instanceof Double)return DoubleTag.valueOf(((Number)value).doubleValue());if(value instanceof Number n)return LongTag.valueOf(n.longValue());if(value instanceof String s)return StringTag.valueOf(s);if(value instanceof byte[] b)return new ByteArrayTag(b);if(value instanceof CompoundTag c)return c.copy();if(value instanceof Map<?,?> map){ListTag list=new ListTag();map.forEach((k,v)->{list.add(StringTag.valueOf(k.toString()));list.add(StringTag.valueOf(v.toString()));});return list;}return ByteTag.valueOf((byte)-1);
    }
    private static Object readSignalValue(Tag value){
        if(value instanceof ByteTag b)return b.getAsByte()==-1?null:b.getAsByte()==1;if(value instanceof LongTag n)return n.getAsLong();if(value instanceof DoubleTag n)return n.getAsDouble();if(value instanceof StringTag s)return s.getAsString();if(value instanceof ByteArrayTag b)return b.getAsByteArray();if(value instanceof ListTag list){Map<String,String> map=new LinkedHashMap<>();for(int i=0;i+1<list.size();i+=2)map.put(list.getString(i),list.getString(i+1));return map;}if(value instanceof CompoundTag tag)return tag.copy();return null;
    }
}
