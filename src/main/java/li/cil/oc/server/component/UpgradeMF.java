package li.cil.oc.server.component;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import li.cil.oc.Settings;
import li.cil.oc.api.Driver;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.driver.DriverBlock;
import li.cil.oc.api.network.*;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/** A powered remote adapter connection to one remembered block and face. */
@EventBusSubscriber(modid = "opencomputers")
public final class UpgradeMF extends AbstractManagedEnvironment implements DeviceInfo {
    private static final CopyOnWriteArrayList<WeakReference<UpgradeMF>> ACTIVE = new CopyOnWriteArrayList<>();
    public final EnvironmentHost host;
    public final ResourceKey<Level> dimension;
    public final BlockPos position;
    public final Direction side;
    private Node nativeNode;
    private ManagedEnvironment adapted;
    private DriverBlock driver;
    private net.minecraft.world.level.block.entity.BlockEntity target;
    private String savedName;
    private CompoundTag savedData;
    private boolean powered=true;
    public UpgradeMF(EnvironmentHost host, ResourceKey<Level> dimension, BlockPos position, Direction side) {
        this.host = host; this.dimension = dimension; this.position = position.immutable(); this.side = side;
        setNode(li.cil.oc.api.Network.newNode(this, Visibility.None).withConnector().create());
    }
    @Override public boolean canUpdate() { return true; }
    @Override public Map<String, String> getDeviceInfo() { return Map.of("class", "bus", "description", "Remote Adapter", "vendor", "Scummtech, Inc.", "product", "ERR NAME NOT FOUND"); }
    private double distance() { return Vec3.atCenterOf(position).distanceTo(new Vec3(host.xPosition(), host.yPosition(), host.zPosition())); }
    private boolean inRange() { return host.world() != null && !host.world().isClientSide && host.world().dimension().equals(dimension) && host.world().hasChunkAt(position) && distance() <= Settings.get().mfuRange; }
    private void saveAdapted(ManagedEnvironment environment) {
        if(environment==null)return;
        savedName=environment.getClass().getName();
        savedData=new CompoundTag();
        environment.save(savedData,host.world().registryAccess());
    }
    private void disconnect() {
        Node nativePrevious=nativeNode;
        ManagedEnvironment previous=adapted;
        nativeNode=null;adapted=null;driver=null;target=null;
        if(nativePrevious!=null)node().disconnect(nativePrevious);
        if(previous!=null){
            if(previous.node()!=null)node().disconnect(previous.node());
            saveAdapted(previous);
            if(previous.node()!=null)previous.node().remove();
        }
    }
    public void refresh() {
        if (node().network() == null || !powered || !inRange()) { disconnect(); return; }
        var level = host.world(); var entity = level.getBlockEntity(position);
        Node remote = li.cil.oc.server.network.Network.getNetworkNode(entity, side);
        if (entity instanceof Environment && remote == null) { disconnect(); return; }
        if (remote != null && remote != node()) {
            if (nativeNode == remote) return;
            disconnect(); nativeNode = remote; node().connect(remote); return;
        }
        if (nativeNode != null) { node().disconnect(nativeNode); nativeNode = null; }
        DriverBlock next = Driver.driverFor(level, position, side);
        if (next != null && next.equals(driver) && entity == target && adapted != null) return;
        boolean changed = driver != null && next != null && !next.equals(driver);
        disconnect(); if (changed) { savedName = null; savedData = null; }
        if (next == null) return;
        ManagedEnvironment environment = next.createEnvironment(level, position, side);
        if (environment == null) return;
        if (environment.getClass().getName().equals(savedName) && savedData != null) environment.load(savedData, level.registryAccess());
        adapted = environment; driver = next; target = entity;
        savedName = environment.getClass().getName(); savedData = new CompoundTag();
        if (environment.node() != null) node().connect(environment.node());
        host.markChanged();
    }
    @Override public void update() {
        if (host.world() == null || host.world().isClientSide) return;
        // Modern neighbor events do not cover every state/BE replacement or flags=2 update.
        refresh();
        if (adapted != null && adapted.canUpdate()) adapted.update();
        double frequency=Math.max(1,Settings.get().tickFrequency);
        if (host.world().getGameTime() % frequency == 0) {
            powered=((Connector)node()).tryChangeBuffer(-Settings.get().mfuCost*frequency*distance());
            if(powered)refresh();else disconnect();
        }
    }
    @Override public void onConnect(Node connected) {
        if (connected == node()) { powered=true;ACTIVE.removeIf(reference -> reference.get() == null || reference.get() == this); ACTIVE.add(new WeakReference<>(this)); refresh(); }
    }
    @Override public void onDisconnect(Node disconnected) {
        if (disconnected == node()) { ACTIVE.removeIf(reference -> reference.get() == null || reference.get() == this); disconnect(); }
        else if (disconnected == nativeNode) nativeNode = null;
        else if (adapted != null && disconnected == adapted.node()) disconnect();
    }
    @SubscribeEvent public static void blockChanged(BlockEvent.NeighborNotifyEvent event) {
        for (WeakReference<UpgradeMF> reference : ACTIVE) { UpgradeMF upgrade = reference.get(); if (upgrade == null) { ACTIVE.remove(reference); continue; } if (event.getLevel() == upgrade.host.world() && event.getPos().equals(upgrade.position)) upgrade.refresh(); }
    }
    @Override public void load(CompoundTag tag) { load(tag, null); }
    @Override public void save(CompoundTag tag) { save(tag, null); }
    @Override public void load(CompoundTag tag, HolderLookup.Provider registries) { super.load(tag); CompoundTag block = tag.getCompound("oc:adapter.block"); if (block.contains("name") && block.contains("data")) { savedName = block.getString("name"); savedData = block.getCompound("data").copy(); } }
    @Override public void save(CompoundTag tag, HolderLookup.Provider registries) { super.save(tag); saveAdapted(adapted); CompoundTag block = new CompoundTag(); if (savedName != null && savedData != null) { block.putString("name", savedName); block.put("data", savedData.copy()); } tag.put("oc:adapter.block", block); }
}
