package li.cil.oc.server.network;

import li.cil.oc.Settings;
import li.cil.oc.api.detail.Builder;
import li.cil.oc.api.detail.NetworkAPI;
import li.cil.oc.api.internal.Colored;
import li.cil.oc.api.network.*;
import li.cil.oc.common.capabilities.Capabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class Network implements NetworkAPI {
    public static final Network INSTANCE = new Network();
    public interface ComponentListener { void addComponent(li.cil.oc.api.network.Component component); void removeComponent(li.cil.oc.api.network.Component component); }
    /** Optional geometry provider for multipart hosts; no absent-mod classes are linked. */
    public interface SideConnectivity { boolean isNetworkSideOpen(Direction side); }
    public void joinOrCreateNetwork(BlockEntity entity) { if (entity != null && entity.getLevel() != null) joinOrCreateNetwork(entity.getLevel(), entity.getBlockPos()); }
    public void joinOrCreateNetwork(BlockGetter access, BlockPos pos) {
        BlockEntity entity = access.getBlockEntity(pos);
        if (entity == null || entity.isRemoved() || entity.getLevel() == null || entity.getLevel().isClientSide) return;
        Level level = entity.getLevel();
        for (Direction side : Direction.values()) {
            li.cil.oc.api.network.Node local = getNetworkNode(entity, side);
            if (local == null) continue;
            BlockPos adjacent = pos.relative(side);
            if (level.hasChunkAt(adjacent)) {
                BlockEntity other = level.getBlockEntity(adjacent);
                li.cil.oc.api.network.Node remote = getNetworkNode(other, side.getOpposite());
                if (remote != null && remote != local && remote.network() != null) {
                    boolean colors = connectionColor(entity) == connectionColor(other) || connectionColor(entity) == wildcardColor() || connectionColor(other) == wildcardColor();
                    boolean geometry = (!(entity instanceof SideConnectivity c) || c.isNetworkSideOpen(side)) && (!(other instanceof SideConnectivity otherConnectivity) || otherConnectivity.isNetworkSideOpen(side.getOpposite()));
                    if (colors && geometry) remote.connect(local); else local.disconnect(remote);
                }
            }
            if (local.network() == null) joinNewNetwork(local);
        }
    }
    public static li.cil.oc.api.network.Node getNetworkNode(BlockEntity entity, Direction side) {
        if (entity == null || entity.getLevel() == null) return null;
        Level level = entity.getLevel();
        SidedEnvironment sided = level.getCapability(Capabilities.SidedEnvironmentCapability, entity.getBlockPos(), side);
        if (sided != null) return sided.sidedNode(side);
        Environment environment = level.getCapability(Capabilities.EnvironmentCapability, entity.getBlockPos(), side);
        return environment == null ? null : environment.node();
    }
    private static int wildcardColor() { return 0xABABAB; }
    private static int connectionColor(BlockEntity entity) {
        if (entity != null && entity.getLevel() != null) {
            Colored colored = entity.getLevel().getCapability(Capabilities.ColoredCapability, entity.getBlockPos(), null);
            if (colored != null && colored.controlsConnectivity()) return colored.getColor();
        }
        return wildcardColor();
    }
    public void joinNewNetwork(li.cil.oc.api.network.Node node) { if (!(node instanceof Node own)) throw new IllegalArgumentException("Node was not created by OpenComputers"); if (own.graph == null) new NetworkGraph(own); }
    public void joinWirelessNetwork(WirelessEndpoint endpoint) { WirelessNetwork.add(endpoint); }
    public void updateWirelessNetwork(WirelessEndpoint endpoint) { WirelessNetwork.update(endpoint); }
    public void leaveWirelessNetwork(WirelessEndpoint endpoint) { WirelessNetwork.remove(endpoint); }
    public void leaveWirelessNetwork(WirelessEndpoint endpoint, ResourceKey<Level> dimension) { WirelessNetwork.remove(endpoint, dimension); }
    public void sendWirelessPacket(WirelessEndpoint source, double strength, li.cil.oc.api.network.Packet packet) { for (WirelessEndpoint target : WirelessNetwork.computeReachableFrom(source, strength)) target.receivePacket(packet, source); }
    public Builder.NodeBuilder newNode(Environment host, Visibility reachability) { return new BasicBuilder(host, reachability); }
    public li.cil.oc.api.network.Packet newPacket(String source, String destination, int port, Object[] data) {
        var packet = new NetworkPacket(source, destination, port, data);
        if (packet.size() > Settings.get().maxNetworkPacketSize) throw new IllegalArgumentException("packet too big (max " + Settings.get().maxNetworkPacketSize + ")");
        return packet;
    }
    public li.cil.oc.api.network.Packet newPacket(CompoundTag nbt) { return NetworkPacket.load(nbt); }
    private record BasicBuilder(Environment host, Visibility reachability) implements Builder.NodeBuilder {
        public Node create() { return li.cil.oc.util.SideTracker.isServer() ? new Node(host, reachability) : null; }
        public Builder.ComponentBuilder withComponent(String name, Visibility visibility) { return new Network.ComponentBuilder(host, reachability, name, visibility); }
        public Builder.ComponentBuilder withComponent(String name) { return withComponent(name, reachability); }
        public Builder.ConnectorBuilder withConnector(double capacity) { return new Network.ConnectorBuilder(host, reachability, capacity); }
        public Builder.ConnectorBuilder withConnector() { return withConnector(0); }
    }
    private record ComponentBuilder(Environment host, Visibility reachability, String name, Visibility visibility) implements Builder.ComponentBuilder {
        public Component create() { return li.cil.oc.util.SideTracker.isServer() ? new Component(host, reachability, name, visibility) : null; }
        public Builder.ComponentConnectorBuilder withConnector(double capacity) { return new BothBuilder(host, reachability, name, visibility, capacity); }
        public Builder.ComponentConnectorBuilder withConnector() { return withConnector(0); }
    }
    private record ConnectorBuilder(Environment host, Visibility reachability, double capacity) implements Builder.ConnectorBuilder {
        public Connector create() { return li.cil.oc.util.SideTracker.isServer() ? new Connector(host, reachability, capacity) : null; }
        public Builder.ComponentConnectorBuilder withComponent(String name, Visibility visibility) { return new BothBuilder(host, reachability, name, visibility, capacity); }
        public Builder.ComponentConnectorBuilder withComponent(String name) { return withComponent(name, reachability); }
    }
    private record BothBuilder(Environment host, Visibility reachability, String name, Visibility visibility, double capacity) implements Builder.ComponentConnectorBuilder {
        public ComponentConnector create() { return li.cil.oc.util.SideTracker.isServer() ? new ComponentConnector(host, reachability, name, visibility, capacity) : null; }
    }
}
