package li.cil.oc.server.portable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Supplier;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.machine.MachineHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.util.Lifecycle;
import li.cil.oc.common.component.TextBuffer;
import li.cil.oc.server.component.Keyboard;
import li.cil.oc.server.machine.Machine;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Inventory components stay in the private computer graph, never in a plug graph. */
public final class PortableRuntime {
    private final MachineHost host;
    private final Supplier<ItemStack[]> inventory;
    private final Map<Integer, ManagedEnvironment> components = new LinkedHashMap<>();
    private final List<ManagedEnvironment> awaitingInitialization = new ArrayList<>();
    private Machine machine;
    private Node control;
    private CompoundTag pending;
    private boolean initialized, closing, initializing;
    public PortableRuntime(MachineHost host, Supplier<ItemStack[]> inventory) { this.host=host; this.inventory=inventory; }
    public Machine machine() {
        if(machine==null&&host.world()!=null&&!host.world().isClientSide){machine=(Machine)li.cil.oc.api.Machine.create(host);machine.node().setLocalBufferSize(0);}
        return machine;
    }
    public boolean initialized(){return initialized;}
    public void initialize(Node control) {
        if(initialized||closing||host.world()==null||host.world().isClientSide)return;
        initialized=true;initializing=true;this.control=control;machine();
        try{ItemStack[] stacks=inventory.get();for(int index=0;index<stacks.length;index++)create(index,stacks[index]);
            if(pending!=null){machine.load(pending,host.world().registryAccess());pending=null;}
            Network.joinNewNetwork(machine.node());connect();machine.onHostChanged();
        }finally{initializing=false;}
    }
    private void create(int slot,ItemStack stack) {
        if(stack.isEmpty())return;var driver=Driver.driverFor(stack,host.getClass());if(driver==null)return;
        ManagedEnvironment environment=driver.createEnvironment(stack,host);if(environment==null)return;
        components.put(slot,environment);lifecycle(environment,Lifecycle.LifecycleState.Constructing);
        try{environment.load(componentData(driver,stack),host.world().registryAccess());}catch(Throwable error){li.cil.oc.OpenComputers.LOGGER.warn("A portable item component failed while loading",error);}
        lifecycle(environment,Lifecycle.LifecycleState.Initializing);
        awaitingInitialization.add(environment);
        if(machine!=null&&machine.node().network()!=null)connect();
        if(!initializing)saveComponent(slot,environment);
    }
    private static CompoundTag componentData(li.cil.oc.api.driver.DriverItem driver,ItemStack stack){CompoundTag data=driver.dataTag(stack);return data!=null?data:stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound("oc:data");}
    private static void lifecycle(ManagedEnvironment environment,Lifecycle.LifecycleState state){if(environment instanceof Lifecycle listener)listener.onLifecycleStateChange(state);}
    /** A real hot swap closes and saves only the old slot before the caller replaces its stack. */
    public void remove(int slot){
        if(!initialized||closing)return;ManagedEnvironment old=components.remove(slot);if(old==null)return;awaitingInitialization.remove(old);
        lifecycle(old,Lifecycle.LifecycleState.Disposing);if(old.node()!=null)old.node().remove();saveComponent(slot,old);if(old.node()!=null)old.node().remove();lifecycle(old,Lifecycle.LifecycleState.Disposed);
    }
    public void install(int slot){if(!initialized||closing)return;ItemStack[] stacks=inventory.get();if(slot<0||slot>=stacks.length)return;create(slot,stacks[slot]);connect();machine.onHostChanged();}
    public void connect() {
        if(machine==null||closing)return;if(control!=null)machine.node().connect(control);
        for(var environment:components.values())if(environment.node()!=null)machine.node().connect(environment.node());
        TextBuffer display=screen();if(display!=null)for(var environment:components.values())
            if(environment instanceof Keyboard||environment instanceof li.cil.oc.server.component.GraphicsCard||environment instanceof li.cil.oc.server.component.APU)
                if(environment.node()!=null)display.node().connect(environment.node());
        if(machine.node().network()!=null){List<ManagedEnvironment> ready=List.copyOf(awaitingInitialization);awaitingInitialization.clear();for(ManagedEnvironment environment:ready)lifecycle(environment,Lifecycle.LifecycleState.Initialized);}
    }
    public void rebuild() {
        if(!initialized||closing)return;for(var environment:components.values())if(environment.node()!=null)environment.node().remove();components.clear();awaitingInitialization.clear();
        initializing=true;try{ItemStack[] stacks=inventory.get();for(int index=0;index<stacks.length;index++)create(index,stacks[index]);connect();machine.onHostChanged();}finally{initializing=false;}
    }
    public void tick(){if(!initialized||closing)return;machine.update();for(var environment:components.values())if(environment.canUpdate())environment.update();}
    public TextBuffer screen(){for(var environment:components.values())if(environment instanceof TextBuffer buffer)return buffer;return null;}
    public List<Keyboard> keyboards(){List<Keyboard> result=new ArrayList<>();for(var environment:components.values())if(environment instanceof Keyboard keyboard)result.add(keyboard);return result;}
    public int componentSlot(String address){for(var entry:components.entrySet())if(entry.getValue().node()!=null&&java.util.Objects.equals(address,entry.getValue().node().address()))return entry.getKey();return -1;}
    public void saveComponents(){if(machine!=null){synchronized(machine){saveComponentsLocked();}}else saveComponentsLocked();}
    private void saveComponent(int slot,ManagedEnvironment environment){
        if(host.world()==null||host.world().isClientSide)return;ItemStack[] stacks=inventory.get();if(slot<0||slot>=stacks.length)return;ItemStack stack=stacks[slot];var driver=Driver.driverFor(stack,host.getClass());if(driver==null||stack.isEmpty())return;
        CompoundTag data=componentData(driver,stack);environment.save(data,host.world().registryAccess());driver.saveDataTag(stack,data);
    }
    private void saveComponentsLocked(){for(var entry:components.entrySet())saveComponent(entry.getKey(),entry.getValue());}
    public void load(CompoundTag state){pending=state.copy();}
    public CompoundTag save(HolderLookup.Provider registries){saveComponents();CompoundTag state=pending==null?new CompoundTag():pending.copy();if(machine!=null)machine.save(state,registries);pending=state.copy();return state;}
    public void close(){if(closing)return;saveComponents();closing=true;if(machine!=null){machine.stop();machine.node().remove();}for(var environment:components.values())if(environment.node()!=null)environment.node().remove();if(control!=null)control.remove();initialized=false;}
}
