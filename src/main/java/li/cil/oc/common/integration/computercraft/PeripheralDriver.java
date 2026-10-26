package li.cil.oc.common.integration.computercraft;

import dan200.computercraft.api.filesystem.*;
import dan200.computercraft.api.lua.*;
import dan200.computercraft.api.peripheral.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import li.cil.oc.OpenComputers;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.*;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.*;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import li.cil.oc.common.integration.PeripheralBlacklist;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Original low-priority Adapter driver, now using the optional NeoForge peripheral capability. */
public final class PeripheralDriver implements DriverBlock {
    private final Map<String,Optional<Class<?>>> blacklistClasses=new ConcurrentHashMap<>();
    private boolean blacklisted(Object target){
        if(target==null)return false;
        if(target instanceof BlacklistedPeripheral filter)return filter.isPeripheralBlacklisted();
        for(String entry:PeripheralBlacklist.classes()){
            String name=entry.equals("net.minecraft.tileentity.TileEntityCommandBlock")?"net.minecraft.world.level.block.entity.CommandBlockEntity":entry;
            Class<?> type=blacklistClasses.computeIfAbsent(name,key->{try{return Optional.of(Class.forName(key,false,getClass().getClassLoader()));}catch(ClassNotFoundException|LinkageError error){return Optional.empty();}}).orElse(null);
            if(type!=null&&type.isInstance(target))return true;
        }
        return false;
    }
    private IPeripheral peripheral(Level level,BlockPos pos,Direction side){
        try{IPeripheral peripheral=level.getCapability(PeripheralCapability.get(),pos,side);return blacklisted(peripheral)?null:peripheral;}
        catch(RuntimeException error){OpenComputers.LOGGER.warn("Error accessing ComputerCraft peripheral at {}",pos,error);return null;}
    }
    @Override public boolean worksWith(Level level,BlockPos pos,Direction side){
        if(level==null||level.isClientSide||!level.hasChunkAt(pos))return false;var target=level.getBlockEntity(pos);
        return target!=null&&!(target instanceof li.cil.oc.api.network.Environment)&&!blacklisted(target)&&peripheral(level,pos,side)!=null;
    }
    @Override public ManagedEnvironment createEnvironment(Level level,BlockPos pos,Direction side){IPeripheral peripheral=peripheral(level,pos,side);return peripheral==null||!(level instanceof ServerLevel server)?null:new Environment(peripheral,server);}

    public static final class Environment extends AbstractManagedEnvironment implements ManagedPeripheral,NamedBlock {
        private final IPeripheral peripheral;
        private final ServerLevel level;
        private final LuaMethods methods;
        private final Map<String,FakeComputerAccess> accesses=new LinkedHashMap<>();
        Environment(IPeripheral peripheral,ServerLevel level){this.peripheral=peripheral;this.level=level;methods=new LuaMethods(peripheral);setNode(Network.newNode(this,Visibility.Network).create());}
        @Override public String[] methods(){return methods.names();}
        @Override public Object[] invoke(String method,Context context,Arguments args)throws Exception{
            Node computer=context.node();if(computer==null)throw new LuaException("ComputerCraft peripherals require an OpenComputers computer context");
            FakeComputerAccess access;boolean temporary;
            synchronized(accesses){access=accesses.get(computer.address());temporary=access==null;if(temporary){access=new FakeComputerAccess(this,context);peripheral.attach(access);}}
            try{return methods.invoke(method,access,LuaMethods.arguments(args),level);}
            finally{if(temporary){try{peripheral.detach(access);}finally{access.close();}}}
        }
        @Override public void onConnect(Node node){if(node.host() instanceof Context context)synchronized(accesses){if(!accesses.containsKey(node.address())){var access=new FakeComputerAccess(this,context);accesses.put(node.address(),access);peripheral.attach(access);}}}
        @Override public void onDisconnect(Node node){
            synchronized(accesses){
                if(node==node()){for(FakeComputerAccess access:new ArrayList<>(accesses.values()))detach(access);accesses.clear();}
                else if(node.host() instanceof Context){FakeComputerAccess access=accesses.remove(node.address());if(access!=null)detach(access);}
            }
        }
        private void detach(FakeComputerAccess access){try{peripheral.detach(access);}finally{access.close();}}
        @Override public String preferredName(){return peripheral.getType();}
        @Override public int priority(){return -1;}
    }

    /** Real attachment/event and filesystem lifecycle mapped onto an OC Context. */
    private static final class FakeComputerAccess implements IComputerAccess {
        private final Environment owner;
        private final Context context;
        private final Map<String,ManagedEnvironment> mounts=new LinkedHashMap<>();
        private volatile boolean closed;
        FakeComputerAccess(Environment owner,Context context){this.owner=owner;this.context=context;}
        private void check(){if(closed)throw new NotAttachedException();}
        void close(){for(ManagedEnvironment environment:new ArrayList<>(mounts.values()))if(environment.node()!=null)environment.node().remove();mounts.clear();closed=true;}
        private String mountFilesystem(String path,Mount mount,String label){
            check();if(mounts.containsKey(path))return null;ManagedEnvironment filesystem=li.cil.oc.api.FileSystem.asManagedEnvironment(new MountFileSystem(mount),label);
            if(filesystem==null||filesystem.node()==null)return null;mounts.put(path,filesystem);context.node().connect(filesystem.node());return path;
        }
        @Override public String mount(String path,Mount mount,String driveName){return mountFilesystem(path,mount,driveName);}
        @Override public String mountWritable(String path,WritableMount mount,String driveName){return mountFilesystem(path,mount,driveName);}
        @Override public void unmount(String path){check();ManagedEnvironment filesystem=mounts.remove(path);if(filesystem!=null&&filesystem.node()!=null)filesystem.node().remove();}
        @Override public int getID(){check();return context.node().address().hashCode()&Integer.MAX_VALUE;}
        @Override public void queueEvent(String event,Object...args){check();context.signal(event,args);}
        @Override public String getAttachmentName(){check();return owner.node().address();}
        @Override public Map<String,IPeripheral> getAvailablePeripherals(){
            check();Map<String,IPeripheral> result=new LinkedHashMap<>();result.put(getAttachmentName(),owner.peripheral);
            if(owner.node().network()!=null)for(Node node:owner.node().reachableNodes())if(node.host() instanceof Environment environment)result.put(node.address(),environment.peripheral);return Collections.unmodifiableMap(result);
        }
        @Override public IPeripheral getAvailablePeripheral(String name){return getAvailablePeripherals().get(name);}
        @Override public WorkMonitor getMainThreadMonitor(){check();return null;}
    }
}
