package li.cil.oc.server.machine.luaj;

import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.api.driver.item.Memory;
import li.cil.oc.api.machine.*;
import li.cil.oc.server.machine.ArchitectureCallbackContext;
import li.cil.repack.org.luaj.vm2.*;
import li.cil.repack.org.luaj.vm2.lib.jse.JsePlatform;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import java.io.*;

/** Original OC kernel/coroutine protocol running on the upstream customized LuaJ VM. */
@Architecture.Name("LuaJ")
public final class LuaJLuaArchitecture implements Architecture {
    final Machine machine;
    Globals lua;
    private LuaThread thread;
    private LuaFunction synchronizedCall;
    private LuaValue synchronizedResult;
    private boolean initialized;
    int memory;
    private final LuaJAPI[] apis;
    public LuaJLuaArchitecture(Machine machine){this.machine=machine;apis=new LuaJAPI[]{new ComponentAPI(this),new ComputerAPI(this),new OSAPI(this),new SystemAPI(this),new UnicodeAPI(this),new UserdataAPI(this)};}
    @FunctionalInterface interface Invocation { Object[] call()throws Exception; }
    @FunctionalInterface interface Documentation { String call()throws Exception; }
    Varargs invoke(Invocation invocation){
        try{Object[] result=ArchitectureCallbackContext.call(machine,invocation::call);if(result==null)return LuaValue.TRUE;LuaValue[] values=new LuaValue[result.length+1];values[0]=LuaValue.TRUE;for(int index=0;index<result.length;index++)values[index+1]=LuaValueBridge.toLua(result[index]);return LuaValue.varargsOf(values);}
        catch(Throwable error){
            if(Settings.get().logLuaCallbackErrors&&!(error instanceof LimitReachedException))OpenComputers.LOGGER.warn("Exception in Lua callback",error);
            if(error instanceof LimitReachedException)return LuaValue.NONE;
            if(error instanceof IllegalArgumentException&&error.getMessage()!=null)return LuaValue.varargsOf(LuaValue.FALSE,LuaValue.valueOf(error.getMessage()));
            if(error.getMessage()!=null)return LuaValue.varargsOf(new LuaValue[]{LuaValue.TRUE,LuaValue.NIL,LuaValue.valueOf(error.getMessage())});
            if(error instanceof IndexOutOfBoundsException)return LuaValue.varargsOf(LuaValue.FALSE,LuaValue.valueOf("index out of bounds"));
            if(error instanceof IllegalArgumentException)return LuaValue.varargsOf(LuaValue.FALSE,LuaValue.valueOf("bad argument"));
            if(error instanceof NoSuchMethodException)return LuaValue.varargsOf(LuaValue.FALSE,LuaValue.valueOf("no such method"));
            String message=error instanceof FileNotFoundException?"file not found":error instanceof SecurityException?"access denied":error instanceof IOException?"i/o error":"unknown error";
            if(message.equals("unknown error"))OpenComputers.LOGGER.warn("Unexpected Lua callback error",error);
            return LuaValue.varargsOf(new LuaValue[]{LuaValue.TRUE,LuaValue.NIL,LuaValue.valueOf(message)});
        }
    }
    Varargs documentation(Documentation query){try{String doc=ArchitectureCallbackContext.call(machine,query::call);return doc==null||doc.isEmpty()?LuaValue.NIL:LuaValue.valueOf(doc);}catch(Throwable error){return LuaValue.varargsOf(LuaValue.NIL,LuaValue.valueOf(error instanceof NoSuchMethodException?"no such method":error.getMessage()==null?error.toString():error.getMessage()));}}
    public boolean isInitialized(){return initialized;}
    public boolean recomputeMemory(Iterable<ItemStack> components){double total=0;for(ItemStack stack:components){var driver=li.cil.oc.api.Driver.driverFor(stack);if(driver instanceof Memory ram)total+=ram.amount(stack)*1024;}memory=Math.max(0,Math.min(Settings.get().maxTotalRam,(int)total));return memory>0;}
    public void runSynchronized(){if(synchronizedCall==null)throw new IllegalStateException("No synchronized Lua call pending");synchronizedResult=synchronizedCall.call();synchronizedCall=null;}
    public ExecutionResult runThreaded(boolean synchronizedReturn){
        try{
            Varargs results;
            if(synchronizedReturn){results=thread.resume(synchronizedResult);synchronizedResult=null;}
            else if(!initialized){results=thread.resume(LuaValue.NONE);initialized=true;if(results.narg()==1)results=LuaValue.varargsOf(LuaValue.TRUE,LuaValue.valueOf(0));}
            else{Signal signal=machine.popSignal();if(signal==null)results=thread.resume(LuaValue.NONE);else{Object[] arguments=signal.args();LuaValue[] values=new LuaValue[arguments.length+1];values[0]=LuaValue.valueOf(signal.name());for(int index=0;index<arguments.length;index++)values[index+1]=LuaValueBridge.toLua(arguments[index]);results=thread.resume(LuaValue.varargsOf(values));}}
            if(thread.state.status==LuaThread.STATUS_SUSPENDED){
                if(results.narg()==2&&results.isfunction(2)){synchronizedCall=results.checkfunction(2);return new ExecutionResult.SynchronizedCall();}
                if(results.narg()==2&&results.type(2)==LuaValue.TBOOLEAN)return new ExecutionResult.Shutdown(results.toboolean(2));
                int ticks=results.narg()==2&&results.isnumber(2)?(int)(results.todouble(2)*20):Integer.MAX_VALUE;
                return new ExecutionResult.Sleep(ticks);
            }
            boolean inner=results.type(2)==LuaValue.TBOOLEAN&&(results.isstring(3)||results.isnoneornil(3));
            boolean outer=results.isstring(2)||results.isnoneornil(2);
            if(results.type(1)!=LuaValue.TBOOLEAN||(!inner&&!outer))OpenComputers.LOGGER.warn("Kernel returned unexpected results");
            if((outer&&results.toboolean(1))||(inner&&results.toboolean(2))){OpenComputers.LOGGER.warn("Kernel stopped unexpectedly");return new ExecutionResult.Shutdown(false);}
            int index=inner?3:2;String error=results.isuserdata(index)?String.valueOf(results.touserdata(index)):results.tojstring(index);return new ExecutionResult.Error(error==null?"unknown error":error);
        }catch(Throwable error){OpenComputers.LOGGER.warn("Kernel crashed",error);return new ExecutionResult.Error("kernel panic: this is a bug, check your log file and report it");}
    }
    public boolean initialize(){
        lua=JsePlatform.debugGlobals();for(String name:new String[]{"package","require","io","os","luajava","dofile","loadfile"})lua.set(name,LuaValue.NIL);
        for(LuaJAPI api:apis)api.initialize();recomputeMemory(machine.host().internalComponents());
        try(InputStream source=LuaJLuaArchitecture.class.getResourceAsStream(Settings.scriptPath+"machine.lua")){if(source==null)throw new FileNotFoundException("OpenComputers kernel machine.lua is missing");LuaValue kernel=lua.load(source,"=machine","t",lua);thread=new LuaThread(lua,kernel);return true;}
        catch(IOException error){OpenComputers.LOGGER.error("Could not load OpenComputers Lua kernel",error);close();return false;}
    }
    public void onSignal(){}
    public void onConnect(){}
    public void close(){lua=null;thread=null;synchronizedCall=null;synchronizedResult=null;initialized=false;}
    public void load(CompoundTag nbt){if(machine.isRunning()){machine.stop();machine.start();}}
    /** Upstream LuaJ cannot persist coroutine stacks; only the native architecture does. */
    public void save(CompoundTag nbt){}
}
