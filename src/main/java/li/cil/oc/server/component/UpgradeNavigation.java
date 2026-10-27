package li.cil.oc.server.component;

import li.cil.oc.Settings;
import li.cil.oc.api.internal.Rotatable;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.*;
import li.cil.oc.common.item.data.NavigationUpgradeData;
import li.cil.oc.server.network.Waypoints;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class UpgradeNavigation extends UpgradeBase {
    private final NavigationUpgradeData data = new NavigationUpgradeData();

    public UpgradeNavigation(EnvironmentHost host) {
        super(host, "navigation", "Navigation upgrade", "PathFinder v3", true);
        ((Component) node()).setVisibility(Visibility.Neighbors);
    }

    @Override public Map<String, String> getDeviceInfo() {
        Map<String, String> info = new HashMap<>(super.getDeviceInfo());
        info.put("capacity", Integer.toString(data.getSize(host.world())));
        return info;
    }

    @Callback(doc = "function():number, number, number -- Get the current relative position of the robot.")
    public Object[] getPosition(Context context, Arguments args) {
        var map = data.mapData(host.world());
        int size = data.getSize(host.world());
        double x = host.xPosition() - map.centerX, z = host.zPosition() - map.centerZ;
        return Math.abs(x) <= size / 2d && Math.abs(z) <= size / 2d
            ? new Object[]{x, host.yPosition(), z} : new Object[]{null, "out of range"};
    }

    @Callback(doc = "function():number -- Get the current orientation of the robot.")
    public Object[] getFacing(Context context, Arguments args) {
        return new Object[]{((Rotatable) host).facing().get3DDataValue()};
    }

    @Callback(doc = "function():number -- Get the operational range of the navigation upgrade.")
    public Object[] getRange(Context context, Arguments args) {
        return new Object[]{data.getSize(host.world()) / 2};
    }

    @Callback(doc = "function(range:number):table -- Find waypoints in the specified range.")
    public Object[] findWaypoints(Context context, Arguments args) {
        double range = Math.max(0, Math.min(Settings.get().maxWirelessRange[1], args.checkDouble(0)));
        if (range <= 0) return new Object[]{new Object[0]};
        if (!connector().tryChangeBuffer(-range * Settings.get().wirelessCostPerRange[1] * .25))
            return new Object[]{null, "not enough energy"};
        context.pause(.5);

        BlockPos origin = position();
        Vec3 here = Vec3.atCenterOf(origin);
        double rangeSq = range * range;
        List<Map<String, Object>> values = new ArrayList<>();
        for (var waypoint : Waypoints.findWaypoints(host.world(), origin, range)) {
            if (Vec3.atCenterOf(waypoint.getBlockPos()).distanceToSqr(here) > rangeSq) continue;
            Vec3 delta = Vec3.atCenterOf(waypoint.getBlockPos().relative(waypoint.facing())).subtract(here);
            Map<String, Object> value = new HashMap<>();
            value.put("position", new double[]{delta.x, delta.y, delta.z});
            value.put("redstone", waypoint.maxInput());
            value.put("label", waypoint.label());
            value.put("address", waypoint.node().address());
            values.add(value);
        }
        return new Object[]{values.toArray()};
    }

    @Override public void onMessage(Message message) {
        super.onMessage(message);
        TabletUseData use = TabletUseData.from(message);
        if (use == null) return;
        var map = data.mapData(host.world());
        use.result().putInt("posX", use.position().getX() - map.centerX);
        use.result().putInt("posY", use.position().getY());
        use.result().putInt("posZ", use.position().getZ() - map.centerZ);
    }

    @Override public void load(CompoundTag tag) {
        load(tag, host.world().registryAccess());
    }

    @Override public void save(CompoundTag tag) {
        save(tag, host.world().registryAccess());
    }

    @Override public void load(CompoundTag tag, HolderLookup.Provider registries) {
        super.load(tag);
        data.load(tag, registries);
    }

    @Override public void save(CompoundTag tag, HolderLookup.Provider registries) {
        super.save(tag);
        data.save(tag, registries);
    }
}
