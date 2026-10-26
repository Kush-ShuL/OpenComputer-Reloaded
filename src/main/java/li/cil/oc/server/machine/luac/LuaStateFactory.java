package li.cil.oc.server.machine.luac;

import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.api.driver.item.MutableProcessor;
import li.cil.oc.api.machine.Architecture;
import li.cil.repack.com.naef.jnlua.*;
import net.minecraft.world.item.ItemStack;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Extracts and loads the original upstream JNI libraries once per Lua version. */
public final class LuaStateFactory {
    private static boolean cleaned;
    private static boolean loggedEnvironment;
    private static final Object LOCK=new Object();
    public static final LuaStateFactory LUA52=new LuaStateFactory("52"),LUA53=new LuaStateFactory("53"),LUA54=new LuaStateFactory("54");
    private final String version;
    private boolean available;
    private Path library;
    private LuaStateFactory(String version){this.version=version;init();}
    public String version(){return version;}
    public boolean isAvailable(){return available;}
    private LuaState create(){return switch(version){case "52"->Settings.get().limitMemory?new LuaState(Integer.MAX_VALUE):new LuaState();case "53"->Settings.get().limitMemory?new LuaStateFiveThree(Integer.MAX_VALUE):new LuaStateFiveThree();case "54"->Settings.get().limitMemory?new LuaStateFiveFour(Integer.MAX_VALUE):new LuaStateFiveFour();default->throw new IllegalStateException(version);};}
    private static synchronized void cleanup(Path directory)throws IOException{if(cleaned)return;cleaned=true;if(!Files.isDirectory(directory))return;try(var stream=Files.list(directory)){for(Path file:stream.filter(path->path.getFileName().toString().matches("OpenComputersMod-.*\\.(dll|so|dylib)")).toList())try{Files.deleteIfExists(file);}catch(IOException ignored){}}}

    private static String loadingContext(Class<?> type){
        Module module=type.getModule();ClassLoader loader=type.getClassLoader();
        return "module="+(module.isNamed()?module.getName():"unnamed")+", loader="+(loader==null?"bootstrap":loader.getName()+"/"+loader.getClass().getName());
    }
    private static synchronized void logEnvironment(){
        if(loggedEnvironment)return;loggedEnvironment=true;
        OpenComputers.LOGGER.info("Native Lua loading contexts: OC [{}], JNLua [{}]",loadingContext(LuaStateFactory.class),loadingContext(LuaState.class));
        if(LuaStateFactory.class.getClassLoader()!=LuaState.class.getClassLoader())OpenComputers.LOGGER.warn("OC and JNLua have different class loaders; JNI libraries loaded by OC cannot resolve JNLua native methods. JNLua must be loaded alongside the mod in the game layer.");
    }

