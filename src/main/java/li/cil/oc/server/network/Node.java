package li.cil.oc.server.network;

import li.cil.oc.OpenComputers;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Visibility;
import net.minecraft.nbt.CompoundTag;
import java.util.List;

/** A component-network vertex. Graph ownership and addresses are managed internally. */
public class Node implements li.cil.oc.api.network.Node {
    final Environment host;
    final Visibility reachability;
    String address;
    NetworkGraph graph;
    final java.util.Set<Node> edges = new java.util.LinkedHashSet<>();
    Node(Environment host, Visibility reachability) { this.host = java.util.Objects.requireNonNull(host); this.reachability = java.util.Objects.requireNonNull(reachability); }
    public Environment host() { return host; }
    public Visibility reachability() { return reachability; }
    public String address() { return address; }
    public li.cil.oc.api.network.Network network() { return graph; }
    public boolean isNeighborOf(li.cil.oc.api.network.Node other) { return graph != null && other instanceof Node node && node.graph == graph && edges.contains(node); }
    public boolean canBeReachedFrom(li.cil.oc.api.network.Node other) { return graph != null && other != null && other.network() == graph && switch (reachability) { case None -> false; case Neighbors -> isNeighborOf(other); case Network -> true; }; }
    public Iterable<li.cil.oc.api.network.Node> neighbors() { return graph == null ? List.of() : graph.neighbors(this); }
    public Iterable<li.cil.oc.api.network.Node> reachableNodes() { return graph == null ? List.of() : graph.nodes(this); }
    public void connect(li.cil.oc.api.network.Node node) { if (graph != null) graph.connect(this, node); }
    public void disconnect(li.cil.oc.api.network.Node node) { if (graph != null && node != null && node.network() == graph) graph.disconnect(this, node); }
    public void remove() { if (graph != null) graph.remove(this); }
    public void sendToAddress(String target, String name, Object... data) { if (graph != null) graph.sendToAddress(this, target, name, data); }
    public void sendToNeighbors(String name, Object... data) { if (graph != null) graph.sendToNeighbors(this, name, data); }
    public void sendToReachable(String name, Object... data) { if (graph != null) graph.sendToReachable(this, name, data); }
    public void sendToVisible(String name, Object... data) { if (graph != null) graph.sendToVisible(this, name, data); }
    public void load(CompoundTag nbt) { String saved = nbt.getString("address"); if (!saved.isEmpty() && !saved.equals(address)) { if (graph == null) address = saved; else graph.remap(this, saved); } }
    public void save(CompoundTag nbt) { if (address != null) nbt.putString("address", address); }
    void connected(Node other) { try { host.onConnect(other); } catch (Throwable error) { OpenComputers.LOGGER.warn("Component {} failed while connecting", host.getClass().getName(), error); } }
    void disconnected(Node other) { try { host.onDisconnect(other); } catch (Throwable error) { OpenComputers.LOGGER.warn("Component {} failed while disconnecting", host.getClass().getName(), error); } }
    @Override public String toString() { return "Node(" + address + ", " + host + ")"; }
}
