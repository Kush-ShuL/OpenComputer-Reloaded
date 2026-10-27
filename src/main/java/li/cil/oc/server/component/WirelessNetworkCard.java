package li.cil.oc.server.component;

import java.io.IOException;
import java.util.Map;
import li.cil.oc.Settings;
import li.cil.oc.api.Network;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.ComponentConnector;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Packet;
import li.cil.oc.api.network.WirelessEndpoint;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/** Two original wireless tiers, range power and moving endpoint tracking. */
public final class WirelessNetworkCard extends NetworkCard implements WirelessEndpoint {
    private double strength;
    public WirelessNetworkCard(EnvironmentHost host, int tier) {
        super(host, tier);
        if (tier < 0 || tier > 1) throw new IllegalArgumentException("invalid wireless card tier");
        strength = maxRange();
    }
    private double maxRange() { return Settings.get().maxWirelessRange[wirelessTier]; }
    private boolean wired() { return wirelessTier == 1; }
    @Override public int x() { return BlockPos.containing(host.xPosition(), host.yPosition(), host.zPosition()).getX(); }
    @Override public int y() { return BlockPos.containing(host.xPosition(), host.yPosition(), host.zPosition()).getY(); }
    @Override public int z() { return BlockPos.containing(host.xPosition(), host.yPosition(), host.zPosition()).getZ(); }
    @Override public Level world() { return host.world(); }
    @Override public void receivePacket(Packet packet, WirelessEndpoint sender) {
        double dx = sender.x() + .5 - host.xPosition(), dy = sender.y() + .5 - host.yPosition(), dz = sender.z() + .5 - host.zPosition();
        receive(packet, Math.sqrt(dx*dx + dy*dy + dz*dz));
    }
    @Callback(direct=true,doc="function():number -- Get transmission range.")
    public synchronized Object[] getStrength(Context context, Arguments args) { return new Object[]{strength}; }
    @Callback(doc="function(strength:number):number -- Set transmission range.")
    public synchronized Object[] setStrength(Context context, Arguments args) {
        double value = args.checkDouble(0);
        strength = Math.max(0, Math.min(maxRange(), value)); host.markChanged(); return new Object[]{strength};
    }
    @Override public Object[] isWireless(Context context, Arguments args) { return new Object[]{true}; }
    @Override public Object[] isWired(Context context, Arguments args) { return new Object[]{wired()}; }
    @Override protected synchronized void doSend(Packet packet) throws IOException {
        if (strength > 0) {
            double cost = strength * Settings.get().wirelessCostPerRange[wirelessTier];
            if (!Settings.get().ignorePower && cost > 0 && !((ComponentConnector) node()).tryChangeBuffer(-cost)) throw new IOException("not enough energy");
            Network.sendWirelessPacket(this, strength, packet);
        }
        if (wired()) super.doSend(packet);
    }
    @Override protected boolean accepts(Packet packet, double distance) {
        return distance <= maxRange() && (distance > 0 || wired()) && super.accepts(packet, distance);
    }
    @Override public boolean canUpdate() { return true; }
    @Override public void update() { if (world() != null && world().getGameTime() % 20 == 0) Network.updateWirelessNetwork(this); }
    @Override public void onConnect(Node node) { if (node == node()) Network.joinWirelessNetwork(this); }
    @Override public void onDisconnect(Node node) {
        super.onDisconnect(node);
        if (node == node() || world() == null || !world().hasChunkAt(new BlockPos(x(), y(), z()))) Network.leaveWirelessNetwork(this);
    }
    @Override public synchronized void load(CompoundTag tag) {
        super.load(tag); strength = tag.contains("strength") ? Math.max(0, Math.min(maxRange(), tag.getDouble("strength"))) : maxRange();
    }
    @Override public synchronized void save(CompoundTag tag) { super.save(tag); tag.putDouble("strength", strength); }
    @Override public Map<String,String> getDeviceInfo() {
        return Map.of("class","network","description","Wireless ethernet controller","vendor","MightyPirates GmbH & Co. KG",
                "product",wirelessTier == 0 ? "39i110 (LPPW-01)" : "62i230 (MPW-01)","version",wirelessTier == 0 ? "1.0" : "2.0",
                "capacity",Integer.toString(Settings.get().maxNetworkPacketSize),"size",Integer.toString(maxOpenPorts()),"width",Double.toString(maxRange()));
    }
}