    /** Resource-only JNI jars are separate automatic modules in NeoForge's game layer. */
    private static byte[] bundledLibrary(String name)throws IOException{
        String resource="assets/"+Settings.resourceDomain+"/lib/"+name;
        try(InputStream stream=LuaStateFactory.class.getResourceAsStream("/"+resource)){
            if(stream!=null)return stream.readAllBytes();
        }

        // Class.getResource only searches the caller's named module. A ModuleReader reads
        // the dependency itself, including resource-only packages which aren't exported.
        Set<ModuleLayer> visited=Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<ModuleLayer> layers=new ArrayDeque<>();
        for(Module module:new Module[]{LuaStateFactory.class.getModule(),LuaState.class.getModule()})
            if(module.getLayer()!=null)layers.add(module.getLayer());
        layers.add(ModuleLayer.boot());
        while(!layers.isEmpty()){
            ModuleLayer layer=layers.removeFirst();
            if(!visited.add(layer))continue;
            List<java.lang.module.ResolvedModule> modules=new ArrayList<>(layer.configuration().modules());
            modules.sort(Comparator.comparingInt(module->module.name().toLowerCase(Locale.ROOT).contains("jnlua")?0:1));
            for(var module:modules){
                try(var reader=module.reference().open()){
                    Optional<InputStream> found=reader.open(resource);
                    if(found.isPresent())try(InputStream stream=found.get()){
                        OpenComputers.LOGGER.info("Found native Lua resource {} in module {} ({})",name,module.name(),module.reference().location().orElse(null));
                        return stream.readAllBytes();
                    }
                }
            }
            layers.addAll(layer.parents());
        }

        // Development launches may keep the dependency on the ordinary class path.
        Set<ClassLoader> loaders=Collections.newSetFromMap(new IdentityHashMap<>());
        Collections.addAll(loaders,Thread.currentThread().getContextClassLoader(),LuaStateFactory.class.getClassLoader(),LuaState.class.getClassLoader(),ClassLoader.getSystemClassLoader());
        for(ClassLoader loader:loaders)if(loader!=null)try(InputStream stream=loader.getResourceAsStream(resource)){
            if(stream!=null)return stream.readAllBytes();
        }
        return null;
    }
    private void init(){
        String os=System.getProperty("os.name","").toLowerCase(Locale.ROOT),arch=System.getProperty("os.arch","").toLowerCase(Locale.ROOT);
        String system=os.contains("win")?"windows":os.contains("mac")||os.contains("darwin")?"darwin":os.contains("freebsd")?"freebsd":os.contains("netbsd")?"netbsd":os.contains("openbsd")?"openbsd":os.contains("sunos")||os.contains("solaris")?"solaris":os.contains("linux")?"linux":"unknown";
        String architecture=arch.startsWith("aarch64")||arch.startsWith("arm64")?"aarch64":arch.startsWith("arm")?"arm":arch.startsWith("x86_64")||arch.startsWith("amd64")?"x86_64":arch.startsWith("x86")||arch.startsWith("i386")?"x86":"unknown";
        String platform=Settings.get().forceNativeLibPlatform.isEmpty()?system+"-"+architecture:Settings.get().forceNativeLibPlatform;
        String name="libjnlua"+version+"-"+platform+(system.equals("windows")?".dll":system.equals("darwin")?".dylib":".so");
        try{
            logEnvironment();
            if(!Settings.get().forceNativeLibPathFirst.isEmpty()){Path forced=Paths.get(Settings.get().forceNativeLibPathFirst).resolve(name);if(Files.isReadable(forced))library=forced.toAbsolutePath();else OpenComputers.LOGGER.warn("Forced native library {} does not exist, using bundled library",forced);}
            if(library==null){
                byte[] bytes=bundledLibrary(name);if(bytes==null){OpenComputers.LOGGER.warn("Native library {} was not found in the game module layer or class path",name);return;}
                Path directory=Settings.get().nativeInTmpDir?Paths.get(System.getProperty("java.io.tmpdir","")):Paths.get("opencomputers/natives").toAbsolutePath();Files.createDirectories(directory);if(!Settings.get().nativeInTmpDir)cleanup(directory);
                library=directory.resolve("OpenComputersMod-"+OpenComputers.VERSION+"-"+version+"-"+name).toAbsolutePath();
                boolean matching=Files.isRegularFile(library)&&Arrays.equals(bytes,Files.readAllBytes(library));
                if(!matching){try{Files.deleteIfExists(library);Files.write(library,bytes);}catch(IOException error){if(!Files.isRegularFile(library))throw error;OpenComputers.LOGGER.warn("Could not replace native library {}",library,error);}}
                library.toFile().deleteOnExit();library.toFile().setReadable(true,false);library.toFile().setExecutable(true,false);
            }
            synchronized(LOCK){System.load(library.toString());create().close();}available=true;OpenComputers.LOGGER.info("Loaded OpenComputers native Lua {} from {}",version,library);
        }catch(Throwable error){if(Settings.get().logFullLibLoadErrors)OpenComputers.LOGGER.warn("Could not load native Lua {} from {}",version,library,error);else OpenComputers.LOGGER.warn("Could not load native Lua {} from {}: {}",version,library,error.toString());}
    }
    public LuaState createState(){
        if(!available)return null;
        LuaState state=null;
        try{
            synchronized(LOCK){System.load(library.toString());state=create();}
            List<LuaState.Library> libraries=new ArrayList<>(List.of(LuaState.Library.BASE,LuaState.Library.COROUTINE,LuaState.Library.DEBUG,LuaState.Library.ERIS,LuaState.Library.MATH,LuaState.Library.STRING,LuaState.Library.TABLE));libraries.add(version.equals("52")?LuaState.Library.BIT32:LuaState.Library.UTF8);
            for(var module:libraries)state.openLib(module);state.pop(libraries.size());
            if(!Settings.get().disableLocaleChanging){state.openLib(LuaState.Library.OS);state.getField(-1,"setlocale");state.pushString("C");state.call(1,0);state.pop(1);}
            state.newTable();state.setGlobal("os");for(String name:new String[]{"unpack","loadstring","dofile","loadfile"}){state.pushNil();state.setGlobal(name);}
            state.getGlobal("math");state.pushNil();state.setField(-2,"log10");state.pop(1);state.getGlobal("table");state.pushNil();state.setField(-2,"maxn");state.pop(1);
            Random random=new Random();state.getGlobal("math");state.pushJavaFunction(lua->{double value=random.nextDouble();switch(lua.getTop()){case 0->lua.pushNumber(value);case 1->{double upper=lua.checkNumber(1);lua.checkArg(1,upper>=1,"interval is empty");lua.pushNumber(Math.floor(value*upper)+1);}case 2->{double lower=lua.checkNumber(1),upper=lua.checkNumber(2);lua.checkArg(2,lower<=upper,"interval is empty");lua.pushNumber(Math.floor(value*(upper-lower+1))+lower);}default->throw new IllegalArgumentException("wrong number of arguments");}return 1;});state.setField(-2,"random");state.pushJavaFunction(lua->{random.setSeed(lua.checkInteger(1));return 0;});state.setField(-2,"randomseed");state.pop(1);return state;
        }catch(Throwable error){OpenComputers.LOGGER.warn("Failed creating native Lua {} state",version,error);if(state!=null)state.close();return null;}
    }
    public static boolean isAvailableNative(){return LUA52.available||LUA53.available||LUA54.available;}
    public static boolean includeLuaJ(){return !isAvailableNative()||Settings.get().forceLuaJ||Settings.get().registerLuaJArchitecture;}
    public static boolean include52(){return LUA52.available&&!Settings.get().forceLuaJ;}
    public static boolean include53(){return LUA53.available&&!Settings.get().forceLuaJ&&Settings.get().enableLua53;}
    public static boolean include54(){return LUA54.available&&!Settings.get().forceLuaJ&&Settings.get().enableLua54;}
    public static boolean default53(){return include53()&&Settings.get().defaultLua53;}
    public static ItemStack setDefaultArch(ItemStack stack){if(default53()&&li.cil.oc.api.Driver.driverFor(stack) instanceof MutableProcessor processor)processor.setArchitecture(stack,NativeLua53Architecture.class);return stack;}
}
