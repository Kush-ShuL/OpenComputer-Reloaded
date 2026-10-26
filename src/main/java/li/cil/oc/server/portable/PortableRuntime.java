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
import li.cil.oc.common.component.TextBuffer;
import li.cil.oc.server.component.Keyboard;
import li.cil.oc.server.machine.Machine;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Inventory components stay in the private computer graph, never in a plug graph. */
public final class PortableRuntime {
    private final MachineHost host;
    private final Supplier<ItemStack[]> inventory;
    private final Map<Integer, ManagedEnvironment> components = new LinkedHashMap<>();
    private Machine machine;
    private Node control;
    private CompoundTag pending;
    private boolean initialized, closing;
    public PortableRuntime(MachineHost host, Supplier<ItemStack[]> inventory) { this.host=host; this.inventory=inventory; }
    public Machine machine() {
        if(machine==null&&host.world()!=null&&!host.world().isClientSide){machine=(Machine)li.cil.oc.api.Machine.create(host);machine.node().setLocalBufferSize(0);}
        return machine;
    }
    public boolean initialized(){return initialized;}
    public void initialize(Node control) {
        if(initialized||closing||host.world()==null||host.world().isClientSide)return;
        initialized=true;this.control=control;machine();
        ItemStack[] stacks=inventory.get();for(int index=0;index<stacks.length;index++)create(index,stacks[index]);
        if(pending!=null){machine.load(pending,host.world().registryAccess());pending=null;}
        Network.joinNewNetwork(machine.node());connect();machine.onHostChanged();
    }
    private void create(int slot,ItemStack stack) {
        if(stack.isEmpty())return;var driver=Driver.driverFor(stack,host.getClass());if(driver==null)return;
        ManagedEnvironment environment=driver.createEnvironment(stack,host);if(environment==null)return;
        CompoundTag data=driver.dataTag(stack);if(data!=null)environment.load(data,host.world().registryAccess());components.put(slot,environment);
    }
    public void connect() {
        if(machine==null||closing)return;if(control!=null)machine.node().connect(control);
        for(var environment:components.values())if(environment.node()!=null)machine.node().connect(environment.node());
        TextBuffer display=screen();if(display!=null)for(var environment:components.values())
            if(environment instanceof Keyboard||environment instanceof li.cil.oc.server.component.GraphicsCard||environment instanceof li.cil.oc.server.component.APU)
                if(environment.node()!=null)display.node().connect(environment.node());
    }
    public void rebuild() {
        if(!initialized||closing)return;for(var environment:components.values())if(environment.node()!=null)environment.node().remove();components.clear();
        ItemStack[] stacks=inventory.get();for(int index=0;index<stacks.length;index++)create(index,stacks[index]);connect();machine.onHostChanged();
    }
    public void tick(){if(!initialized||closing)return;machine.update();for(var environment:components.values())if(environment.canUpdate())environment.update();}
    public TextBuffer screen(){for(var environment:components.values())if(environment instanceof TextBuffer buffer)return buffer;return null;}
    public List<Keyboard> keyboards(){List<Keyboard> result=new ArrayList<>();for(var environment:components.values())if(environment instanceof Keyboard keyboard)result.add(keyboard);return result;}
    public int componentSlot(String address){for(var entry:components.entrySet())if(entry.getValue().node()!=null&&address.equals(entry.getValue().node().address()))return entry.getKey();return -1;}
    public void saveComponents(){if(machine!=null){synchronized(machine){saveComponentsLocked();}}else saveComponentsLocked();}
    private void saveComponentsLocked(){if(host.world()==null||host.world().isClientSide)return;ItemStack[] stacks=inventory.get();for(var entry:components.entrySet()){
        ItemStack stack=stacks[entry.getKey()];var driver=Driver.driverFor(stack,host.getClass());if(driver==null||stack.isEmpty())continue;
        CompoundTag data=driver.dataTag(stack);if(data==null)data=new CompoundTag();entry.getValue().save(data,host.world().registryAccess());driver.saveDataTag(stack,data);
    }}
    public void load(CompoundTag state){pending=state.copy();}
    public CompoundTag save(HolderLookup.Provider registries){saveComponents();CompoundTag state=pending==null?new CompoundTag():pending.copy();if(machine!=null)machine.save(state,registries);pending=state.copy();return state;}
    public void close(){if(closing)return;saveComponents();closing=true;if(machine!=null){machine.stop();machine.node().remove();}for(var environment:components.values())if(environment.node()!=null)environment.node().remove();if(control!=null)control.remove();initialized=false;}
}
