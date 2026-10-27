package li.cil.oc.server.component;

import li.cil.oc.OpenComputers;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class UpgradeLeash extends UpgradeBase {
    private static final int MAX_LEASHED_ENTITIES = 8;
    private final Entity entity;
    private final Set<UUID> leashed = new HashSet<>();
    private boolean restore;

    public UpgradeLeash(EnvironmentHost host) {
        super(host, "leash", "Leash", "FlockControl (FC-3LS)", false);
        entity = (Entity) host;
        ((Component) node()).setVisibility(Visibility.Network);
    }

    @Override public Map<String, String> getDeviceInfo() {
        Map<String, String> info = new HashMap<>(super.getDeviceInfo());
        info.put("capacity", Integer.toString(MAX_LEASHED_ENTITIES));
        return info;
    }

    @Callback(doc = "function(side:number):boolean -- Tries to put an entity on the specified side of the device onto a leash.")
    public Object[] leash(Context context, Arguments args) {
        if (leashed.size() >= MAX_LEASHED_ENTITIES) return new Object[]{null, "too many leashed entities"};
        int value = args.checkInteger(0);
        if (value < 0 || value > 5) throw new IllegalArgumentException("invalid side");
        Direction side = Direction.from3DDataValue(value);
        AABB bounds = new AABB(position()).minmax(new AABB(position().relative(side, 2)));
        for (Mob mob : host.world().getEntitiesOfClass(Mob.class, bounds, Mob::canHaveALeashAttachedToIt)) {
            mob.setLeashedTo(entity, true);
            leashed.add(mob.getUUID());
            changed();
            context.pause(0.1);
            return new Object[]{true};
        }
        return new Object[]{null, "no unleashed entity"};
    }

    @Callback(doc = "function() -- Unleashes all currently leashed entities.")
    public Object[] unleash(Context context, Arguments args) {
        unleashAll();
        return null;
    }

    private void unleashAll() {
        for (Mob mob : host.world().getEntitiesOfClass(Mob.class, new AABB(position()).inflate(5))) {
            if (leashed.contains(mob.getUUID()) && mob.getLeashHolder() == entity) mob.dropLeash(true, false);
        }
        leashed.clear();
        changed();
    }

    @Override public boolean canUpdate() { return true; }

    @Override public void update() {
        if (!restore) return;
        restore = false;
        Set<UUID> found = new HashSet<>();
        for (Mob mob : host.world().getEntitiesOfClass(Mob.class, new AABB(position()).inflate(5))) {
            if (leashed.contains(mob.getUUID())) {
                mob.setLeashedTo(entity, true);
                found.add(mob.getUUID());
            }
        }
        int missing = leashed.size() - found.size();
        if (missing > 0) OpenComputers.LOGGER.info("Could not find {} leashed entities after loading!", missing);
        leashed.retainAll(found);
        changed();
    }

    @Override public void onDisconnect(Node disconnected) {
        super.onDisconnect(disconnected);
        if (disconnected == node()) unleashAll();
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        leashed.clear();
        ListTag list = tag.getList("leashedEntities", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) leashed.add(UUID.fromString(list.getString(i)));
        restore = true;
    }

    @Override public void save(CompoundTag tag) {
        super.save(tag);
        ListTag list = new ListTag();
        for (UUID id : leashed) list.add(StringTag.valueOf(id.toString()));
        tag.put("leashedEntities", list);
    }
}
