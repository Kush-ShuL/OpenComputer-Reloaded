package li.cil.oc.common;

import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.util.SideTracker;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import java.io.*;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Auxiliary VM/file-system snapshots, outside block-entity NBT, as in OC. */
public final class SaveHandler {
    private SaveHandler() {}
    private static final ThreadLocal<Boolean> CLIENT_SYNC=ThreadLocal.withInitial(()->false);
    private static final Map<Path,Future<?>> SAVING=new ConcurrentHashMap<>();
    private static volatile ExecutorService executor;
    public static boolean isSavingForClients(){return CLIENT_SYNC.get();}
    public static void forClientSync(Runnable action){boolean old=CLIENT_SYNC.get();CLIENT_SYNC.set(true);try{action.run();}finally{if(old)CLIENT_SYNC.set(true);else CLIENT_SYNC.remove();}}
    private static synchronized ExecutorService executor(){
        if(executor==null||executor.isShutdown())executor=Executors.newSingleThreadExecutor(r->li.cil.oc.util.ThreadPoolFactory.daemon(SideTracker.serverTask(r),"OpenComputers-SaveHandler"));
        return executor;
    }
    public static Path savePath(){
        var server=ServerLifecycleHooks.getCurrentServer();
        if(server==null)throw new IllegalStateException("OpenComputers save root requires an active server");
        return server.getWorldPath(LevelResource.ROOT).resolve(Settings.savePath);
    }
    private static String dimensionName(CompoundTag metadata){
        if(metadata.contains("dimension",Tag.TAG_STRING))return metadata.getString("dimension");
        return switch(metadata.getInt("dimension")){case -1->"minecraft:the_nether";case 1->"minecraft:the_end";default->"minecraft:overworld";};
    }
    private static Path file(CompoundTag metadata,String name){
        if(!name.matches("[a-zA-Z0-9_.-]+")||name.equals(".")||name.equals(".."))throw new IllegalArgumentException("Invalid auxiliary state name");
        var dimension=ResourceLocation.tryParse(dimensionName(metadata));
        if(dimension==null)throw new IllegalArgumentException("Invalid saved dimension");
        Path root=savePath().resolve("state").toAbsolutePath().normalize();
        Path result=root.resolve(dimension.getNamespace()).resolve(dimension.getPath())
            .resolve(metadata.getInt("chunkX")+"."+metadata.getInt("chunkZ")).resolve(name).normalize();
        if(!result.startsWith(root))throw new IllegalArgumentException("State path escapes save root");
        return result;
    }
    public static void scheduleSave(EnvironmentHost host,CompoundTag metadata,String name,Consumer<CompoundTag> writer){
        CompoundTag data=new CompoundTag();writer.accept(data);
        try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();NbtIo.write(data,new DataOutputStream(bytes));scheduleSave(host,metadata,name,bytes.toByteArray());}
        catch(IOException exception){throw new UncheckedIOException(exception);}
    }
    public static void scheduleSave(EnvironmentHost host,CompoundTag metadata,String name,byte[] data){scheduleSave(host.world(),host.xPosition(),host.zPosition(),metadata,name,data);}
    public static void scheduleSave(Level world,double x,double z,CompoundTag metadata,String name,byte[] data){
        if(world==null||world.isClientSide||isSavingForClients())return;
        metadata.putString("dimension",world.dimension().location().toString());
        metadata.putInt("chunkX",((int)Math.floor(x))>>4);metadata.putInt("chunkZ",((int)Math.floor(z))>>4);
        Path path=file(metadata,name);byte[] copy=data.clone();
        Future<?> future=executor().submit(()->{
            Path temporary=null;
            try{
                Files.createDirectories(path.getParent());temporary=Files.createTempFile(path.getParent(),name,".tmp");
                Files.write(temporary,copy);
                try{Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
                catch(AtomicMoveNotSupportedException exception){Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);}
            }catch(IOException exception){OpenComputers.LOGGER.error("Could not save auxiliary state {}",path,exception);throw new UncheckedIOException(exception);}
            finally{if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException ignored){}}
        });
        SAVING.put(path,future);
    }
    public static byte[] load(CompoundTag metadata,String name){
        Path path=file(metadata,name);Future<?> pending=SAVING.get(path);
        if(pending!=null)try{pending.get();SAVING.remove(path,pending);}
        catch(InterruptedException exception){Thread.currentThread().interrupt();throw new IllegalStateException("Interrupted loading computer state",exception);}
        catch(ExecutionException exception){throw new IllegalStateException("The latest computer state could not be saved",exception.getCause());}
        try{
            if(Files.isRegularFile(path))return Files.readAllBytes(path);
            // Legacy vanilla dimension layout from the original save handler.
            if(!metadata.contains("dimension",Tag.TAG_STRING)){
                Path legacy=savePath().resolve("state").resolve(Integer.toString(metadata.getInt("dimension")))
                    .resolve(metadata.getInt("chunkX")+"."+metadata.getInt("chunkZ")).resolve(name);
                if(Files.isRegularFile(legacy))return Files.readAllBytes(legacy);
            }
            return new byte[0];
        }catch(IOException exception){throw new UncheckedIOException(exception);}
    }
    public static CompoundTag loadNBT(CompoundTag metadata,String name){
        byte[] bytes=load(metadata,name);if(bytes.length==0)return new CompoundTag();
        try{return NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)),NbtAccounter.create(64L*1024*1024));}
        catch(IOException exception){throw new UncheckedIOException("Corrupted auxiliary computer state",exception);}
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){
        ExecutorService pool=executor;if(pool==null)return;pool.shutdown();
        try{if(!pool.awaitTermination(15,TimeUnit.SECONDS)){OpenComputers.LOGGER.warn("Waiting for computer snapshots to finish");if(!pool.awaitTermination(105,TimeUnit.SECONDS))pool.shutdownNow();}}
        catch(InterruptedException exception){Thread.currentThread().interrupt();pool.shutdownNow();}
        finally{SAVING.clear();executor=null;}
    }
}
