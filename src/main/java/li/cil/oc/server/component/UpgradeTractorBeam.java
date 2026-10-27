package li.cil.oc.server.component;

import li.cil.oc.Settings;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.internal.Drone;
import li.cil.oc.util.InventoryUtils;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.List;

public final class UpgradeTractorBeam extends UpgradeBase {
    public UpgradeTractorBeam(EnvironmentHost host) {
        super(host, "tractor_beam", "Tractor beam", "T313-K1N.3515", false);
        ((Component) node()).setVisibility(Visibility.Network);
    }

    @Callback(doc = "function():boolean -- Tries to pick up a random item in the robots' vicinity.")
    public Object[] suck(Context context, Arguments args) {
        var level = host.world();
        var entities = level.getEntitiesOfClass(ItemEntity.class, new AABB(position()).inflate(3),
                entity -> entity.isAlive() && !entity.hasPickUpDelay());
        if (entities.isEmpty()) return new Object[]{false};
        ItemEntity entity = entities.get(level.random.nextInt(entities.size()));
        ItemStack stack = entity.getItem();
        int before = stack.getCount();
        if (host instanceof Drone drone) {
            List<Integer> slots = new ArrayList<>();
            int size = drone.mainInventory().getContainerSize();
            for (int slot = drone.selectedSlot(); slot < size; slot++) slots.add(slot);
            for (int slot = 0; slot < drone.selectedSlot(); slot++) slots.add(slot);
            InventoryUtils.insertIntoInventory(stack, InventoryUtils.asItemHandler(drone.mainInventory()), 64, false, slots);
        } else entity.playerTouch(fakePlayer());
        if (stack.getCount() < before || !entity.isAlive()) {
            context.pause(Settings.get().suckDelay);
            level.levelEvent(2003, entity.blockPosition(), 0);
            changed();
            return new Object[]{true};
        }
        return new Object[]{false};
    }
}
