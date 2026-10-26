package li.cil.oc.server.network;

import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.server.machine.Callbacks;
import net.minecraft.nbt.CompoundTag;
import java.util.Collection;

public class Component extends Node implements li.cil.oc.api.network.Component {
    private final String name;
    private Visibility visibility;
    Component(Environment host, Visibility reachability, String name, Visibility visibility) {
        super(host, reachability); this.name = java.util.Objects.requireNonNull(name);
        if (visibility.ordinal() > reachability.ordinal()) throw new IllegalArgumentException("Component visibility exceeds node reachability");
        this.visibility = visibility;
    }
    public String name() { return name; }
    public Visibility visibility() { return visibility; }
    public void setVisibility(Visibility value) {
        if (value.ordinal() > reachability.ordinal()) throw new IllegalArgumentException("Component visibility exceeds node reachability");
        if (visibility == value) return;
        if (graph != null) for (li.cil.oc.api.network.Node node : graph.nodes()) {
            if (node != this && node.host() instanceof Network.ComponentListener listener) {
                boolean wasVisible = canBeSeenFrom(node);
                boolean nowVisible = value == Visibility.Network ? canBeReachedFrom(node) : value == Visibility.Neighbors && isNeighborOf(node);
                if (wasVisible && !nowVisible) listener.removeComponent(this);
                if (!wasVisible && nowVisible) listener.addComponent(this);
            }
        }
        visibility = value;
    }
    public boolean canBeSeenFrom(li.cil.oc.api.network.Node other) { return switch (visibility) { case None -> false; case Neighbors -> isNeighborOf(other); case Network -> canBeReachedFrom(other); }; }
    public Collection<String> methods() { return Callbacks.methods(host); }
    public Callback annotation(String method) { return Callbacks.annotation(host, method); }
    public Object[] invoke(String method, Context context, Object... arguments) throws Exception { return Callbacks.invoke(host, method, context, arguments); }
    public void load(CompoundTag nbt) { super.load(nbt); if (nbt.contains("visibility")) { int value = nbt.getInt("visibility"); if (value >= 0 && value <= reachability.ordinal()) visibility = Visibility.values()[value]; } }
    public void save(CompoundTag nbt) { super.save(nbt); nbt.putInt("visibility", visibility.ordinal()); }
}
