package li.cil.oc.server.network;
import li.cil.oc.api.network.Packet;
import java.util.*;
public final class QuantumNetwork {
    private static final Map<String, Map<QuantumNode, Boolean>> TUNNELS = new HashMap<>();
    public interface QuantumNode { String tunnel(); void receivePacket(Packet packet); }
    public static synchronized void add(QuantumNode node) { TUNNELS.computeIfAbsent(node.tunnel(), ignored -> new WeakHashMap<>()).put(node, Boolean.TRUE); }
    public static synchronized void remove(QuantumNode node) { Map<QuantumNode,Boolean> nodes=TUNNELS.get(node.tunnel());if(nodes!=null){nodes.remove(node);if(nodes.isEmpty())TUNNELS.remove(node.tunnel());} }
    public static synchronized Collection<QuantumNode> getEndpoints(String tunnel) { Map<QuantumNode,Boolean> nodes=TUNNELS.get(tunnel);return nodes==null?List.of():new ArrayList<>(nodes.keySet()); }
}
