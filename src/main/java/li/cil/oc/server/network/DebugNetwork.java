package li.cil.oc.server.network;
import li.cil.oc.api.network.Packet;
import java.util.*;
public final class DebugNetwork {
    private static final Map<DebugNode, Boolean> CARDS = new WeakHashMap<>();
    public interface DebugNode { String address(); void receivePacket(Packet packet); }
    public static synchronized void add(DebugNode node) { CARDS.put(node,Boolean.TRUE); }
    public static synchronized void remove(DebugNode node) { CARDS.remove(node); }
    public static synchronized Optional<DebugNode> getEndpoint(String tunnel) { return CARDS.keySet().stream().filter(node->Objects.equals(node.address(),tunnel)).findFirst(); }
}
