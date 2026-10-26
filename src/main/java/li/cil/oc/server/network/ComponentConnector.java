package li.cil.oc.server.network;

import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Visibility;
import net.minecraft.nbt.CompoundTag;
import java.util.Collection;

/** Both facets share one graph vertex and one persisted node address. */
public final class ComponentConnector extends Connector implements li.cil.oc.api.network.ComponentConnector {
    private final Component component;
    ComponentConnector(Environment host, Visibility reachability, String name, Visibility visibility, double capacity) { super(host, reachability, capacity); component = new Component(host, reachability, name, visibility); }
    public String name() { return component.name(); }
    public Visibility visibility() { return component.visibility(); }
    private void sync() { component.graph = graph; component.address = address; component.edges.clear(); component.edges.addAll(edges); }
    public void setVisibility(Visibility value) {
        if (value.ordinal() > reachability.ordinal()) throw new IllegalArgumentException("Component visibility exceeds node reachability");
        Visibility previous = component.visibility();
        if (graph != null && previous != value) for (li.cil.oc.api.network.Node other : graph.nodes()) {
            if (other != this && other.host() instanceof Network.ComponentListener listener) {
                boolean before = canBeSeenFrom(other);
                boolean after = value == Visibility.Network ? canBeReachedFrom(other) : value == Visibility.Neighbors && isNeighborOf(other);
                if (before && !after) listener.removeComponent(this);
                if (!before && after) listener.addComponent(this);
            }
        }
        component.graph = null; component.setVisibility(value);
    }
    public boolean canBeSeenFrom(li.cil.oc.api.network.Node other) { return switch (visibility()) { case None -> false; case Neighbors -> isNeighborOf(other); case Network -> canBeReachedFrom(other); }; }
    public Collection<String> methods() { return component.methods(); }
    public Callback annotation(String method) { return component.annotation(method); }
    public Object[] invoke(String method, Context context, Object... arguments) throws Exception { return component.invoke(method, context, arguments); }
    public void load(CompoundTag nbt) { super.load(nbt); if (nbt.contains("visibility")) { int value = nbt.getInt("visibility"); if (value >= 0 && value <= reachability.ordinal()) component.setVisibility(Visibility.values()[value]); } }
    public void save(CompoundTag nbt) { super.save(nbt); nbt.putInt("visibility", visibility().ordinal()); }
}
