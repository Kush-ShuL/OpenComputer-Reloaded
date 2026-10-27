package li.cil.oc.server.agent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.internal.MultiTank;
import li.cil.oc.api.network.*;
import li.cil.oc.common.component.TextBuffer;
import li.cil.oc.server.component.Keyboard;
import li.cil.oc.server.machine.Machine;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.fluids.IFluidTank;

/** Stable VM/component lifetime shared by moving proxy robots and entity drones. */
public final class AgentRuntime implements MultiTank {
    public final AgentHost host;
    private Machine machine;
    private li.cil.oc.server.component.AgentComponent control;
    private final Map<Integer, ManagedEnvironment> environments = new LinkedHashMap<>();
    private final Map<ManagedEnvironment, ItemStack> environmentItems = new java.util.IdentityHashMap<>();
    private CompoundTag pendingMachine, pendingControl;
    private boolean initialized, closing;
    private AgentPlayer player;
    public AgentRuntime(AgentHost host) { this.host = host; }
    public Machine machine() {
        if (machine == null && host.world() != null && !host.world().isClientSide) {
            machine = (Machine)li.cil.oc.api.Machine.create(host);
            machine.setCostPerTick(host.robot() ? li.cil.oc.Settings.get().robotCost : li.cil.oc.Settings.get().droneCost);
            machine.node().setLocalBufferSize(0);
        }
        return machine;
    }
    public li.cil.oc.server.component.AgentComponent control() { return control; }
    public AgentPlayer player() { if (player == null || player.level() != host.world()) player = new AgentPlayer((ServerLevel)host.world(), host); return player; }
    public ManagedEnvironment environment(int index) { return environments.get(index); }
    public TextBuffer screen() { for (var environment : environments.values()) if (environment instanceof TextBuffer buffer) return buffer; return null; }
    public List<Keyboard> keyboards() { List<Keyboard> result = new ArrayList<>(); for (var environment : environments.values()) if (environment instanceof Keyboard keyboard) result.add(keyboard); return result; }
    public boolean initialized() { return initialized; }
    public void initialize() {
        if (initialized || host.world() == null || host.world().isClientSide) return;
        initialized = true; closing = false;
        environments.clear();environmentItems.clear();
        machine();
        control = host.robot() ? new li.cil.oc.server.component.Robot(host) : new li.cil.oc.server.component.Drone(host);
        if (pendingControl != null) { control.load(pendingControl, host.world().registryAccess()); pendingControl = null; }
        for (var slot : host.componentStacks()) create(slot.index(), slot.stack());
        if (pendingMachine != null) { machine.load(pendingMachine, host.world().registryAccess()); pendingMachine = null; }
        Network.joinNewNetwork(machine.node()); connect(); machine.onHostChanged();
        connectDisplay();
    }
    private void create(int index, ItemStack stack) {
        if (stack.isEmpty()) return;
        var driver = Driver.driverFor(stack, host.getClass()); if (driver == null) return;
        var environment = driver.createEnvironment(stack, host); if (environment == null) return;
        CompoundTag data = driver.dataTag(stack);
        if (data == null) data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound("oc:data");
        environment.load(data, host.world().registryAccess()); environments.put(index, environment);environmentItems.put(environment,stack);
    }
    public void connect() {
        if (machine == null || control == null || closing) return;
        machine.node().connect(control.node());
        for (var environment : environments.values()) if (environment.node() != null) machine.node().connect(environment.node());
        connectDisplay();
    }
    private void connectDisplay() {
        TextBuffer screen = screen(); if (screen == null || screen.node() == null) return;
        for (var environment : environments.values()) if (environment instanceof Keyboard || environment instanceof li.cil.oc.server.component.GraphicsCard || environment instanceof li.cil.oc.server.component.APU)
            if (environment.node() != null) screen.node().connect(environment.node());
    }
    public void rebuild() {
        if (!initialized || closing) return;
        // Hot swaps must preserve unrelated live environments, including open disk handles.
        // Built-in slot indices can move when an inventory upgrade changes visible capacity.
        var desired=host.componentStacks();
        Map<ItemStack,ManagedEnvironment> byStack=new java.util.IdentityHashMap<>();
        for(var environment:environments.values())byStack.put(environmentItems.get(environment),environment);
        Map<Integer,ManagedEnvironment> retained=new LinkedHashMap<>();
        var keep=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<ManagedEnvironment,Boolean>());
        for(var slot:desired){var existing=byStack.remove(slot.stack());if(existing!=null&&!slot.stack().isEmpty()){retained.put(slot.index(),existing);keep.add(existing);}}
        for(int index:new ArrayList<>(environments.keySet()))if(!keep.contains(environments.get(index)))removeComponent(index);
        environments.clear();environments.putAll(retained);
        for(var slot:desired)if(!environments.containsKey(slot.index()))create(slot.index(),slot.stack());
        connect(); machine.onHostChanged();
    }
    /** Inventory calls this before mutating the stack, so the returned item owns the closed state. */
    public void removeComponent(int index){
        var environment=environments.remove(index);if(environment==null)return;
        ItemStack stack=environmentItems.remove(environment);
        if(environment.node()!=null)environment.node().remove();
        saveComponent(environment,stack);
        // Like upstream ComponentInventory, remove again only if save actually rejoined a network.
        if(environment.node()!=null&&environment.node().network()!=null)environment.node().remove();
    }
    public void tick() {
        initialize(); if (!initialized || closing) return;
        machine.update(); for (var environment : environments.values()) if (environment.canUpdate()) environment.update();
    }
    public void saveComponents() {
        if (host.world() == null || host.world().isClientSide) return;
        for(var environment:new ArrayList<>(environments.values()))saveComponent(environment,environmentItems.get(environment));
    }
    private void saveComponent(ManagedEnvironment environment,ItemStack stack){
        if(stack==null||stack.isEmpty()||host.world()==null||host.world().isClientSide)return;
        var driver=Driver.driverFor(stack,host.getClass());if(driver==null)return;CompoundTag data=driver.dataTag(stack);
        if(data==null)data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound("oc:data");
        environment.save(data,host.world().registryAccess());driver.saveDataTag(stack,data);
    }
    public void load(CompoundTag tag) { pendingMachine = tag.getCompound("oc:computer").copy(); pendingControl = tag.getCompound("oc:robot").copy(); }
    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        saveComponents(); CompoundTag state = pendingMachine == null ? new CompoundTag() : pendingMachine.copy();
        if (machine != null) machine.save(state, registries); tag.put("oc:computer", state);
        CompoundTag controlState = pendingControl == null ? new CompoundTag() : pendingControl.copy();
        if (control != null) control.save(controlState, registries); tag.put("oc:robot", controlState);
    }
    public void close() {
        if (closing) return; closing = true; saveComponents();
        if (machine != null) { machine.stop(); machine.node().remove(); }
        for (var environment : environments.values()) if (environment.node() != null) environment.node().remove();
        if (control != null) control.node().remove(); saveComponents(); initialized = false;
    }
    public int componentSlot(String address) { if (address == null) return -1; for (var entry : environments.entrySet()) if (entry.getValue().node() != null && address.equals(entry.getValue().node().address())) return entry.getKey(); return -1; }
    @Override public int tankCount() { return tanks().size(); }
    @Override public IFluidTank getFluidTank(int index) { return tanks().get(index); }
    private List<IFluidTank> tanks() { List<IFluidTank> tanks = new ArrayList<>(); for (var environment : environments.values()) if (environment instanceof IFluidTank tank) tanks.add(tank); return tanks; }
}
