package li.cil.oc.server.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import li.cil.oc.api.network.Packet;

/** Original channel-based, dimension-independent linked-card transport. */
public final class QuantumNetwork {
    public interface QuantumNode {
        String tunnel();
        void receivePacket(Packet packet);
    }
    private static final Map<String, WeakHashMap<QuantumNode, Boolean>> TUNNELS = new HashMap<>();
    private QuantumNetwork() {}
    public static synchronized void add(QuantumNode card) {
        TUNNELS.computeIfAbsent(card.tunnel(), key -> new WeakHashMap<>()).put(card, Boolean.TRUE);
    }
    public static synchronized void remove(QuantumNode card) {
        // Also handles a host changing its channel before removing the old registration.
        TUNNELS.values().forEach(nodes -> nodes.remove(card));
        TUNNELS.values().removeIf(Map::isEmpty);
    }
    public static synchronized List<QuantumNode> getEndpoints(String tunnel) {
        var nodes = TUNNELS.get(tunnel);
        return nodes == null ? List.of() : new ArrayList<>(nodes.keySet());
    }
}
