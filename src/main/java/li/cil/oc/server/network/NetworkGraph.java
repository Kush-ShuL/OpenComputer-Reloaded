package li.cil.oc.server.network;

import li.cil.oc.Settings;
import li.cil.oc.api.network.Visibility;
import java.util.*;

/** The original undirected graph model, with reachability filtered independently of edges. */
final class NetworkGraph implements li.cil.oc.api.network.Network {
    final LinkedHashMap<String, Node> members = new LinkedHashMap<>();
    double buffer, capacity;
    NetworkGraph() {}
    NetworkGraph(Node initial) { insert(initial); initial.connected(initial); }
    private static Node own(li.cil.oc.api.network.Node node) {
        if (!(Objects.requireNonNull(node) instanceof Node result)) throw new IllegalArgumentException("Node was not created by OpenComputers");
        return result;
    }
    private void insert(Node node) {
        while (node.address == null || members.containsKey(node.address)) node.address = UUID.randomUUID().toString();
        members.put(node.address, node); node.graph = this; refreshEnergy();
    }
    private boolean contains(Node node) { return node.graph == this && members.get(node.address) == node; }
    private void require(Node node) { if (!contains(node)) throw new IllegalArgumentException("Node must belong to this network"); }
    private static Set<Node> reachable(Node source) {
        Set<Node> result = new LinkedHashSet<>();
        if (source.graph != null) for (Node target : source.graph.members.values()) if (target != source && target.canBeReachedFrom(source)) result.add(target);
        return result;
    }
    private static Map<Node, Set<Node>> snapshot(Collection<Node> nodes) {
        Map<Node, Set<Node>> result = new LinkedHashMap<>();
        for (Node node : nodes) result.put(node, reachable(node));
        return result;
    }
    private static void notifyDifference(Map<Node, Set<Node>> before, boolean added, Node removed) {
        for (var entry : before.entrySet()) {
            Node observer = entry.getKey();
            if (observer == removed || observer.graph == null) continue;
            Set<Node> after = reachable(observer);
            Set<Node> difference = new LinkedHashSet<>(added ? after : entry.getValue());
            difference.removeAll(added ? entry.getValue() : after);
            for (Node changed : difference) { if (added) observer.connected(changed); else observer.disconnected(changed); }
        }
    }
    public boolean connect(li.cil.oc.api.network.Node nodeA, li.cil.oc.api.network.Node nodeB) {
        Node a = own(nodeA), b = own(nodeB);
        if (a == b) throw new IllegalArgumentException("Cannot connect a node to itself");
        if (!contains(a) && !contains(b)) throw new IllegalArgumentException("At least one node must belong to this network");
        if (!contains(a)) { Node temporary = a; a = b; b = temporary; }
        if (a.edges.contains(b)) return false;
        LinkedHashSet<Node> combined = new LinkedHashSet<>(members.values());
        NetworkGraph other = b.graph;
        if (other == null) combined.add(b); else combined.addAll(other.members.values());
        Map<Node, Set<Node>> before = snapshot(combined);
        boolean newNode = other == null;
        if (other != this) {
            Collection<Node> incoming = other == null ? List.of(b) : new ArrayList<>(other.members.values());
            for (Node node : incoming) insert(node);
            if (other != null) { other.members.clear(); other.refreshEnergy(); }
        }
        a.edges.add(b); b.edges.add(a); refreshEnergy();
        if (newNode) b.connected(b);
        notifyDifference(before, true, null);
        return true;
    }
    public boolean disconnect(li.cil.oc.api.network.Node nodeA, li.cil.oc.api.network.Node nodeB) {
        Node a = own(nodeA), b = own(nodeB);
        if (a == b) throw new IllegalArgumentException("Cannot disconnect a node from itself");
        require(a); require(b);
        if (!a.edges.contains(b)) return false;
        Map<Node, Set<Node>> before = snapshot(new ArrayList<>(members.values()));
        a.edges.remove(b); b.edges.remove(a); repartition();
        notifyDifference(before, false, null);
        return true;
    }
    public boolean remove(li.cil.oc.api.network.Node value) {
        Node node = own(value);
        if (!contains(node)) return false;
        Map<Node, Set<Node>> before = snapshot(new ArrayList<>(members.values()));
        members.remove(node.address);
        for (Node neighbor : new ArrayList<>(node.edges)) neighbor.edges.remove(node);
        node.edges.clear(); node.graph = null; repartition();
        node.disconnected(node);
        notifyDifference(before, false, node);
        return true;
    }
    private void repartition() {
        Set<Node> unseen = new LinkedHashSet<>(members.values());
        List<LinkedHashSet<Node>> components = new ArrayList<>();
        while (!unseen.isEmpty()) {
            Node seed = unseen.iterator().next();
            LinkedHashSet<Node> component = new LinkedHashSet<>();
            ArrayDeque<Node> queue = new ArrayDeque<>(); queue.add(seed); unseen.remove(seed);
            while (!queue.isEmpty()) { Node current = queue.remove(); component.add(current); for (Node next : current.edges) if (unseen.remove(next)) queue.add(next); }
            components.add(component);
        }
        members.clear(); refreshEnergy();
        boolean first = true;
        for (Set<Node> component : components) {
            NetworkGraph target = first ? this : new NetworkGraph(); first = false;
            for (Node node : component) { target.members.put(node.address, node); node.graph = target; }
            target.refreshEnergy();
        }
    }
    void remap(Node node, String address) {
        List<Node> neighbors = new ArrayList<>(node.edges);
        remove(node); node.address = address;
        new NetworkGraph(node);
        for (Node neighbor : neighbors) if (neighbor.graph != null) neighbor.connect(node);
    }
    public li.cil.oc.api.network.Node node(String address) { return members.get(address); }
    public Iterable<li.cil.oc.api.network.Node> nodes() { return new ArrayList<li.cil.oc.api.network.Node>(members.values()); }
    public Iterable<li.cil.oc.api.network.Node> nodes(li.cil.oc.api.network.Node reference) { Node node = own(reference); require(node); return new ArrayList<li.cil.oc.api.network.Node>(reachable(node)); }
    public Iterable<li.cil.oc.api.network.Node> neighbors(li.cil.oc.api.network.Node reference) { Node node = own(reference); require(node); return new ArrayList<li.cil.oc.api.network.Node>(node.edges); }
    private void send(Node source, Collection<Node> targets, String name, Object[] data) {
        require(source);
        Message message = new Message(source, name, data);
        for (Node target : new ArrayList<>(targets)) { if (message.canceled) break; target.host.onMessage(message); }
    }
    public void sendToAddress(li.cil.oc.api.network.Node source, String target, String name, Object... data) {
        Node sender = own(source); require(sender); Node receiver = members.get(target);
        if (receiver != null && receiver.canBeReachedFrom(sender)) send(sender, List.of(receiver), name, data);
    }
    public void sendToNeighbors(li.cil.oc.api.network.Node source, String name, Object... data) { Node sender = own(source); send(sender, sender.edges.stream().filter(n -> n.reachability != Visibility.None).toList(), name, data); }
    public void sendToReachable(li.cil.oc.api.network.Node source, String name, Object... data) { Node sender = own(source); send(sender, reachable(sender), name, data); }
    public void sendToVisible(li.cil.oc.api.network.Node source, String name, Object... data) { Node sender = own(source); send(sender, reachable(sender).stream().filter(n -> n instanceof li.cil.oc.api.network.Component c && c.canBeSeenFrom(sender)).toList(), name, data); }
    synchronized void refreshEnergy() { buffer = 0; capacity = 0; for (Node node : members.values()) if (node instanceof Connector connector) { buffer += connector.buffer; capacity += connector.capacity; } }
    synchronized double changeBuffer(double delta) {
        if (delta == 0) return 0;
        if (Settings.get().ignorePower) return delta < 0 ? 0 : delta;
        double previous = buffer;
        buffer = Math.min(Math.max(buffer + delta, 0), capacity);
        if (buffer == previous) return delta;
        if (delta < 0) {
            double remaining = -delta;
            for (Node node : members.values()) if (remaining > 0 && node instanceof Connector connector && connector.buffer > 0) {
                if (connector.buffer < remaining) {
                    remaining -= connector.buffer;
                    connector.buffer = 0;
                } else {
                    connector.buffer -= remaining;
                    // The connector fulfilled the whole request. Subtracting its
                    // rounded before/after values would invent a residual debt.
                    remaining = 0;
                }
            }
            return -remaining;
        } else {
            double remaining = delta;
            for (Node node : members.values()) if (remaining > 0 && node instanceof Connector connector && connector.buffer < connector.capacity) {
                double space = connector.capacity - connector.buffer;
                if (space < remaining) {
                    remaining -= space;
                    connector.buffer = connector.capacity;
                } else {
                    connector.buffer += remaining;
                    remaining = 0;
                }
            }
            return remaining;
        }
    }
    private static final class Message implements li.cil.oc.api.network.Message {
        final Node source; final String name; final Object[] data; boolean canceled;
        Message(Node source, String name, Object[] data) { this.source = source; this.name = name; this.data = data; }
        public li.cil.oc.api.network.Node source() { return source; }
        public String name() { return name; }
        public Object[] data() { return data; }
        public void cancel() { canceled = true; }
    }
}
