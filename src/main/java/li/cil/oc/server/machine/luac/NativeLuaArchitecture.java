package li.cil.oc.server.machine.luac;

import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.api.driver.item.Memory;
import li.cil.oc.api.machine.*;
import li.cil.oc.common.SaveHandler;
import li.cil.repack.com.naef.jnlua.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import java.io.*;
import java.util.Arrays;

/** The upstream native Lua coroutine execution and ERIS persistence protocol. */
public abstract class NativeLuaArchitecture implements Architecture {
    final Machine machine;
    LuaState lua;
    int kernelMemory;
    double ramScale=1;
    private int syncState;
    private final PersistenceAPI persistence;
    private final NativeLuaAPI[] apis;
    protected NativeLuaArchitecture(Machine machine){this.machine=machine;persistence=new PersistenceAPI(this);apis=new NativeLuaAPI[]{new ComponentAPI(this),new ComputerAPI(this),new OSAPI(this),new SystemAPI(this),new UnicodeAPI(this),new UserdataAPI(this),persistence};}
    protected abstract LuaStateFactory factory();
    @FunctionalInterface interface Invocation{Object[] call()throws Exception;}
    @FunctionalInterface interface Documentation{String call()throws Exception;}
    int invoke(Invocation function){
        try{Object[] values=function.call();lua.pushBoolean(true);if(values==null)return 1;for(Object value:values)NativeValueBridge.push(lua,value);return values.length+1;}
        catch(Throwable error){
            if(Settings.get().logLuaCallbackErrors&&!(error instanceof LimitReachedException))OpenComputers.LOGGER.warn("Exception in Lua callback",error);
            if(error instanceof LimitReachedException)return 0;
            if(error instanceof IllegalArgumentException&&error.getMessage()!=null){lua.pushBoolean(false);lua.pushString(error.getMessage());return 2;}
            if(error.getMessage()!=null){lua.pushBoolean(true);lua.pushNil();lua.pushString(error.getMessage());if(Settings.get().logLuaCallbackErrors){StringWriter trace=new StringWriter();error.printStackTrace(new PrintWriter(trace));lua.pushString(trace.toString());return 4;}return 3;}
            boolean bad=error instanceof IndexOutOfBoundsException||error instanceof IllegalArgumentException||error instanceof NoSuchMethodException||error instanceof UnsupportedOperationException;
            String message=error instanceof IndexOutOfBoundsException?"index out of bounds":error instanceof IllegalArgumentException?"bad argument":error instanceof NoSuchMethodException?"no such method":error instanceof FileNotFoundException?"file not found":error instanceof SecurityException?"access denied":error instanceof IOException?"i/o error":error instanceof UnsupportedOperationException?"unsupported operation":"unknown error";
            lua.pushBoolean(!bad);if(!bad)lua.pushNil();lua.pushString(message);return bad?2:3;
        }
    }
    int documentation(Documentation query){try{String text=query.call();if(text==null||text.isEmpty())lua.pushNil();else lua.pushString(text);return 1;}catch(Throwable error){lua.pushNil();lua.pushString(error instanceof NoSuchMethodException?"no such method":error.getMessage()==null?error.toString():error.getMessage());return 2;}}
    public boolean isInitialized(){return kernelMemory>0;}
    public boolean recomputeMemory(Iterable<ItemStack> components){double total=0;for(ItemStack stack:components)if(li.cil.oc.api.Driver.driverFor(stack) instanceof Memory memory)total+=memory.amount(stack)*1024;int bytes=Math.max(0,Math.min(Settings.get().maxTotalRam,(int)total));if(lua!=null&&Settings.get().limitMemory){lua.setTotalMemory(Integer.MAX_VALUE);if(kernelMemory>0)lua.setTotalMemory((int)Math.min(Integer.MAX_VALUE,kernelMemory+Math.ceil(bytes*ramScale)));}return bytes>0;}
    public void runSynchronized(){if(lua.getTop()!=2||!lua.isThread(1)||!lua.isFunction(2))throw new IllegalStateException("Invalid synchronized Lua call stack");try{lua.call(0,1);lua.checkType(2,LuaType.TABLE);syncState=2;}catch(LuaMemoryAllocationException error){throw new OutOfMemoryError("not enough memory");}}
    public ExecutionResult runThreaded(boolean synchronizedReturn){
        try{
            if(!lua.isThread(1))throw new IllegalStateException("Missing Lua kernel thread");int results;
            if(synchronizedReturn){if(lua.getTop()!=2||!lua.isTable(2))throw new IllegalStateException("Invalid synchronized Lua result");syncState=0;results=lua.resume(1,1);}
            else if(kernelMemory==0){if(lua.resume(1,0)>0)results=0;else{lua.gc(LuaState.GcAction.COLLECT,0);kernelMemory=Math.max(lua.getTotalMemory()-lua.getFreeMemory(),1);recomputeMemory(machine.host().internalComponents());lua.pushInteger(0);results=1;}}
            else{Signal signal=machine.popSignal();if(signal==null)results=lua.resume(1,0);else{lua.pushString(signal.name());for(Object argument:signal.args())NativeValueBridge.push(lua,argument);results=lua.resume(1,1+signal.args().length);}}
            if(lua.status(1)==LuaState.YIELD){
                if(results==1&&lua.isFunction(2)){syncState=1;return new ExecutionResult.SynchronizedCall();}
                if(results==1&&lua.isBoolean(2))return new ExecutionResult.Shutdown(lua.toBoolean(2));
                int ticks=results==1&&lua.isNumber(2)?(int)(lua.toNumber(2)*20):Integer.MAX_VALUE;lua.pop(results);return new ExecutionResult.Sleep(ticks);
            }
            if(!lua.isBoolean(2)||!(lua.isString(3)||lua.isNoneOrNil(3)))OpenComputers.LOGGER.warn("Kernel returned unexpected results");
            if(lua.toBoolean(2)){OpenComputers.LOGGER.warn("Kernel stopped unexpectedly");return new ExecutionResult.Shutdown(false);}
            if(Settings.get().limitMemory)lua.setTotalMemory(Integer.MAX_VALUE);String message=lua.isJavaObjectRaw(3)?String.valueOf(lua.toJavaObjectRaw(3)):lua.toString(3);return new ExecutionResult.Error(message==null?"unknown error":message);
        }catch(LuaMemoryAllocationException error){return new ExecutionResult.Error("not enough memory");}
        catch(LuaGcMetamethodException error){return new ExecutionResult.Error("kernel panic:\n"+(error.getMessage()==null?"error in garbage collection metamethod":error.getMessage()));}
        catch(Error error){if("not enough memory".equals(error.getMessage()))return new ExecutionResult.Error("not enough memory");throw error;}
        catch(LuaRuntimeException error){OpenComputers.LOGGER.warn("Kernel crashed",error);return new ExecutionResult.Error("kernel panic: this is a bug, check your log file and report it");}
    }
    public boolean initialize(){lua=factory().createState();if(lua==null){machine.crash("native libraries not available");return false;}ramScale=lua.getPointerWidth()>=8?Settings.get().ramScaleFor64Bit:1;for(NativeLuaAPI api:apis)api.initialize();try(InputStream source=NativeLuaArchitecture.class.getResourceAsStream(Settings.scriptPath+"machine.lua")){if(source==null)throw new FileNotFoundException("OpenComputers kernel machine.lua is missing");lua.load(source,"=machine","t");lua.newThread();return true;}catch(IOException error){OpenComputers.LOGGER.error("Could not load native Lua kernel",error);close();return false;}}
    public void onSignal(){}
    public void onConnect(){}
    public void close(){if(lua!=null){if(Settings.get().limitMemory)lua.setTotalMemory(Integer.MAX_VALUE);lua.close();}lua=null;kernelMemory=0;syncState=0;}
    public void load(CompoundTag nbt){
        if(!machine.isRunning())return;if(Settings.get().limitMemory)lua.setTotalMemory(Integer.MAX_VALUE);
        persistence.load(nbt);syncState=nbt.getInt("native.syncState");if(syncState==0){int[] states=nbt.getIntArray("state");if(Arrays.stream(states).anyMatch(value->value==5))syncState=1;else if(Arrays.stream(states).anyMatch(value->value==6))syncState=2;}
        try{lua.setTop(0);persistence.unpersist(SaveHandler.load(nbt,machine.node().address()+"_kernel"));if(!lua.isThread(1))throw new LuaRuntimeException("Invalid kernel");if(syncState!=0){persistence.unpersist(SaveHandler.load(nbt,machine.node().address()+"_stack"));if(syncState==1?!lua.isFunction(2):!lua.isTable(2))throw new LuaRuntimeException("Invalid stack");}kernelMemory=(int)(nbt.getInt("kernelMemory")*ramScale);for(NativeLuaAPI api:apis)api.load(nbt);collect("loaded");}finally{recomputeMemory(machine.host().internalComponents());}
    }
    public void save(CompoundTag nbt){
        if(Settings.get().limitMemory)lua.setTotalMemory(Integer.MAX_VALUE);
        try{if(!lua.isThread(1))throw new LuaRuntimeException("Invalid kernel");SaveHandler.scheduleSave(machine.host(),nbt,machine.node().address()+"_kernel",persistence.persist(1));if(syncState!=0){if(syncState==1?!lua.isFunction(2):!lua.isTable(2))throw new LuaRuntimeException("Invalid synchronized stack");SaveHandler.scheduleSave(machine.host(),nbt,machine.node().address()+"_stack",persistence.persist(2));}nbt.putInt("native.syncState",syncState);nbt.putInt("kernelMemory",(int)Math.ceil(kernelMemory/ramScale));for(NativeLuaAPI api:apis)api.save(nbt);collect("saved");}
        catch(LuaRuntimeException|LuaGcMetamethodException error){OpenComputers.LOGGER.warn("Could not persist computer at {}",machine.host().machinePosition(),error);nbt.remove("state");}
        finally{recomputeMemory(machine.host().internalComponents());}
    }
    private void collect(String operation){try{lua.gc(LuaState.GcAction.COLLECT,0);}catch(Throwable error){OpenComputers.LOGGER.warn("Error cleaning up {} computer at {}",operation,machine.host().machinePosition(),error);machine.crash("error in garbage collector, most likely __gc method timed out");}}
}
