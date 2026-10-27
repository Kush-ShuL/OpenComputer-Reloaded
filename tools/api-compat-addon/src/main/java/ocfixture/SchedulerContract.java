package ocfixture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import li.cil.oc.api.API;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.item.CallBudget;
import li.cil.oc.api.driver.item.HostAware;
import li.cil.oc.api.driver.item.Processor;
import li.cil.oc.api.driver.item.Slot;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.machine.Architecture;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.ExecutionResult;
import li.cil.oc.api.machine.LimitReachedException;
import li.cil.oc.api.machine.Machine;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A genuine external architecture exercises the scheduler of actual world Case machines. */
public final class SchedulerContract {
    private static final Logger LOGGER=LoggerFactory.getLogger("oc-scheduler");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,"oc_api_compat");
    private static final Supplier<Item> CPU=ITEMS.register("scheduler_cpu",()->new Item(new Item.Properties().stacksTo(1)));
    private static final Supplier<Item> BUDGET=ITEMS.register("scheduler_budget_card",()->new Item(new Item.Properties().stacksTo(1)));
    private static final Map<Machine,Script> SCRIPTS=new ConcurrentHashMap<>();
    private static Run active;
    private SchedulerContract(){}
    public static void register(IEventBus bus){ITEMS.register(bus);}
    public static void registerDrivers(){Driver.add(new CpuDriver());Driver.add(new BudgetDriver());}
    public static void start(ServerLevel level,BlockPos position,List<String> results){Run run=new Run(level,position,results);active=run;try{run.start();}catch(Throwable error){run.fail(error);active=null;run.cleanup();}}
    public static boolean isRunning(){return active!=null;}
    public static void tick(){Run run=active;if(run==null)return;try{if(!run.tick())return;}catch(Throwable error){run.fail(error);}active=null;run.cleanup();}
    public static void close(){Run run=active;active=null;if(run!=null)run.cleanup();}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static final class CpuDriver implements Processor,CallBudget,HostAware {
        @Override public boolean worksWith(ItemStack stack){return stack.is(CPU.get());}
        @Override public boolean worksWith(ItemStack stack,Class<? extends EnvironmentHost> host){return worksWith(stack)&&Case.class.isAssignableFrom(host);}
        @Override public String slot(ItemStack stack){return Slot.CPU;}
        @Override public int tier(ItemStack stack){return 0;}
        @Override public int supportedComponents(ItemStack stack){return 32;}
        @Override public double getCallBudget(ItemStack stack){return .5;}
        @Override public Class<? extends Architecture> architecture(ItemStack stack){return SchedulerArchitecture.class;}
        @Override public ManagedEnvironment createEnvironment(ItemStack stack,EnvironmentHost host){return null;}
        @Override public CompoundTag dataTag(ItemStack stack){return new CompoundTag();}
    }
    private static final class BudgetDriver implements CallBudget,HostAware {
        @Override public boolean worksWith(ItemStack stack){return stack.is(BUDGET.get());}
        @Override public boolean worksWith(ItemStack stack,Class<? extends EnvironmentHost> host){return worksWith(stack)&&Case.class.isAssignableFrom(host);}
        @Override public String slot(ItemStack stack){return Slot.Card;}
        @Override public int tier(ItemStack stack){return 0;}
        @Override public double getCallBudget(ItemStack stack){return 1.5;}
        @Override public ManagedEnvironment createEnvironment(ItemStack stack,EnvironmentHost host){return null;}
        @Override public CompoundTag dataTag(ItemStack stack){return new CompoundTag();}
    }
    private static final class Probe extends AbstractManagedEnvironment {
        final Script script;
        Probe(Script script){this.script=script;setNode(Network.newNode(this,Visibility.Network).withComponent("scheduler_probe").create());}
        @Callback(direct=true,limit=1) public Object[] limited(Context context,Arguments args){script.callbacks.incrementAndGet();context.consumeCallBudget(.25);return new Object[]{true};}
        @Override public void onMessage(Message message){if(message.name().equals("computer.started"))script.started.incrementAndGet();if(message.name().equals("computer.stopped"))script.stopped.incrementAndGet();}
    }
    private static final class Script {
        final String scenario;final ServerLevel level;final Machine machine;
        final AtomicInteger boots=new AtomicInteger(),closes=new AtomicInteger(),callbacks=new AtomicInteger(),started=new AtomicInteger(),stopped=new AtomicInteger(),notified=new AtomicInteger();
        final CountDownLatch release=new CountDownLatch(1);
        volatile Probe probe;volatile boolean waiting,queueSent,completed,entered,handled,expectedSyncThrown;volatile int step,queueLength;volatile long synchronizedTick;volatile Throwable failure;
        Script(String scenario,ServerLevel level,Machine machine){this.scenario=scenario;this.level=level;this.machine=machine;}
    }
    @Architecture.Name("Scheduler SDK contract")
    @Architecture.NoMemoryRequirements
    public static final class SchedulerArchitecture implements Architecture {
        private final Script script;private volatile boolean initialized;
        public SchedulerArchitecture(Machine machine){script=SCRIPTS.get(machine);require(script!=null,"Architecture was not created for its real registered fixture Case");}
        @Override public boolean isInitialized(){return initialized;}
        @Override public boolean recomputeMemory(Iterable<ItemStack> components){return true;}
        @Override public boolean initialize(){initialized=true;script.boots.incrementAndGet();return true;}
        @Override public void close(){initialized=false;script.closes.incrementAndGet();}
        @Override public void onConnect(){}
        @Override public void onSignal(){script.notified.incrementAndGet();}
        @Override public void load(CompoundTag tag){}
        @Override public void save(CompoundTag tag){}
        @Override public void runSynchronized(){
            try{
                require(script.level.getServer().isSameThread(),"Synchronized architecture ran off the server thread");
                if(script.scenario.equals("sync_oom")){script.expectedSyncThrown=true;throw new java.lang.Error("not enough memory");}
                if(script.scenario.equals("sync_throw")){script.expectedSyncThrown=true;throw new IllegalStateException("fixture synchronous failure");}
                require(script.scenario.equals("flow")&&script.step==1,"Unexpected synchronized execution");
                script.machine.consumeCallBudget(Double.POSITIVE_INFINITY);
                require(Boolean.TRUE.equals(script.machine.invoke(script.probe.node().address(),"limited",new Object[0])[0]),"Synchronized callback lost its result");
                require(script.callbacks.get()==1,"Limited direct callback was not executed exactly once by the synchronized path");
                require(script.machine.pause(.1)&&script.machine.isPaused(),"Synchronized pause did not take effect");
                script.synchronizedTick=script.level.getGameTime();script.step=2;
            }catch(java.lang.Error|RuntimeException error){throw error;}catch(Exception error){throw new IllegalStateException(error);}
        }
        @Override public ExecutionResult runThreaded(boolean synchronizedReturn){
            try{return threaded(synchronizedReturn);}catch(Throwable error){
                if(!(script.scenario.equals("thread_throw")&&error instanceof IllegalStateException))script.failure=error;
                if(error instanceof java.lang.Error fatal)throw fatal;
                if(error instanceof RuntimeException runtime)throw runtime;
                throw new IllegalStateException(error);
            }
        }
        private ExecutionResult threaded(boolean synchronizedReturn)throws Exception {
            require(!script.level.getServer().isSameThread()&&Thread.currentThread().isDaemon(),"Architecture did not execute on a background daemon worker");
            int configured=API.config.getInt("misc.threadPriority");int expected=configured<Thread.MIN_PRIORITY?Thread.MIN_PRIORITY+(Thread.NORM_PRIORITY-Thread.MIN_PRIORITY)/2:Math.clamp(configured,Thread.MIN_PRIORITY,Thread.MAX_PRIORITY);
            require(Thread.currentThread().getPriority()==expected,"Computer worker did not use the configured original thread priority");
            switch(script.scenario){
                case "external_stop","external_restart":{
                    if(script.boots.get()==1){script.entered=true;require(script.release.await(3,TimeUnit.SECONDS),"Server-side start/stop blocked on the executing architecture");return new ExecutionResult.Sleep(100);}
                    require(script.scenario.equals("external_restart")&&script.boots.get()==2&&script.started.get()==2&&script.stopped.get()>=1,"External restart lost its real initialization/network events");script.completed=true;return new ExecutionResult.Shutdown(false);
                }
                case "thread_throw":throw new IllegalStateException("fixture threaded failure");
                case "sync_oom","sync_throw":return new ExecutionResult.SynchronizedCall();
                case "null_result":return null;
                case "error_result":return new ExecutionResult.Error("scheduler-error");
                default:break;
            }
            if(script.step==0){
                boolean limited=false;try{script.machine.consumeCallBudget(1.01);}catch(Exception expectedLimit){require(expectedLimit instanceof LimitReachedException,"Unexpected direct budget error: "+expectedLimit);limited=true;}
                require(limited,"CPU/card budgets were summed instead of averaged to one");
                script.machine.consumeCallBudget(-100);script.machine.consumeCallBudget(1);
                limited=false;try{script.machine.invoke(script.probe.node().address(),"limited",new Object[0]);}catch(LimitReachedException expectedLimit){limited=true;}
                require(limited&&script.callbacks.get()==0,"Exhausted direct-call budget entered the callback body");script.step=1;return new ExecutionResult.SynchronizedCall();
            }
            if(script.step==2){
                require(synchronizedReturn&&script.level.getGameTime()-script.synchronizedTick>=2,"Paused synchronized return resumed too early or with the wrong flag");
                script.step=3;script.waiting=true;return new ExecutionResult.Sleep(100);
            }
            if(script.step==3){
                require(!synchronizedReturn&&script.queueSent,"Sleeping architecture resumed without its queued signal");
                for(int index=0;index<script.queueLength;index++){var signal=script.machine.popSignal();require(signal!=null&&signal.name().equals("scheduler_queue_"+index)&&((Number)signal.args()[0]).intValue()==index,"Signal queue lost FIFO order or values at "+index);}
                require(script.machine.popSignal()==null&&script.notified.get()>=script.queueLength,"Signal queue length/onSignal callbacks changed");
                script.step=4;return new ExecutionResult.Shutdown(true);
            }
            require(script.step==4&&!synchronizedReturn&&script.boots.get()==2&&script.closes.get()>=1,"Requested reboot did not close and initialize the same actual architecture");
            require(script.started.get()==2&&script.stopped.get()>=1,"Reboot omitted actual computer.started/stopped network events");
            script.completed=true;return new ExecutionResult.Shutdown(false);
        }
    }
    private static final class Run {
        final ServerLevel level;final BlockPos position;final List<String> results;final List<Case> hosts=new ArrayList<>();final List<Script> scripts=new ArrayList<>();
        final Map<BlockPos,BlockState> restored=new LinkedHashMap<>();final Set<Long> forced=new LinkedHashSet<>();int ticks;boolean started;
        Run(ServerLevel level,BlockPos position,List<String> results){this.level=level;this.position=position;this.results=results;}
        void insert(Case host,ItemStack stack){for(int slot=0;slot<host.getContainerSize();slot++)if(host.getItem(slot).isEmpty()&&host.canPlaceItem(slot,stack)){host.setItem(slot,stack);return;}throw new AssertionError("Real Case rejected registered scheduler hardware");}
        void start(){int index=0;for(String scenario:List.of("flow","thread_throw","sync_oom","sync_throw","null_result","error_result","external_stop","external_restart")){
            BlockPos pos=position.east(3*index++);if(level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true))forced.add(ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4));level.getChunkAt(pos);
            require(level.isEmptyBlock(pos)&&level.getBlockEntity(pos)==null,"Scheduler fixture cell is occupied");restored.put(pos,level.getBlockState(pos));
            require(level.setBlock(pos,li.cil.oc.api.Items.get("case3").block().defaultBlockState(),3)&&level.getBlockEntity(pos) instanceof Case,"Cannot place actual scheduler Case");
            Case host=(Case)level.getBlockEntity(pos);hosts.add(host);Script script=new Script(scenario,level,host.machine());scripts.add(script);SCRIPTS.put(host.machine(),script);
            insert(host,new ItemStack(CPU.get()));insert(host,new ItemStack(BUDGET.get()));script.probe=new Probe(script);host.machine().node().connect(script.probe.node());
        }}
        boolean tick(){require(++ticks<=400,"Scheduler contract exceeded 400 actual ticks");if(ticks<3)return false;
            if(!started){for(Case host:hosts){((li.cil.oc.api.network.Connector)host.machine().node()).changeBuffer(Double.POSITIVE_INFINITY);require(host.machine().start(),"Real scheduler Case failed to start: "+host.machine().lastError());}started=true;return false;}
            boolean complete=true;
            for(Script script:scripts){require(script.failure==null,"Architecture assertion failed for "+script.scenario+": "+script.failure);
                if(script.entered&&!script.handled){
                    long before=System.nanoTime();require(!script.machine.start(),"Starting an already executing machine did not return false");require(script.machine.stop()&&!script.machine.stop(),"External stop did not retain its original idempotent result");require(!script.machine.signal("after_stop"),"Stopping machine still accepted signals");
                    if(script.scenario.equals("external_restart"))require(script.machine.start(),"Start while stopping did not request a restart");
                    require(System.nanoTime()-before<TimeUnit.MILLISECONDS.toNanos(250),"Start/stop waited on the running worker");script.handled=true;script.release.countDown();
                }
                if(script.waiting&&!script.queueSent){require(script.machine.pause(.05),"Could not pause the actual sleeping architecture for its queue input");while(script.machine.popSignal()!=null){}int maximum=API.config.getInt("computer.maxSignalQueueSize");script.queueLength=maximum;
                    // Queue input while publicly paused, then resume on the following normal tick.
                    for(int index=0;index<maximum;index++)require(script.machine.signal("scheduler_queue_"+index,index),"Real signal queue rejected item "+index);
                    require(!script.machine.signal("scheduler_overflow"),"Real machine accepted a signal beyond its configured queue capacity");script.queueSent=true;require(script.machine.start(),"Public start did not resume the actual paused architecture");
                }
                if(script.machine.isRunning()||script.closes.get()==0||(script.scenario.equals("flow")||script.scenario.equals("external_restart"))&&script.closes.get()<2){complete=false;continue;}
                String expected=switch(script.scenario){case "flow","external_stop","external_restart"->null;case "sync_oom"->"gui.Error.OutOfMemory";case "error_result"->"scheduler-error";default->"gui.Error.InternalError";};
                require(java.util.Objects.equals(script.machine.lastError(),expected),"Unexpected final error for "+script.scenario+": "+script.machine.lastError());
                require(!(script.scenario.equals("sync_oom")||script.scenario.equals("sync_throw"))||script.expectedSyncThrown,"Synchronized error case never reached its deliberate fault on the actual server thread");
                require(!(script.scenario.equals("flow")||script.scenario.equals("external_restart"))||script.completed,"Scheduler flow stopped before its state transitions completed");
            }
            if(!complete)return false;
            results.add("PASS scheduler.flow actual Case plus external Processor/Architecture averages CPU/card call budgets, rejects exhausted direct calls, runs synchronized callbacks without direct budget, pauses/resumes with the synchronized-return flag, wakes sleep from a bounded FIFO signal queue, then reboots and stops with real network lifecycle events");
            results.add("PASS scheduler.faults actual Cases close after threaded/synchronized exceptions, original native-style memory Error, malformed null execution result and ordinary execution Error; workers are configured-priority daemon threads");
            results.add("PASS scheduler.external main-thread start on an executing Case returns false promptly; stop rejects later signals and a following start requests restart while the actual worker is still executing, without waiting for its monitor");
            LOGGER.info("SCHEDULER PASS actual world Cases and external architecture transitions/faults");return true;
        }
        void fail(Throwable error){results.add("FAIL scheduler "+error);LOGGER.error("SCHEDULER failed",error);}
        void cleanup(){for(Script script:scripts){script.release.countDown();script.machine.stop();if(script.probe!=null)script.probe.node().remove();}var entries=new ArrayList<>(restored.entrySet());Collections.reverse(entries);for(var entry:entries){if(level.getBlockEntity(entry.getKey()) instanceof Container inventory)inventory.clearContent();level.setBlock(entry.getKey(),entry.getValue(),3);}for(Script script:scripts)SCRIPTS.remove(script.machine);for(long chunk:forced)level.setChunkForced(ChunkPos.getX(chunk),ChunkPos.getZ(chunk),false);}
    }
}
